package de.mio.visionmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Camera.entity field directly (read + write).
 *
 * Camera's entity getter was renamed across MC versions (getEntity() in older
 * builds, a record-style accessor in 1.21.10+). Neither name compiles in plain
 * Java with Mojang mappings in this build. @Accessor reads/writes the backing
 * field by name ("entity"), which is stable across all versions.
 */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Accessor("entity")
    Entity visionmod_getCameraEntity();

    @Mutable
    @Accessor("entity")
    void visionmod_setCameraEntity(Entity entity);
}
