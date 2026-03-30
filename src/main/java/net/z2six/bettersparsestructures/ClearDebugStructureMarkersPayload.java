package net.z2six.bettersparsestructures;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearDebugStructureMarkersPayload() implements CustomPacketPayload {
    public static final ClearDebugStructureMarkersPayload INSTANCE = new ClearDebugStructureMarkersPayload();
    public static final Type<ClearDebugStructureMarkersPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Bettersparsestructures.MODID, "clear_debug_structure_markers")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearDebugStructureMarkersPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
            },
            ClearDebugStructureMarkersPayload::new
    );

    public ClearDebugStructureMarkersPayload(RegistryFriendlyByteBuf buffer) {
        this();
    }

    @Override
    public Type<ClearDebugStructureMarkersPayload> type() {
        return TYPE;
    }
}
