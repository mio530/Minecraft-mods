package de.mio.visionmod.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.esp.OreESP;
import de.mio.visionmod.esp.SusChunks;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class OverlayWindow {

    public static final OverlayWindow INSTANCE = new OverlayWindow();
    private OverlayWindow() {}

    // No secondary window needed – renders directly into MC's GL context,
    // which works in windowed, borderless-fullscreen, and exclusive-fullscreen.
    public void init(Minecraft mc) {
        System.out.println("[VisionMod] Direct ESP renderer ready.");
    }

    public void destroy() {}

    public void onRenderEnd(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cam     = mc.player.getEyePosition(1.0f);
        PoseStack ps = ctx.matrices();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VisionConfig cfg = VisionConfig.get();

        // Disable depth test so boxes render through walls
        RenderSystem.disableDepthTest();

        VertexConsumer vc = buf.getBuffer(RenderType.lines());

        if (cfg.entityEspEnabled) {
            for (EntityESP.EntityData e : EntityESP.snapshot) {
                drawBox(ps, vc, cam,
                        e.minX(), e.minY(), e.minZ(),
                        e.maxX(), e.maxY(), e.maxZ(), e.boxColor());
            }
        }

        if (cfg.oreEspEnabled) {
            for (OreESP.OreData o : OreESP.snapshot) {
                drawBox(ps, vc, cam,
                        o.x() - 0.5, o.y() - 0.5, o.z() - 0.5,
                        o.x() + 0.5, o.y() + 0.5, o.z() + 0.5, o.boxColor());
            }
        }

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

        buf.endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }

    private static void drawBox(PoseStack ps, VertexConsumer vc, Vec3 cam,
                                 double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >>  8) & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        AABB bb = new AABB(minX - cam.x, minY - cam.y, minZ - cam.z,
                           maxX - cam.x, maxY - cam.y, maxZ - cam.z);
        LevelRenderer.renderLineBox(ps, vc, bb, r, g, b, a);
    }
}
