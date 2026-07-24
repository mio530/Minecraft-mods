package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Better Tab (Meteor-style): removes the vanilla 80-player cap on the tab list so
 * every player on the server is shown. The cap is expressed as a constant in
 * {@code PlayerTabOverlay.render}; depending on the build it is an int or a long
 * literal, so both forms are targeted with require=0 (whichever exists is patched,
 * neither failing the build if absent). Only takes effect while Better Tab is on.
 */
@Mixin(PlayerTabOverlay.class)
public class BetterTabMixin {

    @ModifyConstant(method = "render", constant = @Constant(intValue = 80), require = 0)
    private int visionmod_tabCapInt(int original) {
        return VisionConfig.get().masterEnabled && VisionConfig.get().betterTabEnabled ? 1000 : original;
    }

    @ModifyConstant(method = "render", constant = @Constant(longValue = 80L), require = 0)
    private long visionmod_tabCapLong(long original) {
        return VisionConfig.get().masterEnabled && VisionConfig.get().betterTabEnabled ? 1000L : original;
    }
}
