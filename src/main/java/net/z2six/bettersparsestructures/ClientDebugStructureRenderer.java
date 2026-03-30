package net.z2six.bettersparsestructures;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Bettersparsestructures.MODID, value = Dist.CLIENT)
public final class ClientDebugStructureRenderer {
    private static final Long2ObjectMap<DebugMarker> MARKERS = new Long2ObjectOpenHashMap<>();

    private static final int ACTION_BAR_RANGE_BLOCKS = 160;
    private static final int RENDER_RANGE_BLOCKS = 2048;
    private static final int MAX_MARKERS_PER_FRAME = 128;

    private static final float ACCEPTED_RED = 0.2F;
    private static final float ACCEPTED_GREEN = 1.0F;
    private static final float ACCEPTED_BLUE = 0.25F;
    private static final int ACCEPTED_TEXT_COLOR = 0x55FF55;

    private static final float REJECTED_RED = 1.0F;
    private static final float REJECTED_GREEN = 0.2F;
    private static final float REJECTED_BLUE = 0.2F;
    private static final int REJECTED_TEXT_COLOR = 0xFF5555;

    private ClientDebugStructureRenderer() {
    }

    public static void handleMarker(DebugStructureMarkerPayload payload) {
        long chunkKey = net.minecraft.world.level.ChunkPos.asLong(payload.chunkX(), payload.chunkZ());
        DebugMarker current = MARKERS.get(chunkKey);
        if (current == null || payload.markerType() > current.markerType()) {
            MARKERS.put(chunkKey, new DebugMarker(payload.markerType(), payload.structureId()));
        }
    }

    public static void clearMarkers() {
        MARKERS.clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearMarkers();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientConfig.showDebugStructureMarkers() || MARKERS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }

        DebugMarkerDistance nearestMarker = findNearestMarker(minecraft.player.getX(), minecraft.player.getZ(), ACTION_BAR_RANGE_BLOCKS);
        if (nearestMarker != null) {
            minecraft.gui.setOverlayMessage(buildOverlayMessage(nearestMarker), false);
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ) {
        if (!ClientConfig.showDebugStructureMarkers() || MARKERS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }

        double rangeSquared = RENDER_RANGE_BLOCKS * RENDER_RANGE_BLOCKS;
        int maxY = level.getMaxBuildHeight() - 1;
        int rendered = 0;

        for (Long2ObjectMap.Entry<DebugMarker> entry : MARKERS.long2ObjectEntrySet()) {
            if (rendered >= MAX_MARKERS_PER_FRAME) {
                break;
            }

            int chunkX = net.minecraft.world.level.ChunkPos.getX(entry.getLongKey());
            int chunkZ = net.minecraft.world.level.ChunkPos.getZ(entry.getLongKey());
            int blockX = chunkX * 16 + 8;
            int blockZ = chunkZ * 16 + 8;

            if (squaredHorizontalDistance(minecraft.player.getX(), minecraft.player.getZ(), blockX, blockZ) > rangeSquared) {
                continue;
            }

            if (!level.hasChunkAt(blockX, blockZ)) {
                continue;
            }

            int surfaceY = Math.max(level.getMinBuildHeight(), level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ));
            if (surfaceY >= maxY) {
                surfaceY = Math.max(level.getMinBuildHeight(), maxY - 1);
            }

            renderMarker(poseStack, bufferSource, camX, camY, camZ, entry.getValue(), chunkX, chunkZ, blockX, blockZ, surfaceY, maxY);
            rendered++;
        }
    }

    private static void renderMarker(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            double camX,
            double camY,
            double camZ,
            DebugMarker marker,
            int chunkX,
            int chunkZ,
            int blockX,
            int blockZ,
            int surfaceY,
            int maxY
    ) {
        boolean accepted = marker.markerType() == DebugStructureMarkerService.MARKER_ACCEPTED;
        float red = accepted ? ACCEPTED_RED : REJECTED_RED;
        float green = accepted ? ACCEPTED_GREEN : REJECTED_GREEN;
        float blue = accepted ? ACCEPTED_BLUE : REJECTED_BLUE;

        AABB beam = new AABB(blockX - 1.75D - camX, surfaceY - camY, blockZ - 1.75D - camZ, blockX + 1.75D - camX, maxY - camY, blockZ + 1.75D - camZ);
        AABB base = new AABB(blockX - 3.5D - camX, surfaceY - camY, blockZ - 3.5D - camZ, blockX + 3.5D - camX, surfaceY + 3.0D - camY, blockZ + 3.5D - camZ);

        DebugRenderer.renderFilledBox(poseStack, bufferSource, beam, red, green, blue, 0.22F);

        DebugRenderer.renderFilledBox(poseStack, bufferSource, base, red, green, blue, 0.30F);

        String label = (accepted ? "ALLOWED " : "BLOCKED ") + marker.structureId() + " [" + chunkX + ", " + chunkZ + "]";
        DebugRenderer.renderFloatingText(
                poseStack,
                bufferSource,
                label,
                blockX + 0.5D,
                surfaceY + 4.5D,
                blockZ + 0.5D,
                accepted ? ACCEPTED_TEXT_COLOR : REJECTED_TEXT_COLOR,
                0.10F,
                true,
                0.0F,
                true
        );
    }

    private static DebugMarkerDistance findNearestMarker(double playerX, double playerZ, int rangeBlocks) {
        double rangeSquared = rangeBlocks * rangeBlocks;
        DebugMarkerDistance nearest = null;

        for (Long2ObjectMap.Entry<DebugMarker> entry : MARKERS.long2ObjectEntrySet()) {
            int chunkX = net.minecraft.world.level.ChunkPos.getX(entry.getLongKey());
            int chunkZ = net.minecraft.world.level.ChunkPos.getZ(entry.getLongKey());
            double markerX = chunkX * 16.0D + 8.0D;
            double markerZ = chunkZ * 16.0D + 8.0D;
            double distanceSquared = squaredHorizontalDistance(playerX, playerZ, markerX, markerZ);

            if (distanceSquared > rangeSquared) {
                continue;
            }

            if (nearest == null || distanceSquared < nearest.distanceSquared()) {
                nearest = new DebugMarkerDistance(entry.getValue(), chunkX, chunkZ, distanceSquared);
            }
        }

        return nearest;
    }

    private static Component buildOverlayMessage(DebugMarkerDistance markerDistance) {
        boolean accepted = markerDistance.marker().markerType() == DebugStructureMarkerService.MARKER_ACCEPTED;
        int distanceBlocks = (int) Math.round(Math.sqrt(markerDistance.distanceSquared()));

        return Component.literal(accepted ? "ALLOWED " : "BLOCKED ")
                .withStyle(accepted ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.literal(markerDistance.marker().structureId()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" [" + markerDistance.chunkX() + ", " + markerDistance.chunkZ() + "] " + distanceBlocks + "m").withStyle(ChatFormatting.GRAY));
    }

    private static double squaredHorizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private record DebugMarker(byte markerType, String structureId) {
    }

    private record DebugMarkerDistance(DebugMarker marker, int chunkX, int chunkZ, double distanceSquared) {
    }
}
