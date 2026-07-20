package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoSlow: cancels the stuck-speed multiplier applied by cobwebs and berry bushes. */
@Mixin(Entity.class)
public class MovementSpeedMixin {

    // The stuck-speed slowdown is applied by Entity.makeStuckInBlock(BlockState, Vec3)
    // in 1.21.11 Mojang mappings (there is no setStuckSpeedMultiplier). Cancelling it at
    // HEAD skips the slowdown for the local player. require=0 so a future rename no-ops
    // instead of crashing the game at load.
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_noSlow(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if (VisionConfig.get().noSlowEnabled && (Object) this instanceof LocalPlayer) {
            ci.cancel();
        }
    }
}
