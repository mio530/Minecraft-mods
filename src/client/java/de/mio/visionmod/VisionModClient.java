package de.mio.visionmod;

import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.config.VisionConfigScreen;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.esp.OreESP;
import de.mio.visionmod.esp.SusChunks;
import de.mio.visionmod.overlay.OverlayWindow;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class VisionModClient implements ClientModInitializer {

    private static final Map<Integer, Boolean> prevKey = new HashMap<>();

    @Override
    public void onInitializeClient() {
        VisionConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long win = mc.getWindow().getWindow();
            VisionConfig cfg = VisionConfig.get();

            // Always poll to track state — prevents false triggers after screen closes
            boolean f_entity  = justPressed(win, cfg.keyEntityEsp);
            boolean f_ore     = justPressed(win, cfg.keyOreEsp);
            boolean f_config  = justPressed(win, cfg.keyOpenConfig);
            boolean f_sus     = justPressed(win, cfg.keySusChunks);

            if (mc.screen == null) {
                if (f_entity) { cfg.entityEspEnabled = !cfg.entityEspEnabled; VisionConfig.save(); }
                if (f_ore)    { cfg.oreEspEnabled    = !cfg.oreEspEnabled;    VisionConfig.save(); }
                if (f_config) { mc.setScreen(new VisionConfigScreen(null)); }
                if (f_sus)    { cfg.susChunksEnabled = !cfg.susChunksEnabled; VisionConfig.save(); }
            }

            EntityESP.tick(mc);
            OreESP.tick(mc);
            SusChunks.tick(mc);
        });

        WorldRenderEvents.END_MAIN.register(ctx -> OverlayWindow.INSTANCE.onRenderEnd(ctx));
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> OverlayWindow.INSTANCE.init(mc));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> OverlayWindow.INSTANCE.destroy());
    }

    /** Returns true only on the tick the key transitions from released → pressed. */
    public static boolean justPressed(long window, int key) {
        boolean now  = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        boolean prev = prevKey.getOrDefault(key, false);
        prevKey.put(key, now);
        return now && !prev;
    }
}
