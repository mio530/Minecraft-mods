package de.mio.visionmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Camera.entity field directly.
 *
 * Camera's entity getter was renamed across MC versions (getEntity() in older
 * builds, entity() as a record accessor in 1.21.10+). Neither name compiles
 * with Mojang mappings in this build. @Accessor reads the backing field by
 * name ("entity") which is stable across all versions.
 */
@Mixin(Camera.class)
public interface CameraEntityAccessor {
    @Accessor("entity")
    Entity visionmod_getCameraEntity();
}
