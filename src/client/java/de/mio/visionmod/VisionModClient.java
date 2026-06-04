package de.mio.visionmod;

import de.mio.visionmod.combat.CombatHacks;
import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.config.VisionConfigScreen;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.esp.ItemESP;
import de.mio.visionmod.esp.OreESP;
import de.mio.visionmod.esp.StorageESP;
import de.mio.visionmod.esp.SusChunks;
import de.mio.visionmod.hud.HudOverlay;
import de.mio.visionmod.movement.MovementHacks;
import de.mio.visionmod.overlay.OverlayWindow;
import de.mio.visionmod.player.PlayerHacks;
import de.mio.visionmod.render.RenderHacks;
import de.mio.visionmod.world.Nuker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class VisionModClient implements ClientModInitializer {

    private static final Map<Integer, Boolean> prevKey = new HashMap<>();

    /**
     * Counts ticks where player+level are both non-null. Reset on disconnect.
     * The glow mixin uses this to skip glow until Camera.setup() has run.
     */
    public static volatile int postJoinTicks = 0;

    /**
     * True only after ClientPlayConnectionEvents.JOIN fires, i.e. the server
     * connection is fully in the play-phase and the Netty channel is ready.
     * mc.getConnection() != null is NOT sufficient: the handler exists during
     * the configuration phase (MC 1.19.3+) but the channel isn't play-ready,
     * so sending packets there crashes with a channel NPE.
     */
    public static volatile boolean fullyJoined = false;

    @Override
    public void onInitializeClient() {
        VisionConfig.load();

        // Set fullyJoined only after the play-phase handshake completes.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            fullyJoined = true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long win = GLFW.glfwGetCurrentContext();
            VisionConfig cfg = VisionConfig.get();

            // Track ticks since world join so glow mixin can wait for camera init
            if (mc.player != null && mc.level != null) {
                if (postJoinTicks < 200) postJoinTicks++;
            } else {
                postJoinTicks = 0;
            }

            // Always poll to track state — prevents false triggers after screen closes
            boolean f_entity  = justPressed(win, cfg.keyEntityEsp);
            boolean f_ore     = justPressed(win, cfg.keyOreEsp);
            boolean f_config  = justPressed(win, cfg.keyOpenConfig);
            boolean f_sus     = justPressed(win, cfg.keySusChunks);
            boolean f_bright  = justPressed(win, cfg.keyFullbright);
            boolean f_item    = cfg.keyItemEsp > 0 && justPressed(win, cfg.keyItemEsp);
            boolean f_storage = cfg.keyStorageEsp > 0 && justPressed(win, cfg.keyStorageEsp);
            boolean f_panic   = cfg.keyPanic   > 0 && justPressed(win, cfg.keyPanic);

            // Zoom: hold-key (no toggle)
            RenderHacks.zoomActive = cfg.keyZoom > 0
                && GLFW.glfwGetKey(win, cfg.keyZoom) == GLFW.GLFW_PRESS;

            // Panic: runs regardless of screen state — disable all, disconnect, exit
            if (f_panic) {
                cfg.resetFeatureToggles();
                VisionConfig.save();
                if (mc.getConnection() != null) {
                    mc.getConnection().getConnection().disconnect(
                            net.minecraft.network.chat.Component.translatable("menu.disconnect"));
                }
                mc.stop();
                return;
            }

            if (mc.screen == null) {
                if (f_entity)  { cfg.entityEspEnabled   = !cfg.entityEspEnabled;   VisionConfig.save(); }
                if (f_ore)     { cfg.oreEspEnabled       = !cfg.oreEspEnabled;       VisionConfig.save(); }
                if (f_config)  { mc.setScreen(new VisionConfigScreen(null)); }
                if (f_sus)     { cfg.susChunksEnabled    = !cfg.susChunksEnabled;    VisionConfig.save(); }
                if (f_bright)  { cfg.fullbrightEnabled   = !cfg.fullbrightEnabled;   VisionConfig.save(); }
                if (f_item)    { cfg.itemEspEnabled      = !cfg.itemEspEnabled;      VisionConfig.save(); }
                if (f_storage) { cfg.storageEspEnabled   = !cfg.storageEspEnabled;   VisionConfig.save(); }
            }

            // ESP ticks are read-only and guard themselves internally
            EntityESP.tick(mc);
            OreESP.tick(mc);
            ItemESP.tick(mc);
            StorageESP.tick(mc);
            SusChunks.tick(mc);

            // Hack ticks send network packets — only run after play-phase join
            if (fullyJoined && mc.player != null && mc.level != null) {
                // Fullbright needs connection guard too: addEffect() goes through the network layer
                if (cfg.fullbrightEnabled) {
                    MobEffectInstance existing = mc.player.getEffect(MobEffects.NIGHT_VISION);
                    if (existing == null || existing.getDuration() < 80) {
                        mc.player.addEffect(new MobEffectInstance(
                                MobEffects.NIGHT_VISION, 300, 1, false, false, false));
                    }
                }
                CombatHacks.tick(mc);
                MovementHacks.tick(mc);
                PlayerHacks.tick(mc);
                Nuker.tick(mc);
            }
        });

        WorldRenderEvents.END_MAIN.register(ctx -> OverlayWindow.INSTANCE.onRenderEnd(ctx));
        HudRenderCallback.EVENT.register(HudOverlay::onHudRender);
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> OverlayWindow.INSTANCE.init(mc));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> OverlayWindow.INSTANCE.destroy());

        // Clear all snapshots and reset static hack state on disconnect so stale
        // values from the previous session don't fire immediately on the next join.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            fullyJoined         = false;
            postJoinTicks       = 0;
            VisionConfig cfg = VisionConfig.get();
            if (cfg.resetOnRelog) { cfg.resetFeatureToggles(); VisionConfig.save(); }
            EntityESP.snapshot  = java.util.Collections.emptyList();
            ItemESP.snapshot    = java.util.Collections.emptyList();
            OreESP.resetOnDisconnect();
            StorageESP.resetOnDisconnect();
            SusChunks.resetOnDisconnect();
            MovementHacks.resetOnDisconnect();
            PlayerHacks.resetOnDisconnect(mc);
            Nuker.resetOnDisconnect();
        });
    }

    /** Returns true only on the tick the key transitions from released → pressed. */
    public static boolean justPressed(long window, int key) {
        boolean now  = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        boolean prev = prevKey.getOrDefault(key, false);
        prevKey.put(key, now);
        return now && !prev;
    }
}
