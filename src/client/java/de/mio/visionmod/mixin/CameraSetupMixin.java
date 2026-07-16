package de.mio.visionmod.mixin;

import de.mio.visionmod.render.CameraState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
 * If it would return null we return CameraState.lastEntity, or mc.player as a
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

    /**
     * Layer 0 (setup HEAD, name-independent): if setup() is called with a null
     * focus entity, substitute the last known one (or the player) before the field
     * is ever assigned. This targets the stable "setup" name and the Entity argument,
     * so it works regardless of what the entity getter is called in this mapping.
     */
    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true, require = 0)
    private Entity visionmod_nonNullFocus(Entity focused) {
        if (focused != null) return focused;
        Entity fb = CameraState.lastEntity;
        if (fb == null) {
            try { fb = Minecraft.getInstance().player; } catch (Exception ignored) {}
        }
        return fb != null ? fb : focused;
    }

    /** Layer 1: track the last valid entity from Camera.setup(). */
    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void visionmod_trackEntity(CallbackInfo ci) {
        Entity current = ((CameraAccessor) this).visionmod_getCameraEntity();
        if (current != null) {
            CameraState.lastEntity = current;
        } else if (CameraState.lastEntity != null) {
            ((CameraAccessor) this).visionmod_setCameraEntity(CameraState.lastEntity);
        }
    }

    /**
     * Layer 2: intercept the Camera entity getter so it never returns null.
     * The getter's Mojang name differs across builds (record-style "entity" vs.
     * legacy "getEntity"); we target both so at least one binds regardless of the
     * mapping in this exact MC version. require=0 keeps the build alive if neither
     * matches, in which case Layer 1 still covers us.
     */
    @Inject(method = {"entity", "getEntity"}, at = @At("RETURN"), cancellable = true, require = 0)
    private void visionmod_safeEntityGetter(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null) return;
        Entity fallback = CameraState.lastEntity;
        if (fallback == null) {
            try { fallback = Minecraft.getInstance().player; } catch (Exception ignored) {}
        }
        if (fallback != null) cir.setReturnValue(fallback);
    }
}
