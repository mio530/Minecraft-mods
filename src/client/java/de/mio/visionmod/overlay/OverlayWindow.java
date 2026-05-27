package de.mio.visionmod.overlay;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public final class OverlayWindow {

    public static final OverlayWindow INSTANCE = new OverlayWindow();

    private long overlayHandle = 0L;
    private GLCapabilities overlayCaps;
    private OverlayRenderer renderer;

    private OverlayWindow() {}

    public void init(Minecraft mc) {
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) { w = 854; h = 480; }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE,                  GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED,                GLFW_FALSE);
        glfwWindowHint(GLFW_FLOATING,                 GLFW_TRUE);
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER,  GLFW_TRUE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW,            GLFW_FALSE);
        glfwWindowHint(GLFW_MOUSE_PASSTHROUGH,        GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE,                GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        overlayHandle = glfwCreateWindow(w, h, "VisionMod Overlay", 0L, 0L);
        if (overlayHandle == 0L) {
            System.err.println("[VisionMod] Could not create overlay window!");
            return;
        }

        positionOverlay(mc);

        long mainCtx = glfwGetCurrentContext();
        glfwMakeContextCurrent(overlayHandle);
        overlayCaps = GL.createCapabilities();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glfwMakeContextCurrent(mainCtx);

        glfwShowWindow(overlayHandle);
        renderer = new OverlayRenderer();
        System.out.println("[VisionMod] Overlay window created.");
    }

    public void destroy() {
        if (overlayHandle != 0L) {
            glfwDestroyWindow(overlayHandle);
            overlayHandle = 0L;
        }
    }

    public void onRenderEnd(WorldRenderContext ctx) {
        if (overlayHandle == 0L || renderer == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int sw = mc.getWindow().getWidth();
        int sh = mc.getWindow().getHeight();
        if (sw <= 0 || sh <= 0) return;

        // Model-view: camera rotation from Fabric's PoseStack
        Matrix4f mv   = new Matrix4f(ctx.matrices().last().pose());
        // Projection: built from the player's FOV option (near/far match MC defaults)
        int fovDeg    = mc.options.fov.get();
        Matrix4f proj = new Matrix4f().perspective(
                (float) Math.toRadians(fovDeg), (float) sw / sh, 0.05f, 256f);
        Vec3 camPos   = mc.player.getEyePosition(1.0f);

        long mainCtx = glfwGetCurrentContext();
        GLCapabilities mainCaps = GL.getCapabilities();

        syncSizeAndPosition(mc);

        glfwMakeContextCurrent(overlayHandle);
        GL.setCapabilities(overlayCaps);

        renderer.render(mv, proj, camPos, sw, sh);
        glfwSwapBuffers(overlayHandle);

        glfwMakeContextCurrent(mainCtx);
        GL.setCapabilities(mainCaps);
    }

    private void positionOverlay(Minecraft mc) {
        if (overlayHandle == 0L) return;
        glfwSetWindowPos(overlayHandle, mc.getWindow().getX(), mc.getWindow().getY());
    }

    private void syncSizeAndPosition(Minecraft mc) {
        if (overlayHandle == 0L) return;
        int mw = mc.getWindow().getWidth();
        int mh = mc.getWindow().getHeight();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer wb = stack.mallocInt(1);
            IntBuffer hb = stack.mallocInt(1);
            glfwGetWindowSize(overlayHandle, wb, hb);
            if (wb.get(0) != mw || hb.get(0) != mh) {
                glfwSetWindowSize(overlayHandle, mw, mh);
            }
            glfwSetWindowPos(overlayHandle, mc.getWindow().getX(), mc.getWindow().getY());
        }
    }
}
