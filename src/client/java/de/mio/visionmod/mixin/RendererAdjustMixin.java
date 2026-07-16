package de.mio.visionmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.render.RenderHacks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class RendererAdjustMixin {

    /** Zoom: override FOV when zoom key is held. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void visionmod_zoom(Camera camera, float partialTick, boolean useFov,
                                 CallbackInfoReturnable<Double> cir) {
        if (RenderHacks.zoomActive) {
            cir.setReturnValue((double) VisionConfig.get().zoomFov);
        }
    }

    /** NoHurtCam: suppress camera bob on damage. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_noHurtCam(PoseStack ps, float partialTick, CallbackInfo ci) {
        if (VisionConfig.get().noHurtCamEnabled) ci.cancel();
    }

    /** NoBob: suppress the walking view-bob. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_noBob(PoseStack ps, float partialTick, CallbackInfo ci) {
        if (VisionConfig.get().noBobEnabled) ci.cancel();
    }
}
