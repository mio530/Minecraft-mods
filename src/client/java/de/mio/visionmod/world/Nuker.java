package de.mio.visionmod.world;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class Nuker {
    private Nuker() {}

    private static BlockPos currentTarget = null;
    private static int stuckTimer = 0;
    private static int delay = 0;

    public static void resetOnDisconnect() {
        currentTarget = null;
        stuckTimer    = 0;
        delay         = 0;
    }

    public static void tick(Minecraft mc) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.nukerEnabled || mc.player == null || mc.level == null || mc.screen != null) return;

        if (delay > 0) { delay--; return; }

        BlockPos playerPos = mc.player.blockPosition();
        int range = (int) Math.ceil(cfg.nukerRange);
        double rangeSq = cfg.nukerRange * cfg.nukerRange;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (playerPos.distSqr(pos) > rangeSq) continue;

                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(mc.level, pos) < 0) continue; // bedrock etc.

                    double d = mc.player.position()
                        .distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (d < bestDist) { bestDist = d; best = pos; }
                }
            }
        }

        if (best == null) {
            if (currentTarget != null) { mc.gameMode.stopDestroyBlock(); currentTarget = null; }
            return;
        }

        if (best.equals(currentTarget)) {
            mc.gameMode.continueDestroyBlock(best, Direction.UP);
            stuckTimer++;
            if (stuckTimer > 40) { // give up after 2 seconds on same block
                mc.gameMode.stopDestroyBlock();
                currentTarget = null;
                stuckTimer = 0;
            }
        } else {
            mc.gameMode.startDestroyBlock(best, Direction.UP);
            currentTarget = best;
            stuckTimer = 0;
        }

        delay = Math.max(0, cfg.nukerDelay - 1);
    }
}
