package de.mio.visionmod.mixin;

import de.mio.visionmod.VisionModClient;
import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityRenderStateMixin {

    // require=0: silently skip if Mojang renamed the method in this MC version.
    @Inject(method = "isCurrentlyGlowing()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_isGlowing(CallbackInfoReturnable<Boolean> cir) {
        try {
            VisionConfig cfg = VisionConfig.get();
            if (!cfg.entityEspEnabled || !cfg.entityGlowEnabled) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || !VisionModClient.fullyJoined) return;
            // Guard: don't activate glow if Camera.entity is null — vanilla glow rendering
            // calls camera.entity().getEyePosition() and would NPE during join/leave windows.
            if (((CameraAccessor) mc.gameRenderer.getMainCamera()).visionmod_getCameraEntity() == null) return;

            Entity self = (Entity) (Object) this;
            Identifier typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
            if (typeKey != null && cfg.enabledEntityTypes.contains(typeKey.toString())) {
                cir.setReturnValue(true);
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "getTeamColor()I", at = @At("HEAD"), cancellable = true, require = 0)
    private void visionmod_getTeamColor(CallbackInfoReturnable<Integer> cir) {
        try {
            VisionConfig cfg = VisionConfig.get();
            if (!cfg.entityEspEnabled || !cfg.entityGlowEnabled) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || !VisionModClient.fullyJoined) return;
            if (((CameraAccessor) mc.gameRenderer.getMainCamera()).visionmod_getCameraEntity() == null) return;

            Entity self = (Entity) (Object) this;
            Identifier typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
            if (typeKey != null && cfg.enabledEntityTypes.contains(typeKey.toString())) {
                String hex = cfg.entityBoxColors.getOrDefault(typeKey.toString(), "#FFFF0000");
                cir.setReturnValue(VisionConfig.parseColor(hex) & 0x00FFFFFF);
            }
        } catch (Exception ignored) {}
    }
}
