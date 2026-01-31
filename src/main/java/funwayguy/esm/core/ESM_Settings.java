package funwayguy.esm.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.minecraft.entity.EntityList;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.Level;

public class ESM_Settings {

    // Mod Data
    public static final String Version = "@VERSION@";
    public static final String ID = "ESM_ReignModpack";
    public static final String Channel = "ESM_ReignModpack";
    public static final String Name = "Epic Siege Mod - Unofficial Reign Modpack Edition";
    public static final String Proxy = "funwayguy.esm.core.proxies";

    // Main
    public static int Awareness;
    private static boolean Xray;
    private static boolean XrayOnlyDuringSiege;
    public static int TargetCap;
    public static boolean VillagerTarget;
    public static boolean Apocalypse;
    public static boolean Chaos;
    public static boolean AllowSleep;
    public static boolean QuickPathing;
    public static int ResistanceCoolDown;
    public static boolean keepLoaded;
    public static boolean moreSpawning;
    public static boolean forcePath;
    public static int timedDifficulty;
    public static int hardDay;
    public static boolean hardDamage;
    public static boolean friendlyFire;
    public static ArrayList<String> AIExempt = new ArrayList<String>();
    public static boolean flipBlacklist = false;
    public static int SiegeFrequency;
    public static int SiegeWarmup;
    public static boolean simpleAiCheck = true;
    public static int idleTargetInstantDespawnTicks = 600;

    public static HashMap<Integer, DimSettings> dimSettings = new HashMap<Integer, DimSettings>();

    public static boolean ENFORCE_DEFAULT;

    // Witch
    public static String[] customPotions;

    // Creeper
    public static boolean CreeperBreaching;
    public static boolean CreeperNapalm;
    public static boolean CreeperPowered;
    public static int CreeperPoweredRarity;
    public static boolean CreeperChargers;
    public static boolean CenaCreeper = false;
    public static int CenaCreeperRarity = 9;
    public static boolean CreeperEnhancementsOnlyWhenSiegeAllowed = true;

    // Blaze
    public static boolean BlazeSpawn;
    public static int BlazeRarity;
    public static int BlazeFireballs;
    public static ArrayList<Integer> BlazeDimensionBlacklist;

    // Ghast
    public static boolean GhastSpawn;
    public static int GhastRarity;
    public static double GhastFireDelay;
    public static boolean GhastBreaching;
    public static double GhastFireDist;
    public static ArrayList<Integer> GhastDimensionBlacklist;

    // Skeleton
    public static int SkeletonDistance;
    public static int SkeletonAccuracy;

    // Zombie
    public static boolean ZombieInfectious;
    public static boolean ZombieDiggers;
    public static boolean ZombieDiggerTools;
    public static int ZombiePillaring;
    private static BlockAndMeta[] ZombieGriefBlocks; // use getZombieGriefBlocks instead
    private static Set<String> ZombieGriefBlocksRaw;
    public static boolean ZombieGriefBlocksAnyTime;
    public static boolean ZombieGriefBlocksNoTool;
    public static boolean ZombieGriefBlocksLightSources;
    public static int ZombieGriefBlocksTriesPerTick = 15;
    public static int ZombieGriefBlocksRangeXZ = 6;
    public static int ZombieGriefBlocksRangeY = 4;
    private static BlockAndMeta[] ZombieDigBlacklist; // use getZombieDigBlacklist instead
    private static ArrayList<String> ZombieDigBlacklistRaw;
    public static boolean ZombieSwapList;
    public static boolean DemolitionZombies;
    public static boolean ZombieEnhancementsOnlyWhenSiegeAllowed = true;

    // Enderman
    public static boolean EndermanSlender;
    public static boolean EndermanPlayerTele;

    // Spider
    public static int SpiderWebChance;

    // Advanced
    public static ArrayList<String> MobBombs;
    public static int MobBombRarity;
    public static boolean MobBombAll;
    public static boolean CrystalBombs;
    public static boolean WitherSkeletons;
    public static int WitherSkeletonRarity;
    public static int PotionMobs;
    public static int[] PotionMobEffects;
    public static boolean attackEvasion;
    public static float bossModifier;
    public static boolean animalsAttack;
    public static boolean neutralMobs;
    public static boolean mobBoating = true;
    public static boolean attackPets = true;
    public static boolean MobBombInvert = false;

    // Generation
    public static boolean NewEnd;
    public static boolean NewHell;
    public static boolean SpawnForts;
    public static int fortRarity = 2;
    public static int fortDistance = 16;
    public static ArrayList<Integer> fortDimensions = new ArrayList<Integer>();
    public static ArrayList<String> fortSpawners = new ArrayList<String>();

    // Non-configurables
    public static ArrayList<String> fortDB = new ArrayList<String>();
    public static WorldServer[] currentWorlds = null;
    public static File worldDir = null;
    public static boolean ambiguous_AI = true;
    public static Configuration defConfig;

    public static void LoadMainConfig(File file) {
        boolean flag = !file.exists();
        Configuration config = new Configuration(file, true);
        ESM.log.log(Level.INFO, "Loading ESM Global Config");

        defConfig = config;

        config.load();

        config.setCategoryComment(
            "World",
            "For the main list of options please refer to the ESM_Options.cfg file in your world directory.");

        NewEnd = config.get("World", "Use New End", false)
            .getBoolean(false);
        NewHell = config.get("World", "Use New Nether", false)
            .getBoolean(false);

        config.save();

        ResetToDefault(flag);
    }

