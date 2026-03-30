package net.z2six.bettersparsestructures;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DebugStructureNetworking {
    private DebugStructureNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(Bettersparsestructures.MODID).versioned("1").optional();
        registrar.playToClient(DebugStructureMarkerPayload.TYPE, DebugStructureMarkerPayload.STREAM_CODEC, DebugStructureNetworking::handleMarker);
        registrar.playToClient(ClearDebugStructureMarkersPayload.TYPE, ClearDebugStructureMarkersPayload.STREAM_CODEC, DebugStructureNetworking::handleClear);
    }

    private static void handleMarker(DebugStructureMarkerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDebugStructureRenderer.handleMarker(payload));
    }

    private static void handleClear(ClearDebugStructureMarkersPayload payload, IPayloadContext context) {
        context.enqueueWork(ClientDebugStructureRenderer::clearMarkers);
    }
}
