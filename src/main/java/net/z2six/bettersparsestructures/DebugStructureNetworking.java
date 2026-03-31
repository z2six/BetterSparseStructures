package net.z2six.bettersparsestructures;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class DebugStructureNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Bettersparsestructures.MODID, "debug"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    private DebugStructureNetworking() {
    }

    public static void register() {
        int messageId = 0;
        CHANNEL.registerMessage(
                messageId++,
                DebugStructureMarkerPayload.class,
                DebugStructureMarkerPayload::encode,
                DebugStructureMarkerPayload::decode,
                DebugStructureNetworking::handleMarker
        );
        CHANNEL.registerMessage(
                messageId,
                ClearDebugStructureMarkersPayload.class,
                ClearDebugStructureMarkersPayload::encode,
                ClearDebugStructureMarkersPayload::decode,
                DebugStructureNetworking::handleClear
        );
    }

    private static void handleMarker(DebugStructureMarkerPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientDebugStructureRenderer.handleMarker(payload));
        context.setPacketHandled(true);
    }

    private static void handleClear(ClearDebugStructureMarkersPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(ClientDebugStructureRenderer::clearMarkers);
        context.setPacketHandled(true);
    }
}
