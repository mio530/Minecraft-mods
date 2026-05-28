package de.mio.visionmod.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoFog: pushes fog start/end far away after MC sets them each frame. */
@Mixin(FogRenderer.class)
public class FogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private static void visionmod_noFog(Camera camera, FogRenderer.FogMode fogMode,
                                         float renderDistance, boolean isFoggy,
                                         float partialTick, CallbackInfo ci) {
        if (VisionConfig.get().noFogEnabled) {
            RenderSystem.setShaderFogStart(renderDistance * 2.0f);
            RenderSystem.setShaderFogEnd(renderDistance * 3.0f);
        }
    }
}
