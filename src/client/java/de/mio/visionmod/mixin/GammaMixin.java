package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brightness Boost: overrides the gamma OptionInstance's value while enabled, so the
 * lightmap is driven far past the vanilla 0..1 range → true fullbright that brightens
 * terrain AND entities (both sample the same lightmap).
 *
 * Intercepting get() (rather than set()) sidesteps the vanilla clamp that resets
 * out-of-range gamma to the default. We only act on the one instance that is
 * mc.options.gamma() so every other option keeps its real value. require=0 so a
 * renamed getter no-ops instead of crashing the game at load.
 */
@Mixin(OptionInstance.class)
public class GammaMixin {

    /**
     * True only while Options.save() is running (see OptionsSaveMixin). During a save
     * we must NOT return the boosted value, otherwise the boost is written into
     * options.txt and the real gamma is permanently overwritten.
     */
    public static volatile boolean visionmod_saving = false;

    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0)
    private void visionmod_gammaBoost(CallbackInfoReturnable<Object> cir) {
        if (visionmod_saving) return;               // keep options.txt at the real gamma
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.masterEnabled || !cfg.gammaBoostEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && (Object) this == mc.options.gamma()) {
            cir.setReturnValue((double) cfg.gammaValue);
        }
    }
}
