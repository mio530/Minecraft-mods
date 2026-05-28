package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoSlow: cancels the stuck-speed multiplier applied by cobwebs and berry bushes. */
@Mixin(Entity.class)
public class NoSlowMixin {

    @Inject(method = "setStuckSpeedMultiplier", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_noSlow(Vec3 multiplier, CallbackInfo ci) {
        if (VisionConfig.get().noSlowEnabled && (Object) this instanceof LocalPlayer) {
            ci.cancel();
        }
    }
}
