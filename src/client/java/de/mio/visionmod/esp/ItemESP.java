package de.mio.visionmod.esp;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ItemESP {

    public record ItemData(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int color, String label
    ) {}

    public static volatile List<ItemData> snapshot = Collections.emptyList();

    public static void tick(Minecraft mc) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.itemEspEnabled || mc.level == null || mc.player == null) {
            snapshot = Collections.emptyList();
            return;
        }

        List<ItemData> next = new ArrayList<>();
        ChunkPos playerChunk = mc.player.chunkPosition();
        int radius = Math.max(1, Math.min(cfg.itemEspRadius, 16));
        int color  = VisionConfig.parseColor(cfg.itemEspColor);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item)) continue;

            ChunkPos ec = entity.chunkPosition();
            if (Math.abs(ec.x - playerChunk.x) > radius
             || Math.abs(ec.z - playerChunk.z) > radius) continue;

            AABB bb = entity.getBoundingBox();
            String label = item.getItem().getHoverName().getString();
            next.add(new ItemData(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, color, label));
        }

        snapshot = Collections.unmodifiableList(next);
    }
}
