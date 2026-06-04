package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoWeather: cancels rain/snow particle rendering. */
@Mixin(LevelRenderer.class)
public class WeatherRendererMixin {

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_noWeather(LightTexture lightTexture, float partialTick,
                                      double camX, double camY, double camZ, CallbackInfo ci) {
        if (VisionConfig.get().noWeatherEnabled) ci.cancel();
    }
}
