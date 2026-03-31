package net.z2six.bettersparsestructures;

import net.minecraft.network.FriendlyByteBuf;

public record DebugStructureMarkerPayload(int chunkX, int chunkZ, byte markerType, String structureId) {
    private static final int MAX_STRUCTURE_ID_LENGTH = 256;

    public static DebugStructureMarkerPayload decode(FriendlyByteBuf buffer) {
        return new DebugStructureMarkerPayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByte(),
                buffer.readUtf(MAX_STRUCTURE_ID_LENGTH)
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.chunkX);
        buffer.writeVarInt(this.chunkZ);
        buffer.writeByte(this.markerType);
        buffer.writeUtf(this.structureId, MAX_STRUCTURE_ID_LENGTH);
    }
}
