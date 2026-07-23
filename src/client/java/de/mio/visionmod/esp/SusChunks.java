package de.mio.visionmod.esp;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;

public final class SusChunks {

    public record ChunkData(int chunkX, int chunkZ, boolean suspicious) {}

    public static volatile List<ChunkData> snapshot = Collections.emptyList();

    private static int tickCounter = 0;
    private static Object lastLevel = null;

    public static void resetOnDisconnect() {
        tickCounter = 0;
        lastLevel = null;
        snapshot = Collections.emptyList();
    }

    // Chests/barrels/shulkers/ender-chests and spawners are detected via block entities
    // (see isSuspicious); only these plain redstone blocks need a block scan.
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

        if (mc.level != lastLevel) { lastLevel = mc.level; snapshot = Collections.emptyList(); tickCounter = 0; }

        tickCounter++;
        if (tickCounter % 40 != 0) return; // scan every 2 seconds

        ChunkPos center = mc.player.chunkPosition();
        int radius = Math.max(1, Math.min(cfg.susChunksRadius, 8));
        List<ChunkData> next = new ArrayList<>();

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    if (cfg.showAllChunkBorders) next.add(new ChunkData(cx, cz, false));
                    continue;
                }

                boolean suspicious = cfg.susChunksEnabled && isSuspicious(chunk, cfg, mc);
                if (suspicious || cfg.showAllChunkBorders) {
                    next.add(new ChunkData(cx, cz, suspicious));
                }
            }
        }

        snapshot = Collections.unmodifiableList(next);
    }

    private static boolean isSuspicious(LevelChunk chunk, VisionConfig cfg, Minecraft mc) {
        // Fast path: chests, barrels, shulkers, ender chests and spawners are ALL block
        // entities, so a chunk's getBlockEntities() covers every chest/spawner indicator
        // in O(#block-entities) with no block scan at all.
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (cfg.susDetectChests && (be instanceof ChestBlockEntity
                    || be instanceof BarrelBlockEntity
                    || be instanceof ShulkerBoxBlockEntity
                    || be instanceof EnderChestBlockEntity)) {
                return true;
            }
            if (cfg.susDetectSpawners && (be instanceof SpawnerBlockEntity
                    || be instanceof TrialSpawnerBlockEntity)) {
                return true;
            }
        }

        // Redstone components (repeaters, pistons, hoppers, …) are plain blocks, so they
        // need a block scan — but we skip every all-air 16³ section, which is the vast
        // majority of a column, and read straight from the section palette.
        if (!cfg.susDetectRedstone) return false;
        LevelChunkSection[] sections = chunk.getSections();
        for (LevelChunkSection sec : sections) {
            if (sec == null || sec.hasOnlyAir()) continue;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = sec.getBlockState(x, y, z).getBlock();
                        if (REDSTONE_BLOCKS.contains(block)) return true;
                    }
                }
            }
        }
        return false;
    }
}
