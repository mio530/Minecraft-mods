package de.mio.visionmod.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class VisionConfigScreen extends Screen {

    // ══════════════════════════════════════ COLORS (light-green theme) ════════

    private static final int C_BG        = 0xF0101812;
    private static final int C_HDR       = 0xFF090F0B;
    private static final int C_LEFT_BG   = 0xFF0C130E;
    private static final int C_CAT_HDR   = 0xFF121F14;
    private static final int C_SEL_BG    = 0xFF122010;
    private static final int C_RIGHT_BG  = 0xFF0D140F;
    private static final int C_DIVIDER   = 0xFF1A2E1C;
    private static final int C_IND_ON    = 0xFF4ADE80;  // light green
    private static final int C_IND_OFF   = 0xFF2A4030;
    private static final int C_TEXT      = 0xFFCFE8D0;
    private static final int C_DIM       = 0xFF4A6050;
    private static final int C_ACCENT    = 0xFF86EFAC;
    private static final int C_BTN       = 0xFF141E16;
    private static final int C_BTN_H     = 0xFF1E2E20;
    private static final int C_BTN_BD    = 0xFF243428;

    // ══════════════════════════════════════ LAYOUT ════════════════════════════

    private static final int HDR_H   = 20;
    private static final int LEFT_W  = 142;
    private static final int CAT_H   = 14;
    private static final int MOD_H   = 21;
    private static final int S_ROW   = 19;
    private static final int E_ROW   = 17;
    private static final int SEARCH_H = 16;

    private static final int[] PALETTE = {
        0xFFFF3333, 0xFFFF8800, 0xFFFFDD00, 0xFF33FF44,
        0xFF00FFCC, 0xFF22AAFF, 0xFFAA55FF, 0xFFFF55BB,
        0xFFFFFFFF, 0xFF888888
    };

    // ══════════════════════════════════════ MODULE REGISTRY ═══════════════════

    private record ModDef(String id, String name, String desc, String cat) {}

    private static final List<String>  CATS = List.of(
            "ESP", "Combat", "Movement", "Player", "Render", "World", "Session", "Tasten");

    private static final List<ModDef>  MODS = List.of(
        // ESP
        new ModDef("entityEsp",   "Entity Highlight",   "Entities durch Wände",        "ESP"),
        new ModDef("entityGlow",  "Entity Outline",     "Umriss-Effekt",               "ESP"),
        new ModDef("healthBar",   "Health Display",     "Lebenspunkte über Entities",  "ESP"),
        new ModDef("oreEsp",      "Mineral Highlight",  "Mineralien durch Wände",      "ESP"),
        new ModDef("itemEsp",     "Item Highlight",     "Liegende Items",              "ESP"),
        new ModDef("storageEsp",  "Storage Highlight",  "Kisten & Behälter",           "ESP"),
        // Combat
        new ModDef("killAura",    "Auto Target",        "Automatische Ziel-Auswahl",   "Combat"),
        new ModDef("criticals",   "Hit Enhancement",    "Verbesserte Trefferchance",   "Combat"),
        new ModDef("autoClicker", "Click Assist",       "Klick-Unterstützung",         "Combat"),
        new ModDef("velocity",    "Motion Adjust",      "Bewegungskorrektur",          "Combat"),
        new ModDef("autoTotem",   "Totem Handler",      "Totem automatisch anlegen",   "Combat"),
        new ModDef("noHurtCam",   "Camera Stabilizer",  "Kamera-Stabilisierung",       "Combat"),
        new ModDef("autoLog",     "Auto Disconnect",    "Trennt bei niedrigem HP",     "Combat"),
        new ModDef("maceDmg",        "Mace Boost",        "Auto-Sprung + Mace-Angriff",  "Combat"),
        new ModDef("maceDmgClassic", "Mace Classic",      "Mace-Angriff beim Fallen",    "Combat"),
        new ModDef("stunSlam",       "StunSlam",          "Shield-Stun beim Fallen",     "Combat"),
        new ModDef("autoMace",       "Auto Mace",         "Sanftes Anvisieren mit Mace", "Combat"),
        new ModDef("reach",          "Reach Extend",      "Erhöhte Interaktions-Reichweite","Combat"),
        new ModDef("triggerBot",     "Trigger Assist",    "Auto-Angriff am Fadenkreuz",  "Combat"),
        new ModDef("autoWeapon",     "Weapon Switch",     "Beste Waffe beim Angriff",    "Combat"),
        // Movement
        new ModDef("sprint",      "Sprint Assist",      "Lauf-Optimierung",            "Movement"),
        new ModDef("fly",         "Flight Mode",        "Flug-Modus",                  "Movement"),
        new ModDef("speed",       "Speed Adjust",       "Geschwindigkeits-Anpassung",  "Movement"),
        new ModDef("noFall",      "Fall Protection",    "Fallschutz",                  "Movement"),
        new ModDef("step",        "Step Assist",        "Erhöhte Stufen",              "Movement"),
        new ModDef("jesus",       "Liquid Walk",        "Auf Wasser laufen",           "Movement"),
        new ModDef("noSlow",      "Move Optimizer",     "Bewegungs-Optimierung",       "Movement"),
        new ModDef("scaffold",    "Block Placer",       "Auto-Platzierung",            "Movement"),
        new ModDef("surround",    "Block Surround",     "Blöcke um den Spieler",       "Movement"),
        new ModDef("safeWalk",    "Edge Protection",    "Nicht von Kanten fallen",     "Movement"),
        new ModDef("invMove",     "Inventory Move",     "Bewegung im Inventar",        "Movement"),
        new ModDef("spider",      "Wall Climb",         "Wände hochklettern",          "Movement"),
        new ModDef("antiVoid",    "Void Protection",    "Schweben über der Leere",     "Movement"),
        new ModDef("autoWalk",    "Auto Walk",          "Automatisch vorwärts laufen", "Movement"),
        new ModDef("glide",       "Slow Descent",       "Langsames Fallen",            "Movement"),
        new ModDef("fastLadder",  "Fast Climb",         "Schneller Leitern hoch",      "Movement"),
        new ModDef("autoJump",    "Auto Jump",          "Automatisch springen",        "Movement"),
        new ModDef("autoSneak",   "Auto Sneak",         "Dauerhaft schleichen",        "Movement"),
        // Player
        new ModDef("autoEat",     "Nutrition Assist",   "Automatische Nahrung",        "Player"),
        new ModDef("antiHunger",  "Saturation Keep",    "Hunger-Stabilisierung",       "Player"),
        new ModDef("antiPoison",  "Effect Filter",      "Effekt-Filterung",            "Player"),
        new ModDef("antiAfk",     "Idle Prevention",    "Inaktivitäts-Schutz",         "Player"),
        new ModDef("autoRespawn", "Auto Respawn",       "Automatisch respawnen",       "Player"),
        new ModDef("chestStealer","Item Transfer",      "Kisten automatisch leeren",   "Player"),
        new ModDef("autoTool",    "Tool Switch",        "Bestes Werkzeug beim Abbau",  "Player"),
        new ModDef("autoArmor",   "Armor Equip",        "Rüstung automatisch anlegen", "Player"),
        // Render
        new ModDef("fullbright",  "Light Boost",        "Maximale Sichtweite",         "Render"),
        new ModDef("gammaBoost",  "Brightness Boost",   "Gamma-Vollhell (Regler)",     "Render"),
        new ModDef("noBob",       "Steady View",        "Kein Kamera-Wackeln",         "Render"),
        new ModDef("tracers",     "Path Display",       "Linien zu Zielen",            "Render"),
        new ModDef("boxFill",     "Box Display",        "Box-Darstellung",             "Render"),
        new ModDef("zoom",        "Zoom",               "Zoom (Taste halten)",         "Render"),
        new ModDef("noFog",       "Fog Remover",        "Nebel entfernen",             "Render"),
        new ModDef("noWeather",   "Weather Control",    "Wetter ausblenden",           "Render"),
        new ModDef("antiBlind",   "Vision Clarity",     "Sicht-Optimierung",           "Render"),
        new ModDef("coords",      "Koordinaten",        "XYZ auf dem HUD",             "Render"),
        new ModDef("betterTab",   "Better Tab",         "Alle Spieler in der Tab-Liste","Render"),
        // World
        new ModDef("susChunks",   "Chunk Analyzer",     "Chunk-Analyse",               "World"),
        new ModDef("nuker",       "Area Mining",        "Bereichs-Abbau",              "World"),
        // Session
        new ModDef("session",     "Session",            "Reset-Verhalten",             "Session"),
        // Tasten
        new ModDef("keybinds",    "Keybinds",           "Tastenbelegung",              "Tasten")
    );

    // ══════════════════════════════════════ STATE ═════════════════════════════

    private final Screen parent;
    private int    px, py, pw, ph;
    private String selMod      = "entityEsp";
    private int    leftScroll  = 0;
    private int    rightScroll = 0;
    private int    maxRScroll  = 0;
    private int    maxLScroll  = 0;
    private String rebindingKey = null;
    private boolean hoverLeft  = false;
    private boolean settingsClip = false;
    private String searchQuery = "";
    private EditBox searchBox;

    private final List<int[]>    hits       = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();

    // ══════════════════════════════════════ LIFECYCLE ═════════════════════════

    public VisionConfigScreen(Screen parent) {
        super(Component.literal("Visual Improvement"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 496);
        ph = Math.min(height - 16, 284);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        // Search box at the top of the left module list.
        searchBox = new EditBox(font, px + 3, py + HDR_H + 2, LEFT_W - 6, 12, Component.empty());
        searchBox.setBordered(false);
        searchBox.setMaxLength(48);
        searchBox.setTextColor(C_TEXT);
        searchBox.setHint(Component.literal("§8Suchen..."));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(v -> {
            searchQuery = v == null ? "" : v.toLowerCase(java.util.Locale.ROOT);
            leftScroll = 0; // filtered results should start at the top, not off-screen
        });
        addRenderableWidget(searchBox);

        if ("entityEsp".equals(selMod)) {
            int ex = px + LEFT_W + 5;
            int ey = py + ph - 22;
            int ew = pw - LEFT_W - 9;
            EditBox nb = new EditBox(font, ex, ey, ew, 14, Component.empty());
            nb.setMaxLength(512);
            nb.setValue(String.join(",", VisionConfig.get().enabledPlayerNames));
            nb.setHint(Component.literal("§8Spieler-Filter (leer = alle)"));
            nb.setResponder(v -> {
                VisionConfig c = VisionConfig.get();
                c.enabledPlayerNames.clear();
                for (String n : v.split(",")) { String t = n.trim(); if (!t.isEmpty()) c.enabledPlayerNames.add(t); }
                VisionConfig.save();
            });
            addRenderableWidget(nb);
        }
    }

    // ══════════════════════════════════════ MAIN RENDER ═══════════════════════

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        hitActions.clear();

        g.fill(0, 0, width, height, 0xA0000010);
        g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x60000018);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_DIVIDER);
        g.fill(px, py, px + pw, py + ph, C_BG);

        g.fill(px, py, px + pw, py + HDR_H, C_HDR);
        g.fill(px, py + HDR_H - 1, px + pw, py + HDR_H, C_IND_ON);
        g.drawString(font, "§l§2VISUAL §l§7IMPROVEMENT", px + 7, py + 6, C_ACCENT, false);
        int active = countActive();
        g.drawString(font, "§8[§a" + active + "§8]",
                px + 14 + font.width("VISUAL IMPROVEMENT"), py + 6, C_DIM, false);
        // PANIC quick button: disables every module instantly.
        int pbW = 46;
        int pbX = px + pw - 38 - pbW;
        drawBtn(g, pbX, py + 3, pbW, 14, "§c§lPANIC", mx, my);
        hit(pbX, py + 3, pbW, 14, () -> { VisionConfig.get().resetFeatureToggles(); VisionConfig.save(); });
        g.drawString(font, "§8ESC", px + pw - font.width("ESC") - 8, py + 6, C_DIM, false);
        hit(px + pw - 36, py, 36, HDR_H, () -> { VisionConfig.save(); onClose(); });

        int iy = py + HDR_H;
        int ih = ph - HDR_H;

        g.fill(px, iy, px + LEFT_W, py + ph, C_LEFT_BG);
        g.fill(px + LEFT_W, iy, px + LEFT_W + 1, py + ph, C_DIVIDER);
        g.fill(px + LEFT_W + 1, iy, px + pw, py + ph, C_RIGHT_BG);

        g.enableScissor(px, iy, px + LEFT_W, py + ph);
        renderLeft(g, mx, my, px, iy, LEFT_W, ih);
        g.disableScissor();

        int rx = px + LEFT_W + 1, rw = pw - LEFT_W - 1;
        g.enableScissor(rx, iy, rx + rw, py + ph);
        renderRight(g, mx, my, rx, iy, rw, ih);
        g.disableScissor();

        hoverLeft = mx < px + LEFT_W;
        super.render(g, mx, my, delta);
    }

    // ══════════════════════════════════════ LEFT PANEL ════════════════════════

    private void renderLeft(GuiGraphics g, int mx, int my, int lx, int ly, int lw, int lh) {
        int top = ly + SEARCH_H;
        int vh  = lh - SEARCH_H;
        int y = top - leftScroll;
        for (String cat : CATS) {
            List<ModDef> catMods = new ArrayList<>();
            for (ModDef m : MODS) if (m.cat().equals(cat) && matchesSearch(m)) catMods.add(m);
            if (catMods.isEmpty()) continue;

            if (vis(y, CAT_H, top, vh)) {
                g.fill(lx, y, lx + lw, y + CAT_H, C_CAT_HDR);
                g.drawString(font, cat, lx + 6, y + 3, C_DIM, false);
            }
            y += CAT_H;
            for (ModDef m : catMods) {
                if (vis(y, MOD_H, top, vh)) {
                    boolean sel   = m.id().equals(selMod);
                    boolean hover = inRect(mx, my, lx, Math.max(y, top), lw, MOD_H);
                    boolean on    = isOn(m.id());
                    if (sel)        g.fill(lx, y, lx + lw, y + MOD_H, C_SEL_BG);
                    else if (hover) g.fill(lx, y, lx + lw, y + MOD_H, 0x18FFFFFF);
                    g.fill(lx, y + 4, lx + 3, y + MOD_H - 4, on ? C_IND_ON : C_IND_OFF);
                    g.drawString(font, m.name(), lx + 8, y + 7, on ? C_TEXT : C_DIM, false);
                    final String mid = m.id();
                    final int hy = Math.max(y, top);            // clamp click area below search bar
                    hit(lx, hy, lw, y + MOD_H - hy, () -> selMod(mid));
                }
                y += MOD_H;
            }
            y += 3;
        }

        // Clamp bound for the left list so it can't be scrolled into empty space.
        maxLScroll = Math.max(0, y + leftScroll - (ly + lh));

        // Search-bar background, drawn last so scrolled rows are masked beneath it.
        g.fill(lx, ly, lx + lw, ly + SEARCH_H, C_LEFT_BG);
        g.fill(lx, ly + SEARCH_H - 1, lx + lw, ly + SEARCH_H, C_DIVIDER);
    }

    private int countActive() {
        int n = 0;
        for (String id : VisionConfig.TOGGLEABLE_MODULES) if (isOn(id)) n++;
        return n;
    }

    private boolean matchesSearch(ModDef m) {
        if (searchQuery == null || searchQuery.isEmpty()) return true;
        return m.name().toLowerCase(java.util.Locale.ROOT).contains(searchQuery)
            || m.id().toLowerCase(java.util.Locale.ROOT).contains(searchQuery)
            || m.cat().toLowerCase(java.util.Locale.ROOT).contains(searchQuery);
    }

    // ══════════════════════════════════════ RIGHT PANEL ═══════════════════════

    private int sX, sY, sW, sH, sMX, sMY, sCY;

    private void renderRight(GuiGraphics g, int mx, int my, int rx, int ry, int rw, int rh) {
        ModDef m = MODS.stream().filter(md -> md.id().equals(selMod)).findFirst().orElse(null);
        if (m == null) return;

        g.fill(rx, ry, rx + rw, ry + 16, 0xFF0B110C);
        g.fill(rx, ry + 15, rx + rw, ry + 16, 0xFF1E3824);
        String dot = isOn(m.id()) ? "§a●" : "§8●";
        g.drawString(font, dot + " §f" + m.name(), rx + 6, ry + 4, C_TEXT, false);
        g.drawString(font, "§8" + m.desc(),
                rx + 6 + font.width("● " + m.name()) + 6, ry + 4, C_DIM, false);

        int cy = ry + 16;
        int ch = rh - 16;
        int reserve = "entityEsp".equals(selMod) ? 20 : 0;
        ch -= reserve;

        g.enableScissor(rx, cy, rx + rw, cy + ch);
        sX = rx; sY = cy; sW = rw; sH = ch; sMX = mx; sMY = my;
        sCY = cy - rightScroll;
        settingsClip = true;   // clamp settings-row hit rects to [sY, sY+sH] while drawing them
        drawSettings(g, m.id(), VisionConfig.get());
        settingsClip = false;
        // Add rightScroll back (sCY was started at cy - rightScroll), else the bound
        // shrinks as you scroll and the bottom of long panels becomes unreachable.
        maxRScroll = Math.max(0, sCY + rightScroll - (cy + ch));
        g.disableScissor();

        if (reserve > 0) {
            g.drawString(font, "§8Spieler-Filter:", rx + 5, cy + ch + 3, C_DIM, false);
        }
    }

    // ══════════════════════════════════════ SETTINGS SWITCH ══════════════════

    private void drawSettings(GuiGraphics g, String id, VisionConfig c) {
        switch (id) {

        // ── ESP ──────────────────────────────────────────────────────────────
        case "entityEsp"  -> {
            sToggle(g, "Entity ESP",   c.entityEspEnabled,  () -> { c.entityEspEnabled  = !c.entityEspEnabled;  save(); });
            sRadius(g, "Radius",       c.entityEspRadius,  1, 16,
                () -> { c.entityEspRadius = Math.max(1,  c.entityEspRadius-1); save(); },
                () -> { c.entityEspRadius = Math.min(16, c.entityEspRadius+1); save(); });
            sToggle(g, "Info-Text (Name/Koord)", c.espLabels, () -> { c.espLabels = !c.espLabels; save(); });
            sSep(g, "Entity-Farben");
            for (String eid : VisionConfig.ALL_ENTITY_TYPES) sEntityRow(g, eid, c);
        }
        case "entityGlow" -> {
            sToggle(g, "Entity Glow",  c.entityGlowEnabled, () -> { c.entityGlowEnabled = !c.entityGlowEnabled; save(); });
            sDesc(g, "Vanilla Glow-Umriss durch Wände.");
            sDesc(g, "Überschreibt die ESP-Boxen.");
        }
        case "healthBar"  -> {
            sToggle(g, "Health Bar",   c.healthBarEnabled,  () -> { c.healthBarEnabled  = !c.healthBarEnabled;  save(); });
            sDesc(g, "Vanilla-Herzen über jeder Entity.");
            sDesc(g, "Benötigt Entity ESP.");
        }
        case "oreEsp"     -> {
            sToggle(g, "Ore ESP",      c.oreEspEnabled,     () -> { c.oreEspEnabled     = !c.oreEspEnabled;     save(); });
            sRadius(g, "Radius",       c.oreEspRadius,     1,  8,
                () -> { c.oreEspRadius = Math.max(1, c.oreEspRadius-1); save(); },
                () -> { c.oreEspRadius = Math.min(8, c.oreEspRadius+1); save(); });
            sToggle(g, "Info-Text (Koord)", c.espLabels, () -> { c.espLabels = !c.espLabels; save(); });
            sSep(g, "Erz-Farben");
            for (String oid : VisionConfig.ALL_ORES) sOreRow(g, oid, c);
        }
        case "itemEsp"    -> {
            sToggle(g, "Item ESP",     c.itemEspEnabled,    () -> { c.itemEspEnabled    = !c.itemEspEnabled;    save(); });
            sRadius(g, "Radius",       c.itemEspRadius,    1, 16,
                () -> { c.itemEspRadius = Math.max(1,  c.itemEspRadius-1); save(); },
                () -> { c.itemEspRadius = Math.min(16, c.itemEspRadius+1); save(); });
            sSep(g, "Farbe");
            sColorRow(g, "Items", parseRGB(c.itemEspColor), col -> { c.itemEspColor = rgb(col); save(); });
        }
        case "storageEsp" -> {
            sToggle(g, "Container ESP",c.storageEspEnabled, () -> { c.storageEspEnabled = !c.storageEspEnabled; save(); });
            sRadius(g, "Radius",       c.storageEspRadius, 1,  8,
                () -> { c.storageEspRadius = Math.max(1, c.storageEspRadius-1); save(); },
                () -> { c.storageEspRadius = Math.min(8, c.storageEspRadius+1); save(); });
            sSep(g, "Farben");
            sColorRow(g, "Kiste",      parseRGB(c.chestColor),      col -> { c.chestColor = rgb(col); save(); });
            sColorRow(g, "Fass",       parseRGB(c.barrelColor),     col -> { c.barrelColor = rgb(col); save(); });
            sColorRow(g, "Shulker",    parseRGB(c.shulkerColor),    col -> { c.shulkerColor = rgb(col); save(); });
            sColorRow(g, "Ender Kiste",parseRGB(c.enderChestColor), col -> { c.enderChestColor = rgb(col); save(); });
        }

        // ── Combat ───────────────────────────────────────────────────────────
        case "killAura"   -> {
            sToggle(g, "Kill Aura",        c.killAuraEnabled,    () -> { c.killAuraEnabled    = !c.killAuraEnabled;    save(); });
            sToggle(g, "Spieler targeten", c.killAuraPlayers,    () -> { c.killAuraPlayers    = !c.killAuraPlayers;    save(); });
            sToggle(g, "Mobs targeten",    c.killAuraMobs,       () -> { c.killAuraMobs       = !c.killAuraMobs;       save(); });
            sFloat(g,  "Reichweite",       c.killAuraRange,
                () -> { c.killAuraRange = Math.max(1.5f,  c.killAuraRange - 0.5f); save(); },
                () -> { c.killAuraRange = Math.min(10.0f, c.killAuraRange + 0.5f); save(); });
            sInt(g, "CPS", c.killAuraCps, "", 1, 20,
                () -> { c.killAuraCps = Math.max(1,  c.killAuraCps - 1); save(); },
                () -> { c.killAuraCps = Math.min(20, c.killAuraCps + 1); save(); });
            sToggle(g, "Rotation",         c.killAuraRotate,     () -> { c.killAuraRotate     = !c.killAuraRotate;     save(); });
            sPriority(g, "Priorität", c.killAuraPriority,
                () -> {
                    c.killAuraPriority = switch (c.killAuraPriority) {
                        case "Nearest"   -> "LowestHP";
                        case "LowestHP"  -> "HighestHP";
                        default          -> "Nearest";
                    };
                    save();
                },
                () -> {
                    c.killAuraPriority = switch (c.killAuraPriority) {
                        case "Nearest"   -> "HighestHP";
                        case "HighestHP" -> "LowestHP";
                        default          -> "Nearest";
                    };
                    save();
                });
            sFloat(g, "FOV",               c.killAuraFov,
                () -> { c.killAuraFov = Math.max(10f,  c.killAuraFov - 10f); save(); },
                () -> { c.killAuraFov = Math.min(360f, c.killAuraFov + 10f); save(); });
            sDesc(g, "Greift nächste Entity in Reichweite an.");
        }
        case "criticals"  -> {
            sToggle(g, "Criticals",        c.criticalsEnabled,   () -> { c.criticalsEnabled   = !c.criticalsEnabled;   save(); });
            sDesc(g, "Erzwingt kritische Treffer (150% DMG).");
        }
        case "autoClicker"-> {
            sToggle(g, "Auto Clicker",     c.autoClickerEnabled, () -> { c.autoClickerEnabled = !c.autoClickerEnabled; save(); });
            sInt(g, "CPS", c.autoClickerCps, "", 1, 20,
                () -> { c.autoClickerCps = Math.max(1,  c.autoClickerCps - 1); save(); },
                () -> { c.autoClickerCps = Math.min(20, c.autoClickerCps + 1); save(); });
            sDesc(g, "Linke Maustaste halten → auto klicken.");
        }
        case "velocity"   -> {
            sToggle(g, "Motion Adjust",    c.velocityEnabled,    () -> { c.velocityEnabled    = !c.velocityEnabled;    save(); });
            sFloat(g, "Horizontal %",      c.velocityXZ * 100f,
                () -> { c.velocityXZ = Math.max(0f, c.velocityXZ - 0.05f); save(); },
                () -> { c.velocityXZ = Math.min(1f, c.velocityXZ + 0.05f); save(); });
            sFloat(g, "Vertikal %",        c.velocityY * 100f,
                () -> { c.velocityY = Math.max(0f, c.velocityY - 0.05f); save(); },
                () -> { c.velocityY = Math.min(1f, c.velocityY + 0.05f); save(); });
            sDesc(g, "0% = komplett abbrechen, 100% = normal");
        }
        case "autoTotem"  -> {
            sToggle(g, "Auto Totem",       c.autoTotemEnabled,   () -> { c.autoTotemEnabled   = !c.autoTotemEnabled;   save(); });
            sFloat(g, "Aktivieren bei HP", c.autoTotemHpThresh,
                () -> { c.autoTotemHpThresh = Math.max(1f,  c.autoTotemHpThresh - 1f); save(); },
                () -> { c.autoTotemHpThresh = Math.min(20f, c.autoTotemHpThresh + 1f); save(); });
            sDesc(g, "Bewegt Totem automatisch in Offhand.");
        }
        case "noHurtCam"  -> {
            sToggle(g, "NoHurtCam",        c.noHurtCamEnabled,   () -> { c.noHurtCamEnabled   = !c.noHurtCamEnabled;   save(); });
            sDesc(g, "Kein Kamera-Wackeln bei Schaden.");
        }

        // ── Movement ─────────────────────────────────────────────────────────
        case "sprint"     -> {
            sToggle(g, "Sprint",           c.sprintEnabled,      () -> { c.sprintEnabled      = !c.sprintEnabled;      save(); });
            sDesc(g, "Läuft immer. Nicht beim Schleichen.");
        }
        case "fly"        -> {
            sToggle(g, "Fly",              c.flyEnabled,         () -> { c.flyEnabled         = !c.flyEnabled;         save(); });
            sFloat(g, "Geschwindigkeit",   c.flySpeed,
                () -> { c.flySpeed = Math.max(0.5f, c.flySpeed - 0.5f); save(); },
                () -> { c.flySpeed = Math.min(5.0f, c.flySpeed + 0.5f); save(); });
            sDesc(g, "Creative-Fliegen. Server muss erlauben.");
        }
        case "speed"      -> {
            sToggle(g, "Speed",            c.speedEnabled,       () -> { c.speedEnabled       = !c.speedEnabled;       save(); });
            sFloat(g, "Multiplikator",     c.speedMultiplier,
                () -> { c.speedMultiplier = Math.max(1.0f, c.speedMultiplier - 0.5f); save(); },
                () -> { c.speedMultiplier = Math.min(5.0f, c.speedMultiplier + 0.5f); save(); });
            sDesc(g, "Erhöht Bewegungsgeschwindigkeit.");
        }
        case "noFall"     -> {
            sToggle(g, "NoFall",           c.noFallEnabled,      () -> { c.noFallEnabled      = !c.noFallEnabled;      save(); });
            sDesc(g, "Kein Fallschaden.");
        }
        case "step"       -> {
            sToggle(g, "Step",             c.stepEnabled,        () -> { c.stepEnabled        = !c.stepEnabled;        save(); });
            sFloat(g, "Stufenhöhe",        c.stepHeight,
                () -> { c.stepHeight = Math.max(0.6f, c.stepHeight - 0.25f); save(); },
                () -> { c.stepHeight = Math.min(2.0f, c.stepHeight + 0.25f); save(); });
            sDesc(g, "Erklimmt höhere Stufen automatisch.");
        }
        case "jesus"      -> {
            sToggle(g, "Jesus",            c.jesusEnabled,       () -> { c.jesusEnabled       = !c.jesusEnabled;       save(); });
            sDesc(g, "Läuft auf Wasser.");
        }
        case "noSlow"     -> {
            sToggle(g, "NoSlow",           c.noSlowEnabled,      () -> { c.noSlowEnabled      = !c.noSlowEnabled;      save(); });
            sDesc(g, "Kein Slow durch Cobweb, Beerensträucher.");
        }
        case "scaffold"   -> {
            sToggle(g, "Scaffold",         c.scaffoldEnabled,    () -> { c.scaffoldEnabled    = !c.scaffoldEnabled;    save(); });
            sDesc(g, "Legt Blöcke unter die Füße beim Laufen.");
            sDesc(g, "Block im Hotbar muss vorhanden sein.");
        }
        case "surround"   -> {
            sToggle(g, "Block Surround",   c.surroundEnabled,    () -> { c.surroundEnabled    = !c.surroundEnabled;    save(); });
            sDesc(g, "Platziert Blöcke um den Spieler.");
        }
        case "safeWalk"   -> {
            sToggle(g, "Edge Protection",  c.safeWalkEnabled,    () -> { c.safeWalkEnabled    = !c.safeWalkEnabled;    save(); });
            sDesc(g, "Verhindert das Herunterfallen von Kanten.");
        }
        case "invMove"    -> {
            sToggle(g, "Inventory Move",   c.invMoveEnabled,     () -> { c.invMoveEnabled     = !c.invMoveEnabled;     save(); });
            sDesc(g, "Erlaubt Bewegung während Inventar geöffnet ist.");
        }
        case "spider"     -> {
            sToggle(g, "Wall Climb",       c.spiderEnabled,      () -> { c.spiderEnabled      = !c.spiderEnabled;      save(); });
            sDesc(g, "Klettert an Wänden hoch wie eine Spinne.");
        }
        case "antiVoid"   -> {
            sToggle(g, "Void Protection",  c.antiVoidEnabled,    () -> { c.antiVoidEnabled    = !c.antiVoidEnabled;    save(); });
            sDesc(g, "Stoppt den Fall knapp über der Leere.");
        }
        case "autoWalk"   -> {
            sToggle(g, "Auto Walk",        c.autoWalkEnabled,    () -> { c.autoWalkEnabled    = !c.autoWalkEnabled;    save(); });
            sDesc(g, "Hält die Vorwärts-Taste automatisch.");
        }
        case "glide"      -> {
            sToggle(g, "Slow Descent",     c.glideEnabled,       () -> { c.glideEnabled       = !c.glideEnabled;       save(); });
            sDesc(g, "Verlangsamt das Fallen (wie Elytra-Gleiten).");
        }
        case "fastLadder" -> {
            sToggle(g, "Fast Climb",       c.fastLadderEnabled,  () -> { c.fastLadderEnabled  = !c.fastLadderEnabled;  save(); });
            sDesc(g, "Klettert schneller an Leitern/Ranken hoch.");
        }
        case "autoJump"   -> {
            sToggle(g, "Auto Jump",        c.autoJumpEnabled,    () -> { c.autoJumpEnabled    = !c.autoJumpEnabled;    save(); });
            sDesc(g, "Springt automatisch beim Vorwärtslaufen.");
        }
        case "autoSneak"  -> {
            sToggle(g, "Auto Sneak",       c.autoSneakEnabled,   () -> { c.autoSneakEnabled   = !c.autoSneakEnabled;   save(); });
            sDesc(g, "Hält die Schleichen-Taste dauerhaft.");
        }
        case "autoLog"    -> {
            sToggle(g, "Auto Disconnect",  c.autoLogEnabled,     () -> { c.autoLogEnabled     = !c.autoLogEnabled;     save(); });
            sFloat(g, "HP Schwelle",       c.autoLogHp,
                () -> { c.autoLogHp = Math.max(1f,  c.autoLogHp - 1f); save(); },
                () -> { c.autoLogHp = Math.min(20f, c.autoLogHp + 1f); save(); });
        }
        case "maceDmg"    -> {
            sToggle(g, "Mace Boost",       c.maceDmgEnabled,         () -> { c.maceDmgEnabled         = !c.maceDmgEnabled;         save(); });
            sDesc(g, "Springt automatisch hoch, greift beim Fallen.");
            sDesc(g, "Mace in der Hand erforderlich.");
        }
        case "maceDmgClassic" -> {
            sToggle(g, "Mace Classic",     c.maceDmgClassicEnabled,  () -> { c.maceDmgClassicEnabled  = !c.maceDmgClassicEnabled;  save(); });
            sDesc(g, "Greift automatisch beim Fallen mit Mace an.");
            sDesc(g, "Kein Auto-Sprung — manuell oder Wind Burst.");
        }
        case "stunSlam"   -> {
            sToggle(g, "StunSlam",         c.stunSlamEnabled,        () -> { c.stunSlamEnabled        = !c.stunSlamEnabled;        save(); });
            sFloat(g, "Min. Fallhöhe",     c.stunSlamMinFall,
                () -> { c.stunSlamMinFall = Math.max(1f,  c.stunSlamMinFall - 0.5f); save(); },
                () -> { c.stunSlamMinFall = Math.min(20f, c.stunSlamMinFall + 0.5f); save(); });
            sDesc(g, "Stunnt Schild wenn vom Angriff aus der Luft.");
            sDesc(g, "Wechselt automatisch zur Axt im Hotbar.");
            sDesc(g, "Danach wird vorheriger Slot wiederhergestellt.");
        }
        case "autoMace"   -> {
            sToggle(g, "Auto Mace",        c.autoMaceEnabled,    () -> { c.autoMaceEnabled    = !c.autoMaceEnabled;    save(); });
            sFloat(g, "Reichweite",        c.autoMaceRange,
                () -> { c.autoMaceRange = Math.max(2.0f, c.autoMaceRange - 0.5f); save(); },
                () -> { c.autoMaceRange = Math.min(8.0f, c.autoMaceRange + 0.5f); save(); });
            sFloat(g, "Dreh-Speed °/Tick", c.autoMaceSpeed,
                () -> { c.autoMaceSpeed = Math.max(5f,  c.autoMaceSpeed - 5f); save(); },
                () -> { c.autoMaceSpeed = Math.min(90f, c.autoMaceSpeed + 5f); save(); });
            sDesc(g, "Dreht sanft zum nächsten Gegner (nur mit Mace).");
            sDesc(g, "Niedriger = weicher/natürlicher.");
        }
        case "reach"      -> {
            sToggle(g, "Reach Extend",     c.reachEnabled,       () -> { c.reachEnabled       = !c.reachEnabled;       save(); });
            sFloat(g, "Reichweite",        c.reachDistance,
                () -> { c.reachDistance = Math.max(3.0f, c.reachDistance - 0.25f); save(); },
                () -> { c.reachDistance = Math.min(6.0f, c.reachDistance + 0.25f); save(); });
            sDesc(g, "Erhöht die Interaktions-Reichweite.");
            sDesc(g, "Vanilla = 3.0. Server kann begrenzen.");
        }
        case "triggerBot" -> {
            sToggle(g, "Trigger Assist",   c.triggerBotEnabled,  () -> { c.triggerBotEnabled  = !c.triggerBotEnabled;  save(); });
            sInt(g, "CPS", c.triggerBotCps, "", 1, 20,
                () -> { c.triggerBotCps = Math.max(1,  c.triggerBotCps - 1); save(); },
                () -> { c.triggerBotCps = Math.min(20, c.triggerBotCps + 1); save(); });
            sDesc(g, "Greift Ziel am Fadenkreuz automatisch an.");
        }
        case "autoWeapon" -> {
            sToggle(g, "Weapon Switch",    c.autoWeaponEnabled,  () -> { c.autoWeaponEnabled  = !c.autoWeaponEnabled;  save(); });
            sDesc(g, "Wechselt beim Angriff zur besten Waffe.");
            sDesc(g, "Schwert bevorzugt, sonst Axt.");
        }

        // ── Player ───────────────────────────────────────────────────────────
        case "autoEat"    -> {
            sToggle(g, "Auto Eat",         c.autoEatEnabled,     () -> { c.autoEatEnabled     = !c.autoEatEnabled;     save(); });
            sInt(g, "Hunger-Schwelle", c.autoEatThreshold, "/20", 1, 20,
                () -> { c.autoEatThreshold = Math.max(1,  c.autoEatThreshold - 1); save(); },
                () -> { c.autoEatThreshold = Math.min(20, c.autoEatThreshold + 1); save(); });
            sDesc(g, "Isst wenn Hunger < Schwelle.");
        }
        case "antiHunger" -> {
            sToggle(g, "AntiHunger",       c.antiHungerEnabled,  () -> { c.antiHungerEnabled  = !c.antiHungerEnabled;  save(); });
            sDesc(g, "Verhindert Hunger-Abbau (client-seitig).");
        }
        case "antiPoison" -> {
            sToggle(g, "AntiPoison",       c.antiPoisonEnabled,  () -> { c.antiPoisonEnabled  = !c.antiPoisonEnabled;  save(); });
            sDesc(g, "Entfernt: Gift, Wither, Schwäche,");
            sDesc(g, "Langsamkeit, Bergbaumüdigkeit.");
        }
        case "antiAfk"    -> {
            sToggle(g, "AntiAFK",          c.antiAfkEnabled,     () -> { c.antiAfkEnabled     = !c.antiAfkEnabled;     save(); });
            sInt(g, "Intervall", c.antiAfkInterval / 20, "Sek", 5, 60,
                () -> { c.antiAfkInterval = Math.max(100, c.antiAfkInterval - 20); save(); },
                () -> { c.antiAfkInterval = Math.min(1200, c.antiAfkInterval + 20); save(); });
            sDesc(g, "Mikro-Rotation gegen AFK-Kick.");
        }
        case "autoRespawn" -> {
            sToggle(g, "Auto Respawn",     c.autoRespawnEnabled, () -> { c.autoRespawnEnabled = !c.autoRespawnEnabled; save(); });
            sDesc(g, "Respawnt automatisch nach dem Tod.");
        }
        case "chestStealer" -> {
            sToggle(g, "Item Transfer",    c.chestStealerEnabled,() -> { c.chestStealerEnabled = !c.chestStealerEnabled; save(); });
            sDesc(g, "Leert Kisten automatisch in dein Inventar.");
        }
        case "autoTool"   -> {
            sToggle(g, "Tool Switch",      c.autoToolEnabled,    () -> { c.autoToolEnabled    = !c.autoToolEnabled;    save(); });
            sDesc(g, "Wählt beim Abbau das schnellste Werkzeug.");
            sDesc(g, "Nur während du angreifst/abbaust.");
        }
        case "autoArmor"  -> {
            sToggle(g, "Armor Equip",      c.autoArmorEnabled,   () -> { c.autoArmorEnabled   = !c.autoArmorEnabled;   save(); });
            sDesc(g, "Legt beste Rüstung aus dem Inventar an.");
            sDesc(g, "Nur leere Rüstungs-Slots werden gefüllt.");
        }

        // ── Render ───────────────────────────────────────────────────────────
        case "fullbright" -> {
            sToggle(g, "Fullbright",       c.fullbrightEnabled,  () -> { c.fullbrightEnabled  = !c.fullbrightEnabled;  save(); });
            sDesc(g, "Versteckter Night-Vision-Effekt.");
            sSep(g, "Taste");
            sKeybindRow(g, "Fullbright", "fullbright", c.moduleKeys.getOrDefault("fullbright", 0), c);
        }
        case "gammaBoost" -> {
            sToggle(g, "Brightness Boost", c.gammaBoostEnabled,  () -> { c.gammaBoostEnabled = !c.gammaBoostEnabled; save(); });
            sFloat(g, "Gamma",             c.gammaValue,
                () -> { c.gammaValue = Math.max(1.0f,  c.gammaValue - 1.0f); save(); },
                () -> { c.gammaValue = Math.min(16.0f, c.gammaValue + 1.0f); save(); });
            sDesc(g, "Echtes Vollhell über Gamma — heller als Night Vision.");
            sDesc(g, "Hellt Terrain UND Entities auf. 1 = Vanilla-Max.");
            sSep(g, "Taste");
            sKeybindRow(g, "Brightness", "gammaBoost", c.moduleKeys.getOrDefault("gammaBoost", 0), c);
        }
        case "noBob"      -> {
            sToggle(g, "Steady View",      c.noBobEnabled,       () -> { c.noBobEnabled       = !c.noBobEnabled;       save(); });
            sDesc(g, "Entfernt das Auf-/Ab-Wackeln beim Laufen.");
        }
        case "tracers"    -> {
            sToggle(g, "Tracer Lines",     c.globalLinesEnabled, () -> { c.globalLinesEnabled = !c.globalLinesEnabled; save(); });
            sDesc(g, "Linien zu Entities/Erzen (ESP aktiv).");
        }
        case "boxFill"    -> {
            sToggle(g, "Filled Boxes",     c.fillBoxes,          () -> { c.fillBoxes          = !c.fillBoxes;          save(); });
            sDesc(g, "Ausgefüllte ESP-Boxen.");
        }
        case "zoom"       -> {
            sDesc(g, "Taste halten zum Zoomen.");
            sFloat(g, "Zoom FOV",          c.zoomFov,
                () -> { c.zoomFov = Math.max(5f,  c.zoomFov - 1f); save(); },
                () -> { c.zoomFov = Math.min(60f, c.zoomFov + 1f); save(); });
            sSep(g, "Taste");
            sKeybindRow(g, "Zoom", "zoom", c.keyZoom, c);
        }
        case "noFog"      -> {
            sToggle(g, "NoFog",            c.noFogEnabled,       () -> { c.noFogEnabled       = !c.noFogEnabled;       save(); });
            sDesc(g, "Entfernt Terrain/Wasser/Lava-Nebel.");
        }
        case "noWeather"  -> {
            sToggle(g, "NoWeather",        c.noWeatherEnabled,   () -> { c.noWeatherEnabled   = !c.noWeatherEnabled;   save(); });
            sDesc(g, "Versteckt Regen und Schnee-Partikel.");
        }
        case "antiBlind"  -> {
            sToggle(g, "AntiBlind",        c.antiBlindEnabled,   () -> { c.antiBlindEnabled   = !c.antiBlindEnabled;   save(); });
            sDesc(g, "Verhindert Blindness und Darkness.");
            sDesc(g, "Auch von Wardens.");
        }
        case "coords"     -> {
            sToggle(g, "Koordinaten",      c.coordsHudEnabled,   () -> { c.coordsHudEnabled   = !c.coordsHudEnabled;   save(); });
            sDesc(g, "Zeigt XYZ + Himmelsrichtung auf HUD.");
        }
        case "betterTab"  -> {
            sToggle(g, "Better Tab",       c.betterTabEnabled,   () -> { c.betterTabEnabled   = !c.betterTabEnabled;   save(); });
            sDesc(g, "Zeigt ALLE Spieler in der Tab-Liste.");
            sDesc(g, "Hebt das Vanilla-Limit von 80 auf.");
        }

        // ── World ────────────────────────────────────────────────────────────
        case "susChunks"  -> {
            sToggle(g, "Sus Chunks",       c.susChunksEnabled,   () -> { c.susChunksEnabled   = !c.susChunksEnabled;   save(); });
            sToggle(g, "Chunk-Grenzen",    c.showAllChunkBorders,() -> { c.showAllChunkBorders = !c.showAllChunkBorders; save(); });
            sRadius(g, "Radius",           c.susChunksRadius,  1, 8,
                () -> { c.susChunksRadius = Math.max(1, c.susChunksRadius - 1); save(); },
                () -> { c.susChunksRadius = Math.min(8, c.susChunksRadius + 1); save(); });
            sSep(g, "Erkennen");
            sToggle(g, "Kisten",           c.susDetectChests,    () -> { c.susDetectChests    = !c.susDetectChests;    save(); });
            sToggle(g, "Spawner",          c.susDetectSpawners,  () -> { c.susDetectSpawners  = !c.susDetectSpawners;  save(); });
            sToggle(g, "Redstone",         c.susDetectRedstone,  () -> { c.susDetectRedstone  = !c.susDetectRedstone;  save(); });
        }
        case "nuker"      -> {
            sToggle(g, "Nuker",            c.nukerEnabled,       () -> { c.nukerEnabled       = !c.nukerEnabled;       save(); });
            sFloat(g, "Reichweite",        c.nukerRange,
                () -> { c.nukerRange = Math.max(1.5f, c.nukerRange - 0.5f); save(); },
                () -> { c.nukerRange = Math.min(6.0f, c.nukerRange + 0.5f); save(); });
            sDesc(g, "Bricht Blöcke in Reichweite.");
            sDesc(g, "Kreativ: sofort. Überleben: normal.");
        }

        // ── Session ──────────────────────────────────────────────────────────
        case "session"    -> {
            sToggle(g, "Reset bei Relog",    c.resetOnRelog,   () -> { c.resetOnRelog   = !c.resetOnRelog;   save(); });
            sDesc(g, "Alle Features deaktivieren wenn du den Server verlässt.");
            sToggle(g, "Reset bei Neustart", c.resetOnRestart, () -> { c.resetOnRestart = !c.resetOnRestart; save(); });
            sDesc(g, "Alle Features deaktivieren beim Spielstart.");
        }

        // ── Tasten ───────────────────────────────────────────────────────────
        case "keybinds"   -> {
            sDesc(g, "[Ändern] klicken, dann Taste drücken. §8Nicht im MC-Menü.");
            sCY += 2;
            // Special keys (not toggles)
            sSep(g, "Spezial");
            sKeybindRow(g, "Config öffnen", "openConfig", c.keyOpenConfig, c);
            sKeybindRow(g, "Zoom (halten)", "zoom",       c.keyZoom,       c);
            sKeybindRow(g, "Panic",         "panic",      c.keyPanic,      c);
            sKeybindRow(g, "Panic 2 (nur AUS)", "panic2", c.keyPanic2,     c);
            // Per-module keys grouped by category
            String lastCat = "";
            for (ModDef m : MODS) {
                if (!VisionConfig.TOGGLEABLE_MODULES.contains(m.id())) continue;
                if (!m.cat().equals(lastCat)) { sSep(g, m.cat()); lastCat = m.cat(); }
                sKeybindRow(g, m.name(), m.id(), c.moduleKeys.getOrDefault(m.id(), 0), c);
            }
        }
        }
    }

    // ══════════════════════════════════════ SETTINGS HELPERS ══════════════════

    private void sToggle(GuiGraphics g, String label, boolean val, Runnable toggle) {
        int y = sCY;
        if (vis(y, S_ROW, sY, sH)) {
            boolean hover = inRect(sMX, sMY, sX, y, sW, S_ROW);
            if (hover) g.fill(sX, y, sX + sW, y + S_ROW, 0x14FFFFFF);
            int ic = val ? C_IND_ON : C_IND_OFF;
            g.fill(sX + 5, y + 5, sX + 13, y + 13, ic);
            g.fill(sX + 6, y + 6, sX + 12, y + 12, val ? 0xCC1A7040 : 0xCC101A12);
            g.drawString(font, label, sX + 17, y + 5, val ? C_TEXT : C_DIM, false);
            g.drawString(font, val ? "§aAN" : "§8AUS", sX + sW - 26, y + 5, C_DIM, false);
            hit(sX, y, sW, S_ROW, toggle);
        }
        sCY += S_ROW;
    }

    private void sRadius(GuiGraphics g, String label, int val, int min, int max,
                          Runnable dec, Runnable inc) {
        int y = sCY;
        if (vis(y, S_ROW, sY, sH)) {
            g.drawString(font, label + ": §f" + val + " §8Chunks", sX + 5, y + 5, C_DIM, false);
            int bx = sX + sW - 42;
            drawBtn(g, bx,    y + 2, 18, 14, "§c−", sMX, sMY); hit(bx,    y + 2, 18, 14, dec);
            drawBtn(g, bx+20, y + 2, 18, 14, "§a+", sMX, sMY); hit(bx+20, y + 2, 18, 14, inc);
        }
        sCY += S_ROW;
    }

    private void sInt(GuiGraphics g, String label, int val, String unit,
                       int min, int max, Runnable dec, Runnable inc) {
        int y = sCY;
        if (vis(y, S_ROW, sY, sH)) {
            String disp = unit.isEmpty() ? label + ": §f" + val
                                         : label + ": §f" + val + " §8" + unit;
            g.drawString(font, disp, sX + 5, y + 5, C_DIM, false);
            int bx = sX + sW - 42;
            drawBtn(g, bx,    y + 2, 18, 14, "§c−", sMX, sMY); hit(bx,    y + 2, 18, 14, dec);
            drawBtn(g, bx+20, y + 2, 18, 14, "§a+", sMX, sMY); hit(bx+20, y + 2, 18, 14, inc);
        }
        sCY += S_ROW;
    }

    private void sPriority(GuiGraphics g, String label, String val, Runnable dec, Runnable inc) {
        int y = sCY;
        if (vis(y, S_ROW, sY, sH)) {
            g.drawString(font, label + ": §f" + val, sX + 5, y + 5, C_DIM, false);
            int bx = sX + sW - 42;
            drawBtn(g, bx,    y + 2, 18, 14, "§c←", sMX, sMY); hit(bx,    y + 2, 18, 14, dec);
            drawBtn(g, bx+20, y + 2, 18, 14, "§a→", sMX, sMY); hit(bx+20, y + 2, 18, 14, inc);
        }
        sCY += S_ROW;
    }

    private void sFloat(GuiGraphics g, String label, float val, Runnable dec, Runnable inc) {
        int y = sCY;
        if (vis(y, S_ROW, sY, sH)) {
            g.drawString(font, label + ": §f" + String.format("%.1f", val), sX + 5, y + 5, C_DIM, false);
            int bx = sX + sW - 42;
            drawBtn(g, bx,    y + 2, 18, 14, "§c−", sMX, sMY); hit(bx,    y + 2, 18, 14, dec);
            drawBtn(g, bx+20, y + 2, 18, 14, "§a+", sMX, sMY); hit(bx+20, y + 2, 18, 14, inc);
        }
        sCY += S_ROW;
    }

    private void sSep(GuiGraphics g, String label) {
        int y = sCY;
        if (vis(y, 14, sY, sH)) {
            g.fill(sX, y, sX + sW, y + 13, 0xFF0C0C1A);
            g.fill(sX, y + 12, sX + sW, y + 13, 0xFF1A2E1C);
            g.drawString(font, "§8" + label, sX + 5, y + 3, 0xFF2E4830, false);
        }
        sCY += 14;
    }

    private void sDesc(GuiGraphics g, String text) {
        int y = sCY;
        if (vis(y, 11, sY, sH))
            g.drawString(font, text, sX + 16, y + 1, C_DIM, false);
        sCY += 11;
    }

    private void sColorRow(GuiGraphics g, String label, int currentRgb,
                            java.util.function.IntConsumer onPick) {
        int y = sCY;
        if (vis(y, S_ROW, sY, sH)) {
            g.drawString(font, label, sX + 5, y + 5, C_DIM, false);
            int swX = sX + 72;
            drawSwatch(g, swX, y + 4, 12, 11, currentRgb, true, null);
            swX += 15;
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final int pc = PALETTE[ci];
                boolean sel = (pc & 0xFFFFFF) == (currentRgb & 0xFFFFFF);
                drawSwatch(g, swX + ci * 12, y + 4, 11, 11, pc & 0xFFFFFF, sel,
                        () -> onPick.accept(pc));
            }
        }
        sCY += S_ROW;
    }

    private void sEntityRow(GuiGraphics g, String id, VisionConfig c) {
        int y = sCY;
        if (vis(y, E_ROW, sY, sH)) {
            boolean on    = c.enabledEntityTypes.contains(id);
            boolean hover = inRect(sMX, sMY, sX, y, sW, E_ROW);
            if (hover) g.fill(sX, y, sX + sW, y + E_ROW, 0x0CFFFFFF);
            int ic = on ? C_IND_ON : C_IND_OFF;
            g.fill(sX + 4, y + 5, sX + 10, y + 11, ic);
            hit(sX + 4, y + 5, 6, 6, () -> { toggleSet(c.enabledEntityTypes, id); save(); });
            g.drawString(font, VisionConfig.displayName(id), sX + 13, y + 4, on ? C_TEXT : C_DIM, false);
            int nameW = font.width(VisionConfig.displayName(id));
            int cx    = sX + 13 + Math.max(nameW, 70) + 4;
            int boxRgb = VisionConfig.parseColor(c.entityBoxColors.getOrDefault(id, "#FFFF0000")) & 0xFFFFFF;
            drawSwatch(g, cx, y + 4, 10, 9, boxRgb, true, null); cx += 13;
            for (int ci = 0; ci < 5; ci++) {
                final String fid = id; final int pc = PALETTE[ci];
                drawSwatch(g, cx + ci * 11, y + 4, 10, 9, pc & 0xFFFFFF, false,
                        () -> { c.entityBoxColors.put(fid, rgb(pc)); save(); });
            }
            cx += 57;
            boolean lineOn = c.entityLinesEnabled.contains(id);
            g.fill(cx, y + 4, cx + 9, y + 13, lineOn ? C_IND_ON : C_IND_OFF);
            g.drawString(font, "§8L", cx + 1, y + 4, 0xFFAABBAA, false);
            hit(cx, y + 4, 9, 9, () -> { toggleSet(c.entityLinesEnabled, id); save(); }); cx += 12;
            int lineRgb = VisionConfig.parseColor(c.entityLineColors.getOrDefault(id, "#FFFF0000")) & 0xFFFFFF;
            drawSwatch(g, cx, y + 4, 10, 9, lineRgb, true, null); cx += 13;
            for (int ci = 0; ci < 5; ci++) {
                final String fid = id; final int pc = PALETTE[ci + 5];
                drawSwatch(g, cx + ci * 11, y + 4, 10, 9, pc & 0xFFFFFF, false,
                        () -> { c.entityLineColors.put(fid, rgb(pc)); save(); });
            }
        }
        sCY += E_ROW;
    }

    private void sOreRow(GuiGraphics g, String id, VisionConfig c) {
        int y = sCY;
        if (vis(y, E_ROW, sY, sH)) {
            boolean on    = c.enabledOres.contains(id);
            boolean hover = inRect(sMX, sMY, sX, y, sW, E_ROW);
            if (hover) g.fill(sX, y, sX + sW, y + E_ROW, 0x0CFFFFFF);
            g.fill(sX + 4, y + 5, sX + 10, y + 11, on ? C_IND_ON : C_IND_OFF);
            hit(sX + 4, y + 5, 6, 6, () -> { toggleSet(c.enabledOres, id); save(); });
            g.drawString(font, VisionConfig.displayName(id), sX + 13, y + 4, on ? C_TEXT : C_DIM, false);
            int nameW = font.width(VisionConfig.displayName(id));
            int cx    = sX + 13 + Math.max(nameW, 80) + 4;
            int bRgb = VisionConfig.parseColor(c.oreBoxColors.getOrDefault(id, "#FFFFFFFF")) & 0xFFFFFF;
            drawSwatch(g, cx, y + 4, 10, 9, bRgb, true, null); cx += 13;
            for (int ci = 0; ci < 5; ci++) {
                final String fid = id; final int pc = PALETTE[ci];
                drawSwatch(g, cx + ci * 11, y + 4, 10, 9, pc & 0xFFFFFF, false,
                        () -> { c.oreBoxColors.put(fid, rgb(pc)); save(); });
            }
            cx += 57;
            boolean lineOn = c.oreLinesEnabled.contains(id);
            g.fill(cx, y + 4, cx + 9, y + 13, lineOn ? C_IND_ON : C_IND_OFF);
            g.drawString(font, "§8L", cx + 1, y + 4, 0xFFAABBAA, false);
            hit(cx, y + 4, 9, 9, () -> { toggleSet(c.oreLinesEnabled, id); save(); }); cx += 12;
            int lRgb = VisionConfig.parseColor(c.oreLineColors.getOrDefault(id, "#FFFFFFFF")) & 0xFFFFFF;
            drawSwatch(g, cx, y + 4, 10, 9, lRgb, true, null); cx += 13;
            for (int ci = 0; ci < 5; ci++) {
                final String fid = id; final int pc = PALETTE[ci + 5];
                drawSwatch(g, cx + ci * 11, y + 4, 10, 9, pc & 0xFFFFFF, false,
                        () -> { c.oreLineColors.put(fid, rgb(pc)); save(); });
            }
        }
        sCY += E_ROW;
    }

    private void sKeybindRow(GuiGraphics g, String label, String bindId, int keyCode, VisionConfig c) {
        int y = sCY;
        if (vis(y, S_ROW, sY, sH)) {
            boolean waiting = bindId.equals(rebindingKey);
            g.drawString(font, label, sX + 5, y + 5, C_TEXT, false);
            String keyStr = waiting ? "§e< Taste... §8Entf=Keine §e>"
                    : (keyCode > 0 ? "§a" + keyName(keyCode) : "§8Keine");
            int kw = font.width(keyStr.replaceAll("§.", ""));
            g.drawString(font, keyStr, sX + sW - kw - 52, y + 5, C_TEXT, false);
            String btnLabel = waiting ? "§cAbbr." : "§7Ändern";
            drawBtn(g, sX + sW - 48, y + 2, 44, 14, btnLabel, sMX, sMY);
            hit(sX + sW - 48, y + 2, 44, 14, () -> rebindingKey = waiting ? null : bindId);
        }
        sCY += S_ROW;
    }

    // ══════════════════════════════════════ PRIMITIVE DRAWING ═════════════════

    private void drawBtn(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my) {
        boolean hover = inRect(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? C_BTN_H : C_BTN);
        g.fill(x, y, x + w, y + 1, C_BTN_BD);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF090910);
        String plain = label.replaceAll("§.", "");
        g.drawString(font, label, x + (w - font.width(plain)) / 2, y + (h - 8) / 2, C_TEXT, false);
    }

    private void drawSwatch(GuiGraphics g, int x, int y, int w, int h,
                             int rgb, boolean current, Runnable onClick) {
        boolean hover = !current && onClick != null && inRect(sMX, sMY, x, y, w, h);
        int borderC = current ? 0xFFAABBAA : (hover ? 0xFFAAFF44 : 0xFF2A3F2C);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, borderC);
        g.fill(x, y, x + w, y + h, 0xFF000000 | rgb);
        if (onClick != null) hit(x, y, w, h, onClick);
    }

    // ══════════════════════════════════════ INPUT ═════════════════════════════

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x();
            double my = click.y();
            for (int i = 0; i < hits.size(); i++) {
                int[] z = hits.get(i);
                if (mx >= z[0] && mx < z[0] + z[2] && my >= z[1] && my < z[1] + z[3]) {
                    hitActions.get(i).run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int delta = (int)(-sy * 14);
        if (hoverLeft) leftScroll  = Math.max(0, Math.min(maxLScroll, leftScroll + delta));
        else           rightScroll = Math.max(0, Math.min(maxRScroll, rightScroll + delta));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (rebindingKey != null) {
            int keyCode = event.key();
            // Delete/Backspace clears the bind (→ "Keine"); Escape cancels without change.
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) keyCode = 0;
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                VisionConfig c = VisionConfig.get();
                if (c.moduleKeys.containsKey(rebindingKey)) {
                    c.moduleKeys.put(rebindingKey, keyCode);
                } else switch (rebindingKey) {
                    case "openConfig" -> c.keyOpenConfig = keyCode;
                    case "zoom"       -> c.keyZoom       = keyCode;
                    case "panic"      -> c.keyPanic      = keyCode;
                    case "panic2"     -> c.keyPanic2     = keyCode;
                }
                VisionConfig.save();
            }
            rebindingKey = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        VisionConfig.save();
        assert minecraft != null;
        minecraft.setScreen(parent);
    }

    // ══════════════════════════════════════ UTILITIES ═════════════════════════

    private void selMod(String id) {
        if (!id.equals(selMod)) { selMod = id; rightScroll = 0; rebuildWidgets(); }
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        if (settingsClip) {
            // Clip settings-row click zones to the scrolled viewport so a row that is
            // scrolled just off the top/bottom can't be clicked via the title bar or
            // the entityEsp filter strip (scissor clips drawing, not hit-testing).
            int ny = Math.max(y, sY);
            int nb = Math.min(y + h, sY + sH);
            if (nb <= ny) return;
            y = ny; h = nb - ny;
        }
        hits.add(new int[]{x, y, w, h});
        hitActions.add(action);
    }

    private boolean isOn(String id) {
        VisionConfig c = VisionConfig.get();
        return switch (id) {
            case "entityEsp"   -> c.entityEspEnabled;
            case "entityGlow"  -> c.entityGlowEnabled;
            case "healthBar"   -> c.healthBarEnabled;
            case "oreEsp"      -> c.oreEspEnabled;
            case "itemEsp"     -> c.itemEspEnabled;
            case "storageEsp"  -> c.storageEspEnabled;
            case "killAura"    -> c.killAuraEnabled;
            case "autoMace"    -> c.autoMaceEnabled;
            case "reach"       -> c.reachEnabled;
            case "triggerBot"  -> c.triggerBotEnabled;
            case "autoWeapon"  -> c.autoWeaponEnabled;
            case "criticals"   -> c.criticalsEnabled;
            case "autoClicker" -> c.autoClickerEnabled;
            case "velocity"    -> c.velocityEnabled;
            case "autoTotem"   -> c.autoTotemEnabled;
            case "noHurtCam"   -> c.noHurtCamEnabled;
            case "sprint"      -> c.sprintEnabled;
            case "fly"         -> c.flyEnabled;
            case "speed"       -> c.speedEnabled;
            case "noFall"      -> c.noFallEnabled;
            case "step"        -> c.stepEnabled;
            case "jesus"       -> c.jesusEnabled;
            case "noSlow"      -> c.noSlowEnabled;
            case "scaffold"    -> c.scaffoldEnabled;
            case "surround"    -> c.surroundEnabled;
            case "safeWalk"    -> c.safeWalkEnabled;
            case "invMove"     -> c.invMoveEnabled;
            case "spider"      -> c.spiderEnabled;
            case "antiVoid"    -> c.antiVoidEnabled;
            case "autoWalk"    -> c.autoWalkEnabled;
            case "glide"       -> c.glideEnabled;
            case "fastLadder"  -> c.fastLadderEnabled;
            case "autoJump"    -> c.autoJumpEnabled;
            case "autoSneak"   -> c.autoSneakEnabled;
            case "autoTool"    -> c.autoToolEnabled;
            case "autoArmor"   -> c.autoArmorEnabled;
            case "noBob"       -> c.noBobEnabled;
            case "autoLog"     -> c.autoLogEnabled;
            case "maceDmg"         -> c.maceDmgEnabled;
            case "maceDmgClassic"  -> c.maceDmgClassicEnabled;
            case "stunSlam"        -> c.stunSlamEnabled;
            case "autoEat"     -> c.autoEatEnabled;
            case "antiHunger"  -> c.antiHungerEnabled;
            case "antiPoison"  -> c.antiPoisonEnabled;
            case "antiAfk"     -> c.antiAfkEnabled;
            case "autoRespawn" -> c.autoRespawnEnabled;
            case "chestStealer"-> c.chestStealerEnabled;
            case "fullbright"  -> c.fullbrightEnabled;
            case "gammaBoost"  -> c.gammaBoostEnabled;
            case "tracers"     -> c.globalLinesEnabled;
            case "boxFill"     -> c.fillBoxes;
            case "zoom"        -> c.keyZoom > 0;
            case "noFog"       -> c.noFogEnabled;
            case "noWeather"   -> c.noWeatherEnabled;
            case "antiBlind"   -> c.antiBlindEnabled;
            case "coords"      -> c.coordsHudEnabled;
            case "betterTab"   -> c.betterTabEnabled;
            case "susChunks"   -> c.susChunksEnabled;
            case "nuker"       -> c.nukerEnabled;
            case "session"     -> c.resetOnRelog || c.resetOnRestart;
            default -> false;
        };
    }

    private static <T> void toggleSet(Set<T> set, T val) {
        if (!set.remove(val)) set.add(val);
    }

    private static void save() { VisionConfig.save(); }

    private static String rgb(int argb) { return String.format("#%06X", argb & 0xFFFFFF); }

    private static int parseRGB(String hex) { return VisionConfig.parseColor(hex) & 0xFFFFFF; }

    private static String keyName(int k) {
        if (k <= 0) return "Keine";
        try { return InputConstants.Type.KEYSYM.getOrCreate(k).getDisplayName().getString(); }
        catch (Exception e) { return "Key " + k; }
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean vis(int y, int h, int oy, int oh) {
        return y + h > oy && y < oy + oh;
    }
}
