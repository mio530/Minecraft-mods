package de.mio.visionmod.hud;

import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.util.ProjectionUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.Identifier;

public final class HudOverlay {

    // Vanilla heart sprite IDs (from MC assets/minecraft/atlases/gui.json)
    private static final Identifier HEART_CONTAINER = Identifier.fromNamespaceAndPath("minecraft", "hud/heart/container");
    private static final Identifier HEART_FULL      = Identifier.fromNamespaceAndPath("minecraft", "hud/heart/full");
    private static final Identifier HEART_HALF      = Identifier.fromNamespaceAndPath("minecraft", "hud/heart/half");

    // Each heart is 9×9 px; vanilla draws them with 8px stride (1px overlap)
    private static final int HEART_SIZE   = 9;
    private static final int HEART_STRIDE = 8;
    private static final int HEART_COUNT  = 10;
    private static final int BAR_WIDTH    = (HEART_COUNT - 1) * HEART_STRIDE + HEART_SIZE; // 81px

    private HudOverlay() {}

    /** Called every HUD render frame via HudRenderCallback. */
    public static void onHudRender(GuiGraphics g, DeltaTracker delta) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.entityEspEnabled || !cfg.healthBarEnabled) return;

        Minecraft mc = Minecraft.getInstance();
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
        // Normalise to a 20-HP scale so 10 hearts always fit
        float normalised = health / maxHealth * 20f;

        for (int i = 0; i < HEART_COUNT; i++) {
            int x    = startX + i * HEART_STRIDE;
            float lo = i * 2f;   // HP this heart starts at (normalised)

            // Container (empty outline) – always drawn
            g.blitSprite(RenderType::guiTextured, HEART_CONTAINER, x, y, HEART_SIZE, HEART_SIZE);

            // Filled portion
            if (normalised >= lo + 2f) {
                g.blitSprite(RenderType::guiTextured, HEART_FULL, x, y, HEART_SIZE, HEART_SIZE);
            } else if (normalised >= lo + 1f) {
                g.blitSprite(RenderType::guiTextured, HEART_HALF, x, y, HEART_SIZE, HEART_SIZE);
            }
        }
    }
}
