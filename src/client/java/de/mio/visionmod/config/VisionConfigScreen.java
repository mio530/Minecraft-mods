package de.mio.visionmod.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class VisionConfigScreen extends Screen {

    private static final int ROW_H      = 24;
    private static final int LIST_TOP   = 62;
    private static final int FOOTER_H   = 32;
    private static final int COL_TOGGLE = 5;
    private static final int COL_LABEL  = 60;
    private static final int COL_BOX_F  = 185;
    private static final int COL_LINE_T = 270;
    private static final int COL_LINE_F = 325;

    private final Screen parent;
    private int activeTab = 0; // 0=entities, 1=ores, 2=settings
    private int scrollPx  = 0;

    // Items visible in current frame (for rendering labels)
    private final List<RowInfo> visibleRows = new ArrayList<>();

    private record RowInfo(String id, String label, int y) {}

    public VisionConfigScreen(Screen parent) {
        super(Component.translatable("screen.visionmod.config"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        visibleRows.clear();
        VisionConfig cfg = VisionConfig.get();

        // Tab buttons
        addRenderableWidget(Button.builder(
                Component.translatable("tab.visionmod.entities"),
                b -> switchTab(0)).bounds(5, 38, 90, 18).build());
        addRenderableWidget(Button.builder(
                Component.translatable("tab.visionmod.ores"),
                b -> switchTab(1)).bounds(100, 38, 80, 18).build());
        addRenderableWidget(Button.builder(
                Component.translatable("tab.visionmod.settings"),
                b -> switchTab(2)).bounds(185, 38, 80, 18).build());

        // Done button
        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                b -> { VisionConfig.save(); onClose(); })
                .bounds(width / 2 - 60, height - FOOTER_H + 6, 120, 20).build());

        int listBottom = height - FOOTER_H;
        int listHeight = listBottom - LIST_TOP;

        if (activeTab == 0) buildEntityTab(cfg, listBottom);
        else if (activeTab == 1) buildOreTab(cfg, listBottom);
        else buildSettingsTab(cfg, listBottom);
    }

    private void switchTab(int tab) {
        activeTab = tab;
        scrollPx  = 0;
        rebuildWidgets();
    }

    // ---- Tab 1: Entities ------------------------------------------------

    private void buildEntityTab(VisionConfig cfg, int listBottom) {
        List<String> items = VisionConfig.ALL_ENTITY_TYPES;
        int rowCount = items.size();
        int maxScroll = Math.max(0, rowCount * ROW_H - (listBottom - LIST_TOP));
        scrollPx = Math.min(scrollPx, maxScroll);

        int firstRow = scrollPx / ROW_H;
        int pixOff   = scrollPx % ROW_H;

        for (int i = firstRow; i < rowCount; i++) {
            int y = LIST_TOP + (i - firstRow) * ROW_H - pixOff;
            if (y + ROW_H < LIST_TOP) continue;
            if (y > listBottom) break;

            String id = items.get(i);
            visibleRows.add(new RowInfo(id, VisionConfig.displayName(id), y));

            boolean espOn = cfg.enabledEntityTypes.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(espOn ? "§a[AN]" : "§c[AUS]"),
                    b -> { toggleEntityEsp(id); rebuildWidgets(); })
                    .bounds(COL_TOGGLE, y + 2, 50, 18).build());

            // Box color field
            EditBox boxField = new EditBox(font, COL_BOX_F, y + 2, 80, 18,
                    Component.literal(cfg.entityBoxColors.getOrDefault(id, "#FFFF0000")));
            boxField.setMaxLength(10);
            boxField.setValue(cfg.entityBoxColors.getOrDefault(id, "#FFFF0000"));
            final String fid = id;
            boxField.setResponder(val -> {
                cfg.entityBoxColors.put(fid, val);
                VisionConfig.save();
            });
            addRenderableWidget(boxField);

            // Line toggle button
            boolean lineOn = cfg.entityLinesEnabled.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(lineOn ? "§aL§r" : "§cL§r"),
                    b -> { toggleEntityLine(id); rebuildWidgets(); })
                    .bounds(COL_LINE_T, y + 2, 30, 18).build());

            // Line color field
            EditBox lineField = new EditBox(font, COL_LINE_F, y + 2, 80, 18,
                    Component.literal(cfg.entityLineColors.getOrDefault(id, "#FFFF0000")));
            lineField.setMaxLength(10);
            lineField.setValue(cfg.entityLineColors.getOrDefault(id, "#FFFF0000"));
            lineField.setResponder(val -> {
                cfg.entityLineColors.put(fid, val);
                VisionConfig.save();
            });
            addRenderableWidget(lineField);
        }

        // Player name whitelist field at bottom of entity tab
        int fieldY = listBottom - 22;
        EditBox namesField = new EditBox(font, COL_LABEL, fieldY, width - COL_LABEL - 10, 18,
                Component.literal("Spielernamen (kommagetrennt)"));
        namesField.setMaxLength(512);
        namesField.setValue(String.join(",", cfg.enabledPlayerNames));
        namesField.setResponder(val -> {
            cfg.enabledPlayerNames.clear();
            for (String n : val.split(",")) {
                String t = n.trim();
                if (!t.isEmpty()) cfg.enabledPlayerNames.add(t);
            }
            VisionConfig.save();
        });
        addRenderableWidget(namesField);
    }

    // ---- Tab 2: Ores ----------------------------------------------------

    private void buildOreTab(VisionConfig cfg, int listBottom) {
        List<String> items = VisionConfig.ALL_ORES;
        int rowCount = items.size();
        int maxScroll = Math.max(0, rowCount * ROW_H - (listBottom - LIST_TOP));
        scrollPx = Math.min(scrollPx, maxScroll);

        int firstRow = scrollPx / ROW_H;
        int pixOff   = scrollPx % ROW_H;

        for (int i = firstRow; i < rowCount; i++) {
            int y = LIST_TOP + (i - firstRow) * ROW_H - pixOff;
            if (y + ROW_H < LIST_TOP) continue;
            if (y > listBottom) break;

            String id = items.get(i);
            visibleRows.add(new RowInfo(id, VisionConfig.displayName(id), y));

            boolean oreOn = cfg.enabledOres.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(oreOn ? "§a[AN]" : "§c[AUS]"),
                    b -> { toggleOreEsp(id); rebuildWidgets(); })
                    .bounds(COL_TOGGLE, y + 2, 50, 18).build());

            EditBox boxField = new EditBox(font, COL_BOX_F, y + 2, 80, 18,
                    Component.literal(cfg.oreBoxColors.getOrDefault(id, "#FFFFFFFF")));
            boxField.setMaxLength(10);
            boxField.setValue(cfg.oreBoxColors.getOrDefault(id, "#FFFFFFFF"));
            final String fid = id;
            boxField.setResponder(val -> {
                cfg.oreBoxColors.put(fid, val);
                VisionConfig.save();
            });
            addRenderableWidget(boxField);

            boolean lineOn = cfg.oreLinesEnabled.contains(id);
            addRenderableWidget(Button.builder(
                    Component.literal(lineOn ? "§aL§r" : "§cL§r"),
                    b -> { toggleOreLine(id); rebuildWidgets(); })
                    .bounds(COL_LINE_T, y + 2, 30, 18).build());

            EditBox lineField = new EditBox(font, COL_LINE_F, y + 2, 80, 18,
                    Component.literal(cfg.oreLineColors.getOrDefault(id, "#FFFFFFFF")));
            lineField.setMaxLength(10);
            lineField.setValue(cfg.oreLineColors.getOrDefault(id, "#FFFFFFFF"));
            lineField.setResponder(val -> {
                cfg.oreLineColors.put(fid, val);
                VisionConfig.save();
            });
            addRenderableWidget(lineField);
        }
    }

    // ---- Tab 3: Settings ------------------------------------------------

    private void buildSettingsTab(VisionConfig cfg, int listBottom) {
        int y = LIST_TOP;

        // Entity ESP + individual radius
        addRenderableWidget(Button.builder(
                Component.literal("Entity-ESP: " + (cfg.entityEspEnabled ? "§aAN" : "§cAUS")),
                b -> { cfg.entityEspEnabled = !cfg.entityEspEnabled; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE, y, 140, 20).build());
        visibleRows.add(new RowInfo("entity_radius", "Radius: " + cfg.entityEspRadius + " Chunks", y));
        addRenderableWidget(Button.builder(Component.literal("-"),
                b -> { cfg.entityEspRadius = Math.max(1, cfg.entityEspRadius - 1); VisionConfig.save(); rebuildWidgets(); })
                .bounds(215, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                b -> { cfg.entityEspRadius = Math.min(16, cfg.entityEspRadius + 1); VisionConfig.save(); rebuildWidgets(); })
                .bounds(237, y, 20, 20).build());
        y += ROW_H + 4;

        // Ore ESP + individual radius
        addRenderableWidget(Button.builder(
                Component.literal("Erz-ESP: " + (cfg.oreEspEnabled ? "§aAN" : "§cAUS")),
                b -> { cfg.oreEspEnabled = !cfg.oreEspEnabled; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE, y, 140, 20).build());
        visibleRows.add(new RowInfo("ore_radius", "Radius: " + cfg.oreEspRadius + " Chunks", y));
        addRenderableWidget(Button.builder(Component.literal("-"),
                b -> { cfg.oreEspRadius = Math.max(1, cfg.oreEspRadius - 1); VisionConfig.save(); rebuildWidgets(); })
                .bounds(215, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                b -> { cfg.oreEspRadius = Math.min(8, cfg.oreEspRadius + 1); VisionConfig.save(); rebuildWidgets(); })
                .bounds(237, y, 20, 20).build());
        y += ROW_H + 4;

        // Sus Chunks + individual radius
        addRenderableWidget(Button.builder(
                Component.literal("Sus Chunks (F9): " + (cfg.susChunksEnabled ? "§aAN" : "§cAUS")),
                b -> { cfg.susChunksEnabled = !cfg.susChunksEnabled; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE, y, 140, 20).build());
        visibleRows.add(new RowInfo("sus_radius", "Radius: " + cfg.susChunksRadius + " Chunks", y));
        addRenderableWidget(Button.builder(Component.literal("-"),
                b -> { cfg.susChunksRadius = Math.max(1, cfg.susChunksRadius - 1); VisionConfig.save(); rebuildWidgets(); })
                .bounds(215, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                b -> { cfg.susChunksRadius = Math.min(8, cfg.susChunksRadius + 1); VisionConfig.save(); rebuildWidgets(); })
                .bounds(237, y, 20, 20).build());
        y += ROW_H + 4;

        // Entity Glow toggle
        addRenderableWidget(Button.builder(
                Component.literal("Entity Glow (statt Box): " + (cfg.entityGlowEnabled ? "§aAN" : "§cAUS")),
                b -> { cfg.entityGlowEnabled = !cfg.entityGlowEnabled; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE, y, 270, 20).build());
        y += ROW_H + 4;

        // Global toggles side by side
        addRenderableWidget(Button.builder(
                Component.literal("Alle Linien: " + (cfg.globalLinesEnabled ? "§aAN" : "§cAUS")),
                b -> { cfg.globalLinesEnabled = !cfg.globalLinesEnabled; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE, y, 130, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal("Box-Stil: " + (cfg.fillBoxes ? "§eGefüllt" : "§eOutline")),
                b -> { cfg.fillBoxes = !cfg.fillBoxes; VisionConfig.save(); rebuildWidgets(); })
                .bounds(140, y, 130, 20).build());
        y += ROW_H + 4;

        // Sus chunks sub-settings
        addRenderableWidget(Button.builder(
                Component.literal("  Kisten: " + (cfg.susDetectChests ? "§aAN" : "§cAUS")),
                b -> { cfg.susDetectChests = !cfg.susDetectChests; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE, y, 130, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal("  Spawner: " + (cfg.susDetectSpawners ? "§aAN" : "§cAUS")),
                b -> { cfg.susDetectSpawners = !cfg.susDetectSpawners; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE + 135, y, 130, 20).build());
        y += ROW_H + 4;

        addRenderableWidget(Button.builder(
                Component.literal("  Redstone: " + (cfg.susDetectRedstone ? "§aAN" : "§cAUS")),
                b -> { cfg.susDetectRedstone = !cfg.susDetectRedstone; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE, y, 130, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal("  Alle Grenzen: " + (cfg.showAllChunkBorders ? "§aAN" : "§cAUS")),
                b -> { cfg.showAllChunkBorders = !cfg.showAllChunkBorders; VisionConfig.save(); rebuildWidgets(); })
                .bounds(COL_TOGGLE + 135, y, 145, 20).build());
    }

    // ---- Toggle helpers -------------------------------------------------

    private void toggleEntityEsp(String id) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.enabledEntityTypes.remove(id)) cfg.enabledEntityTypes.add(id);
        VisionConfig.save();
    }

    private void toggleEntityLine(String id) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.entityLinesEnabled.remove(id)) cfg.entityLinesEnabled.add(id);
        VisionConfig.save();
    }

    private void toggleOreEsp(String id) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.enabledOres.remove(id)) cfg.enabledOres.add(id);
        VisionConfig.save();
    }

    private void toggleOreLine(String id) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.oreLinesEnabled.remove(id)) cfg.oreLinesEnabled.add(id);
        VisionConfig.save();
    }

    // ---- Rendering ------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);

        // Title
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        // Tab headers
        g.drawString(font, "§7[Tab 1]", 5, 25, 0xAAAAAA, false);

        // Column headers
        if (activeTab == 0 || activeTab == 1) {
            g.drawString(font, "§7ESP", COL_TOGGLE + 10, LIST_TOP - 12, 0xAAAAAA, false);
            g.drawString(font, "§7Name", COL_LABEL, LIST_TOP - 12, 0xAAAAAA, false);
            g.drawString(font, "§7Box-Farbe", COL_BOX_F, LIST_TOP - 12, 0xAAAAAA, false);
            g.drawString(font, "§7Linie", COL_LINE_T, LIST_TOP - 12, 0xAAAAAA, false);
            g.drawString(font, "§7L-Farbe", COL_LINE_F, LIST_TOP - 12, 0xAAAAAA, false);
        }

        // Row labels (only for entity/ore tabs)
        if (activeTab == 0 || activeTab == 1) {
            for (RowInfo row : visibleRows) {
                g.drawString(font, row.label(), COL_LABEL, row.y() + 6, 0xFFFFFF, false);
            }
        } else {
            for (RowInfo row : visibleRows) {
                switch (row.id()) {
                    case "entity_radius", "ore_radius", "sus_radius" ->
                        g.drawString(font, row.label(), 153, row.y() + 6, 0xAAAAAA, false);
                }
            }
        }

        // Separator line above footer
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0x88FFFFFF);

        super.render(g, mx, my, delta);
    }

    // ---- Scroll ---------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollPx -= (int)(scrollY * ROW_H);
        scrollPx = Math.max(0, scrollPx);
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
