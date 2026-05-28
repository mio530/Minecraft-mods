package de.mio.visionmod.render;

public final class RenderHacks {
    private RenderHacks() {}

    /** True while the zoom key is held — read by GameRendererMixin. */
    public static volatile boolean zoomActive = false;
}
