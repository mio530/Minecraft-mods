package de.mio.visionmod.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flags the window where Options.save() is writing options.txt, so GammaMixin can
 * return the REAL gamma during the save instead of the Brightness-Boost value. Without
 * this, saving options while the boost is on persists the boosted gamma as the actual
 * setting. require=0 so a renamed save() just disables the guard (falls back to prior
 * behaviour) rather than crashing.
 */
@Mixin(Options.class)
public class OptionsSaveMixin {

    @Inject(method = "save", at = @At("HEAD"), require = 0)
    private void visionmod_saveStart(CallbackInfo ci) {
        GammaMixin.visionmod_saving = true;
    }

    @Inject(method = "save", at = @At("RETURN"), require = 0)
    private void visionmod_saveEnd(CallbackInfo ci) {
        GammaMixin.visionmod_saving = false;
    }
}
