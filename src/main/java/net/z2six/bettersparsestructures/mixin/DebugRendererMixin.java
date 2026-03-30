package net.z2six.bettersparsestructures.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.z2six.bettersparsestructures.ClientDebugStructureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void bettersparsestructures$renderStructureMarkers(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            double camX,
            double camY,
            double camZ,
            CallbackInfo ci
    ) {
        ClientDebugStructureRenderer.render(poseStack, bufferSource, camX, camY, camZ);
    }
}
