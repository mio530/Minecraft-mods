package de.mio.visionmod.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class VisionConfigScreen extends Screen {

    private static final int ROW_H    = 44;
    private static final int LIST_TOP = 68;
    private static final int FOOTER_H = 32;

    // 10 clickable preset colors shown in the palette row
    private static final int[] PALETTE = {
        0xFFFF3333,  // Rot
        0xFFFF8800,  // Orange
        0xFFFFDD00,  // Gelb
        0xFF33FF44,  // Grün
        0xFF00FFCC,  // Türkis
        0xFF22AAFF,  // Blau
        0xFFAA55FF,  // Lila
        0xFFFF55BB,  // Pink
        0xFFFFFFFF,  // Weiß
        0xFF888888,  // Grau
    };
    private static final String[] PALETTE_NAMES = {
        "Rot","Orange","Gelb","Grün","Türkis","Blau","Lila","Pink","Weiß","Grau"
    };

    private final Screen parent;
    private int activeTab = 0;
    private int scrollPx  = 0;

    // Non-widget drawables (text labels and color swatches) rebuilt each init()
    private final List<Label>  labels   = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();

    private record Label(int x, int y, String text) {}
    private record Swatch(int x, int y, int w, int h, int color,
                          boolean isCurrent, Runnable onClick) {}

    public VisionConfigScreen(Screen parent) {
        super(Component.translatable("screen.visionmod.config"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    // =========================================================  INIT  ===

    @Override
    protected void init() {
        labels.clear();
        swatches.clear();
        VisionConfig cfg = VisionConfig.get();

        // Tab buttons – highlight active tab with colour
        addRenderableWidget(Button.builder(
                Component.literal(activeTab == 0 ? "§e▶ Entities" : "Entities"),
                b -> switchTab(0)).bounds(5, 38, 88, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal(activeTab == 1 ? "§e▶ Erze" : "Erze"),
                b -> switchTab(1)).bounds(98, 38, 65, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal(activeTab == 2 ? "§e▶ Optionen" : "Optionen"),
                b -> switchTab(2)).bounds(168, 38, 88, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> { VisionConfig.save(); onClose(); })
                .bounds(width / 2 - 50, height - FOOTER_H + 6, 100, 20).build());

        int listBottom = height - FOOTER_H;
        if (activeTab == 0)      buildEntityTab(cfg, listBottom);
        else if (activeTab == 1) buildOreTab(cfg, listBottom);
        else                     buildSettingsTab(cfg);
    }

    private void switchTab(int t) { activeTab = t; scrollPx = 0; rebuildWidgets(); }

    // ====================================================  ENTITY TAB  ===

    private void buildEntityTab(VisionConfig cfg, int listBottom) {
        // Column headers above the list
        labels.add(new Label(18,  LIST_TOP - 20, "§7AN/AUS"));
        labels.add(new Label(62,  LIST_TOP - 20, "§7Typ"));
        labels.add(new Label(5,   LIST_TOP - 8,  "§8← Box-Farbe anklicken     §8L=Linie  ← Linie-Farbe anklicken"));

        List<String> items = VisionConfig.ALL_ENTITY_TYPES;
        int rowCount = items.size();
        scrollPx = clamp(scrollPx, 0, Math.max(0, rowCount * ROW_H - (listBottom - LIST_TOP)));

        int first  = scrollPx / ROW_H;
        int pixOff = scrollPx % ROW_H;

        for (int i = first; i < rowCount; i++) {
            int y = LIST_TOP + (i - first) * ROW_H - pixOff;
            if (y + ROW_H < LIST_TOP) continue;
            if (y > listBottom) break;

            String id = items.get(i);

            // ── Sub-row 1: toggle + name ───────────────────────────────
            boolean espOn = cfg.enabledEntityTypes.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(espOn ? "§a[AN]" : "§c[AUS]"),
                    b -> { toggleSet(cfg.enabledEntityTypes, id); rebuildWidgets(); })
                    .bounds(5, y + 2, 52, 16).build());
            labels.add(new Label(62, y + 6, VisionConfig.displayName(id)));

            // ── Sub-row 2: box palette ─────────────────────────────────
            int bCol = parseRGB(cfg.entityBoxColors.getOrDefault(id, "#FFFF0000"));
            addCurrent(5, y + 25, bCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id;
                final int pc = PALETTE[ci];
                addPalette(22 + ci * 13, y + 25, pc, () -> {
                    cfg.entityBoxColors.put(fid, rgb(pc));
                    VisionConfig.save(); rebuildWidgets();
                });
            }

            // ── Sub-row 2: line toggle + line palette ──────────────────
            boolean lineOn = cfg.entityLinesEnabled.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(lineOn ? "§a[L]" : "§8[L]"),
                    b -> { toggleSet(cfg.entityLinesEnabled, id); rebuildWidgets(); })
                    .bounds(158, y + 23, 28, 16).build());

            int lCol = parseRGB(cfg.entityLineColors.getOrDefault(id, "#FFFF0000"));
            addCurrent(190, y + 25, lCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id;
                final int pc = PALETTE[ci];
                addPalette(207 + ci * 13, y + 25, pc, () -> {
                    cfg.entityLineColors.put(fid, rgb(pc));
                    VisionConfig.save(); rebuildWidgets();
                });
            }
        }

        // ── Spieler-Namensfilter at bottom ─────────────────────────────
        int fy = listBottom - 22;
        labels.add(new Label(5, fy - 11,
                "§8Spieler-Filter: leer = alle anzeigen, sonst kommagetrennte Namen"));
        EditBox names = new EditBox(font, 5, fy, width - 10, 18, Component.empty());
        names.setMaxLength(512);
        names.setValue(String.join(",", cfg.enabledPlayerNames));
        names.setHint(Component.literal("§7z.B. Steve,Alex  (leer = alle Spieler)"));
        names.setResponder(val -> {
            cfg.enabledPlayerNames.clear();
            for (String n : val.split(",")) { String t = n.trim(); if (!t.isEmpty()) cfg.enabledPlayerNames.add(t); }
            VisionConfig.save();
        });
        addRenderableWidget(names);
    }

    // ======================================================  ORE TAB  ===

    private void buildOreTab(VisionConfig cfg, int listBottom) {
        labels.add(new Label(18,  LIST_TOP - 20, "§7AN/AUS"));
        labels.add(new Label(62,  LIST_TOP - 20, "§7Erztyp"));
        labels.add(new Label(5,   LIST_TOP - 8,  "§8← Box-Farbe anklicken     §8L=Linie  ← Linie-Farbe anklicken"));

        List<String> items = VisionConfig.ALL_ORES;
        int rowCount = items.size();
        scrollPx = clamp(scrollPx, 0, Math.max(0, rowCount * ROW_H - (listBottom - LIST_TOP)));

        int first  = scrollPx / ROW_H;
        int pixOff = scrollPx % ROW_H;

        for (int i = first; i < rowCount; i++) {
            int y = LIST_TOP + (i - first) * ROW_H - pixOff;
            if (y + ROW_H < LIST_TOP) continue;
            if (y > listBottom) break;

            String id = items.get(i);

            boolean oreOn = cfg.enabledOres.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(oreOn ? "§a[AN]" : "§c[AUS]"),
                    b -> { toggleSet(cfg.enabledOres, id); rebuildWidgets(); })
                    .bounds(5, y + 2, 52, 16).build());
            labels.add(new Label(62, y + 6, VisionConfig.displayName(id)));

            int bCol = parseRGB(cfg.oreBoxColors.getOrDefault(id, "#FFFFFFFF"));
            addCurrent(5, y + 25, bCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id;
                final int pc = PALETTE[ci];
                addPalette(22 + ci * 13, y + 25, pc, () -> {
                    cfg.oreBoxColors.put(fid, rgb(pc));
                    VisionConfig.save(); rebuildWidgets();
                });
            }

            boolean lineOn = cfg.oreLinesEnabled.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(lineOn ? "§a[L]" : "§8[L]"),
                    b -> { toggleSet(cfg.oreLinesEnabled, id); rebuildWidgets(); })
                    .bounds(158, y + 23, 28, 16).build());

            int lCol = parseRGB(cfg.oreLineColors.getOrDefault(id, "#FFFFFFFF"));
            addCurrent(190, y + 25, lCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id;
                final int pc = PALETTE[ci];
                addPalette(207 + ci * 13, y + 25, pc, () -> {
                    cfg.oreLineColors.put(fid, rgb(pc));
                    VisionConfig.save(); rebuildWidgets();
                });
            }
        }
    }

    // =================================================  SETTINGS TAB  ===

    private void buildSettingsTab(VisionConfig cfg) {
        int y = LIST_TOP;

        // ── Entity-ESP ───────────────────────────────────────────────────
        labels.add(new Label(5, y - 2, "§7─── Entity-ESP ───────────────────────────"));
        y += 10;
        addToggle("§eEntity-ESP  §f(F6 zum Umschalten): " + onOff(cfg.entityEspEnabled),
                y, () -> { cfg.entityEspEnabled = !cfg.entityEspEnabled; VisionConfig.save(); rebuildWidgets(); });
        labels.add(new Label(7, y + 20, "§8Zeigt Entities (Spieler, Mobs) als farbige Boxen durch Wände sichtbar"));
        addRadius(y, cfg.entityEspRadius, 1, 16,
                () -> { cfg.entityEspRadius = Math.max(1, cfg.entityEspRadius - 1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.entityEspRadius = Math.min(16, cfg.entityEspRadius + 1); VisionConfig.save(); rebuildWidgets(); });
        y += 34;

        addToggle("§eEntity Glow §f(statt Box): " + onOff(cfg.entityGlowEnabled),
                y, () -> { cfg.entityGlowEnabled = !cfg.entityGlowEnabled; VisionConfig.save(); rebuildWidgets(); });
        labels.add(new Label(7, y + 20, "§8Vanilla Glow-Effekt – farbiger Umriss durch Wände. Nur für dich sichtbar, nicht in OBS."));
        y += 34;

        // ── Erz-ESP ──────────────────────────────────────────────────────
        labels.add(new Label(5, y, "§7─── Erz-ESP ──────────────────────────────"));
        y += 12;
        addToggle("§eErz-ESP  §f(F7 zum Umschalten): " + onOff(cfg.oreEspEnabled),
                y, () -> { cfg.oreEspEnabled = !cfg.oreEspEnabled; VisionConfig.save(); rebuildWidgets(); });
        labels.add(new Label(7, y + 20, "§8Zeigt Erze (Diamant, Eisen …) als farbige Boxen durch Wände"));
        addRadius(y, cfg.oreEspRadius, 1, 8,
                () -> { cfg.oreEspRadius = Math.max(1, cfg.oreEspRadius - 1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.oreEspRadius = Math.min(8, cfg.oreEspRadius + 1); VisionConfig.save(); rebuildWidgets(); });
        y += 34;

        // ── Sus Chunks ───────────────────────────────────────────────────
        labels.add(new Label(5, y, "§7─── Sus Chunks ───────────────────────────"));
        y += 12;
        addToggle("§eSus Chunks  §f(F9 zum Umschalten): " + onOff(cfg.susChunksEnabled),
                y, () -> { cfg.susChunksEnabled = !cfg.susChunksEnabled; VisionConfig.save(); rebuildWidgets(); });
        labels.add(new Label(7, y + 20, "§8Hebt Chunks hervor die Kisten/Spawner/Redstone enthalten (= versteckte Basen)"));
        addRadius(y, cfg.susChunksRadius, 1, 8,
                () -> { cfg.susChunksRadius = Math.max(1, cfg.susChunksRadius - 1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.susChunksRadius = Math.min(8, cfg.susChunksRadius + 1); VisionConfig.save(); rebuildWidgets(); });
        y += 34;

        addRenderableWidget(Button.builder(
                Component.literal("Kisten erkennen: " + onOff(cfg.susDetectChests)),
                b -> { cfg.susDetectChests = !cfg.susDetectChests; VisionConfig.save(); rebuildWidgets(); })
                .bounds(5, y, 125, 16).build());
        addRenderableWidget(Button.builder(
                Component.literal("Spawner erkennen: " + onOff(cfg.susDetectSpawners)),
                b -> { cfg.susDetectSpawners = !cfg.susDetectSpawners; VisionConfig.save(); rebuildWidgets(); })
                .bounds(135, y, 130, 16).build());
        addRenderableWidget(Button.builder(
                Component.literal("Redstone erkennen: " + onOff(cfg.susDetectRedstone)),
                b -> { cfg.susDetectRedstone = !cfg.susDetectRedstone; VisionConfig.save(); rebuildWidgets(); })
                .bounds(270, y, 135, 16).build());
        y += 24;

        addRenderableWidget(Button.builder(
                Component.literal("Alle Chunk-Grenzen: " + onOff(cfg.showAllChunkBorders)),
                b -> { cfg.showAllChunkBorders = !cfg.showAllChunkBorders; VisionConfig.save(); rebuildWidgets(); })
                .bounds(5, y, 185, 16).build());
        y += 24;

        // ── Sonstiges ────────────────────────────────────────────────────
        labels.add(new Label(5, y, "§7─── Sonstiges ────────────────────────────"));
        y += 12;
        addRenderableWidget(Button.builder(
                Component.literal("Tracer-Linien global: " + onOff(cfg.globalLinesEnabled)),
                b -> { cfg.globalLinesEnabled = !cfg.globalLinesEnabled; VisionConfig.save(); rebuildWidgets(); })
                .bounds(5, y, 175, 16).build());
        addRenderableWidget(Button.builder(
                Component.literal("Box-Stil: " + (cfg.fillBoxes ? "§eGefüllt" : "§eOutline")),
                b -> { cfg.fillBoxes = !cfg.fillBoxes; VisionConfig.save(); rebuildWidgets(); })
                .bounds(185, y, 130, 16).build());
    }

    // Helper: adds a 210px-wide toggle button at y
    private void addToggle(String label, int y, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(5, y, 220, 18).build());
    }

    // Helper: adds "Radius: N Chunks [-][+]" at (228, y)
    private void addRadius(int y, int current, int min, int max, Runnable dec, Runnable inc) {
        labels.add(new Label(228, y + 4, "§7Radius: §f" + current + " §7Chunks"));
        addRenderableWidget(Button.builder(Component.literal("§c−"), b -> dec.run())
                .bounds(313, y, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§a+"), b -> inc.run())
                .bounds(333, y, 18, 18).build());
    }

    // ===================================================  HELPER UTIL  ===

    private static <T> void toggleSet(java.util.Set<T> set, T val) {
        if (!set.remove(val)) set.add(val);
        VisionConfig.save();
    }

    private static String onOff(boolean on) { return on ? "§aAN" : "§cAUS"; }

    private static String rgb(int argb) { return String.format("#%06X", argb & 0x00FFFFFF); }

    private static int parseRGB(String hex) { return VisionConfig.parseColor(hex) & 0x00FFFFFF; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // Adds a "current colour" display swatch (non-clickable, white border)
    private void addCurrent(int x, int y, int rgb) {
        swatches.add(new Swatch(x, y, 14, 12, rgb, true, null));
    }

    // Adds a clickable palette swatch (dark border, yellow on hover)
    private void addPalette(int x, int y, int argb, Runnable onClick) {
        swatches.add(new Swatch(x, y, 12, 12, argb & 0x00FFFFFF, false, onClick));
    }

    // =====================================================  RENDERING  ===

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);

        // Title
        g.drawCenteredString(font, title, width / 2, 13, 0xFFFFFF);

        // Text labels
        for (Label l : labels) {
            g.drawString(font, l.text(), l.x(), l.y(), 0xAAAAAA, false);
        }

        // Color swatches
        for (Swatch s : swatches) {
            boolean hover = s.onClick() != null
                    && mx >= s.x() && mx < s.x() + s.w()
                    && my >= s.y() && my < s.y() + s.h();

            int border = s.isCurrent() ? 0xFFFFFFFF : (hover ? 0xFFFFFF44 : 0xFF444444);
            int bOff   = s.isCurrent() ? 2 : 1;
            g.fill(s.x() - bOff, s.y() - bOff, s.x() + s.w() + bOff, s.y() + s.h() + bOff, border);
            g.fill(s.x(), s.y(), s.x() + s.w(), s.y() + s.h(), 0xFF000000 | s.color());
        }

        // Footer separator
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0x66FFFFFF);

        super.render(g, mx, my, delta);
    }

    // =====================================================  INPUT  ===

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            for (Swatch s : swatches) {
                if (s.onClick() != null
                        && mx >= s.x() && mx < s.x() + s.w()
                        && my >= s.y() && my < s.y() + s.h()) {
                    s.onClick().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scrollPx = clamp(scrollPx - (int)(sy * ROW_H), 0, Integer.MAX_VALUE);
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        VisionConfig.save();
        assert minecraft != null;
        minecraft.setScreen(parent);
    }
}
