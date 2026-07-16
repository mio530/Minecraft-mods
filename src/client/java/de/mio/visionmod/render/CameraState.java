package de.mio.visionmod.render;

import net.minecraft.world.entity.Entity;

/**
 * Shared holder for the last known non-null camera entity. Written by
 * CameraSetupMixin and read back as a fallback when the vanilla outline pass
 * would otherwise dereference a null camera entity. Kept outside the mixin so the
 * disconnect handler can clear it (a mixin's own static field can't be reached
 * cleanly from ordinary code).
 */
public final class CameraState {
    private CameraState() {}

    public static volatile Entity lastEntity = null;
}
