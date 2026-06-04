package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AntiBlind: prevents blindness and darkness effects from being applied to the local player. */
@Mixin(LivingEntity.class)
public class EffectHandlerMixin {

    @Inject(method = "onEffectAdded", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_antiBlind(MobEffectInstance effect, Entity source, CallbackInfo ci) {
        if (!VisionConfig.get().antiBlindEnabled) return;
        if (!((Object) this instanceof LocalPlayer)) return;
        if (effect.getEffect() == MobEffects.BLINDNESS || effect.getEffect() == MobEffects.DARKNESS) {
            ci.cancel();
        }
    }
}
