package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Velocity: applies XZ and Y multipliers to incoming knockback for the local player. */
@Mixin(LivingEntity.class)
public class EntityMotionMixin {

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void visionmod_velocity(double strength, double x, double z, CallbackInfo ci) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.velocityEnabled) return;
        if (!((Object) this instanceof LocalPlayer self)) return;

        ci.cancel();

        // If both multipliers are 0, cancel entirely (original behaviour)
        if (cfg.velocityXZ == 0f && cfg.velocityY == 0f) return;

        // Replicate vanilla knockback formula with multipliers applied
        double len = Math.sqrt(x * x + z * z);
        if (len == 0.0) return;

        double nx = x / len;
        double nz = z / len;

        double resistance = self.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double s = strength * (1.0 - resistance);

        Vec3 vec = self.getDeltaMovement();

        float xzMult = cfg.velocityXZ;
        float yMult  = cfg.velocityY;

        double newX = (vec.x / 2.0 - nx * s) * xzMult + vec.x * (1f - xzMult) / 2.0;
        double newZ = (vec.z / 2.0 - nz * s) * xzMult + vec.z * (1f - xzMult) / 2.0;

        // Simplified: lerp between original and vanilla knockback
        // Actually: apply the full formula but scale the delta contribution
        // new = orig + delta * multiplier, where vanilla delta = (x/2-nx*s - x, y/2+s - y, z/2-nz*s - z)
        // Easier: directly compute vanilla then lerp
        double vanillaX = vec.x / 2.0 - nx * s;
        double vanillaY = self.onGround() ? Math.min(0.4, vec.y / 2.0 + s) : vec.y;
        double vanillaZ = vec.z / 2.0 - nz * s;

        double finalX = vanillaX * xzMult + vec.x * (1f - xzMult);
        double finalY = vanillaY * yMult  + vec.y * (1f - yMult);
        double finalZ = vanillaZ * xzMult + vec.z * (1f - xzMult);

        // Never feed non-finite motion to the player: a NaN/Inf delta propagates
        // into the position packet and the server kicks with "Invalid player data".
        if (!Double.isFinite(finalX) || !Double.isFinite(finalY) || !Double.isFinite(finalZ)) {
            return;
        }

        self.setDeltaMovement(finalX, finalY, finalZ);
    }
}