    public static void ResetToDefault(boolean newFile) {
        if (defConfig == null) {
            ESM.log.log(Level.ERROR, "Failed to reset options to default! Global default is null");
            return;
        }

        defConfig.load();

        // Main
        timedDifficulty = defConfig.getInt(
            "Warm Up Days",
            "Main",
            7,
            0,
            Integer.MAX_VALUE,
            "How many days until ESM spawns mobs at full rate.");
        hardDay = defConfig.getInt(
            "Hardcore Day Cycle",
            "Main",
            8,
            0,
            Integer.MAX_VALUE,
            "The interval in which 'hard' days will occur where mob spawning is increased and lighting is ignored (0 = off, default = 8/full moon)");
        hardDamage = defConfig
            .getBoolean("Hardcore Double Damage", "Main", false, "Mobs inflict double damage on hardcore days");
        Awareness = defConfig.get("Main", "Awareness Radius", 64)
            .getInt(64);
        Xray = defConfig.get("Main", "Xray Mobs", true)
            .getBoolean(true);
        XrayOnlyDuringSiege = defConfig.get(
            "Main",
            "Xray Mobs only during siege",
            true,
            "If enabled, then xray targeting will only be allowed during sieges. Main Xray option must also be enabled.")
            .getBoolean(true);
        TargetCap = defConfig.get("Main", "Pathing Cap", 16)
            .getInt(16);
        VillagerTarget = defConfig.get("Main", "Villager Targeting", true)
            .getBoolean(true);
        Apocalypse = defConfig.get("Main", "Apocalypse Mode", false)
            .getBoolean(false);
        Chaos = defConfig.get("Main", "Chaos Mode", false)
            .getBoolean(false);
        AllowSleep = defConfig.get("Main", "Allow Sleep", false)
            .getBoolean(false);
        ambiguous_AI = defConfig
            .get(
                "Main",
                "Ambiguous AI",
                true,
                "If set to true, ESM will not check whether the entity is a mob or not when setting up new AI")
            .getBoolean(true);
        QuickPathing = defConfig
            .get(
                "Main",
                "Quick Pathing",
                false,
                "If set to fales, mobs can use much longer routes to get to their target")
            .getBoolean(false);
        ResistanceCoolDown = defConfig
            .get(
                "Main",
                "Resistance Cooldown",
                200,
                "The amount of ticks of resistance given to the player after changing dimensions")
            .getInt(200);
        keepLoaded = defConfig
            .get(
                "Main",
                "Keep Loaded",
                false,
                "Keeps mobs with an active target from despawning. Can causes issues with chunk loading/unloading")
            .getBoolean(false);
        moreSpawning = defConfig
            .get(
                "Main",
                "More Spawning",
                true,
                "Reduces spawning safe zone from 24 blocks to 8 and makes mobs require only basic conditions to spawn")
            .getBoolean(true);
        forcePath = defConfig
            .get(
                "Main",
                "Force Non-AI Pathing",
                false,
                "Forces non pathing mobs to attack from further away. Can cause additional lag")
            .getBoolean(false);
        ENFORCE_DEFAULT = defConfig
            .get(
                "Main",
                "Enforce Defaults",
                true,
                "Ignores world specific settings and just uses the global defaults instead")
            .getBoolean(true);
        friendlyFire = defConfig
            .getBoolean("Friendly Fire", "Main", true, "Can mobs harm eachother (type specific in chaos mode)");
        attackPets = defConfig.getBoolean("Attack Pets", "Main", true, "Mobs will attack player owned pets");
        flipBlacklist = defConfig
            .getBoolean("Flip Exemtions", "Main", false, "Flips the exemption listing to whitelist mode");
        SiegeFrequency = defConfig.getInt(
            "Siege Frequency",
            "Main",
            1,
            1,
            Integer.MAX_VALUE,
            "How often in days the various mobs are allowed to gain siege capabilites (dig, pillar, demolition and/or grief - whatever is enabled). Default of 1 means every day/night.\n"
                + "The frequency of every x days specified here start from day y, where day y will be the Siege Warmup option below counting down to 0.\n");
        SiegeWarmup = defConfig.getInt(
            "Siege Warmup",
            "Main",
            0,
            0,
            Integer.MAX_VALUE,
            "Specify how many days should act as a warmup period before mobs can become siege-capable. Default of 0 means that mobs on the very first day can siege - no countdown at all.\n"
                + "An example use of this is if you set Siege Frequency to 8 for every full moon (under a vanilla lunar cycle), but you want to avoid the first day full moon bringing sieges - set this to any value from 1 to 7. By the time the next Siege Frequeny is reached on day 8, the Siege Warmup would have elapsed either way.\n");
        simpleAiCheck = defConfig.getBoolean(
            "Simple AI Check",
            "Main",
            simpleAiCheck,
            "Use a more performance-friendly check for new entity AI. Might not catch some special mod-added entities.");
        idleTargetInstantDespawnTicks = defConfig.getInt(
            "Idle Target Instant Despawn Timout",
            "Main",
            idleTargetInstantDespawnTicks,
            0,
            Integer.MAX_VALUE,
            "If a mob has had no target for this many ticks, instantly despawn it. Set to 0 to disable.");

        String[] tmpAIE = defConfig.get("Main", "AI Exempt Mob IDs", new String[] { "VillagerGolem" })
            .getStringList();
        AIExempt = new ArrayList<String>();
        AIExempt.addAll(Arrays.asList(tmpAIE));

        // Witch
        String[] defPot = new String[] { Potion.harm.id + ":1:0", Potion.moveSlowdown.id + ":300:0",
            Potion.blindness.id + ":300:0", Potion.poison.id + ":300:0", Potion.weakness.id + "300:1",
            Potion.digSlowdown.id + "300:2" };
        customPotions = defConfig.getStringList(
            "Custom Potions",
            "Witch",
            defPot,
            "List of potion types witches can throw (\"id:duration:lvl\")");

        // Creeper
        CreeperBreaching = defConfig.get("Creeper", "Breaching", true)
            .getBoolean(true);
        CreeperNapalm = defConfig.get("Creeper", "Napalm", true)
            .getBoolean(true);
        CreeperPowered = defConfig.get("Creeper", "Powered", true)
            .getBoolean(true);
        CreeperPoweredRarity = defConfig.get("Creeper", "Powered Rarity", 9)
            .getInt(9);
        CreeperChargers = defConfig
            .getBoolean("Chargering", "Creeper", true, "Creepers will run at you at speed before detonating");
        CenaCreeper = defConfig.getBoolean("Cena Creeper", "Creeper", false, "AND HIS NAME IS...");
        CenaCreeperRarity = defConfig
            .getInt("Cena Creeper Rarity", "Creeper", 9, 0, Integer.MAX_VALUE, "How rare are they");
        CreeperEnhancementsOnlyWhenSiegeAllowed = defConfig.getBoolean(
            "Enhance only when siege allowed",
            "Creeper",
            true,
            "If true, all creeper enhancements will be skipped if siege is not allowed.\n"
                + "See freqency and warmup settings under Main. Applies to newly-spawned creepers only.");

        // Skeletons
        SkeletonAccuracy = defConfig.get("Skeleton", "Arrow Error", 0)
            .getInt(0);
        SkeletonDistance = defConfig.get("Skeleton", "Fire Distance", 64)
            .getInt(64);

        // Zombies
        ZombieInfectious = defConfig.get("Zombie", "Infectious", true)
            .getBoolean(true);
        ZombieDiggers = defConfig.get("Zombie", "Diggers", true)
            .getBoolean(true);
        ZombieDiggerTools = defConfig.get("Zombie", "Need Required Tools", true)
            .getBoolean(true);
        ZombiePillaring = defConfig
            .get("Zombie", "Pillaring Blocks", 64, "How many blocks to give zombies to pillar up with")
            .getInt(64);
        String[] defGrief = new String[] { "minecraft:chest", // Might not be a good idea for loot reasons
            "minecraft:furnace", "minecraft:crafting_table", "minecraft:melon_stem", "minecraft:pumpkin_stem",
            "minecraft:fence_gate", "minecraft:melon_block", "minecraft:pumpkin", "minecraft:glass",
            "minecraft:glass_pane", "minecraft:stained_glass", "minecraft:stained_glass_pane", "minecraft:carrots",
            "minecraft:potatoes", "minecraft:brewing_stand", "minecraft:enchanting_table", "minecraft:cake",
            "minecraft:ladder", "minecraft:wooden_door", "minecraft:farmland", "minecraft:bookshelf",
            "minecraft:sapling", "minecraft:bed", "minecraft:fence" };
        ZombieGriefBlocksRaw = new HashSet<String>(
            Arrays.asList(
                defConfig.get(
                    "Zombie",
                    "General Griefable Blocks",
                    defGrief,
                    "What blocks will be targeted for destruction when not attacking players (Does not affect general digging, light sources are included by default, add ':#' for metadata e.g. 'minecraft:wool:1')")
                    .getStringList()));
        ZombieGriefBlocksAnyTime = defConfig.get(
            "Zombie",
            "Always Griefable",
            true,
            "If true, Zombies will be able to grief blocks from the block list any time - not just when a siege is underway.")
            .getBoolean(true);
        ZombieGriefBlocksNoTool = defConfig
            .get(
                "Zombie",
                "Griefable Blocks Ignore Tool Requirement",
                true,
                "If true, Zombies will be able to grief blocks from the block list without needing the right tool.")
            .getBoolean(true);
        ZombieGriefBlocksLightSources = defConfig.get(
            "Zombie",
            "All lightsources griefable",
            true,
            "If true, Zombies will target any light source for griefing. Set to false if you want to manually specify them.")
            .getBoolean(true);
        ZombieGriefBlocksTriesPerTick = defConfig.get(
            "Zombie",
            "How many iterations to search for Griefable Blocks per tick",
            ZombieGriefBlocksTriesPerTick,
            "Zombies will look at this many random nearby blocks each tick to decide if it will grief it. Note that Air and Liquid blocks are not counted.")
            .getInt(ZombieGriefBlocksTriesPerTick);
        ZombieGriefBlocksRangeXZ = defConfig.get(
            "Zombie",
            "The X/Z range for Zombies to search for griefing",
            ZombieGriefBlocksRangeXZ,
            "A radius value, defining a box centered on the zombie. E.g. a value of 8 will cause zombies to search in a 17x17 area centered on the zombie.")
            .getInt(ZombieGriefBlocksRangeXZ);
        ZombieGriefBlocksRangeY = defConfig.get(
            "Zombie",
            "The Y range to for Zombies to search for griefing",
            ZombieGriefBlocksRangeY,
            "A height upwards or downwards to search e.g. a value of 4 will search a total height of 9 centered on the zombie's foot level. Combines with ZombieGriefBlocksRangeXZ to form a search cube.")
            .getInt(ZombieGriefBlocksRangeY);
        ZombieDigBlacklistRaw = new ArrayList<String>(
            Arrays.asList(
                defConfig
                    .get(
                        "Zombie",
                        "Digging Blacklist",
                        new String[] {},
                        "Blacklisted blocks for digging (Add ':#' for metadata e.g. 'minecraft:wool:1')")
                    .getStringList()));
        ZombieSwapList = defConfig
            .get("Zombie", "Blacklist to Whitelist", false, "Use the digging blacklist as a whitelist instead")
            .getBoolean(false);
        DemolitionZombies = defConfig.get("Zombie", "Demolition Zombies", true, "Zombies can placed armed TNT")
            .getBoolean(true);
        ZombieEnhancementsOnlyWhenSiegeAllowed = defConfig.getBoolean(
            "Enhance only when siege allowed",
            "Zombie",
            true,
            "If true, the Zombie siege capabilites will be disabled if siege is not allowed.\n"
                + "See freqency and warmup settings under Main. Applies to all existing Zombies - including already-spawned ones.");

        // Blazes
        BlazeSpawn = defConfig.get("Blaze", "Spawn", true)
            .getBoolean(true);
        BlazeRarity = defConfig.get("Blaze", "Rarity", 9)
            .getInt(9);
        BlazeFireballs = defConfig.get("Blaze", "Fireballs", 9)
            .getInt(9);
        int[] tmpBDB = defConfig.get("Blaze", "Dimension Blacklist", new int[] {})
            .getIntList();
        BlazeDimensionBlacklist = new ArrayList<Integer>();
        for (int i : tmpBDB) {
            BlazeDimensionBlacklist.add(i);
        }

        // Ghasts
        GhastSpawn = defConfig.get("Ghast", "Spawn", false)
            .getBoolean(false);
        GhastRarity = defConfig.get("Ghast", "Rarity", 9)
            .getInt(9);
        GhastFireDelay = defConfig.get("Ghast", "Fire Delay", 1.0D)
            .getDouble(1.0D);
        GhastBreaching = defConfig.get("Ghast", "Breaching", true)
            .getBoolean(true);
        GhastFireDist = defConfig.get("Ghast", "Fire Distance", 64.0D)
            .getDouble(64.0D);
        int[] tmpGDB = defConfig.get("Ghast", "Dimension Blacklist", new int[] {})
            .getIntList();
        GhastDimensionBlacklist = new ArrayList<Integer>();
        for (int i : tmpGDB) {
            GhastDimensionBlacklist.add(i);
        }

        // Endermen
        EndermanSlender = defConfig
            .get("Enderman", "Slender-Mode", false, "Makes Endermen stalk players with side effects")
            .getBoolean();
        EndermanPlayerTele = defConfig.get("Enderman", "Player Teleport", true)
            .getBoolean(true);

        // Spider
        SpiderWebChance = MathHelper.clamp_int(
            defConfig.get("Spider", "Webbing Chance (0-100)", 25)
                .getInt(25),
            0,
            100);

        // Advanced
        String[] tmp = defConfig.get("Advanced Mobs", "Mob Bombs", new String[] { EntityList.getStringFromID(52) })
            .getStringList();
        MobBombs = new ArrayList<String>();
        MobBombs.addAll(Arrays.asList(tmp));
        MobBombInvert = defConfig.getBoolean(
            "Mob Bomb Invert",
            "Advanced Mobs",
            false,
            "Inverts the mob bomb listing to be act as a blacklist");
        MobBombRarity = defConfig.get("Advanced Mobs", "Mob Bomb Rarity", 9)
            .getInt(9);
        MobBombAll = defConfig
            .get("Advanced Mobs", "Mob Bomb All", true, "Skip the Mob Bomb list and allow everything!")
            .getBoolean(true);
        CrystalBombs = defConfig
            .get("Advanced Mobs", "Crystal Bombs", false, "Mob Bombs are now Crystals instead of Creepers")
            .getBoolean(false);
        WitherSkeletons = defConfig.get("Advanced Mobs", "Wither Skeletons", true)
            .getBoolean(true);
        WitherSkeletonRarity = defConfig.get("Advanced Mobs", "Wither Skeleton Rarity", 9)
            .getInt(9);
        PotionMobs = defConfig.get("Advanced Mobs", "Potion Buff Chance (0-100)", 1)
            .getInt(1);
        PotionMobEffects = defConfig
            .get(
                "Advanced Mobs",
                "Potion Buff List",
                new int[] { 14, 12, 5, 1 },
                "List of all the valid potion IDs a mob can spawn with. Amplifier is always x1")
            .getIntList();
        attackEvasion = defConfig.get("Advanced Mobs", "Attack Evasion", true)
            .getBoolean(true);
        bossModifier = defConfig.getFloat(
            "Boss Kill Modifier",
            "Advanced Mobs",
            0.1F,
            0F,
            Float.MAX_VALUE,
            "Every time a boss is killed all mob heal and damage multipliers will be increased by this");
        animalsAttack = defConfig
            .getBoolean("Animals Retaliate", "Advanced Mobs", true, "Animals will fight back if provoked");
        neutralMobs = defConfig.getBoolean("Neutral Mobs", "Advanced Mobs", false, "Mobs are passive until provoked");
        mobBoating = defConfig.getBoolean(
            "Mob Boating",
            "Advanced Mobs",
            true,
            "Zombies and Skeletons will use boats in water to catch up to you!");

        // World
        SpawnForts = defConfig.get("World", "Spawn Forts", true)
            .getBoolean(true);
        fortRarity = defConfig.get("World", "Fort Rarity", 100)
            .getInt(100);
        fortDistance = defConfig.get("World", "Fort Distance", 1024)
            .getInt(1024);
        String[] defSpawn = new String[] { "Zombie", "Creeper", "Skeleton", "CaveSpider", "Silverfish", "Spider",
            "Slime", "Witch" };
        fortSpawners = new ArrayList<String>(
            Arrays.asList(
                defConfig.get("World", "Fort Spawner Types", defSpawn)
                    .getStringList()));
        int[] tmpFD = defConfig.get("World", "Fort Dimensions", new int[] { 0 })
            .getIntList();

        for (int dimID : tmpFD) {
            fortDimensions.add(dimID);
        }

        dimSettings.clear();
        Set<ConfigCategory> cats = defConfig.getCategory("Dimension Tweaks")
            .getChildren();

        if (cats.size() <= 0 && newFile) {
            String name = "Overworld";

            defConfig.get("Dimension Tweaks." + name, "01.Dimension ID", 0)
                .getInt(0);
            defConfig.get("Dimension Tweaks." + name, "02.Health Mult", 1.0D)
                .getDouble(1.0D);
            defConfig.get("Dimension Tweaks." + name, "03.Damage Mult", 1.0D)
                .getDouble(1.0D);
            defConfig.get("Dimension Tweaks." + name, "04.Speed Mult", 1.0D)
                .getDouble(1.0D);
            defConfig.get("Dimension Tweaks." + name, "05.Knockback Resistance Mult", 1.0D)
                .getDouble(1.0D);
            cats = defConfig.getCategory("Dimension Tweaks")
                .getChildren();
        }

        Iterator<ConfigCategory> iterator = cats.iterator();

        while (iterator.hasNext()) {
            ConfigCategory cat = iterator.next();
            if (cat.getChildren()
                .size() <= 0) {
                int dimID = defConfig.get(cat.getQualifiedName(), "01.Dimension ID", 0)
                    .getInt(0);
                double hpMult = defConfig.get(cat.getQualifiedName(), "02.Health Mult", 1.0D)
                    .getDouble(1.0D);
                double dmgMult = defConfig.get(cat.getQualifiedName(), "03.Damage Mult", 1.0D)
                    .getDouble(1.0D);
                double spdMult = defConfig.get(cat.getQualifiedName(), "04.Speed Mult", 1.0D)
                    .getDouble(1.0D);
                double knockResist = defConfig.get(cat.getQualifiedName(), "05.Knockback Resistance Mult", 1.0D)
                    .getDouble(1.0D);

                DimSettings dimSet = new DimSettings(hpMult, dmgMult, spdMult, knockResist);
                dimSettings.put(dimID, dimSet);
            }
        }

        defConfig.save();
    }

