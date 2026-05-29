package de.mio.visionmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents NPE in vanilla glow/outline rendering.
 *
 * camera.entity() (or getEntity() in older builds) returns null during the
 * server-join and server-disconnect windows. Vanilla glow rendering then
 * calls camera.entity().getEyePosition(partialTick) and crashes.
 *
 * We substitute the local player entity when null so vanilla can finish the
 * frame cleanly. require=0 on both variants: whichever name exists in this
 * MC build applies; the other silently skips.
 */
@Mixin(Camera.class)
public class CameraSafetyMixin {

    // MC >= 1.21.10: Camera refactored to record-style accessor entity()
    @Inject(method = "entity()Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void visionmod_safeEntity(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() == null) {
            Entity player = Minecraft.getInstance().player;
            if (player != null) cir.setReturnValue(player);
        }
    }

    // MC < 1.21.10: traditional getter getEntity()
    @Inject(method = "getEntity()Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void visionmod_safeGetEntity(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() == null) {
            Entity player = Minecraft.getInstance().player;
            if (player != null) cir.setReturnValue(player);
        }
    }
}
