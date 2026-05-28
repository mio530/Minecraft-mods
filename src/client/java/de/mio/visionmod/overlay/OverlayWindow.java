package de.mio.visionmod.overlay;

import com.mojang.blaze3d.systems.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.esp.ItemESP;
import de.mio.visionmod.esp.OreESP;
import de.mio.visionmod.esp.StorageESP;
import de.mio.visionmod.esp.SusChunks;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class OverlayWindow {

    public static final OverlayWindow INSTANCE = new OverlayWindow();
    private OverlayWindow() {}

    public void init(Minecraft mc) {}
    public void destroy() {}

    public void onRenderEnd(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cam = mc.player.getEyePosition(1.0f);
        PoseStack ps = ctx.matrices();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VisionConfig cfg = VisionConfig.get();

        GlStateManager._disableDepthTest();
        VertexConsumer vc = buf.getBuffer(RenderTypes.lines());

        // ── Entity ESP ──────────────────────────────────────────────────
        if (cfg.entityEspEnabled && !cfg.entityGlowEnabled) {
            for (EntityESP.EntityData e : EntityESP.snapshot) {
                drawBox(ps, vc, cam,
                        e.minX(), e.minY(), e.minZ(),
                        e.maxX(), e.maxY(), e.maxZ(), e.boxColor());

                // Tracer line: camera → entity centre
                if (e.showLine()) {
                    double cx = (e.minX() + e.maxX()) * 0.5;
                    double cy = (e.minY() + e.maxY()) * 0.5;
                    double cz = (e.minZ() + e.maxZ()) * 0.5;
                    drawLine(ps, vc, cam, cam.x, cam.y, cam.z, cx, cy, cz, e.lineColor());
                }

                // Health bar above box
                if (cfg.healthBarEnabled && e.maxHealth() > 0) {
                    double bx  = e.minX(), ex = e.maxX();
                    double barY = e.maxY() + 0.15;
                    double avgZ = (e.minZ() + e.maxZ()) * 0.5;
                    float  frac = Math.max(0f, Math.min(1f, e.health() / e.maxHealth()));
                    // Gray background
                    drawLine(ps, vc, cam, bx, barY, avgZ, ex, barY, avgZ, 0x88333333);
                    // Health-coloured fill
                    drawLine(ps, vc, cam, bx, barY, avgZ,
                             bx + (ex - bx) * frac, barY, avgZ, healthColor(frac));
                }
            }
        }

        // ── Ore ESP ─────────────────────────────────────────────────────
        if (cfg.oreEspEnabled) {
            for (OreESP.OreData o : OreESP.snapshot) {
                double cx = o.x(), cy = o.y(), cz = o.z();
                drawBox(ps, vc, cam,
                        cx - 0.5, cy - 0.5, cz - 0.5,
                        cx + 0.5, cy + 0.5, cz + 0.5, o.boxColor());

                if (o.showLine()) {
                    drawLine(ps, vc, cam, cam.x, cam.y, cam.z, cx, cy, cz, o.lineColor());
                }
            }
        }

        // ── Item ESP ─────────────────────────────────────────────────────
        if (cfg.itemEspEnabled) {
            for (ItemESP.ItemData it : ItemESP.snapshot) {
                drawBox(ps, vc, cam,
                        it.minX(), it.minY(), it.minZ(),
                        it.maxX(), it.maxY(), it.maxZ(), it.color());
                // Tracer to item centre
                double cx = (it.minX() + it.maxX()) * 0.5;
                double cy = (it.minY() + it.maxY()) * 0.5;
                double cz = (it.minZ() + it.maxZ()) * 0.5;
                drawLine(ps, vc, cam, cam.x, cam.y, cam.z, cx, cy, cz, it.color());
            }
        }

        // ── Storage (Container) ESP ──────────────────────────────────────
        if (cfg.storageEspEnabled) {
            for (StorageESP.StorageData st : StorageESP.snapshot) {
                drawBox(ps, vc, cam,
                        st.x() - 0.5, st.y() - 0.5, st.z() - 0.5,
                        st.x() + 0.5, st.y() + 0.5, st.z() + 0.5, st.color());
            }
        }

        // ── Sus Chunks / Chunk borders ───────────────────────────────────
        if (cfg.susChunksEnabled || cfg.showAllChunkBorders) {
            double py = mc.player.getY();
            for (SusChunks.ChunkData chunk : SusChunks.snapshot) {
                int color = chunk.suspicious()
                        ? VisionConfig.parseColor(cfg.susChunkColor)
                        : VisionConfig.parseColor(cfg.chunkBorderColor);
                double bx = chunk.chunkX() * 16.0;
                double bz = chunk.chunkZ() * 16.0;
                drawBox(ps, vc, cam, bx, py - 1, bz, bx + 16, py + 3, bz + 16, color);
            }
        }

        buf.endBatch(RenderTypes.lines());
        GlStateManager._enableDepthTest();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void drawBox(PoseStack ps, VertexConsumer vc, Vec3 cam,
                                 double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >>  8) & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        AABB bb = new AABB(minX - cam.x, minY - cam.y, minZ - cam.z,
                           maxX - cam.x, maxY - cam.y, maxZ - cam.z);
        ShapeRenderer.renderLineBox(ps.last(), vc, bb, r, g, b, a);
    }

    private static void drawLine(PoseStack ps, VertexConsumer vc, Vec3 cam,
                                  double wx1, double wy1, double wz1,
                                  double wx2, double wy2, double wz2, int color) {
        float x1 = (float)(wx1 - cam.x), y1 = (float)(wy1 - cam.y), z1 = (float)(wz1 - cam.z);
        float x2 = (float)(wx2 - cam.x), y2 = (float)(wy2 - cam.y), z2 = (float)(wz2 - cam.z);
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = Math.max(0.001f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        int   r = (color >> 16) & 0xFF;
        int   g = (color >>  8) & 0xFF;
        int   b =  color        & 0xFF;
        int   a = (color >> 24) & 0xFF;
        PoseStack.Pose pose = ps.last();
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx/len, dy/len, dz/len);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, dx/len, dy/len, dz/len);
    }

    /** Green (1.0) → Yellow (0.5) → Red (0.0) */
    private static int healthColor(float frac) {
        int r = frac < 0.5f ? 255 : (int)(255 * 2 * (1f - frac));
        int g = frac > 0.5f ? 255 : (int)(255 * 2 * frac);
        return 0xFF000000 | (r << 16) | (g << 8);
    }
}
