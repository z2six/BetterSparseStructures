package net.z2six.bettersparsestructures;

import net.minecraft.network.FriendlyByteBuf;

public record ClearDebugStructureMarkersPayload() {
    public static final ClearDebugStructureMarkersPayload INSTANCE = new ClearDebugStructureMarkersPayload();

    public static ClearDebugStructureMarkersPayload decode(FriendlyByteBuf buffer) {
        return INSTANCE;
    }

    public void encode(FriendlyByteBuf buffer) {
    }
}
