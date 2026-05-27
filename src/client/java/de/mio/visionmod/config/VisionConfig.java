package de.mio.visionmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class VisionConfig {

    private static VisionConfig INSTANCE = new VisionConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Global toggles ---
    public boolean entityEspEnabled = false;
    public boolean oreEspEnabled    = false;
    public boolean globalLinesEnabled = true;

    // --- Entity config ---
    public Set<String> enabledEntityTypes = new LinkedHashSet<>(Arrays.asList(
            "minecraft:player",
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "minecraft:spider",
            "minecraft:enderman"
    ));
    public Set<String> enabledPlayerNames = new LinkedHashSet<>();

    public Map<String, String> entityBoxColors  = new LinkedHashMap<>();
    public Map<String, String> entityLineColors = new LinkedHashMap<>();
    public Set<String>         entityLinesEnabled = new LinkedHashSet<>();

    // --- Ore config ---
    public Set<String> enabledOres = new LinkedHashSet<>();
    public Map<String, String> oreBoxColors  = new LinkedHashMap<>();
    public Map<String, String> oreLineColors = new LinkedHashMap<>();
    public Set<String>         oreLinesEnabled = new LinkedHashSet<>();

    // --- General ---
    public int  oreScanRadius = 3;
    public boolean fillBoxes  = false;

    // --- Sus Chunks (hidden base detector) ---
    public boolean susChunksEnabled       = false;
    public boolean susDetectChests        = true;   // chests, barrels, shulker boxes
    public boolean susDetectSpawners      = true;   // monster spawners
    public boolean susDetectRedstone      = true;   // pistons, hoppers, comparators, etc.
    public boolean showAllChunkBorders    = false;  // outline every loaded chunk
    public String  susChunkColor          = "#AAFF4400";
    public String  chunkBorderColor       = "#22FFFFFF";

    // Ordered list of all known entity types (display order in config screen)
    public static final List<String> ALL_ENTITY_TYPES = Arrays.asList(
            "minecraft:player",
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "minecraft:spider",
            "minecraft:enderman",
            "minecraft:witch",
            "minecraft:pillager",
            "minecraft:vindicator",
            "minecraft:ravager",
            "minecraft:phantom",
            "minecraft:blaze",
            "minecraft:ghast",
            "minecraft:wither_skeleton",
            "minecraft:piglin",
            "minecraft:hoglin",
            "minecraft:zoglin",
            "minecraft:drowned",
            "minecraft:husk",
            "minecraft:stray"
    );

    // Ordered list of all ore block IDs
    public static final List<String> ALL_ORES = Arrays.asList(
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:redstone_ore",
            "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:coal_ore",
            "minecraft:deepslate_coal_ore",
            "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore",
            "minecraft:ancient_debris",
            "minecraft:nether_gold_ore",
            "minecraft:nether_quartz_ore"
    );

    // Default box colors per entity type (AARRGGBB hex)
    private static final Map<String, String> DEFAULT_ENTITY_BOX = new LinkedHashMap<>();
    private static final Map<String, String> DEFAULT_ORE_BOX = new LinkedHashMap<>();

    static {
        DEFAULT_ENTITY_BOX.put("minecraft:player",         "#FFFF0000");
        DEFAULT_ENTITY_BOX.put("minecraft:zombie",         "#FF00FF00");
        DEFAULT_ENTITY_BOX.put("minecraft:skeleton",       "#FFFFFFFF");
        DEFAULT_ENTITY_BOX.put("minecraft:creeper",        "#FF00FF80");
        DEFAULT_ENTITY_BOX.put("minecraft:spider",         "#FFB22222");
        DEFAULT_ENTITY_BOX.put("minecraft:enderman",       "#FF800080");
        DEFAULT_ENTITY_BOX.put("minecraft:witch",          "#FF9400D3");
        DEFAULT_ENTITY_BOX.put("minecraft:pillager",       "#FFFF8C00");
        DEFAULT_ENTITY_BOX.put("minecraft:vindicator",     "#FFFF4500");
        DEFAULT_ENTITY_BOX.put("minecraft:ravager",        "#FF8B0000");
        DEFAULT_ENTITY_BOX.put("minecraft:phantom",        "#FF4169E1");
        DEFAULT_ENTITY_BOX.put("minecraft:blaze",          "#FFFFD700");
        DEFAULT_ENTITY_BOX.put("minecraft:ghast",          "#FFF0F0F0");
        DEFAULT_ENTITY_BOX.put("minecraft:wither_skeleton","#FF2F2F2F");
        DEFAULT_ENTITY_BOX.put("minecraft:piglin",         "#FFFFC0CB");
        DEFAULT_ENTITY_BOX.put("minecraft:hoglin",         "#FFCD853F");
        DEFAULT_ENTITY_BOX.put("minecraft:zoglin",         "#FFFF69B4");
        DEFAULT_ENTITY_BOX.put("minecraft:drowned",        "#FF008080");
        DEFAULT_ENTITY_BOX.put("minecraft:husk",           "#FFD2B48C");
        DEFAULT_ENTITY_BOX.put("minecraft:stray",          "#FFB0C4DE");

        DEFAULT_ORE_BOX.put("minecraft:diamond_ore",           "#FF00FFFF");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_diamond_ore", "#FF00FFFF");
        DEFAULT_ORE_BOX.put("minecraft:emerald_ore",           "#FF00FF00");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_emerald_ore", "#FF00FF00");
        DEFAULT_ORE_BOX.put("minecraft:gold_ore",              "#FFFFAA00");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_gold_ore",    "#FFFFAA00");
        DEFAULT_ORE_BOX.put("minecraft:iron_ore",              "#FFC0C0C0");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_iron_ore",    "#FFC0C0C0");
        DEFAULT_ORE_BOX.put("minecraft:redstone_ore",          "#FFFF4444");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_redstone_ore","#FFFF4444");
        DEFAULT_ORE_BOX.put("minecraft:lapis_ore",             "#FF4444FF");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_lapis_ore",   "#FF4444FF");
        DEFAULT_ORE_BOX.put("minecraft:coal_ore",              "#FF606060");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_coal_ore",    "#FF606060");
        DEFAULT_ORE_BOX.put("minecraft:copper_ore",            "#FFFF7043");
        DEFAULT_ORE_BOX.put("minecraft:deepslate_copper_ore",  "#FFFF7043");
        DEFAULT_ORE_BOX.put("minecraft:ancient_debris",        "#FF8B0000");
        DEFAULT_ORE_BOX.put("minecraft:nether_gold_ore",       "#FFCD853F");
        DEFAULT_ORE_BOX.put("minecraft:nether_quartz_ore",     "#FFFFE4E1");
    }

    private VisionConfig() {
        // Initialize defaults
        entityBoxColors.putAll(DEFAULT_ENTITY_BOX);
        for (String id : DEFAULT_ENTITY_BOX.keySet()) {
            entityLineColors.put(id, DEFAULT_ENTITY_BOX.get(id));
        }
        entityLinesEnabled.addAll(Arrays.asList("minecraft:player", "minecraft:zombie", "minecraft:skeleton",
                "minecraft:creeper", "minecraft:spider", "minecraft:enderman"));

        oreBoxColors.putAll(DEFAULT_ORE_BOX);
        for (String id : DEFAULT_ORE_BOX.keySet()) {
            oreLineColors.put(id, DEFAULT_ORE_BOX.get(id));
        }
        enabledOres.addAll(Arrays.asList(
                "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
                "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
                "minecraft:ancient_debris"
        ));
        oreLinesEnabled.addAll(Arrays.asList("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
                "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:ancient_debris"));
    }

    public static VisionConfig get() {
        return INSTANCE;
    }

    public static void load() {
        File file = configPath().toFile();
        if (!file.exists()) {
            save();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            VisionConfig loaded = GSON.fromJson(reader, VisionConfig.class);
            if (loaded != null) {
                // Fill in any missing defaults
                for (String id : DEFAULT_ENTITY_BOX.keySet()) {
                    loaded.entityBoxColors.putIfAbsent(id, DEFAULT_ENTITY_BOX.get(id));
                    loaded.entityLineColors.putIfAbsent(id, DEFAULT_ENTITY_BOX.get(id));
                }
                for (String id : DEFAULT_ORE_BOX.keySet()) {
                    loaded.oreBoxColors.putIfAbsent(id, DEFAULT_ORE_BOX.get(id));
                    loaded.oreLineColors.putIfAbsent(id, DEFAULT_ORE_BOX.get(id));
                }
                INSTANCE = loaded;
            }
        } catch (Exception e) {
            System.err.println("[VisionMod] Failed to load config: " + e.getMessage());
            INSTANCE = new VisionConfig();
        }
    }

    public static void save() {
        try {
            File file = configPath().toFile();
            file.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            System.err.println("[VisionMod] Failed to save config: " + e.getMessage());
        }
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("visionmod.json");
    }

    /** Parse "#AARRGGBB" or "#RRGGBB" to packed ARGB int. */
    public static int parseColor(String hex) {
        if (hex == null || hex.isBlank()) return 0xFFFF0000;
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() == 6) s = "FF" + s;
        try {
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return 0xFFFF0000;
        }
    }

    /** Short display name for entity/ore IDs (removes "minecraft:" prefix and formats). */
    public static String displayName(String id) {
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return name.replace('_', ' ');
    }
}
