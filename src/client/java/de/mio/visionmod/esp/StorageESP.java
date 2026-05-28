package de.mio.visionmod.esp;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StorageESP {

    public record StorageData(
            double x, double y, double z,
            int color, String label
    ) {}

    public static volatile List<StorageData> snapshot = Collections.emptyList();

    private static int tickCounter = 0;

    public static void tick(Minecraft mc) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.storageEspEnabled || mc.level == null || mc.player == null) {
            snapshot = Collections.emptyList();
            return;
        }

        tickCounter++;
        if (tickCounter % 20 != 0) return;

        ChunkPos center = mc.player.chunkPosition();
        int radius = Math.max(1, Math.min(cfg.storageEspRadius, 8));
        List<StorageData> next = new ArrayList<>();

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                LevelChunk lc = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (lc == null) continue;

                for (Map.Entry<BlockPos, BlockEntity> entry : lc.getBlockEntities().entrySet()) {
                    BlockEntity be = entry.getValue();
                    BlockPos pos   = entry.getKey();
                    int beColor;
                    String beLabel;

                    if (be instanceof ChestBlockEntity) {
                        beColor = VisionConfig.parseColor(cfg.chestColor);
                        beLabel = "Kiste";
                    } else if (be instanceof BarrelBlockEntity) {
                        beColor = VisionConfig.parseColor(cfg.barrelColor);
                        beLabel = "Fass";
                    } else if (be instanceof ShulkerBoxBlockEntity) {
                        beColor = VisionConfig.parseColor(cfg.shulkerColor);
                        beLabel = "Shulker";
                    } else if (be instanceof EnderChestBlockEntity) {
                        beColor = VisionConfig.parseColor(cfg.enderChestColor);
                        beLabel = "Ender Kiste";
                    } else {
                        continue;
                    }

                    next.add(new StorageData(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            beColor, beLabel));
                }
            }
        }

        snapshot = Collections.unmodifiableList(next);
    }
}
