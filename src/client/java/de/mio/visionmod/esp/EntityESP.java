package de.mio.visionmod.esp;

import de.mio.visionmod.config.VisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EntityESP {

    public record EntityData(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int boxColor,
            int lineColor,
            boolean showLine,
            String label,
            float health,
            float maxHealth
    ) {}

    public static volatile List<EntityData> snapshot = Collections.emptyList();

    public static void tick(Minecraft mc) {
        VisionConfig cfg = VisionConfig.get();
        if (!cfg.entityEspEnabled || mc.level == null || mc.player == null) {
            snapshot = Collections.emptyList();
            return;
        }

        List<EntityData> next = new ArrayList<>();
        ChunkPos playerChunk = mc.player.chunkPosition();
        int radius = Math.max(1, Math.min(cfg.entityEspRadius, 16));

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            ChunkPos ec = entity.chunkPosition();
            if (Math.abs(ec.x - playerChunk.x) > radius || Math.abs(ec.z - playerChunk.z) > radius) continue;

            Identifier typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (typeKey == null) continue;
            String typeId = typeKey.toString();

            if (!cfg.enabledEntityTypes.contains(typeId)) continue;

            // Per-player name filter
            if (entity instanceof Player player) {
                String name = player.getGameProfile().name();
                if (!cfg.enabledPlayerNames.isEmpty() && !cfg.enabledPlayerNames.contains(name)) {
                    continue;
                }
            }

            int boxColor  = VisionConfig.parseColor(cfg.entityBoxColors.getOrDefault(typeId, "#FFFF0000"));
            int lineColor = VisionConfig.parseColor(cfg.entityLineColors.getOrDefault(typeId, "#FFFF0000"));
            boolean showLine = cfg.globalLinesEnabled && cfg.entityLinesEnabled.contains(typeId);

            AABB bb = entity.getBoundingBox();
            String label = entity instanceof Player p
                    ? p.getGameProfile().name()
                    : VisionConfig.displayName(typeId);
            float health    = entity instanceof LivingEntity le ? le.getHealth()    : -1f;
            float maxHealth = entity instanceof LivingEntity le ? le.getMaxHealth() : -1f;

            next.add(new EntityData(
                    bb.minX, bb.minY, bb.minZ,
                    bb.maxX, bb.maxY, bb.maxZ,
                    boxColor, lineColor, showLine, label, health, maxHealth
            ));
        }

        snapshot = Collections.unmodifiableList(next);
    }
}
