package de.mio.visionmod;

import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.config.VisionConfigScreen;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.esp.ItemESP;
import de.mio.visionmod.esp.OreESP;
import de.mio.visionmod.esp.StorageESP;
import de.mio.visionmod.esp.SusChunks;
import de.mio.visionmod.overlay.OverlayWindow;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
            boolean f_bright  = justPressed(win, cfg.keyFullbright);
            boolean f_item    = cfg.keyItemEsp > 0 && justPressed(win, cfg.keyItemEsp);
            boolean f_storage = cfg.keyStorageEsp > 0 && justPressed(win, cfg.keyStorageEsp);

            if (mc.screen == null) {
                if (f_entity)  { cfg.entityEspEnabled   = !cfg.entityEspEnabled;   VisionConfig.save(); }
                if (f_ore)     { cfg.oreEspEnabled       = !cfg.oreEspEnabled;       VisionConfig.save(); }
                if (f_config)  { mc.setScreen(new VisionConfigScreen(null)); }
                if (f_sus)     { cfg.susChunksEnabled    = !cfg.susChunksEnabled;    VisionConfig.save(); }
                if (f_bright)  { cfg.fullbrightEnabled   = !cfg.fullbrightEnabled;   VisionConfig.save(); }
                if (f_item)    { cfg.itemEspEnabled      = !cfg.itemEspEnabled;      VisionConfig.save(); }
                if (f_storage) { cfg.storageEspEnabled   = !cfg.storageEspEnabled;   VisionConfig.save(); }
            }

            // Fullbright: re-apply hidden Night Vision every 4 sec so it never expires
            if (cfg.fullbrightEnabled && mc.player != null) {
                MobEffectInstance existing = mc.player.getEffect(MobEffects.NIGHT_VISION);
                if (existing == null || existing.getDuration() < 80) {
                    // ambient=false, visible=false, showIcon=false → completely hidden
                    mc.player.addEffect(new MobEffectInstance(
                            MobEffects.NIGHT_VISION, 300, 1, false, false, false));
                }
            }

            EntityESP.tick(mc);
            OreESP.tick(mc);
            ItemESP.tick(mc);
            StorageESP.tick(mc);
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
