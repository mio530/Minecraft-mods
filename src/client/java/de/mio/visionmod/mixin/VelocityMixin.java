package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anti-Knockback: cancels incoming knockback for the local player. */
@Mixin(LivingEntity.class)
public class VelocityMixin {

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void visionmod_velocity(double strength, double x, double z, CallbackInfo ci) {
        if (!VisionConfig.get().velocityEnabled) return;
        if (!((Object) this instanceof LocalPlayer)) return;
        ci.cancel();
    }
}
