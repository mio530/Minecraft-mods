package de.mio.visionmod.hud;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class HudOverlay {

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

        // Health bars disabled: blitSprite API requires RenderPipeline in 1.21.11
        // and the projection matrix needed for 3D→2D positioning is unavailable.
    }
}
