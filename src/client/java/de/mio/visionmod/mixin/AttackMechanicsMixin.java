package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Criticals: forces isCritHit to return true when enabled. */
@Mixin(Player.class)
public class AttackMechanicsMixin {

    @Inject(method = "isCritHit", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_crit(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (VisionConfig.get().criticalsEnabled && entity instanceof LivingEntity) {
            cir.setReturnValue(true);
        }
    }
}
