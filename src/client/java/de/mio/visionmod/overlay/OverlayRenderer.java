package de.mio.visionmod.overlay;

import de.mio.visionmod.config.VisionConfig;
import de.mio.visionmod.esp.EntityESP;
import de.mio.visionmod.esp.OreESP;
import de.mio.visionmod.esp.SusChunks;
import de.mio.visionmod.util.ProjectionUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;

public final class OverlayRenderer {

    public void render(Matrix4f mv, Matrix4f proj, Vec3 camPos, int sw, int sh) {
        glClearColor(0f, 0f, 0f, 0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glViewport(0, 0, sw, sh);

        // 2D orthographic projection: (0,0) = top-left
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, sw, sh, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        float cx = sw * 0.5f;
        float cy = sh * 0.5f;

        VisionConfig cfg = VisionConfig.get();

        if (cfg.entityEspEnabled) {
            for (EntityESP.EntityData e : EntityESP.snapshot) {
                renderBox(mv, proj, camPos, sw, sh,
                        e.minX(), e.minY(), e.minZ(),
                        e.maxX(), e.maxY(), e.maxZ(),
                        e.boxColor(), e.lineColor(), e.showLine(), cx, cy, cfg.fillBoxes);
            }
        }

        if (cfg.oreEspEnabled) {
            for (OreESP.OreData o : OreESP.snapshot) {
                renderBox(mv, proj, camPos, sw, sh,
                        o.x() - 0.5, o.y() - 0.5, o.z() - 0.5,
                        o.x() + 0.5, o.y() + 0.5, o.z() + 0.5,
                        o.boxColor(), o.lineColor(), o.showLine(), cx, cy, cfg.fillBoxes);
            }
        }

        // Sus Chunks – draw at player eye level
        if (cfg.susChunksEnabled || cfg.showAllChunkBorders) {
            for (SusChunks.ChunkData chunk : SusChunks.snapshot) {
                int color = chunk.suspicious()
                        ? VisionConfig.parseColor(cfg.susChunkColor)
                        : VisionConfig.parseColor(cfg.chunkBorderColor);
                renderChunkOutline(mv, proj, camPos, sw, sh, chunk.chunkX(), chunk.chunkZ(), color);
            }
        }
    }

    private void renderChunkOutline(Matrix4f mv, Matrix4f proj, Vec3 cam,
                                     int sw, int sh, int chunkX, int chunkZ, int color) {
        double minX = chunkX * 16.0;
        double minZ = chunkZ * 16.0;
        double maxX = minX + 16.0;
        double maxZ = minZ + 16.0;

        // Y offset = 0 means we project at the camera's own height plane
        float[] c0 = ProjectionUtil.project((float)(minX - cam.x), 0f, (float)(minZ - cam.z), mv, proj, sw, sh);
        float[] c1 = ProjectionUtil.project((float)(maxX - cam.x), 0f, (float)(minZ - cam.z), mv, proj, sw, sh);
        float[] c2 = ProjectionUtil.project((float)(maxX - cam.x), 0f, (float)(maxZ - cam.z), mv, proj, sw, sh);
        float[] c3 = ProjectionUtil.project((float)(minX - cam.x), 0f, (float)(maxZ - cam.z), mv, proj, sw, sh);

        if (c0[2] <= 0f && c1[2] <= 0f && c2[2] <= 0f && c3[2] <= 0f) return;

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;

        glLineWidth(2.0f);
        glColor4f(r, g, b, a);
        glBegin(GL_LINES);
        if (c0[2] > 0f && c1[2] > 0f) { glVertex2f(c0[0], c0[1]); glVertex2f(c1[0], c1[1]); }
        if (c1[2] > 0f && c2[2] > 0f) { glVertex2f(c1[0], c1[1]); glVertex2f(c2[0], c2[1]); }
        if (c2[2] > 0f && c3[2] > 0f) { glVertex2f(c2[0], c2[1]); glVertex2f(c3[0], c3[1]); }
        if (c3[2] > 0f && c0[2] > 0f) { glVertex2f(c3[0], c3[1]); glVertex2f(c0[0], c0[1]); }
        glEnd();
    }

    private void renderBox(Matrix4f mv, Matrix4f proj, Vec3 cam,
                            int sw, int sh,
                            double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ,
                            int boxColor, int lineColor, boolean showLine,
                            float cx, float cy, boolean fill) {
        // 8 corners of the AABB relative to camera
        float[][] corners = {
                {(float)(minX - cam.x), (float)(minY - cam.y), (float)(minZ - cam.z)},
                {(float)(maxX - cam.x), (float)(minY - cam.y), (float)(minZ - cam.z)},
                {(float)(maxX - cam.x), (float)(minY - cam.y), (float)(maxZ - cam.z)},
                {(float)(minX - cam.x), (float)(minY - cam.y), (float)(maxZ - cam.z)},
                {(float)(minX - cam.x), (float)(maxY - cam.y), (float)(minZ - cam.z)},
                {(float)(maxX - cam.x), (float)(maxY - cam.y), (float)(minZ - cam.z)},
                {(float)(maxX - cam.x), (float)(maxY - cam.y), (float)(maxZ - cam.z)},
                {(float)(minX - cam.x), (float)(maxY - cam.y), (float)(maxZ - cam.z)},
        };

        float[][] sc = new float[8][];
        int behindCount = 0;
        for (int i = 0; i < 8; i++) {
            sc[i] = ProjectionUtil.project(corners[i][0], corners[i][1], corners[i][2], mv, proj, sw, sh);
            if (sc[i][2] <= 0f) behindCount++;
        }
        if (behindCount == 8) return; // all corners behind camera

        // 12 edges of the box
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom face
                {4, 5}, {5, 6}, {6, 7}, {7, 4}, // top face
                {0, 4}, {1, 5}, {2, 6}, {3, 7}  // vertical edges
        };

        float ba = ((boxColor >> 24) & 0xFF) / 255f;
        float br = ((boxColor >> 16) & 0xFF) / 255f;
        float bg = ((boxColor >> 8)  & 0xFF) / 255f;
        float bb = ( boxColor        & 0xFF) / 255f;

        glLineWidth(1.5f);
        glColor4f(br, bg, bb, ba);
        glBegin(GL_LINES);
        for (int[] edge : edges) {
            int i0 = edge[0], i1 = edge[1];
            if (sc[i0][2] <= 0f || sc[i1][2] <= 0f) continue;
            glVertex2f(sc[i0][0], sc[i0][1]);
            glVertex2f(sc[i1][0], sc[i1][1]);
        }
        glEnd();

        // Tracer line: from screen center to projected center of the box
        if (showLine) {
            // Project box center
            float relCX = (float)((minX + maxX) * 0.5 - cam.x);
            float relCY = (float)((minY + maxY) * 0.5 - cam.y);
            float relCZ = (float)((minZ + maxZ) * 0.5 - cam.z);
            float[] center = ProjectionUtil.project(relCX, relCY, relCZ, mv, proj, sw, sh);
            if (center[2] > 0f) {
                float la = ((lineColor >> 24) & 0xFF) / 255f;
                float lr = ((lineColor >> 16) & 0xFF) / 255f;
                float lg = ((lineColor >> 8)  & 0xFF) / 255f;
                float lb = ( lineColor        & 0xFF) / 255f;

                glLineWidth(1.0f);
                glColor4f(lr, lg, lb, la);
                glBegin(GL_LINES);
                glVertex2f(cx, cy);
                glVertex2f(center[0], center[1]);
                glEnd();
            }
        }
    }
}
