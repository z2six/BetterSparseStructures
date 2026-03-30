package net.z2six.bettersparsestructures;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DebugStructureMarkerPayload(int chunkX, int chunkZ, byte markerType, String structureId) implements CustomPacketPayload {
    private static final int MAX_STRUCTURE_ID_LENGTH = 256;

    public static final Type<DebugStructureMarkerPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Bettersparsestructures.MODID, "debug_structure_marker")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugStructureMarkerPayload> STREAM_CODEC = CustomPacketPayload.codec(
            DebugStructureMarkerPayload::write,
            DebugStructureMarkerPayload::new
    );

    public DebugStructureMarkerPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readByte(), buffer.readUtf(MAX_STRUCTURE_ID_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.chunkX);
        buffer.writeVarInt(this.chunkZ);
        buffer.writeByte(this.markerType);
        buffer.writeUtf(this.structureId, MAX_STRUCTURE_ID_LENGTH);
    }

    @Override
    public Type<DebugStructureMarkerPayload> type() {
        return TYPE;
    }
}
