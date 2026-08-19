package de.mio.visionmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class VisionConfig {

    // INSTANCE is initialized at the end of the static block, AFTER DEFAULT_ENTITY_BOX
    // and DEFAULT_ORE_BOX are filled — declaring it here would cause a NPE on putAll().
    private static VisionConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Master switch: when false the whole client is dormant (no ticks, no
    //     rendering, no mixin effects) but the JAR stays loaded and settings are kept. ---
    public boolean masterEnabled = true;
    public int     keyMaster     = 0;   // toggle the whole client on/off

    // --- Global toggles ---
    public boolean entityEspEnabled = false;
    public boolean entityGlowEnabled = false;
    public boolean oreEspEnabled    = false;
    public boolean globalLinesEnabled = true;
    public boolean espLabels          = true;  // show name/coords/distance text on entity & ore ESP

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
    public int  entityEspRadius = 4;   // chunk radius for entity ESP
    public int  oreEspRadius    = 3;   // chunk radius for ore ESP
    public boolean oreEspExposedOnly = false; // only show ores touching air (cave-exposed)
    public int  susChunksRadius = 4;   // chunk radius for sus chunk scan
    public boolean fillBoxes    = false;

    // --- Entity extras ---
    public boolean healthBarEnabled   = false;  // 3D health bar above entity boxes

    // --- Fullbright ---
    public boolean fullbrightEnabled  = false;

    // --- Item ESP ---
    public boolean itemEspEnabled     = false;
    public int     itemEspRadius      = 3;      // chunk radius
    public String  itemEspColor       = "#FFFF00FF"; // Magenta

    // --- Storage (Container) ESP ---
    public boolean storageEspEnabled  = false;
    public int     storageEspRadius   = 3;
    public String  chestColor         = "#FFFFAA00"; // Gold
    public String  barrelColor        = "#FFA0522D"; // Sienna
    public String  shulkerColor       = "#FFDA70D6"; // Orchid
    public String  enderChestColor    = "#FF800080"; // Purple

    // --- Keybinds (GLFW key codes, not registered in MC settings) ---
    public int keyEntityEsp   = 295; // F6
    public int keyOreEsp      = 296; // F7
    public int keyOpenConfig  = 297; // F8
    public int keySusChunks   = 298; // F9
    public int keyFullbright  = 299; // F10
    public int keyItemEsp     = 0;   // unbound
    public int keyStorageEsp  = 0;   // unbound

    // === COMBAT ===
    public boolean maceDmgEnabled         = false;
    public boolean maceDmgClassicEnabled  = false;
    public boolean killAuraEnabled        = false;
    public float   killAuraRange      = 4.5f;
    public boolean killAuraPlayers    = true;
    public boolean killAuraMobs       = true;
    public int     killAuraCps        = 12;
    public boolean killAuraRotate     = true;
    public boolean killAuraSmooth       = true;  // move the camera smoothly instead of snapping
    public int     killAuraRotateSpeed  = 20;    // max degrees per tick when smooth (lower = smoother)
    public boolean killAuraRequireClick = false; // only attack while the attack key is held
    public boolean killAuraRequireCross = false; // only attack the entity under the crosshair
    public String  killAuraPriority   = "Nearest"; // Nearest, LowestHP, HighestHP
    public float   killAuraFov        = 360f;
    public boolean criticalsEnabled   = false;
    public boolean autoClickerEnabled = false;
    public int     autoClickerCps     = 12;
    public boolean velocityEnabled    = false;
    public float   velocityXZ         = 0.0f;  // 0=cancel, 1=normal horizontal
    public float   velocityY          = 0.0f;  // 0=cancel, 1=normal vertical
    public boolean autoTotemEnabled   = false;
    public float   autoTotemHpThresh  = 8.0f;
    public boolean noHurtCamEnabled   = false;
    public boolean autoLogEnabled     = false;
    public float   autoLogHp          = 6.0f;
    public boolean stunSlamEnabled    = false;
    public float   stunSlamMinFall    = 3.0f;  // min fallDistance blocks to trigger
    public float   stunSlamRange      = 4.5f;  // own reach (no longer tied to KillAura)
    public float   stunSlamFov        = 360f;  // only target what's in front of you
    public String  stunSlamPriority   = "Blocking"; // Nearest, LowestHP, Blocking
    public boolean stunSlamOnlyBlocking = true;  // require a raised shield
    public boolean stunSlamPredict    = true;    // lead the target's movement
    public int     stunSlamPredictTicks = 2;     // how many ticks ahead to aim
    public boolean stunSlamRotate     = true;    // face the (predicted) target
    public boolean stunSlamMaceCombo   = false;  // axe breaks the shield, mace follows up
    public int     stunSlamAxeMaceDelay = 0;     // ticks axe->mace; 0 = same tick (stun window is tiny)
    public boolean stunSlamShieldFallback = true; // no fall? still axe-hit to break the shield
    public int     stunSlamFallbackCooldown = 30; // ticks between fallback shield breaks
    public int     stunSlamCooldown   = 20;      // ticks between slams
    public int     stunSlamRestoreDelay = 3;     // ticks before switching the slot back
    public boolean autoMaceEnabled    = false;
    public float   autoMaceRange      = 5.0f;
    public float   autoMaceSpeed      = 25.0f; // max degrees turned per tick (lower = smoother)
    public boolean reachEnabled       = false;
    public float   reachDistance      = 3.5f;  // entity interaction range (vanilla 3.0)
    public boolean triggerBotEnabled  = false;
    public int     triggerBotCps      = 10;

    // === MOVEMENT ===
    public boolean sprintEnabled      = false;
    public boolean flyEnabled         = false;
    public float   flySpeed           = 1.0f;
    public boolean speedEnabled       = false;
    public float   speedMultiplier    = 1.5f;
    public boolean noFallEnabled      = false;
    public boolean stepEnabled        = false;
    public float   stepHeight         = 1.25f;
    public boolean jesusEnabled       = false;
    public boolean noSlowEnabled      = false;
    public boolean scaffoldEnabled    = false;
    public boolean surroundEnabled    = false;
    public boolean safeWalkEnabled    = false;
    public boolean invMoveEnabled     = false;
    public boolean spiderEnabled      = false;
    public boolean antiVoidEnabled    = false;
    public boolean autoWalkEnabled    = false;
    public boolean glideEnabled       = false;
    public boolean fastLadderEnabled  = false;
    public boolean autoJumpEnabled    = false;
    public boolean autoSneakEnabled   = false;

    // === PLAYER ===
    public boolean autoEatEnabled     = false;
    public int     autoEatThreshold   = 16;
    public boolean antiHungerEnabled  = false;
    public boolean antiPoisonEnabled  = false;
    public boolean antiAfkEnabled     = false;
    public int     antiAfkInterval    = 200;
    public boolean autoRespawnEnabled = false;
    public boolean chestStealerEnabled = false;
    public boolean autoToolEnabled    = false;
    public boolean autoWeaponEnabled  = false;
    public boolean autoArmorEnabled   = false;
    public boolean noBobEnabled       = false;

    // === RENDER EXTRAS ===
    public int     keyPanic           = 0;   // disable all + disconnect + fake crash + halt(1)
    public int     keyPanic2          = 0;   // disable all hacks silently, no exit
    public int     keyZoom            = 0;

    // Per-module toggle keybinds: module-id → GLFW key code (0 = unbound)
    public Map<String, Integer> moduleKeys = new LinkedHashMap<>();

    public static final List<String> TOGGLEABLE_MODULES = Arrays.asList(
        "entityEsp", "entityGlow", "healthBar", "oreEsp", "itemEsp", "storageEsp",
        "killAura", "maceDmg", "maceDmgClassic", "stunSlam", "autoMace", "reach", "triggerBot",
        "criticals", "autoClicker",
        "velocity", "autoTotem", "noHurtCam", "autoLog",
        "sprint", "fly", "speed", "noFall", "step", "jesus", "noSlow",
        "scaffold", "surround", "safeWalk", "invMove",
        "spider", "antiVoid", "autoWalk", "glide", "fastLadder", "autoJump", "autoSneak",
        "autoEat", "antiHunger", "antiPoison", "antiAfk", "autoRespawn", "chestStealer",
        "autoTool", "autoWeapon", "autoArmor",
        "fullbright", "gammaBoost", "noBob", "noFog", "noWeather", "antiBlind", "coords", "betterTab",
        "susChunks", "nuker"
    );
    public float   zoomFov            = 15f;
    public boolean noFogEnabled       = false;
    public boolean noWeatherEnabled   = false;
    public boolean antiBlindEnabled   = false;
    public boolean coordsHudEnabled   = false;
    public boolean betterTabEnabled   = false;  // Meteor-style Better Tab: show all players (no 80 cap)
    public boolean gammaBoostEnabled  = false;  // true fullbright via gamma override
    public float   gammaValue         = 5.0f;   // gamma level while boost is on (1 = vanilla max)

    // === WORLD ===
    public boolean nukerEnabled       = false;
    public float   nukerRange         = 4.5f;
    public int     nukerDelay         = 1;

    // --- Session reset ---
    public boolean resetOnRelog    = false;
    public boolean resetOnRestart  = false;

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
            "minecraft:stray",
            // more hostiles
            "minecraft:zombie_villager",
            "minecraft:zombified_piglin",
            "minecraft:piglin_brute",
            "minecraft:cave_spider",
            "minecraft:slime",
            "minecraft:magma_cube",
            "minecraft:silverfish",
            "minecraft:endermite",
            "minecraft:guardian",
            "minecraft:elder_guardian",
            "minecraft:shulker",
            "minecraft:evoker",
            "minecraft:vex",
            "minecraft:warden",
            "minecraft:breeze",
            "minecraft:bogged",
            "minecraft:creaking",
            "minecraft:wither",
            "minecraft:ender_dragon",
            // passive / normal mobs
            "minecraft:villager",
            "minecraft:wandering_trader",
            "minecraft:iron_golem",
            "minecraft:snow_golem",
            "minecraft:cow",
            "minecraft:mooshroom",
            "minecraft:pig",
            "minecraft:sheep",
            "minecraft:chicken",
            "minecraft:rabbit",
            "minecraft:horse",
            "minecraft:donkey",
            "minecraft:mule",
            "minecraft:llama",
            "minecraft:camel",
            "minecraft:goat",
            "minecraft:wolf",
            "minecraft:cat",
            "minecraft:ocelot",
            "minecraft:fox",
            "minecraft:panda",
            "minecraft:polar_bear",
            "minecraft:bee",
            "minecraft:turtle",
            "minecraft:frog",
            "minecraft:axolotl",
            "minecraft:allay",
            "minecraft:armadillo",
            "minecraft:sniffer",
            "minecraft:parrot",
            "minecraft:bat",
            "minecraft:squid",
            "minecraft:glow_squid",
            "minecraft:dolphin",
            "minecraft:cod",
            "minecraft:salmon",
            "minecraft:tropical_fish",
            "minecraft:pufferfish",
            "minecraft:strider",
            "minecraft:trader_llama",
            "minecraft:skeleton_horse",
            "minecraft:zombie_horse"
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
        // more hostiles — reddish/dark tones
        DEFAULT_ENTITY_BOX.put("minecraft:zombie_villager","#FF5F9E5F");
        DEFAULT_ENTITY_BOX.put("minecraft:zombified_piglin","#FFE08A9E");
        DEFAULT_ENTITY_BOX.put("minecraft:piglin_brute",   "#FFB5651D");
        DEFAULT_ENTITY_BOX.put("minecraft:cave_spider",    "#FF008B8B");
        DEFAULT_ENTITY_BOX.put("minecraft:slime",          "#FF66DD44");
        DEFAULT_ENTITY_BOX.put("minecraft:magma_cube",     "#FFFF6600");
        DEFAULT_ENTITY_BOX.put("minecraft:silverfish",     "#FF9AA0A6");
        DEFAULT_ENTITY_BOX.put("minecraft:endermite",      "#FF7B4FA8");
        DEFAULT_ENTITY_BOX.put("minecraft:guardian",       "#FF3FA9A0");
        DEFAULT_ENTITY_BOX.put("minecraft:elder_guardian", "#FF86B3AF");
        DEFAULT_ENTITY_BOX.put("minecraft:shulker",        "#FF9B6FA8");
        DEFAULT_ENTITY_BOX.put("minecraft:evoker",         "#FFD8D8C0");
        DEFAULT_ENTITY_BOX.put("minecraft:vex",            "#FFBFD4FF");
        DEFAULT_ENTITY_BOX.put("minecraft:warden",         "#FF0F5B60");
        DEFAULT_ENTITY_BOX.put("minecraft:breeze",         "#FF9AC4FF");
        DEFAULT_ENTITY_BOX.put("minecraft:bogged",         "#FF8FA36B");
        DEFAULT_ENTITY_BOX.put("minecraft:creaking",       "#FF6B5B4B");
        DEFAULT_ENTITY_BOX.put("minecraft:wither",         "#FF303030");
        DEFAULT_ENTITY_BOX.put("minecraft:ender_dragon",   "#FF6A0DAD");
        // passive / normal mobs — soft tones
        DEFAULT_ENTITY_BOX.put("minecraft:villager",       "#FFC8A165");
        DEFAULT_ENTITY_BOX.put("minecraft:wandering_trader","#FF4C7BD1");
        DEFAULT_ENTITY_BOX.put("minecraft:iron_golem",     "#FFD9D9D9");
        DEFAULT_ENTITY_BOX.put("minecraft:snow_golem",     "#FFEFFFFF");
        DEFAULT_ENTITY_BOX.put("minecraft:cow",            "#FF8B5A2B");
        DEFAULT_ENTITY_BOX.put("minecraft:mooshroom",      "#FFB03030");
        DEFAULT_ENTITY_BOX.put("minecraft:pig",            "#FFFFAEC9");
        DEFAULT_ENTITY_BOX.put("minecraft:sheep",          "#FFF2F2F2");
        DEFAULT_ENTITY_BOX.put("minecraft:chicken",        "#FFFFF0A0");
        DEFAULT_ENTITY_BOX.put("minecraft:rabbit",         "#FFD2B48C");
        DEFAULT_ENTITY_BOX.put("minecraft:horse",          "#FFC19A6B");
        DEFAULT_ENTITY_BOX.put("minecraft:donkey",         "#FF9C7A5B");
        DEFAULT_ENTITY_BOX.put("minecraft:mule",           "#FF8A6642");
        DEFAULT_ENTITY_BOX.put("minecraft:llama",          "#FFE5D3B3");
        DEFAULT_ENTITY_BOX.put("minecraft:camel",          "#FFE0B87A");
        DEFAULT_ENTITY_BOX.put("minecraft:goat",           "#FFCFC6BA");
        DEFAULT_ENTITY_BOX.put("minecraft:wolf",           "#FFBFBFBF");
        DEFAULT_ENTITY_BOX.put("minecraft:cat",            "#FFE0A030");
        DEFAULT_ENTITY_BOX.put("minecraft:ocelot",         "#FFEFC050");
        DEFAULT_ENTITY_BOX.put("minecraft:fox",            "#FFE07A3C");
        DEFAULT_ENTITY_BOX.put("minecraft:panda",          "#FFF0F0F0");
        DEFAULT_ENTITY_BOX.put("minecraft:polar_bear",     "#FFFAFAF0");
        DEFAULT_ENTITY_BOX.put("minecraft:bee",            "#FFFFD84D");
        DEFAULT_ENTITY_BOX.put("minecraft:turtle",         "#FF64C864");
        DEFAULT_ENTITY_BOX.put("minecraft:frog",           "#FFD3A02C");
        DEFAULT_ENTITY_BOX.put("minecraft:axolotl",        "#FFFFB5D8");
        DEFAULT_ENTITY_BOX.put("minecraft:allay",          "#FF7FD8FF");
        DEFAULT_ENTITY_BOX.put("minecraft:armadillo",      "#FFA07A55");
        DEFAULT_ENTITY_BOX.put("minecraft:sniffer",        "#FF9AD1B0");
        DEFAULT_ENTITY_BOX.put("minecraft:parrot",         "#FF33CC33");
        DEFAULT_ENTITY_BOX.put("minecraft:bat",            "#FF6B5040");
        DEFAULT_ENTITY_BOX.put("minecraft:squid",          "#FF4A6B8A");
        DEFAULT_ENTITY_BOX.put("minecraft:glow_squid",     "#FF3FE0C8");
        DEFAULT_ENTITY_BOX.put("minecraft:dolphin",        "#FFB9D6E8");
        DEFAULT_ENTITY_BOX.put("minecraft:cod",            "#FFC7A87A");
        DEFAULT_ENTITY_BOX.put("minecraft:salmon",         "#FFE07A6B");
        DEFAULT_ENTITY_BOX.put("minecraft:tropical_fish",  "#FFFF9E3D");
        DEFAULT_ENTITY_BOX.put("minecraft:pufferfish",     "#FFFFD966");
        DEFAULT_ENTITY_BOX.put("minecraft:strider",        "#FFA33B3B");
        DEFAULT_ENTITY_BOX.put("minecraft:trader_llama",   "#FFD6C39A");
        DEFAULT_ENTITY_BOX.put("minecraft:skeleton_horse", "#FFD8D8D8");
        DEFAULT_ENTITY_BOX.put("minecraft:zombie_horse",   "#FF6E9E6E");

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

        // Initialize INSTANCE here so the maps above are populated first
        INSTANCE = new VisionConfig();
    }

    private VisionConfig() {
        // Initialize defaults
        entityBoxColors.putAll(DEFAULT_ENTITY_BOX);
        for (String id : DEFAULT_ENTITY_BOX.keySet()) {
            entityLineColors.put(id, DEFAULT_ENTITY_BOX.get(id));
        }
        entityLinesEnabled.addAll(Arrays.asList("minecraft:player", "minecraft:zombie", "minecraft:skeleton",
                "minecraft:creeper", "minecraft:spider", "minecraft:enderman"));

        for (String id : TOGGLEABLE_MODULES) moduleKeys.put(id, 0);
        // Default hotkeys matching old dedicated fields
        moduleKeys.put("entityEsp",  295); // F6
        moduleKeys.put("oreEsp",     296); // F7
        moduleKeys.put("susChunks",  298); // F9
        moduleKeys.put("fullbright", 299); // F10

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
                // Migrate old dedicated key fields into moduleKeys (one-time upgrade)
                if (loaded.keyEntityEsp  > 0) loaded.moduleKeys.putIfAbsent("entityEsp",  loaded.keyEntityEsp);
                if (loaded.keyOreEsp     > 0) loaded.moduleKeys.putIfAbsent("oreEsp",     loaded.keyOreEsp);
                if (loaded.keySusChunks  > 0) loaded.moduleKeys.putIfAbsent("susChunks",  loaded.keySusChunks);
                if (loaded.keyFullbright > 0) loaded.moduleKeys.putIfAbsent("fullbright", loaded.keyFullbright);
                if (loaded.keyItemEsp    > 0) loaded.moduleKeys.putIfAbsent("itemEsp",    loaded.keyItemEsp);
                if (loaded.keyStorageEsp > 0) loaded.moduleKeys.putIfAbsent("storageEsp", loaded.keyStorageEsp);
                // Ensure all module IDs exist
                for (String id : TOGGLEABLE_MODULES) loaded.moduleKeys.putIfAbsent(id, 0);
                INSTANCE = loaded;
                if (loaded.resetOnRestart) loaded.resetFeatureToggles();
            }
        } catch (Exception e) {
            // config load error — fall through to defaults
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
            // save error — silently ignore
        }
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("visionmod.json");
    }

    // Fields ending in "Enabled" that the panic/reset must NOT touch: the tracer-line
    // display pref, and the master switch (panic disables features, not the whole client).
    private static final Set<String> RESET_SKIP = Set.of("globalLinesEnabled", "masterEnabled");

    /**
     * Sets every feature toggle to false (keeps settings like colors, radii, keybinds intact).
     * Reflection-driven: every public {@code boolean *Enabled} field is cleared, so any newly
     * added module is automatically covered by both panic buttons without extra wiring.
     */
    public void resetFeatureToggles() {
        for (Field f : getClass().getFields()) {
            if (f.getType() == boolean.class
                    && f.getName().endsWith("Enabled")
                    && !RESET_SKIP.contains(f.getName())) {
                try { f.setBoolean(this, false); } catch (IllegalAccessException ignored) {}
            }
        }
        showAllChunkBorders = false;
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
