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
import java.util.Map.Entry;

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

            // Zoom: hold-key (no toggle)
            RenderHacks.zoomActive = cfg.keyZoom > 0
                && GLFW.glfwGetKey(win, cfg.keyZoom) == GLFW.GLFW_PRESS;

            // Always poll all module keys to track state — prevents false triggers after screen closes.
            // Module toggles are only applied when no screen is open.
            boolean f_config = justPressed(win, cfg.keyOpenConfig);
            boolean f_panic  = cfg.keyPanic > 0 && justPressed(win, cfg.keyPanic);
            for (Entry<String, Integer> e : cfg.moduleKeys.entrySet()) {
                int k = e.getValue();
                boolean fired = k > 0 && justPressed(win, k);
                if (fired && mc.screen == null) toggleModule(cfg, e.getKey());
            }

            // Panic: runs regardless of screen state — disable all, disconnect, hard exit
            if (f_panic) {
                cfg.resetFeatureToggles();
                VisionConfig.save();
                if (mc.getConnection() != null) {
                    mc.getConnection().getConnection().disconnect(
                            net.minecraft.network.chat.Component.translatable("menu.disconnect"));
                }
                Runtime.getRuntime().halt(1);
                return;
            }

            if (mc.screen == null && f_config) mc.setScreen(new VisionConfigScreen(null));

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

    private static void toggleModule(VisionConfig c, String id) {
        switch (id) {
            case "entityEsp"     -> c.entityEspEnabled      = !c.entityEspEnabled;
            case "entityGlow"    -> c.entityGlowEnabled     = !c.entityGlowEnabled;
            case "healthBar"     -> c.healthBarEnabled      = !c.healthBarEnabled;
            case "oreEsp"        -> c.oreEspEnabled         = !c.oreEspEnabled;
            case "itemEsp"       -> c.itemEspEnabled        = !c.itemEspEnabled;
            case "storageEsp"    -> c.storageEspEnabled     = !c.storageEspEnabled;
            case "killAura"      -> c.killAuraEnabled       = !c.killAuraEnabled;
            case "maceDmg"       -> c.maceDmgEnabled        = !c.maceDmgEnabled;
            case "maceDmgClassic"-> c.maceDmgClassicEnabled = !c.maceDmgClassicEnabled;
            case "criticals"     -> c.criticalsEnabled      = !c.criticalsEnabled;
            case "autoClicker"   -> c.autoClickerEnabled    = !c.autoClickerEnabled;
            case "velocity"      -> c.velocityEnabled       = !c.velocityEnabled;
            case "autoTotem"     -> c.autoTotemEnabled      = !c.autoTotemEnabled;
            case "noHurtCam"     -> c.noHurtCamEnabled      = !c.noHurtCamEnabled;
            case "autoLog"       -> c.autoLogEnabled        = !c.autoLogEnabled;
            case "sprint"        -> c.sprintEnabled         = !c.sprintEnabled;
            case "fly"           -> c.flyEnabled            = !c.flyEnabled;
            case "speed"         -> c.speedEnabled          = !c.speedEnabled;
            case "noFall"        -> c.noFallEnabled         = !c.noFallEnabled;
            case "step"          -> c.stepEnabled           = !c.stepEnabled;
            case "jesus"         -> c.jesusEnabled          = !c.jesusEnabled;
            case "noSlow"        -> c.noSlowEnabled         = !c.noSlowEnabled;
            case "scaffold"      -> c.scaffoldEnabled       = !c.scaffoldEnabled;
            case "surround"      -> c.surroundEnabled       = !c.surroundEnabled;
            case "safeWalk"      -> c.safeWalkEnabled       = !c.safeWalkEnabled;
            case "invMove"       -> c.invMoveEnabled        = !c.invMoveEnabled;
            case "autoEat"       -> c.autoEatEnabled        = !c.autoEatEnabled;
            case "antiHunger"    -> c.antiHungerEnabled     = !c.antiHungerEnabled;
            case "antiPoison"    -> c.antiPoisonEnabled     = !c.antiPoisonEnabled;
            case "antiAfk"       -> c.antiAfkEnabled        = !c.antiAfkEnabled;
            case "autoRespawn"   -> c.autoRespawnEnabled    = !c.autoRespawnEnabled;
            case "chestStealer"  -> c.chestStealerEnabled   = !c.chestStealerEnabled;
            case "fullbright"    -> c.fullbrightEnabled     = !c.fullbrightEnabled;
            case "noFog"         -> c.noFogEnabled          = !c.noFogEnabled;
            case "noWeather"     -> c.noWeatherEnabled      = !c.noWeatherEnabled;
            case "antiBlind"     -> c.antiBlindEnabled      = !c.antiBlindEnabled;
            case "coords"        -> c.coordsHudEnabled      = !c.coordsHudEnabled;
            case "susChunks"     -> c.susChunksEnabled      = !c.susChunksEnabled;
            case "nuker"         -> c.nukerEnabled          = !c.nukerEnabled;
            default -> { return; }
        }
        VisionConfig.save();
    }
}
