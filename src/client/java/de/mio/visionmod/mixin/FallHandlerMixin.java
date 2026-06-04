package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoFall backup: also resets fallDistance at the start of aiStep so damage never accumulates. */
@Mixin(LocalPlayer.class)
public class FallHandlerMixin {

    @Inject(method = "aiStep", at = @At("HEAD"), require = 0)
    private void visionmod_noFall(CallbackInfo ci) {
        if (VisionConfig.get().noFallEnabled) {
            ((LocalPlayer)(Object)this).fallDistance = 0f;
        }
    }
}
