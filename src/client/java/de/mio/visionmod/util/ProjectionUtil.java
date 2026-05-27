package de.mio.visionmod.util;

import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class ProjectionUtil {

    private ProjectionUtil() {}

    /**
     * Project a world-space offset (relative to camera position) to 2D screen coordinates.
     *
     * @param relX  world X minus camera X
     * @param relY  world Y minus camera Y
     * @param relZ  world Z minus camera Z
     * @param mv    model-view (position) matrix from WorldRenderContext
     * @param proj  projection matrix from WorldRenderContext
     * @param sw    screen width in pixels
     * @param sh    screen height in pixels
     * @return float[3]: {screenX, screenY, clipW}; if clipW <= 0 the point is behind the camera
     */
    public static float[] project(float relX, float relY, float relZ,
                                   Matrix4f mv, Matrix4f proj,
                                   int sw, int sh) {
        Vector4f pos = new Vector4f(relX, relY, relZ, 1.0f);
        mv.transform(pos);
        proj.transform(pos);

        if (Math.abs(pos.w) < 1e-6f) {
            return new float[]{0f, 0f, -1f};
        }

        float ndcX = pos.x / pos.w;
        float ndcY = pos.y / pos.w;
        float sx = (ndcX + 1f) * 0.5f * sw;
        float sy = (1f - ndcY) * 0.5f * sh;  // Y flipped: OpenGL +Y up, screen +Y down

        return new float[]{sx, sy, pos.w};
    }
}
