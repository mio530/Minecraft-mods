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
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
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

            // Compute each distinct bound key's rising edge exactly ONCE this tick.
            // justPressed() mutates prevKey, so calling it twice for the same code made
            // only the first caller see the press — a key bound to several actions would
            // silently fire just one of them. Cache edges here and read them below.
            Map<Integer, Boolean> edges = new HashMap<>();
            edgeOf(edges, win, cfg.keyOpenConfig);
            edgeOf(edges, win, cfg.keyPanic);
            edgeOf(edges, win, cfg.keyPanic2);
            for (int k : cfg.moduleKeys.values()) edgeOf(edges, win, k);

            boolean f_config = cfg.keyOpenConfig > 0 && edges.getOrDefault(cfg.keyOpenConfig, false);
            boolean f_panic  = cfg.keyPanic  > 0 && edges.getOrDefault(cfg.keyPanic, false);
            boolean f_panic2 = cfg.keyPanic2 > 0 && edges.getOrDefault(cfg.keyPanic2, false);
            for (Entry<String, Integer> e : cfg.moduleKeys.entrySet()) {
                int k = e.getValue();
                if (k > 0 && edges.getOrDefault(k, false) && mc.screen == null) toggleModule(cfg, e.getKey());
            }

            // Don't let panic fire while the user is typing (chat / config search box),
            // otherwise a panic key bound to a letter triggers mid-word.
            boolean typing = mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen
                    || (mc.screen != null
                        && mc.screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox);

            // Panic 2: silent — just disable all hacks, no disconnect, no exit
            if (f_panic2 && !typing) {
                cfg.resetFeatureToggles();
                VisionConfig.save();
            }

            // Panic 1: disable all, disconnect, write fake crash report, hard JVM exit
            if (f_panic && !typing) {
                cfg.resetFeatureToggles();
                VisionConfig.save();
                if (mc.getConnection() != null) {
                    mc.getConnection().getConnection().disconnect(
                            net.minecraft.network.chat.Component.translatable("menu.disconnect"));
                }
                writeFakeCrash(mc);
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

        // Add a button to the title screen and pause menu so the config GUI can be
        // opened without being in a world — needed because the in-game keybind is
        // unreachable if the player can't join (e.g. a crash on connect).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
                Button btn = Button.builder(
                        Component.literal("AppleskinV2"),
                        b -> client.setScreen(new VisionConfigScreen(screen)))
                        .bounds(scaledWidth - 84, 6, 78, 20)
                        .build();
                Screens.getButtons(screen).add(btn);
            }
        });
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
            CombatHacks.resetOnDisconnect();
            MovementHacks.resetOnDisconnect();
            PlayerHacks.resetOnDisconnect(mc);
            Nuker.resetOnDisconnect();
            de.mio.visionmod.render.CameraState.lastEntity = null;
        });
    }

    /** Returns true only on the tick the key transitions from released → pressed. */
    public static boolean justPressed(long window, int key) {
        boolean now  = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        boolean prev = prevKey.getOrDefault(key, false);
        prevKey.put(key, now);
        return now && !prev;
    }

    /** Records the rising edge of a key code exactly once (justPressed mutates prevKey). */
    private static void edgeOf(Map<Integer, Boolean> edges, long window, int key) {
        if (key <= 0 || edges.containsKey(key)) return;
        edges.put(key, justPressed(window, key));
    }

    private static void writeFakeCrash(Minecraft mc) {
        try {
            java.io.File dir = new java.io.File(mc.gameDirectory, "crash-reports");
            dir.mkdirs();
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")
                    .format(new java.util.Date());
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.FileWriter(new java.io.File(dir, "crash-" + ts + "-client.txt")))) {
                pw.println("---- Minecraft Crash Report ----");
                pw.println("// You should try our sister game, Minceraft!");
                pw.println();
                pw.println("Time: " + new java.util.Date());
                pw.println("Description: Unexpected error");
                pw.println();
                pw.println("java.lang.OutOfMemoryError: Java heap space");
                pw.println("\tat java.util.Arrays.copyOf(Arrays.java:3210)");
                pw.println("\tat java.util.ArrayList.grow(ArrayList.java:265)");
                pw.println("\tat java.util.ArrayList.ensureCapacityInternal(ArrayList.java:231)");
                pw.println("\tat java.util.ArrayList.add(ArrayList.java:462)");
                pw.println("\tat net.minecraft.client.renderer.chunk.SectionRenderDispatcher"
                        + ".compileSectionLayer(SectionRenderDispatcher.java:289)");
                pw.println("\tat net.minecraft.client.renderer.chunk.SectionRenderDispatcher"
                        + "$RenderSection$CompileTask.doTask(SectionRenderDispatcher.java:494)");
                pw.println("\tat net.minecraft.client.renderer.chunk.SectionRenderDispatcher"
                        + "$RenderSection$CompileTask.run(SectionRenderDispatcher.java:477)");
                pw.println("\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)");
                pw.println("\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)");
                pw.println("\tat java.lang.Thread.run(Thread.java:750)");
                pw.println();
                pw.println("A detailed walkthrough of the error, its code path and all known details is as follows:");
                pw.println("---------------------------------------------------------------------------------------");
                pw.println();
                pw.println("-- System Details --");
                pw.println("Details:");
                pw.println("\tMinecraft Version: 1.21.1");
                pw.println("\tOperating System: " + System.getProperty("os.name")
                        + " (" + System.getProperty("os.arch") + ") version "
                        + System.getProperty("os.version"));
                pw.println("\tJava Version: " + System.getProperty("java.version")
                        + ", " + System.getProperty("java.vendor"));
                Runtime rt = Runtime.getRuntime();
                long free = rt.freeMemory(), total = rt.totalMemory(), max = rt.maxMemory();
                pw.printf("\tMemory: %d bytes (%d%%) free, %d bytes (%d%%) allocated,"
                        + " %d bytes maximum%n", free, free * 100 / max,
                        total, total * 100 / max, max);
                pw.println("\tCPU: " + Runtime.getRuntime().availableProcessors() + "x "
                        + System.getProperty("os.arch"));
            }
        } catch (Exception ignored) {}
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
            case "autoMace"      -> c.autoMaceEnabled       = !c.autoMaceEnabled;
            case "reach"         -> c.reachEnabled          = !c.reachEnabled;
            case "triggerBot"    -> c.triggerBotEnabled     = !c.triggerBotEnabled;
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
            case "spider"        -> c.spiderEnabled         = !c.spiderEnabled;
            case "antiVoid"      -> c.antiVoidEnabled       = !c.antiVoidEnabled;
            case "autoWalk"      -> c.autoWalkEnabled       = !c.autoWalkEnabled;
            case "glide"         -> c.glideEnabled          = !c.glideEnabled;
            case "fastLadder"    -> c.fastLadderEnabled     = !c.fastLadderEnabled;
            case "autoJump"      -> c.autoJumpEnabled       = !c.autoJumpEnabled;
            case "autoSneak"     -> c.autoSneakEnabled      = !c.autoSneakEnabled;
            case "autoTool"      -> c.autoToolEnabled       = !c.autoToolEnabled;
            case "autoWeapon"    -> c.autoWeaponEnabled     = !c.autoWeaponEnabled;
            case "autoArmor"     -> c.autoArmorEnabled      = !c.autoArmorEnabled;
            case "noBob"         -> c.noBobEnabled          = !c.noBobEnabled;
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
            case "stunSlam"      -> c.stunSlamEnabled       = !c.stunSlamEnabled;
            case "betterTab"     -> c.betterTabEnabled      = !c.betterTabEnabled;
            default -> { return; }
        }
        VisionConfig.save();
    }
}
