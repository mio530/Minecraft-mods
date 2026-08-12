package de.mio.visionmod.render;

public final class RenderHacks {
    private RenderHacks() {}

    /** True while the zoom key is held — read by GameRendererMixin. */
    public static volatile boolean zoomActive = false;

    /**
     * True only while Options.save() runs. GammaMixin must not return the boosted gamma
     * during a save (else it persists into options.txt). This flag lives here — NOT in the
     * mixin — because Mixin forbids non-private static fields in a mixin class.
     */
    public static volatile boolean gammaSaving = false;
}
