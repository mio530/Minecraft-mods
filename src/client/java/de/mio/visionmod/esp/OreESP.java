package de.mio.visionmod.esp;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

public final class OreESP {

    public record OreData(
            double x, double y, double z,
            int boxColor,
            int lineColor,
            boolean showLine
    ) {}

    public static volatile List<OreData> snapshot = Collections.emptyList();

    private static int tickCounter = 0;

    // Approximate Y-level ranges per ore for scan optimisation (minY inclusive, maxY inclusive)
    private static final Map<String, int[]> Y_RANGES = new HashMap<>();

    static {
        Y_RANGES.put("minecraft:diamond_ore",            new int[]{-64, 16});
        Y_RANGES.put("minecraft:deepslate_diamond_ore",  new int[]{-64, 16});
        Y_RANGES.put("minecraft:emerald_ore",            new int[]{-16, 320});
        Y_RANGES.put("minecraft:deepslate_emerald_ore",  new int[]{-64, 16});
        Y_RANGES.put("minecraft:gold_ore",               new int[]{-64, 32});
        Y_RANGES.put("minecraft:deepslate_gold_ore",     new int[]{-64, 0});
        Y_RANGES.put("minecraft:iron_ore",               new int[]{-64, 72});
        Y_RANGES.put("minecraft:deepslate_iron_ore",     new int[]{-64, 8});
        Y_RANGES.put("minecraft:redstone_ore",           new int[]{-64, 16});
        Y_RANGES.put("minecraft:deepslate_redstone_ore", new int[]{-64, 16});
        Y_RANGES.put("minecraft:lapis_ore",              new int[]{-64, 64});
        Y_RANGES.put("minecraft:deepslate_lapis_ore",    new int[]{-64, 0});
        Y_RANGES.put("minecraft:coal_ore",               new int[]{0, 256});
        Y_RANGES.put("minecraft:deepslate_coal_ore",     new int[]{-8, 0});
        Y_RANGES.put("minecraft:copper_ore",             new int[]{-16, 112});
        Y_RANGES.put("minecraft:deepslate_copper_ore",   new int[]{-16, 0});
        Y_RANGES.put("minecraft:ancient_debris",         new int[]{8, 119});
        Y_RANGES.put("minecraft:nether_gold_ore",        new int[]{10, 117});
        Y_RANGES.put("minecraft:nether_quartz_ore",      new int[]{10, 117});
    }

    public static void tick(Minecraft mc) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.oreEspEnabled || mc.level == null || mc.player == null) {
            snapshot = Collections.emptyList();
            return;
        }

        tickCounter++;
        if (tickCounter % 20 != 0) return;

        ChunkPos center = mc.player.chunkPosition();
        int radius = Math.max(1, Math.min(cfg.oreScanRadius, 8));
        List<OreData> next = new ArrayList<>();

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);

                for (String oreId : cfg.enabledOres) {
                    ResourceLocation blockKey = ResourceLocation.tryParse(oreId);
                    if (blockKey == null) continue;
                    Block targetBlock = BuiltInRegistries.BLOCK.get(blockKey);
                    if (targetBlock == Blocks.AIR) continue;

                    int[] yRange = Y_RANGES.getOrDefault(oreId, new int[]{-64, 320});
                    int minY = Math.max(yRange[0], mc.level.getMinBuildHeight());
                    int maxY = Math.min(yRange[1], mc.level.getMaxBuildHeight() - 1);

                    int boxColor  = VisionConfig.parseColor(cfg.oreBoxColors.getOrDefault(oreId, "#FFFFFFFF"));
                    int lineColor = VisionConfig.parseColor(cfg.oreLineColors.getOrDefault(oreId, "#FFFFFFFF"));
                    boolean showLine = cfg.globalLinesEnabled && cfg.oreLinesEnabled.contains(oreId);

                    for (int x = cx * 16; x < cx * 16 + 16; x++) {
                        for (int z = cz * 16; z < cz * 16 + 16; z++) {
                            for (int y = minY; y <= maxY; y++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                if (chunk.getBlockState(pos).is(targetBlock)) {
                                    next.add(new OreData(
                                            x + 0.5, y + 0.5, z + 0.5,
                                            boxColor, lineColor, showLine
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }

        snapshot = Collections.unmodifiableList(next);
    }
}
