package de.mio.visionmod.esp;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public final class SusChunks {

    public record ChunkData(int chunkX, int chunkZ, boolean suspicious) {}

    public static volatile List<ChunkData> snapshot = Collections.emptyList();

    private static int tickCounter = 0;

    public static void resetOnDisconnect() {
        tickCounter = 0;
        snapshot = Collections.emptyList();
    }

    // All block types that count as "sus" per category
    private static final Set<Block> CHEST_BLOCKS = new HashSet<>(Arrays.asList(
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.ENDER_CHEST,
            Blocks.BARREL,
            Blocks.WHITE_SHULKER_BOX,   Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX,
            Blocks.YELLOW_SHULKER_BOX,  Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX,    Blocks.GRAY_SHULKER_BOX,
            Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX,
            Blocks.PURPLE_SHULKER_BOX,  Blocks.BLUE_SHULKER_BOX,
            Blocks.BROWN_SHULKER_BOX,   Blocks.GREEN_SHULKER_BOX,
            Blocks.RED_SHULKER_BOX,     Blocks.BLACK_SHULKER_BOX,
            Blocks.SHULKER_BOX
    ));

    private static final Set<Block> SPAWNER_BLOCKS = new HashSet<>(Arrays.asList(
            Blocks.SPAWNER,
            Blocks.TRIAL_SPAWNER
    ));

    private static final Set<Block> REDSTONE_BLOCKS = new HashSet<>(Arrays.asList(
            Blocks.REPEATER,
            Blocks.COMPARATOR,
            Blocks.PISTON,
            Blocks.STICKY_PISTON,
            Blocks.PISTON_HEAD,
            Blocks.MOVING_PISTON,
            Blocks.HOPPER,
            Blocks.DROPPER,
            Blocks.DISPENSER,
            Blocks.OBSERVER,
            Blocks.TARGET,
            Blocks.LEVER,
            Blocks.DAYLIGHT_DETECTOR
    ));

    public static void tick(Minecraft mc) {
        VisionConfig cfg = VisionConfig.get();
        if ((!cfg.susChunksEnabled && !cfg.showAllChunkBorders) || mc.level == null || mc.player == null) {
            snapshot = Collections.emptyList();
            return;
        }

        tickCounter++;
        if (tickCounter % 40 != 0) return; // scan every 2 seconds

        ChunkPos center = mc.player.chunkPosition();
        int radius = Math.max(1, Math.min(cfg.susChunksRadius, 8));
        List<ChunkData> next = new ArrayList<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) {
                    if (cfg.showAllChunkBorders) {
                        next.add(new ChunkData(cx, cz, false));
                    }
                    continue;
                }

                boolean suspicious = false;

                if (cfg.susChunksEnabled) {
                    int minY = mc.level.dimensionType().minY();
                    int maxY = mc.level.dimensionType().minY() + mc.level.dimensionType().height() - 1;

                    outer:
                    for (int x = cx * 16; x < cx * 16 + 16; x++) {
                        for (int z = cz * 16; z < cz * 16 + 16; z++) {
                            for (int y = minY; y <= maxY; y++) {
                                mpos.set(x, y, z);
                                Block block = mc.level.getBlockState(mpos).getBlock();
                                if (cfg.susDetectChests && CHEST_BLOCKS.contains(block)) {
                                    suspicious = true;
                                    break outer;
                                }
                                if (cfg.susDetectSpawners && SPAWNER_BLOCKS.contains(block)) {
                                    suspicious = true;
                                    break outer;
                                }
                                if (cfg.susDetectRedstone && REDSTONE_BLOCKS.contains(block)) {
                                    suspicious = true;
                                    break outer;
                                }
                            }
                        }
                    }
                }

                if (suspicious || cfg.showAllChunkBorders) {
                    next.add(new ChunkData(cx, cz, suspicious));
                }
            }
        }

        snapshot = Collections.unmodifiableList(next);
    }
}
