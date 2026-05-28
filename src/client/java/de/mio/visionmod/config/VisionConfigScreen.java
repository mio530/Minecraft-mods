package de.mio.visionmod.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class VisionConfigScreen extends Screen {

    private static final int ROW_H    = 44;
    private static final int LIST_TOP = 68;
    private static final int FOOTER_H = 32;

    private static final int[] PALETTE = {
        0xFFFF3333, 0xFFFF8800, 0xFFFFDD00, 0xFF33FF44,
        0xFF00FFCC, 0xFF22AAFF, 0xFFAA55FF, 0xFFFF55BB,
        0xFFFFFFFF, 0xFF888888,
    };

    private final Screen parent;
    private int    activeTab    = 0;
    private int    scrollPx     = 0;
    private String rebindingKey = null;

    private final List<Label>  labels   = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();

    private record Label(int x, int y, String text) {}
    private record Swatch(int x, int y, int w, int h, int color,
                          boolean isCurrent, Runnable onClick) {}

    public VisionConfigScreen(Screen parent) {
        super(Component.literal("§6Visual Improvement §7– Einstellungen"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    // =========================================================  INIT  ===

    @Override
    protected void init() {
        labels.clear();
        swatches.clear();
        VisionConfig cfg = VisionConfig.get();

        // 4 tab buttons
        int[] tx = {5, 84, 163, 244};
        int[] tw = {76, 76, 78, 70};
        String[] tn = {"Entities", "Erze", "Optionen", "Tasten"};
        for (int i = 0; i < 4; i++) {
            final int tab = i;
            addRenderableWidget(Button.builder(
                    Component.literal((activeTab == tab ? "§e▶ " : "") + tn[i]),
                    b -> switchTab(tab)).bounds(tx[i], 38, tw[i], 18).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> { VisionConfig.save(); onClose(); })
                .bounds(width / 2 - 50, height - FOOTER_H + 6, 100, 20).build());

        int listBottom = height - FOOTER_H;
        switch (activeTab) {
            case 0 -> buildEntityTab(cfg, listBottom);
            case 1 -> buildOreTab(cfg, listBottom);
            case 2 -> buildSettingsTab(cfg, listBottom);
            case 3 -> buildKeybindsTab(cfg);
        }
    }

    private void switchTab(int t) { activeTab = t; scrollPx = 0; rebindingKey = null; rebuildWidgets(); }

    // ====================================================  ENTITY TAB  ===

    private void buildEntityTab(VisionConfig cfg, int listBottom) {
        labels.add(new Label(18, LIST_TOP - 20, "§7AN/AUS"));
        labels.add(new Label(62, LIST_TOP - 20, "§7Typ"));
        labels.add(new Label(5,  LIST_TOP - 8,
                "§8← Box-Farbe anklicken        §8[L]=Linie  ← Linie-Farbe anklicken"));

        List<String> items = VisionConfig.ALL_ENTITY_TYPES;
        int rowCount = items.size();
        scrollPx = clamp(scrollPx, 0, Math.max(0, rowCount * ROW_H - (listBottom - LIST_TOP - 30)));

        int first = scrollPx / ROW_H, pixOff = scrollPx % ROW_H;

        for (int i = first; i < rowCount; i++) {
            int y = LIST_TOP + (i - first) * ROW_H - pixOff;
            if (y + ROW_H < LIST_TOP) continue;
            if (y > listBottom - 30)  break;

            String id = items.get(i);
            boolean on = cfg.enabledEntityTypes.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(on ? "§a[AN]" : "§c[AUS]"),
                    b -> { toggleSet(cfg.enabledEntityTypes, id); rebuildWidgets(); })
                    .bounds(5, y + 2, 52, 16).build());
            labels.add(new Label(62, y + 6, VisionConfig.displayName(id)));

            int bCol = parseRGB(cfg.entityBoxColors.getOrDefault(id, "#FFFF0000"));
            addCurrent(5, y + 25, bCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id; final int pc = PALETTE[ci];
                addPalette(22 + ci * 13, y + 25, pc, () -> {
                    cfg.entityBoxColors.put(fid, rgb(pc)); VisionConfig.save(); rebuildWidgets(); });
            }

            boolean lineOn = cfg.entityLinesEnabled.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(lineOn ? "§a[L]" : "§8[L]"),
                    b -> { toggleSet(cfg.entityLinesEnabled, id); rebuildWidgets(); })
                    .bounds(158, y + 23, 28, 16).build());

            int lCol = parseRGB(cfg.entityLineColors.getOrDefault(id, "#FFFF0000"));
            addCurrent(190, y + 25, lCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id; final int pc = PALETTE[ci];
                addPalette(207 + ci * 13, y + 25, pc, () -> {
                    cfg.entityLineColors.put(fid, rgb(pc)); VisionConfig.save(); rebuildWidgets(); });
            }
        }

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
        labels.add(new Label(18, LIST_TOP - 20, "§7AN/AUS"));
        labels.add(new Label(62, LIST_TOP - 20, "§7Erztyp"));
        labels.add(new Label(5,  LIST_TOP - 8,
                "§8← Box-Farbe anklicken        §8[L]=Linie  ← Linie-Farbe anklicken"));

        List<String> items = VisionConfig.ALL_ORES;
        int rowCount = items.size();
        scrollPx = clamp(scrollPx, 0, Math.max(0, rowCount * ROW_H - (listBottom - LIST_TOP)));

        int first = scrollPx / ROW_H, pixOff = scrollPx % ROW_H;

        for (int i = first; i < rowCount; i++) {
            int y = LIST_TOP + (i - first) * ROW_H - pixOff;
            if (y + ROW_H < LIST_TOP) continue;
            if (y > listBottom)        break;

            String id = items.get(i);
            boolean on = cfg.enabledOres.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(on ? "§a[AN]" : "§c[AUS]"),
                    b -> { toggleSet(cfg.enabledOres, id); rebuildWidgets(); })
                    .bounds(5, y + 2, 52, 16).build());
            labels.add(new Label(62, y + 6, VisionConfig.displayName(id)));

            int bCol = parseRGB(cfg.oreBoxColors.getOrDefault(id, "#FFFFFFFF"));
            addCurrent(5, y + 25, bCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id; final int pc = PALETTE[ci];
                addPalette(22 + ci * 13, y + 25, pc, () -> {
                    cfg.oreBoxColors.put(fid, rgb(pc)); VisionConfig.save(); rebuildWidgets(); });
            }

            boolean lineOn = cfg.oreLinesEnabled.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(lineOn ? "§a[L]" : "§8[L]"),
                    b -> { toggleSet(cfg.oreLinesEnabled, id); rebuildWidgets(); })
                    .bounds(158, y + 23, 28, 16).build());

            int lCol = parseRGB(cfg.oreLineColors.getOrDefault(id, "#FFFFFFFF"));
            addCurrent(190, y + 25, lCol);
            for (int ci = 0; ci < PALETTE.length; ci++) {
                final String fid = id; final int pc = PALETTE[ci];
                addPalette(207 + ci * 13, y + 25, pc, () -> {
                    cfg.oreLineColors.put(fid, rgb(pc)); VisionConfig.save(); rebuildWidgets(); });
            }
        }
    }

    // ================================================  SETTINGS TAB  ===

    private void buildSettingsTab(VisionConfig cfg, int lb) {
        // ── estimate total content height for scroll cap ──────────────────
        // 9 sections of ~34px + sub-rows ~24px each ≈ 580px total
        scrollPx = clamp(scrollPx, 0, Math.max(0, 620 - (lb - LIST_TOP)));

        // content y = LIST_TOP + position - scrollPx
        // helper: add y offset to content position
        final int off = LIST_TOP - scrollPx;

        settingsHeader(off + 0,  lb, "§7─── Entity-ESP ─────────────────────────────────────");
        settingsToggle(off + 12, lb, "§eEntity-ESP §8[" + keyName(cfg.keyEntityEsp) + "]: " + onOff(cfg.entityEspEnabled),
                () -> { cfg.entityEspEnabled = !cfg.entityEspEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 32, lb, "§8Zeigt Entities/Spieler als farbige Boxen durch Wände");
        settingsRadius(off + 12, lb, cfg.entityEspRadius, 1, 16,
                () -> { cfg.entityEspRadius = Math.max(1,  cfg.entityEspRadius-1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.entityEspRadius = Math.min(16, cfg.entityEspRadius+1); VisionConfig.save(); rebuildWidgets(); });

        settingsToggle(off + 48, lb, "§eEntity Glow §8(statt Box): " + onOff(cfg.entityGlowEnabled),
                () -> { cfg.entityGlowEnabled = !cfg.entityGlowEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 68, lb, "§8Vanilla-Umriss durch Wände. Nicht in OBS sichtbar.");

        settingsToggle(off + 84, lb, "§eHealth Bar über Entity: " + onOff(cfg.healthBarEnabled),
                () -> { cfg.healthBarEnabled = !cfg.healthBarEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 104, lb, "§83D-Balken über jeder Entity: grün→gelb→rot je nach HP");

        settingsHeader(off + 120, lb, "§7─── Vollhelligkeit ─────────────────────────────────");
        settingsToggle(off + 132, lb, "§eFullbright §8[" + keyName(cfg.keyFullbright) + "]: " + onOff(cfg.fullbrightEnabled),
                () -> { cfg.fullbrightEnabled = !cfg.fullbrightEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 152, lb, "§8Versteckter Night-Vision-Effekt. Kein Icon, kein Partikel.");

        settingsHeader(off + 168, lb, "§7─── Erz-ESP ────────────────────────────────────────");
        settingsToggle(off + 180, lb, "§eErz-ESP §8[" + keyName(cfg.keyOreEsp) + "]: " + onOff(cfg.oreEspEnabled),
                () -> { cfg.oreEspEnabled = !cfg.oreEspEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 200, lb, "§8Zeigt Erze (Diamant, Eisen …) als Boxen durch Wände");
        settingsRadius(off + 180, lb, cfg.oreEspRadius, 1, 8,
                () -> { cfg.oreEspRadius = Math.max(1, cfg.oreEspRadius-1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.oreEspRadius = Math.min(8, cfg.oreEspRadius+1); VisionConfig.save(); rebuildWidgets(); });

        settingsHeader(off + 216, lb, "§7─── Item-ESP ───────────────────────────────────────");
        settingsToggle(off + 228, lb, "§eItem-ESP" + (cfg.keyItemEsp > 0 ? " §8["+keyName(cfg.keyItemEsp)+"]" : "") + ": " + onOff(cfg.itemEspEnabled),
                () -> { cfg.itemEspEnabled = !cfg.itemEspEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 248, lb, "§8Zeigt liegende Items als Boxen. Tracer zu jedem Item.");
        settingsRadius(off + 228, lb, cfg.itemEspRadius, 1, 16,
                () -> { cfg.itemEspRadius = Math.max(1,  cfg.itemEspRadius-1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.itemEspRadius = Math.min(16, cfg.itemEspRadius+1); VisionConfig.save(); rebuildWidgets(); });
        settingsSwatch(off + 255, lb, "§8Item-Farbe:", parseRGB(cfg.itemEspColor),
                c -> { cfg.itemEspColor = rgb(c); VisionConfig.save(); rebuildWidgets(); });

        settingsHeader(off + 282, lb, "§7─── Container-ESP ──────────────────────────────────");
        settingsToggle(off + 294, lb, "§eContainer-ESP" + (cfg.keyStorageEsp > 0 ? " §8["+keyName(cfg.keyStorageEsp)+"]" : "") + ": " + onOff(cfg.storageEspEnabled),
                () -> { cfg.storageEspEnabled = !cfg.storageEspEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 314, lb, "§8Hebt Kisten, Fässer, Shulker, Ender-Kisten hervor");
        settingsRadius(off + 294, lb, cfg.storageEspRadius, 1, 8,
                () -> { cfg.storageEspRadius = Math.max(1, cfg.storageEspRadius-1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.storageEspRadius = Math.min(8, cfg.storageEspRadius+1); VisionConfig.save(); rebuildWidgets(); });
        settingsSwatch(off + 321, lb, "§8Kiste:",      parseRGB(cfg.chestColor),
                c -> { cfg.chestColor = rgb(c); VisionConfig.save(); rebuildWidgets(); });
        settingsSwatch(off + 336, lb, "§8Fass:",       parseRGB(cfg.barrelColor),
                c -> { cfg.barrelColor = rgb(c); VisionConfig.save(); rebuildWidgets(); });
        settingsSwatch(off + 351, lb, "§8Shulker:",    parseRGB(cfg.shulkerColor),
                c -> { cfg.shulkerColor = rgb(c); VisionConfig.save(); rebuildWidgets(); });
        settingsSwatch(off + 366, lb, "§8Ender Kiste:",parseRGB(cfg.enderChestColor),
                c -> { cfg.enderChestColor = rgb(c); VisionConfig.save(); rebuildWidgets(); });

        settingsHeader(off + 392, lb, "§7─── Sus Chunks ─────────────────────────────────────");
        settingsToggle(off + 404, lb, "§eSus Chunks §8[" + keyName(cfg.keySusChunks) + "]: " + onOff(cfg.susChunksEnabled),
                () -> { cfg.susChunksEnabled = !cfg.susChunksEnabled; VisionConfig.save(); rebuildWidgets(); });
        settingsDesc  (off + 424, lb, "§8Chunks mit Kisten/Spawner/Redstone hervorheben");
        settingsRadius(off + 404, lb, cfg.susChunksRadius, 1, 8,
                () -> { cfg.susChunksRadius = Math.max(1, cfg.susChunksRadius-1); VisionConfig.save(); rebuildWidgets(); },
                () -> { cfg.susChunksRadius = Math.min(8, cfg.susChunksRadius+1); VisionConfig.save(); rebuildWidgets(); });

        if (inView(off + 436, lb)) {
            addRenderableWidget(Button.builder(Component.literal("Kisten: "  + onOff(cfg.susDetectChests)),
                    b -> { cfg.susDetectChests = !cfg.susDetectChests; VisionConfig.save(); rebuildWidgets(); })
                    .bounds(5, off + 436, 110, 16).build());
            addRenderableWidget(Button.builder(Component.literal("Spawner: " + onOff(cfg.susDetectSpawners)),
                    b -> { cfg.susDetectSpawners = !cfg.susDetectSpawners; VisionConfig.save(); rebuildWidgets(); })
                    .bounds(120, off + 436, 115, 16).build());
            addRenderableWidget(Button.builder(Component.literal("Redstone: "+ onOff(cfg.susDetectRedstone)),
                    b -> { cfg.susDetectRedstone = !cfg.susDetectRedstone; VisionConfig.save(); rebuildWidgets(); })
                    .bounds(240, off + 436, 120, 16).build());
        }
        if (inView(off + 458, lb))
            addRenderableWidget(Button.builder(Component.literal("Alle Chunk-Grenzen: " + onOff(cfg.showAllChunkBorders)),
                    b -> { cfg.showAllChunkBorders = !cfg.showAllChunkBorders; VisionConfig.save(); rebuildWidgets(); })
                    .bounds(5, off + 458, 200, 16).build());

        settingsHeader(off + 480, lb, "§7─── Sonstiges ──────────────────────────────────────");
        if (inView(off + 492, lb)) {
            addRenderableWidget(Button.builder(Component.literal("Tracer-Linien global: " + onOff(cfg.globalLinesEnabled)),
                    b -> { cfg.globalLinesEnabled = !cfg.globalLinesEnabled; VisionConfig.save(); rebuildWidgets(); })
                    .bounds(5, off + 492, 175, 16).build());
            addRenderableWidget(Button.builder(Component.literal("Box-Stil: " + (cfg.fillBoxes ? "§eGefüllt" : "§eOutline")),
                    b -> { cfg.fillBoxes = !cfg.fillBoxes; VisionConfig.save(); rebuildWidgets(); })
                    .bounds(185, off + 492, 130, 16).build());
        }
    }

    // Settings helpers
    private void settingsHeader(int y, int lb, String text) {
        if (inView(y, lb)) labels.add(new Label(5, y, text));
    }
    private void settingsToggle(int y, int lb, String label, Runnable action) {
        if (inView(y, lb))
            addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                    .bounds(5, y, 220, 18).build());
    }
    private void settingsDesc(int y, int lb, String text) {
        if (inView(y, lb)) labels.add(new Label(7, y, text));
    }
    private void settingsRadius(int y, int lb, int current, int min, int max, Runnable dec, Runnable inc) {
        if (!inView(y, lb)) return;
        labels.add(new Label(228, y + 4, "§7Radius: §f" + current + " §7Chunks"));
        addRenderableWidget(Button.builder(Component.literal("§c−"), b -> dec.run()).bounds(313, y, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§a+"), b -> inc.run()).bounds(333, y, 18, 18).build());
    }
    private void settingsSwatch(int y, int lb, String label, int currentRgb, java.util.function.IntConsumer onPick) {
        if (!inView(y, lb)) return;
        labels.add(new Label(5, y + 2, label));
        int labelW = font.width(label.replaceAll("§.", "")) + 8;
        addCurrent(labelW + 5, y, currentRgb);
        for (int ci = 0; ci < PALETTE.length; ci++) {
            final int pc = PALETTE[ci];
            addPalette(labelW + 22 + ci * 13, y, pc, () -> onPick.accept(pc));
        }
    }
    private boolean inView(int y, int lb) { return y >= LIST_TOP - 20 && y < lb + 4; }
    private boolean inView(int y)         { return inView(y, height - FOOTER_H); }

    // ================================================  KEYBINDS TAB  ===

    private void buildKeybindsTab(VisionConfig cfg) {
        int y = LIST_TOP;
        labels.add(new Label(5, y - 18, "§7Klicke §e[Ändern] §7und drücke dann eine Taste. §8ESC = Abbrechen"));
        labels.add(new Label(5, y - 4,  "§8── Funktion ─────────────────── Taste ──────────── Aktion ─"));

        addKeybindRow("§fEntity-ESP",     "entityEsp",  cfg.keyEntityEsp,  y);  y += 28;
        addKeybindRow("§fErz-ESP",        "oreEsp",     cfg.keyOreEsp,     y);  y += 28;
        addKeybindRow("§fConfig öffnen",  "openConfig", cfg.keyOpenConfig, y);  y += 28;
        addKeybindRow("§fSus Chunks",     "susChunks",  cfg.keySusChunks,  y);  y += 28;
        addKeybindRow("§fFullbright",     "fullbright", cfg.keyFullbright, y);  y += 28;
        addKeybindRow("§fItem-ESP",       "itemEsp",    cfg.keyItemEsp,    y);  y += 28;
        addKeybindRow("§fContainer-ESP",  "storageEsp", cfg.keyStorageEsp, y);  y += 28;

        y += 8;
        labels.add(new Label(5, y, "§8Die Tasten sind §lnicht§r§8 im Minecraft-Keybinds-Menü sichtbar."));
        labels.add(new Label(5, y + 12, "§80 / Keine = Taste deaktiviert (nur über Config-Screen togglebar)"));
    }

    private void addKeybindRow(String name, String id, int keyCode, int y) {
        boolean waiting  = id.equals(rebindingKey);
        String  keyLabel = waiting ? "§e< Taste drücken... >" : (keyCode > 0 ? "§a" + keyName(keyCode) : "§8Keine");
        labels.add(new Label(5,   y + 4, name));
        labels.add(new Label(175, y + 4, keyLabel));
        addRenderableWidget(Button.builder(
                Component.literal(waiting ? "§cAbbrechen" : "§7Ändern"),
                b -> { rebindingKey = waiting ? null : id; rebuildWidgets(); })
                .bounds(310, y, 68, 18).build());
    }

    // ===================================================  KEY INPUT  ===

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rebindingKey != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                rebindingKey = null; rebuildWidgets(); return true;
            }
            VisionConfig cfg = VisionConfig.get();
            switch (rebindingKey) {
                case "entityEsp"  -> cfg.keyEntityEsp   = keyCode;
                case "oreEsp"     -> cfg.keyOreEsp      = keyCode;
                case "openConfig" -> cfg.keyOpenConfig  = keyCode;
                case "susChunks"  -> cfg.keySusChunks   = keyCode;
                case "fullbright" -> cfg.keyFullbright  = keyCode;
                case "itemEsp"    -> cfg.keyItemEsp     = keyCode;
                case "storageEsp" -> cfg.keyStorageEsp  = keyCode;
            }
            VisionConfig.save(); rebindingKey = null; rebuildWidgets(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // =====================================================  RENDERING  ===

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        g.drawCenteredString(font, title, width / 2, 13, 0xFFFFFF);

        for (Label l : labels)
            g.drawString(font, l.text(), l.x(), l.y(), 0xAAAAAA, false);

        for (Swatch s : swatches) {
            boolean hover = s.onClick() != null
                    && mx >= s.x() && mx < s.x() + s.w()
                    && my >= s.y() && my < s.y() + s.h();
            int border = s.isCurrent() ? 0xFFFFFFFF : (hover ? 0xFFFFFF44 : 0xFF444444);
            int bOff   = s.isCurrent() ? 2 : 1;
            g.fill(s.x()-bOff, s.y()-bOff, s.x()+s.w()+bOff, s.y()+s.h()+bOff, border);
            g.fill(s.x(), s.y(), s.x()+s.w(), s.y()+s.h(), 0xFF000000 | s.color());
        }

        // Scroll indicator on right edge for Entities/Ores tabs
        if (activeTab < 2) {
            List<String> allItems = activeTab == 0 ? VisionConfig.ALL_ENTITY_TYPES : VisionConfig.ALL_ORES;
            int contentH = allItems.size() * ROW_H;
            int viewH    = height - FOOTER_H - LIST_TOP;
            if (contentH > viewH) {
                int barH   = Math.max(20, viewH * viewH / contentH);
                int barY   = LIST_TOP + (int)((long)scrollPx * (viewH - barH) / Math.max(1, contentH - viewH));
                g.fill(width - 5, LIST_TOP, width - 3, height - FOOTER_H, 0x44FFFFFF);
                g.fill(width - 5, barY, width - 3, barY + barH, 0xAAFFFFFF);
            }
        }

        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0x66FFFFFF);
        super.render(g, mx, my, delta);
    }

    // =====================================================  MOUSE  ===

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            for (Swatch s : swatches) {
                if (s.onClick() != null
                        && mx >= s.x() && mx < s.x() + s.w()
                        && my >= s.y() && my < s.y() + s.h()) {
                    s.onClick().run(); return true;
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

    // ===================================================  HELPER UTIL  ===

    private static <T> void toggleSet(java.util.Set<T> set, T val) {
        if (!set.remove(val)) set.add(val);
        VisionConfig.save();
    }

    private static String onOff(boolean on)  { return on ? "§aAN" : "§cAUS"; }
    private static String rgb(int argb)      { return String.format("#%06X", argb & 0x00FFFFFF); }
    private static int    parseRGB(String h) { return VisionConfig.parseColor(h) & 0x00FFFFFF; }
    private static int    clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String keyName(int keyCode) {
        if (keyCode <= 0) return "Keine";
        try { return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString(); }
        catch (Exception e) { return "Key " + keyCode; }
    }

    private void addCurrent(int x, int y, int rgb) {
        swatches.add(new Swatch(x, y, 14, 12, rgb, true, null));
    }

    private void addPalette(int x, int y, int argb, Runnable onClick) {
        swatches.add(new Swatch(x, y, 12, 12, argb & 0x00FFFFFF, false, onClick));
    }
}
