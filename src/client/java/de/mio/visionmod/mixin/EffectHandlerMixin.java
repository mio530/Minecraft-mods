package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AntiBlind: prevents blindness and darkness effects from being applied to the local player. */
@Mixin(LivingEntity.class)
public class EffectHandlerMixin {

    // Hook canBeAffected: both addEffect() and forceAddEffect() (the path server-pushed
    // effects take) consult it BEFORE storing the effect, so returning false here keeps
    // hasEffect(BLINDNESS/DARKNESS) false and the fog/overlay never render. Cancelling
    // onEffectAdded (the old target) fired only AFTER the effect was already stored, so
    // the blindness still applied — that is the bug this fixes.
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_antiBlind(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        VisionConfig c = VisionConfig.get();
        if (!c.masterEnabled || !c.antiBlindEnabled) return;
        if (!((Object) this instanceof LocalPlayer)) return;
        if (effect.getEffect() == MobEffects.BLINDNESS || effect.getEffect() == MobEffects.DARKNESS) {
            cir.setReturnValue(false);
        }
    }
}
