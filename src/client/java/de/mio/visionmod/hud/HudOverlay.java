package de.mio.visionmod.hud;

import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.util.ProjectionUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class HudOverlay {

    // Vanilla heart sprite IDs (from MC assets/minecraft/atlases/gui.json)
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.fromNamespaceAndPath("minecraft", "hud/heart/container");
    private static final ResourceLocation HEART_FULL      = ResourceLocation.fromNamespaceAndPath("minecraft", "hud/heart/full");
    private static final ResourceLocation HEART_HALF      = ResourceLocation.fromNamespaceAndPath("minecraft", "hud/heart/half");

    // Each heart is 9×9 px; vanilla draws them with 8px stride (1px overlap)
    private static final int HEART_SIZE   = 9;
    private static final int HEART_STRIDE = 8;
    private static final int HEART_COUNT  = 10;
    private static final int BAR_WIDTH    = (HEART_COUNT - 1) * HEART_STRIDE + HEART_SIZE; // 81px

    private HudOverlay() {}

    /** Called every HUD render frame via HudRenderCallback. */
    public static void onHudRender(GuiGraphics g, DeltaTracker delta) {
        VisionConfig cfg = VisionConfig.get();
        Minecraft mc = Minecraft.getInstance();

        // ── Coordinates HUD ───────────────────────────────────────────────────
        if (cfg.coordsHudEnabled && mc.player != null) {
            BlockPos bp = mc.player.blockPosition();
            Direction facing = mc.player.getDirection();
            String coords  = String.format("X: %d  Y: %d  Z: %d", bp.getX(), bp.getY(), bp.getZ());
            String dir     = "Richtung: " + facing.getName().toUpperCase();
            g.drawString(mc.font, coords, 4, 4,  0xFFFFFFFF, true);
            g.drawString(mc.font, dir,    4, 14, 0xFFCCCCCC, true);
        }

        if (!cfg.entityEspEnabled || !cfg.healthBarEnabled) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        for (EntityESP.EntityData e : EntityESP.snapshot) {
            if (e.maxHealth() <= 0f) continue;

            // Project the centre-top of the entity's bounding box
            double cx = (e.minX() + e.maxX()) * 0.5;
            double cy = e.maxY() + 0.4;
            double cz = (e.minZ() + e.maxZ()) * 0.5;

            float[] screen = ProjectionUtil.worldToScreen(cx, cy, cz, sw, sh);
            if (screen == null) continue;

            int sx = (int) screen[0];
            int sy = (int) screen[1];

            // Skip if off-screen
            if (sx < -BAR_WIDTH || sx > sw + BAR_WIDTH || sy < 0 || sy > sh + HEART_SIZE) continue;

            renderHearts(g, sx - BAR_WIDTH / 2, sy - HEART_SIZE, e.health(), e.maxHealth());
        }
    }

    /**
     * Draw 10 vanilla hearts centred at (startX, y).
     * Scales health linearly to 20 HP equivalent so it works for any max HP.
     */
    private static void renderHearts(GuiGraphics g, int startX, int y,
                                      float health, float maxHealth) {
        float normalised = health / maxHealth * 20f;

        for (int i = 0; i < HEART_COUNT; i++) {
            int x    = startX + i * HEART_STRIDE;
            float lo = i * 2f;

            g.blitSprite(HEART_CONTAINER, x, y, HEART_SIZE, HEART_SIZE);

            if (normalised >= lo + 2f) {
                g.blitSprite(HEART_FULL, x, y, HEART_SIZE, HEART_SIZE);
            } else if (normalised >= lo + 1f) {
                g.blitSprite(HEART_HALF, x, y, HEART_SIZE, HEART_SIZE);
            }
        }
    }
}