    public static void LoadWorldConfig() {
        if (worldDir == null) {
            ESM.log.log(Level.ERROR, "Failed to load world configs! Directory is null");
            return;
        }

        ResetToDefault(false);

        if (ENFORCE_DEFAULT) {
            ESM_Utils.UpdateBiomeSpawns();
            return;
        }

        File conFile = new File(worldDir, "ESM_Options.cfg");
        boolean newFile = false;

        if (!conFile.exists()) {
            try {
                conFile.createNewFile();
                newFile = true;
            } catch (Exception e) {
                ESM.log.log(Level.INFO, "Failed to load ESM Config: " + conFile.getPath(), e);
                return;
            }
        }

        Configuration config = new Configuration(conFile, true);
        ESM.log.log(Level.INFO, "Loading ESM Config: " + conFile.getPath());

        config.load();

        // Main
        String[] tmpAIE = config.get("Main", "AI Exempt Mob IDs", AIExempt.toArray(new String[] {}))
            .getStringList();
        AIExempt = new ArrayList<String>();
        AIExempt.addAll(Arrays.asList(tmpAIE));

        timedDifficulty = config.getInt(
            "Warm Up Days",
            "Main",
            timedDifficulty,
            0,
            Integer.MAX_VALUE,
            "How many days until ESM spawns mobs at full rate.");
        hardDay = config.getInt(
            "Hardcore Day Cycle",
            "Main",
            hardDay,
            0,
            Integer.MAX_VALUE,
            "The interval in which 'hard' days will occur where mob spawning is increased and lighting is ignored (0 = off, default = 8/full moon)");
        hardDamage = config
            .getBoolean("Hardcore Double Damage", "Main", hardDamage, "Mobs inflict double damage on hardcore days");
        Awareness = config.get("Main", "Awareness Radius", Awareness)
            .getInt(Awareness);
        Xray = config.get("Main", "Xray Mobs", Xray)
            .getBoolean(Xray);
        XrayOnlyDuringSiege = config.get(
            "Main",
            "Xray Mobs only during siege",
            XrayOnlyDuringSiege,
            "If enabled, then xray targeting will only be allowed during sieges. Main Xray option must also be enabled.")
            .getBoolean(XrayOnlyDuringSiege);
        TargetCap = config.get("Main", "Pathing Cap", TargetCap)
            .getInt(TargetCap);
        VillagerTarget = config.get("Main", "Villager Targeting", VillagerTarget)
            .getBoolean(VillagerTarget);
        Apocalypse = config.get("Main", "Apocalypse Mode", Apocalypse)
            .getBoolean(Apocalypse);
        Chaos = config.get("Main", "Chaos Mode", Chaos)
            .getBoolean(Chaos);
        AllowSleep = config.get("Main", "Allow Sleep", AllowSleep)
            .getBoolean(AllowSleep);
        ambiguous_AI = config
            .get(
                "Main",
                "Ambiguous AI",
                ambiguous_AI,
                "If set to true, ESM will not check whether the entity is a mob or not when setting up new AI")
            .getBoolean(ambiguous_AI);
        QuickPathing = config
            .get(
                "Main",
                "Quick Pathing",
                QuickPathing,
                "If set to fales, mobs can use much longer routes to get to their target")
            .getBoolean(QuickPathing);
        ResistanceCoolDown = config
            .get(
                "Main",
                "Resistance Cooldown",
                ResistanceCoolDown,
                "The amount of ticks of resistance given to the player after changing dimensions")
            .getInt(ResistanceCoolDown);
        keepLoaded = config
            .get(
                "Main",
                "Keep Loaded",
                keepLoaded,
                "Keeps mobs with an active target from despawning. Can causes issues with chunk loading/unloading")
            .getBoolean(false);
        moreSpawning = config
            .get(
                "Main",
                "More Spawning",
                moreSpawning,
                "Reduces spawning safe zone from 24 blocks to 8 and makes mobs require only basic conditions to spawn")
            .getBoolean(true);
        forcePath = config
            .get(
                "Main",
                "Force Non-AI Pathing",
                forcePath,
                "Forces non pathing mobs to attack from further away. Can cause additional lag")
            .getBoolean(false);
        friendlyFire = config
            .getBoolean("Friendly Fire", "Main", friendlyFire, "Can mobs harm eachother (type specific in chaos mode)");
        attackPets = config.getBoolean("Attack Pets", "Main", attackPets, "Mobs will attack player owned pets");
        flipBlacklist = config
            .getBoolean("Flip Exemtions", "Main", false, "Flips the exemption listing to whitelist mode");
        SiegeFrequency = config.getInt(
            "Siege Frequency",
            "Main",
            1,
            1,
            Integer.MAX_VALUE,
            "How often in days the various mobs are allowed to gain siege capabilites (dig, pillar, demolition and/or grief - whatever is enabled). Default of 1 means every day/night.\n"
                + "The frequency of every x days specified here start from day y, where day y will be the Siege Warmup option below counting down to 0.\n");
        SiegeWarmup = config.getInt(
            "Siege Warmup",
            "Main",
            0,
            0,
            Integer.MAX_VALUE,
            "Specify how many days should act as a warmup period before mobs can become siege-capable. Default of 0 means that mobs on the very first day can siege - no countdown at all.\n"
                + "An example use of this is if you set Siege Frequency to 8 for every full moon (under a vanilla lunar cycle), but you want to avoid the first day full moon bringing sieges - set this to any value from 1 to 7. By the time the next Siege Frequeny is reached on day 8, the Siege Warmup would have elapsed either way.\n");
        simpleAiCheck = config.getBoolean(
            "Simple AI Check",
            "Main",
            simpleAiCheck,
            "Use a more performance-friendly check for new entity AI. Might not catch some special mod-added entities.");
        idleTargetInstantDespawnTicks = config.getInt(
            "Idle Target Instant Despawn Timout",
            "Main",
            idleTargetInstantDespawnTicks,
            0,
            Integer.MAX_VALUE,
            "If a mob has had no target for this many ticks, instantly despawn it. Set to 0 to disable.");

        // Witch
        customPotions = config.getStringList(
            "Custom Potions",
            "Witch",
            customPotions,
            "List of potion types witches can throw (\"id:duration:lvl\")");

        // Creeper
        CreeperBreaching = config.get("Creeper", "Breaching", CreeperBreaching)
            .getBoolean(CreeperBreaching);
        CreeperNapalm = config.get("Creeper", "Napalm", CreeperNapalm)
            .getBoolean(CreeperNapalm);
        CreeperPowered = config.get("Creeper", "Powered", CreeperPowered)
            .getBoolean(CreeperPowered);
        CreeperPoweredRarity = config.get("Creeper", "Powered Rarity", CreeperPoweredRarity)
            .getInt(CreeperPoweredRarity);
        CreeperChargers = config
            .getBoolean("Chargering", "Creeper", true, "Creepers will run at you at speed before detonating");
        CenaCreeper = config.getBoolean("Cena Creeper", "Creeper", false, "AND HIS NAME IS...");
        CenaCreeperRarity = config
            .getInt("Cena Creeper Rarity", "Creeper", 9, 0, Integer.MAX_VALUE, "How rare are they");
        CreeperEnhancementsOnlyWhenSiegeAllowed = config.getBoolean(
            "Enhance only when siege allowed",
            "Creeper",
            true,
            "If true, all creeper enhancements will be skipped if siege is not allowed.\n"
                + "(See freqency and warmup settings under Main).");

        // Skeletons
        SkeletonAccuracy = config.get("Skeleton", "Arrow Error", SkeletonAccuracy)
            .getInt(SkeletonAccuracy);
        SkeletonDistance = config.get("Skeleton", "Fire Distance", SkeletonDistance)
            .getInt(SkeletonDistance);

        // Zombies
        ZombieInfectious = config.get("Zombie", "Infectious", ZombieInfectious)
            .getBoolean(ZombieInfectious);
        ZombieDiggers = config.get("Zombie", "Diggers", ZombieDiggers)
            .getBoolean(ZombieDiggers);
        ZombieDiggerTools = config.get("Zombie", "Need Required Tools", ZombieDiggerTools)
            .getBoolean(ZombieDiggerTools);
        ZombiePillaring = config
            .get("Zombie", "Pillaring Blocks", ZombiePillaring, "How many blocks to give zombies to pillar up with")
            .getInt(ZombiePillaring);
        ZombieGriefBlocksRaw = new HashSet<String>(
            Arrays.asList(
                config.get(
                    "Zombie",
                    "General Griefable Blocks",
                    ZombieGriefBlocksRaw.toArray(new String[] {}),
                    "What blocks will be targeted for destruction when not attacking players (Does not affect general digging, light sources are included by default, add ':#' for metadata e.g. 'minecraft:wool:1')")
                    .getStringList()));
        ZombieGriefBlocksAnyTime = config.get(
            "Zombie",
            "Always Griefable",
            true,
            "If true, Zombies will be able to grief blocks from the block list any time - not just when a siege is underway.")
            .getBoolean(true);
        ZombieGriefBlocksNoTool = config
            .get(
                "Zombie",
                "Griefable Blocks Ignore Tool Requirement",
                true,
                "If true, Zombies will be able to grief blocks from the block list without needing the right tool.")
            .getBoolean(true);
        ZombieGriefBlocksLightSources = config.get(
            "Zombie",
            "All lightsources griefable",
            true,
            "If true, Zombies will target any light source for griefing. Set to false if you want to manually specify them.")
            .getBoolean(true);
        ZombieGriefBlocksTriesPerTick = config
            .get(
                "Zombie",
                "How many iterations to search for Griefable Blocks per tick",
                ZombieGriefBlocksTriesPerTick,
                "Zombies will look at this many random nearby blocks each tick to decide if it will grief it")
            .getInt(ZombieGriefBlocksTriesPerTick);
        ZombieGriefBlocksRangeXZ = config.get(
            "Zombie",
            "The X/Z range for Zombies to search for griefing",
            ZombieGriefBlocksRangeXZ,
            "A radius value, defining a box centered on the zombie. E.g. a value of 8 will cause zombies to search in a 17x17 area centered on the zombie.")
            .getInt(ZombieGriefBlocksRangeXZ);
        ZombieGriefBlocksRangeY = config.get(
            "Zombie",
            "The Y range to for Zombies to search for griefing",
            ZombieGriefBlocksRangeY,
            "A height upwards or downwards to search e.g. a value of 4 will search a total height of 9 centered on the zombie's foot level. Combines with ZombieGriefBlocksRangeXZ to form a search cube.")
            .getInt(ZombieGriefBlocksRangeY);
        ZombieDigBlacklistRaw = new ArrayList<String>(
            Arrays.asList(
                config
                    .get(
                        "Zombie",
                        "Digging Blacklist",
                        ZombieDigBlacklistRaw.toArray(new String[] {}),
                        "Blacklisted blocks for digging (Add ':#' for metadata e.g. 'minecraft:wool:1')")
                    .getStringList()));
        ZombieSwapList = config
            .get("Zombie", "Blacklist to Whitelist", false, "Use the digging blacklist as a whitelist instead")
            .getBoolean(false);
        DemolitionZombies = config.get("Zombie", "Demolition Zombies", true, "Zombies can placed armed TNT")
            .getBoolean(true);

        // Blazes
        BlazeSpawn = config.get("Blaze", "Spawn", BlazeSpawn)
            .getBoolean(BlazeSpawn);
        BlazeRarity = config.getInt("Rarity", "Blaze", 9, 1, 100, "How rare are Blazes");
        BlazeFireballs = config.get("Blaze", "Fireballs", BlazeFireballs)
            .getInt(BlazeFireballs);
        int[] tmpBDB = new int[BlazeDimensionBlacklist.size()];
        for (int i = 0; i < BlazeDimensionBlacklist.size(); i++) {
            tmpBDB[i] = BlazeDimensionBlacklist.get(i);
        }
        tmpBDB = config.get("Blaze", "Dimension Blacklist", tmpBDB)
            .getIntList();
        BlazeDimensionBlacklist = new ArrayList<Integer>();
        for (int i : tmpBDB) {
            BlazeDimensionBlacklist.add(i);
        }

        // Ghasts
        GhastSpawn = config.get("Ghast", "Spawn", GhastSpawn)
            .getBoolean(GhastSpawn);
        GhastRarity = config.getInt("Rarity", "Ghast", 9, 1, 100, "How rare are Ghasts");
        GhastFireDelay = config.get("Ghast", "Fire Delay", GhastFireDelay)
            .getDouble(GhastFireDelay);
        GhastBreaching = config.get("Ghast", "Breaching", GhastBreaching)
            .getBoolean(GhastBreaching);
        GhastFireDist = config.get("Ghast", "Fire Distance", GhastFireDist)
            .getDouble(GhastFireDist);
        int[] tmpGDB = new int[GhastDimensionBlacklist.size()];
        for (int i = 0; i < GhastDimensionBlacklist.size(); i++) {
            tmpGDB[i] = GhastDimensionBlacklist.get(i);
        }
        tmpGDB = config.get("Ghast", "Dimension Blacklist", tmpGDB)
            .getIntList();
        GhastDimensionBlacklist = new ArrayList<Integer>();
        for (int i : tmpGDB) {
            GhastDimensionBlacklist.add(i);
        }

        // Endermen
        EndermanSlender = config
            .get("Enderman", "Slender-Mode", EndermanSlender, "Makes Endermen stalk players with side effects")
            .getBoolean();
        EndermanPlayerTele = config.get("Enderman", "Player Teleport", EndermanPlayerTele)
            .getBoolean(EndermanPlayerTele);

        // Spider
        SpiderWebChance = MathHelper.clamp_int(
            config.get("Spider", "Webbing Chance (0-100)", SpiderWebChance)
                .getInt(SpiderWebChance),
            0,
            100);

        // Advanced
        String[] tmp = config.get("Advanced Mobs", "Mob Bombs", MobBombs.toArray(new String[] {}))
            .getStringList();
        MobBombs = new ArrayList<String>();
        MobBombs.addAll(Arrays.asList(tmp));
        MobBombRarity = config.get("Advanced Mobs", "Mob Bomb Rarity", MobBombRarity)
            .getInt(MobBombRarity);
        MobBombInvert = defConfig.getBoolean(
            "Mob Bomb Invert",
            "Advanced Mobs",
            MobBombInvert,
            "Inverts the mob bomb listing to be act as a blacklist");
        MobBombAll = config
            .get("Advanced Mobs", "Mob Bomb All", MobBombAll, "Skip the Mob Bomb list and allow everything!")
            .getBoolean(MobBombAll);
        CrystalBombs = config
            .get("Advanced Mobs", "Crystal Bombs", CrystalBombs, "Mob Bombs are now Crystals instead of Creepers")
            .getBoolean(CrystalBombs);
        WitherSkeletons = config.get("Advanced Mobs", "Wither Skeletons", WitherSkeletons)
            .getBoolean(WitherSkeletons);
        WitherSkeletonRarity = config.get("Advanced Mobs", "Wither Skeleton Rarity", WitherSkeletonRarity)
            .getInt(WitherSkeletonRarity);
        PotionMobs = config.get("Advanced Mobs", "Potion Buff Chance (0-100)", PotionMobs)
            .getInt(PotionMobs);
        PotionMobEffects = config
            .get(
                "Advanced Mobs",
                "Potion Buff List",
                PotionMobEffects,
                "List of all the valid potion IDs a mob can spawn with. Amplifier is always x1")
            .getIntList();
        attackEvasion = config.get("Advanced Mobs", "Attack Evasion", attackEvasion)
            .getBoolean(attackEvasion);
        bossModifier = config.getFloat(
            "Boss Kill Modifier",
            "Advanced Mobs",
            0.1F,
            0F,
            Float.MAX_VALUE,
            "Every time a boss is killed all mob heal and damage multipliers will be increased by this");
        animalsAttack = config
            .getBoolean("Animals Retaliate", "Advanced Mobs", true, "Animals will fight back if provoked");
        neutralMobs = config.getBoolean("Neutral Mobs", "Advanced Mobs", false, "Mobs are passive until provoked");
        mobBoating = config.getBoolean(
            "Mob Boating",
            "Advanced Mobs",
            mobBoating,
            "Zombies and Skeletons will use boats in water to catch up to you!");

        // World
        SpawnForts = config.get("World", "Spawn Forts", SpawnForts)
            .getBoolean(SpawnForts);
        fortRarity = config.get("World", "Fort Rarity", fortRarity)
            .getInt(fortRarity);
        fortDistance = config.get("World", "Fort Distance", fortDistance)
            .getInt(fortDistance);
        String[] defSpawn = new String[] { "Zombie", "Creeper", "Skeleton", "CaveSpider", "Silverfish", "Spider",
            "Slime", "Witch" };
        fortSpawners = new ArrayList<String>(
            Arrays.asList(
                config.get("World", "Fort Spawner Types", defSpawn)
                    .getStringList()));
        int[] tmpFDDef = new int[fortDimensions.size()];
        for (int i = 0; i < fortDimensions.size(); i++) {
            tmpFDDef[i] = fortDimensions.get(i);
        }
        int[] tmpFD = config.get("World", "Fort Dimensions", tmpFDDef)
            .getIntList();
        fortDimensions = new ArrayList<Integer>();
        for (int dimID : tmpFD) {
            fortDimensions.add(dimID);
        }

        dimSettings.clear();
        Set<ConfigCategory> cats = config.getCategory("Dimension Tweaks")
            .getChildren();

        if (cats.size() <= 0 && newFile) {
            String name = "Overworld";

            config.get("Dimension Tweaks." + name, "01.Dimension ID", 0)
                .getInt(0);
            config.get("Dimension Tweaks." + name, "02.Health Mult", 1.0D)
                .getDouble(1.0D);
            config.get("Dimension Tweaks." + name, "03.Damage Mult", 1.0D)
                .getDouble(1.0D);
            config.get("Dimension Tweaks." + name, "04.Speed Mult", 1.0D)
                .getDouble(1.0D);
            config.get("Dimension Tweaks." + name, "05.Knockback Resistance Mult", 1.0D)
                .getDouble(1.0D);
            cats = config.getCategory("Dimension Tweaks")
                .getChildren();
        }

        Iterator<ConfigCategory> iterator = cats.iterator();

        while (iterator.hasNext()) {
            ConfigCategory cat = iterator.next();
            if (cat.getChildren()
                .size() <= 0) {
                int dimID = config.get(cat.getQualifiedName(), "01.Dimension ID", 0)
                    .getInt(0);
                double hpMult = config.get(cat.getQualifiedName(), "02.Health Mult", 1.0D)
                    .getDouble(1.0D);
                double dmgMult = config.get(cat.getQualifiedName(), "03.Damage Mult", 1.0D)
                    .getDouble(1.0D);
                double spdMult = config.get(cat.getQualifiedName(), "04.Speed Mult", 1.0D)
                    .getDouble(1.0D);
                double knockResist = config.get(cat.getQualifiedName(), "05.Knockback Resistance Mult", 1.0D)
                    .getDouble(1.0D);

                DimSettings dimSet = new DimSettings(hpMult, dmgMult, spdMult, knockResist);
                dimSettings.put(dimID, dimSet);
            }
        }

        config.save();

        ESM_Utils.UpdateBiomeSpawns();

        fortDB = loadFortDB();
    }

