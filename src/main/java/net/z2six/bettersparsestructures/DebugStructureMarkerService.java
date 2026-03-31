package net.z2six.bettersparsestructures;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class DebugStructureMarkerService {
    public static final byte MARKER_REJECTED = 1;
    public static final byte MARKER_ACCEPTED = 2;

    private static final Map<ServerLevel, Long2ObjectMap<DebugMarker>> MARKERS = new WeakHashMap<>();

    private DebugStructureMarkerService() {
    }

    public static void recordMarker(ServerLevel level, ChunkPos chunkPos, String structureId, boolean accepted) {
        if (!ServerConfig.sendDebugStructureMarkers()) {
            return;
        }

        byte markerType = accepted ? MARKER_ACCEPTED : MARKER_REJECTED;
        DebugMarker markerToSync = null;
        boolean updated = false;

        synchronized (MARKERS) {
            Long2ObjectMap<DebugMarker> markers = MARKERS.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
            long chunkKey = chunkPos.toLong();
            DebugMarker current = markers.get(chunkKey);
            if (current == null || markerType > current.markerType()) {
                markerToSync = new DebugMarker(markerType, structureId);
                markers.put(chunkKey, markerToSync);
                updated = true;
            }
        }

        if (updated) {
            DebugStructureMarkerPayload payload = new DebugStructureMarkerPayload(chunkPos.x, chunkPos.z, markerToSync.markerType(), markerToSync.structureId());
            for (ServerPlayer serverPlayer : level.players()) {
                sendIfSupported(serverPlayer, payload);
            }
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            syncMarkers(serverPlayer);
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            syncMarkers(serverPlayer);
        }
    }

    private static void syncMarkers(ServerPlayer serverPlayer) {
        sendIfSupported(serverPlayer, ClearDebugStructureMarkersPayload.INSTANCE);

        if (!ServerConfig.sendDebugStructureMarkers()) {
            return;
        }

        ServerLevel level = serverPlayer.serverLevel();
        synchronized (MARKERS) {
            Long2ObjectMap<DebugMarker> markers = MARKERS.get(level);
            if (markers == null || markers.isEmpty()) {
                return;
            }

            for (Long2ObjectMap.Entry<DebugMarker> entry : markers.long2ObjectEntrySet()) {
                DebugMarker marker = entry.getValue();
                sendIfSupported(
                    serverPlayer,
                    new DebugStructureMarkerPayload(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), marker.markerType(), marker.structureId())
                );
            }
        }
    }

    public static void onServerConfigChanged(boolean previousDebugMarkersEnabled, boolean debugMarkersEnabled) {
        if (previousDebugMarkersEnabled == debugMarkersEnabled) {
            return;
        }

        Set<ServerPlayer> players = Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized (MARKERS) {
            for (ServerLevel level : MARKERS.keySet()) {
                players.addAll(level.players());
            }
        }

        for (ServerPlayer serverPlayer : players) {
            if (debugMarkersEnabled) {
                syncMarkers(serverPlayer);
            } else {
                sendIfSupported(serverPlayer, ClearDebugStructureMarkersPayload.INSTANCE);
            }
        }
    }

    private static void sendIfSupported(ServerPlayer serverPlayer, Object payload) {
        if (DebugStructureNetworking.CHANNEL.isRemotePresent(serverPlayer.connection.connection)) {
            DebugStructureNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), payload);
        }
    }

    private record DebugMarker(byte markerType, String structureId) {
    }
}
