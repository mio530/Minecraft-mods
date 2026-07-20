package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AntiHunger: cancels FoodData tick to freeze hunger/saturation.
 * FoodData.tick takes a ServerPlayer in 1.21.11 (food is server-authoritative). This can
 * therefore only freeze hunger in singleplayer, where the integrated server runs in the
 * client JVM and this mixin is loaded; on a dedicated server hunger cannot be stopped
 * client-side. require=0 keeps a future signature change from crashing the game.
 */
@Mixin(FoodData.class)
public class FoodDataMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_antiHunger(ServerPlayer player, CallbackInfo ci) {
        if (VisionConfig.get().antiHungerEnabled) ci.cancel();
    }
}
