package de.mio.visionmod.esp;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;

public final class OreESP {

    public record OreData(
            double x, double y, double z,
            int boxColor,
            int lineColor,
            boolean showLine
    ) {}

    /** Per-ore render params + scan bounds, resolved once per scan. */
    private record OreParams(int boxColor, int lineColor, boolean showLine, int minY, int maxY) {}

    public static volatile List<OreData> snapshot = Collections.emptyList();

    private static int tickCounter = 0;

    public static void resetOnDisconnect() {
        tickCounter = 0;
        snapshot = Collections.emptyList();
    }

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

        // Resolve every enabled ore ONCE into a block→params map, and compute the
        // union Y-range. This lets us read each position's block state a single
        // time and test set membership, instead of re-scanning the whole column
        // once per enabled ore (which was O(ores) block lookups per position).
        int dimMinY = mc.level.dimensionType().minY();
        int dimMaxY = dimMinY + mc.level.dimensionType().height() - 1;
        Map<Block, OreParams> targets = new HashMap<>();
        int unionMin = Integer.MAX_VALUE, unionMax = Integer.MIN_VALUE;
        for (String oreId : cfg.enabledOres) {
            Identifier blockKey = Identifier.tryParse(oreId);
            if (blockKey == null) continue;
            Block targetBlock = BuiltInRegistries.BLOCK.getOptional(blockKey).orElse(null);
            if (targetBlock == null || targetBlock == Blocks.AIR) continue;

            int[] yRange = Y_RANGES.getOrDefault(oreId, new int[]{-64, 320});
            int minY = Math.max(yRange[0], dimMinY);
            int maxY = Math.min(yRange[1], dimMaxY);
            int boxColor  = VisionConfig.parseColor(cfg.oreBoxColors.getOrDefault(oreId, "#FFFFFFFF"));
            int lineColor = VisionConfig.parseColor(cfg.oreLineColors.getOrDefault(oreId, "#FFFFFFFF"));
            boolean showLine = cfg.globalLinesEnabled && cfg.oreLinesEnabled.contains(oreId);
            targets.put(targetBlock, new OreParams(boxColor, lineColor, showLine, minY, maxY));
            unionMin = Math.min(unionMin, minY);
            unionMax = Math.max(unionMax, maxY);
        }
        if (targets.isEmpty()) { snapshot = Collections.emptyList(); return; }

        ChunkPos center = mc.player.chunkPosition();
        int radius = Math.max(1, Math.min(cfg.oreEspRadius, 8));
        List<OreData> next = new ArrayList<>();
        int worldMinY = mc.level.dimensionType().minY();

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                // Iterate by 16³ section and skip all-air sections entirely — most of a
                // column is air, so this reads only the sections that can hold ores and
                // pulls block states straight from the section palette (no BlockPos, no
                // level.getBlockState indirection).
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection sec = sections[si];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    int baseY = worldMinY + si * 16;
                    if (baseY > unionMax || baseY + 15 < unionMin) continue;

                    for (int ly = 0; ly < 16; ly++) {
                        int y = baseY + ly;
                        if (y < unionMin || y > unionMax) continue;
                        for (int lx = 0; lx < 16; lx++) {
                            for (int lz = 0; lz < 16; lz++) {
                                OreParams p = targets.get(sec.getBlockState(lx, ly, lz).getBlock());
                                if (p != null && y >= p.minY() && y <= p.maxY()) {
                                    next.add(new OreData(
                                            cx * 16 + lx + 0.5, y + 0.5, cz * 16 + lz + 0.5,
                                            p.boxColor(), p.lineColor(), p.showLine()
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
