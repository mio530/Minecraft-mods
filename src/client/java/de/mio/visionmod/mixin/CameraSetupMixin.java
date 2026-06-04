package de.mio.visionmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two-layer defence against the vanilla outline NPE:
 *
 * Layer 1 (setup TAIL): whenever setup() is called with a non-null entity we
 * remember it. Whenever it would leave entity as null we restore the last
 * known one so the field is never null after a valid frame.
 *
 * Layer 2 (entity() RETURN): directly intercepts the Camera.entity() getter.
 * If it would return null we return visionmod_lastEntity, or mc.player as a
 * last resort. This fires on every call from vanilla outline code, catching
 * races and the very first frame where lastEntity hasn't been set yet.
 *
 * Covers crashes caused by any mod (xray, etc.) that triggers the outline
 * pass when the player disconnects — not just our own glow feature.
 *
 * require=0: silently skips if the target method is renamed in some MC build.
 */
@Mixin(Camera.class)
public class CameraSetupMixin {

    @Unique
    private static Entity visionmod_lastEntity = null;

    /** Layer 1: track the last valid entity from Camera.setup(). */
    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void visionmod_trackEntity(CallbackInfo ci) {
        Entity current = ((CameraAccessor) this).visionmod_getCameraEntity();
        if (current != null) {
            visionmod_lastEntity = current;
        } else if (visionmod_lastEntity != null) {
            ((CameraAccessor) this).visionmod_setCameraEntity(visionmod_lastEntity);
        }
    }

    /** Layer 2: intercept Camera.entity() so it never returns null. */
    @Inject(method = "entity", at = @At("RETURN"), cancellable = true, require = 0)
    private void visionmod_safeEntityGetter(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null) return;
        Entity fallback = visionmod_lastEntity;
        if (fallback == null) {
            try { fallback = Minecraft.getInstance().player; } catch (Exception ignored) {}
        }
        if (fallback != null) cir.setReturnValue(fallback);
    }
}