    public static BlockAndMeta[] getZombieGriefBlocks() {
        if (ZombieGriefBlocks == null) {
            ZombieGriefBlocks = BlockAndMeta.buildList(
                "Zombie grief block list",
                ZombieGriefBlocksRaw.toArray(new String[ZombieGriefBlocksRaw.size()]));
            if (ZombieGriefBlocks != null && ZombieGriefBlocks.length == 0) ZombieGriefBlocks = null;
        }
        return ZombieGriefBlocks;
    }

    public static BlockAndMeta[] getZombieDigBlacklist() {
        if (ZombieDigBlacklist == null) {
            ZombieDigBlacklist = BlockAndMeta.buildList(
                "Zombie dig blacklist",
                ZombieDigBlacklistRaw.toArray(new String[ZombieDigBlacklistRaw.size()]));
            if (ZombieDigBlacklist != null && ZombieDigBlacklist.length == 0) ZombieDigBlacklist = null;
        }
        return ZombieDigBlacklist;
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<String> loadFortDB() {
        File fileFortDB = new File(worldDir, "ESM_fortDB");
        ESM.log.log(Level.INFO, "Loading fortDB from " + fileFortDB.getPath());

        if (!fileFortDB.exists()) {
            return new ArrayList<String>();
        } else {
            try {
                FileInputStream fileIn = new FileInputStream(fileFortDB);
                BufferedInputStream buffer = new BufferedInputStream(fileIn);
                ObjectInputStream objIn = new ObjectInputStream(buffer);

                ArrayList<String> savedDB = (ArrayList<String>) objIn.readObject();

                objIn.close();
                buffer.close();
                fileIn.close();

                return savedDB;
            } catch (Exception e) {
                return new ArrayList<String>();
            }
        }
    }

    public static void saveFortDB() {
        if (fortDB == null || fortDB.size() <= 0) {
            return;
        }

        File fileFortDB = new File(worldDir, "ESM_fortDB");

        ESM.log.log(Level.INFO, "Saving fortDB to " + fileFortDB.getPath());

        try {
            if (!fileFortDB.exists()) {
                fileFortDB.createNewFile();
            }

            FileOutputStream fileOut = new FileOutputStream(fileFortDB);
            BufferedOutputStream buffer = new BufferedOutputStream(fileOut);
            ObjectOutputStream objOut = new ObjectOutputStream(buffer);

            objOut.writeObject(fortDB);

            objOut.close();
            buffer.close();
            fileOut.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public static boolean isXrayAllowed(long worldTime) {
        if (Xray) {
            if (XrayOnlyDuringSiege) return ESM_Utils.isSiegeAllowed(worldTime);
            else return true;
        }
        return false;
    }
}
