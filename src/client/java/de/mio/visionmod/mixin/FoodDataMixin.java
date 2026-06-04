package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AntiHunger: cancels FoodData tick to freeze hunger/saturation on client. */
@Mixin(FoodData.class)
public class FoodDataMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_antiHunger(Player player, CallbackInfo ci) {
        if (VisionConfig.get().antiHungerEnabled) ci.cancel();
    }
}
