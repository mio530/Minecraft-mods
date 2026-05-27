package de.mio.visionmod.mixin;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing()Z", at = @At("HEAD"), cancellable = true)
    private void visionmod_isGlowing(CallbackInfoReturnable<Boolean> cir) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.entityEspEnabled || !cfg.entityGlowEnabled) return;

        Entity self = (Entity) (Object) this;
        Identifier typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        if (typeKey != null && cfg.enabledEntityTypes.contains(typeKey.toString())) {
            cir.setReturnValue(true);
        }
    }

    // Optional: colored outlines using the entity's configured ESP color.
    // require=0 so it silently skips if Mojang renamed the method in this MC version.
    @Inject(method = "getTeamColor()I", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_getTeamColor(CallbackInfoReturnable<Integer> cir) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.entityEspEnabled || !cfg.entityGlowEnabled) return;

        Entity self = (Entity) (Object) this;
        Identifier typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        if (typeKey != null && cfg.enabledEntityTypes.contains(typeKey.toString())) {
            String hex = cfg.entityBoxColors.getOrDefault(typeKey.toString(), "#FFFF0000");
            cir.setReturnValue(VisionConfig.parseColor(hex) & 0x00FFFFFF);
        }
    }
}
