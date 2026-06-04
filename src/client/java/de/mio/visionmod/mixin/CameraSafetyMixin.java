package de.mio.visionmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents NPE in vanilla glow/outline rendering.
 *
 * Vanilla's outline post-process (GameRenderer) calls
 * camera.entity().getEyePosition(partialTick). During the server join/kick
 * window there is a frame where both mc.player and the camera entity are null
 * (Camera.setup is called with a null entity), so that call NPEs.
 *
 * Camera.setup() runs at the very start of every render frame, BEFORE the
 * outline pass. We hook its tail: whenever the camera ends up with a valid
 * entity we remember it; whenever it would be null we restore the last known
 * entity. A stale entity reference still returns a valid getEyePosition(), so
 * the outline pass can never dereference null — regardless of whether our glow
 * feature is what triggered the pass.
 *
 * require=0: if "setup" is renamed in some MC build the hook silently skips;
 * EntityGlowMixin's own null guard remains as a second line of defence.
 */
@Mixin(Camera.class)
public class CameraSafetyMixin {

    @Unique
    private static Entity visionmod_lastEntity = null;

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void visionmod_keepEntityNonNull(CallbackInfo ci) {
        CameraEntityAccessor self = (CameraEntityAccessor) this;
        Entity current = self.visionmod_getCameraEntity();
        if (current != null) {
            visionmod_lastEntity = current;
        } else if (visionmod_lastEntity != null) {
            self.visionmod_setCameraEntity(visionmod_lastEntity);
        }
    }
}
