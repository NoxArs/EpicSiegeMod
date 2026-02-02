package funwayguy.esm.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.registry.EntityRegistry;
import funwayguy.esm.ai.ESMPathNavigator;
import funwayguy.esm.ai.ESM_EntityAIArrowAttack;
import funwayguy.esm.ai.ESM_EntityAIAttackEvasion;
import funwayguy.esm.ai.ESM_EntityAIAttackOnCollide;
import funwayguy.esm.ai.ESM_EntityAIAvoidDetonations;
import funwayguy.esm.ai.ESM_EntityAIBoat;
import funwayguy.esm.ai.ESM_EntityAIBreakDoor_Proxy;
import funwayguy.esm.ai.ESM_EntityAICreeperSwell;
import funwayguy.esm.ai.ESM_EntityAIDemolition;
import funwayguy.esm.ai.ESM_EntityAIDigging;
import funwayguy.esm.ai.ESM_EntityAIGrief;
import funwayguy.esm.ai.ESM_EntityAIHurtByTarget;
import funwayguy.esm.ai.ESM_EntityAIJohnCena;
import funwayguy.esm.ai.ESM_EntityAINearestAttackableTarget;
import funwayguy.esm.ai.ESM_EntityAIPillarUp;
import funwayguy.esm.ai.ESM_EntityAISwimming;
import funwayguy.esm.handlers.ESM_PathCapHandler;

public class ESM_Utils {

    public static boolean isWarmupElapsed = false;

    public static void resetPlayerMPStats(EntityPlayerMP player) {
        try {
            ObfuscationReflectionHelper
                .setPrivateValue(EntityPlayerMP.class, player, -1, "field_71144_ck", "lastExperience");
            ObfuscationReflectionHelper
                .setPrivateValue(EntityPlayerMP.class, player, -1.0F, "field_71149_ch", "lastHealth");
            ObfuscationReflectionHelper
                .setPrivateValue(EntityPlayerMP.class, player, -1, "field_71146_ci", "lastFoodLevel");
        } catch (Throwable t) {
            // 不要在高频路径里疯狂打印堆栈；最好只打印一次或 debug 模式
            // ESM.log.log(Level.WARN, "Failed to reset MP stats for " + player.getCommandSenderName(), t);
        }
    }

    public static int getAIPathCount(World world, EntityLivingBase targetEntity) {
        List<EntityLivingBase> attackerList = ESM_PathCapHandler.attackMap.get(targetEntity);

        return attackerList == null ? 0 : attackerList.size();
    }

    private static Field findFieldRecursive(Class<?> start, String name) {
        Class<?> c = start;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static int readIntFieldBestEffort(Object obj, Class<?> startClass, String... names) {
        if (obj == null || startClass == null) return 0;
        for (String n : names) {
            try {
                Field f = findFieldRecursive(startClass, n);
                if (f == null) continue;
                f.setAccessible(true);
                return f.getInt(obj);
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static Object readObjectFieldBestEffort(Object obj, Class<?> startClass, String... names) {
        if (obj == null || startClass == null) return null;
        for (String n : names) {
            try {
                Field f = findFieldRecursive(startClass, n);
                if (f == null) continue;
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void writeIntFieldBestEffort(Object obj, Class<?> startClass, int value, String... names) {
        if (obj == null || startClass == null) return;
        for (String n : names) {
            try {
                Field f = findFieldRecursive(startClass, n);
                if (f == null) continue;
                f.setAccessible(true);
                f.setInt(obj, value);
                return;
            } catch (Throwable ignored) {}
        }
    }

    public static boolean isCloserThanOtherAttackers(World world, EntityLivingBase attacker, EntityLivingBase target) {
        List<EntityLivingBase> list = ESM_PathCapHandler.attackMap.get(target);
        if (list == null || list.isEmpty()) return true;
        // 自己已经在名单里，就不必再判断（可选，取决于你的语义）
        // if (list.contains(attacker)) return true;
        final float myDist = attacker.getDistanceToEntity(target);

        EntityLivingBase farthest = null;
        float farthestDist = -1.0F;

        for (int i = 0; i < list.size(); i++) {
            EntityLivingBase e = list.get(i);
            if (e == null || e.isDead) continue;
            if (e.worldObj != target.worldObj) continue;
            if (e == attacker) continue;

            float d = e.getDistanceToEntity(target);
            if (d > farthestDist) {
                farthestDist = d;
                farthest = e;
            }
        }
        if (farthest == null) {
            ESM_PathCapHandler.UpdateAttackers(target);
            return true;
        }
        return myDist < farthestDist;
    }

    public static void replaceAI(EntityLiving entityLiving) {
        replaceAI(entityLiving, false);
    }

    private static boolean hasTask(EntityAITasks tasks, Class<?> taskClass) {
        if (tasks == null || tasks.taskEntries == null || taskClass == null) return false;
        for (Object obj : tasks.taskEntries) {
            if (!(obj instanceof EntityAITasks.EntityAITaskEntry)) continue;
            EntityAITasks.EntityAITaskEntry entry = (EntityAITasks.EntityAITaskEntry) obj;
            if (entry == null || entry.action == null) continue;
            if (taskClass.isAssignableFrom(entry.action.getClass())) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static void replaceAI(EntityLiving entityLiving, boolean firstPass) {
        if (entityLiving == null) return;

        // 0) Navigator
        try {
            PathNavigate oldNav = entityLiving.getNavigator();
            if (oldNav != null && !(oldNav instanceof ESMPathNavigator)) {
                String navName = oldNav.getClass()
                    .getName()
                    .toLowerCase();
                boolean blacklisted = navName.contains("pathfindertweaks") || navName.contains("betterpathfinding")
                    || navName.contains("customnavi");
                if (!blacklisted) {
                    ESMPathNavigator newNav = new ESMPathNavigator(entityLiving, entityLiving.worldObj);
                    ObfuscationReflectionHelper
                        .setPrivateValue(EntityLiving.class, entityLiving, newNav, "field_70699_by", "navigator");
                    try {
                        newNav.inherit(oldNav);
                    } catch (Exception e) {}
                }
            }
        } catch (Exception e) {}

        boolean replacedNAT = false;
        boolean replacedCS = false;
        boolean replacedAE = false;

        // 1) Target Tasks (NAT Merge)
        ESM_EntityAINearestAttackableTarget batchedNAT = null;
        int minMergedChance = Integer.MAX_VALUE;

        // 定义反射用的字段名候选 (MCP/SRG)
        final String[] chanceFields = new String[] { "targetChance", "field_75308_c", "field_75321_d",
            "field_75309_a" };
        final String[] classFields = new String[] { "targetClass", "field_75307_b" };

        for (int i = entityLiving.targetTasks.taskEntries.size() - 1; i >= 0; i--) {
            EntityAITaskEntry task = (EntityAITaskEntry) entityLiving.targetTasks.taskEntries.get(i);
            if (task == null || task.action == null) continue;

            if (task.action instanceof EntityAINearestAttackableTarget && entityLiving instanceof EntityCreature) {
                if (ESM_Settings.neutralMobs) continue;

                EntityAINearestAttackableTarget nat = (EntityAINearestAttackableTarget) task.action;

                // 1. 读取 Chance
                int natChance = readIntFieldBestEffort(nat, nat.getClass(), chanceFields);
                if (natChance <= 0) natChance = 10;

                // 2. 更新最小 Chance
                if (natChance < minMergedChance) {
                    minMergedChance = natChance;
                    // 如果 batchedNAT 已创建，实时回写更小的 chance
                    if (batchedNAT != null) {
                        writeIntFieldBestEffort(
                            batchedNAT,
                            EntityAINearestAttackableTarget.class,
                            minMergedChance,
                            chanceFields);
                    }
                }

                // 3. 读取 Class
                Class<? extends EntityLivingBase> tc = (Class<? extends EntityLivingBase>) readObjectFieldBestEffort(
                    nat,
                    nat.getClass(),
                    classFields);

                // 4. 初始化 batchedNAT
                if (batchedNAT == null) {
                    batchedNAT = new ESM_EntityAINearestAttackableTarget(
                        (EntityCreature) entityLiving,
                        new ArrayList<Class<? extends EntityLivingBase>>(),
                        minMergedChance, // 使用当前的最小值
                        true);

                    if (ESM_Settings.Chaos) {
                        batchedNAT.targetClass.add(EntityLivingBase.class);
                    } else {
                        if (ESM_Settings.ambiguous_AI) batchedNAT.targetClass.add(EntityPlayer.class);
                        if (ESM_Settings.VillagerTarget) batchedNAT.targetClass.add(EntityVillager.class);
                    }
                }

                // 5. 合并 targetClass
                if (tc != null && !batchedNAT.targetClass.contains(tc)) {
                    batchedNAT.targetClass.add(tc);
                }

                // 6. 替换/移除
                if (!replacedNAT) {
                    replacedNAT = true;
                    EntityAITaskEntry replacement = entityLiving.targetTasks.new EntityAITaskEntry(task.priority,
                        batchedNAT);
                    entityLiving.targetTasks.taskEntries.set(i, replacement);
                    try {
                        entityLiving.getEntityAttribute(SharedMonsterAttributes.followRange)
                            .setBaseValue(ESM_Settings.Awareness);
                    } catch (Exception ignored) {}
                } else {
                    entityLiving.targetTasks.taskEntries.remove(i);
                }
                continue;
            }

            if (task.action instanceof EntityAIHurtByTarget && entityLiving instanceof EntityCreature) {
                EntityAITaskEntry replacement = entityLiving.targetTasks.new EntityAITaskEntry(task.priority,
                    new ESM_EntityAIHurtByTarget((EntityCreature) entityLiving, true));
                entityLiving.targetTasks.taskEntries.set(i, replacement);
            }
        }

        // 2) Cache Old AIs
        ESM_EntityAIBreakDoor_Proxy cachedBD = null;
        EntityAIBreakDoor oldBD = null;
        if (entityLiving instanceof EntityZombie) {
            try {
                oldBD = ObfuscationReflectionHelper
                    .getPrivateValue(EntityZombie.class, (EntityZombie) entityLiving, "field_146075_bs");
            } catch (Throwable ignored) {}
        }
        ESM_EntityAIAttackOnCollide cachedAOC = null;
        EntityAIAttackOnCollide oldAOC = null;
        EntityAIArrowAttack cachedAA = null;
        EntityAIArrowAttack oldAA = null;
        if (entityLiving instanceof EntitySkeleton) {
            try {
                oldAOC = ObfuscationReflectionHelper.getPrivateValue(
                    EntitySkeleton.class,
                    (EntitySkeleton) entityLiving,
                    "field_85038_e",
                    "aiAttackOnCollide");
            } catch (Throwable ignored) {}
            try {
                oldAA = ObfuscationReflectionHelper.getPrivateValue(
                    EntitySkeleton.class,
                    (EntitySkeleton) entityLiving,
                    "field_85037_d",
                    "aiArrowAttack");
            } catch (Throwable ignored) {}
        }

        // 3) Replace Main Tasks
        for (int i = entityLiving.tasks.taskEntries.size() - 1; i >= 0; i--) {
            EntityAITaskEntry task = (EntityAITaskEntry) entityLiving.tasks.taskEntries.get(i);
            if (task == null || task.action == null) continue;

            if (task.action instanceof EntityAIFollowOwner) {
                try {
                    ObfuscationReflectionHelper.setPrivateValue(
                        EntityAIFollowOwner.class,
                        (EntityAIFollowOwner) task.action,
                        entityLiving.getNavigator(),
                        "field_75337_g",
                        "petPathfinder");
                } catch (Throwable ignored) {}
                continue;
            }

            if (task.action.getClass() == EntityAICreeperSwell.class && entityLiving instanceof EntityCreeper) {
                if (ESM_Settings.CreeperEnhancementsOnlyWhenSiegeAllowed
                    && isSiegeAllowed(entityLiving.worldObj.getWorldTime())) {
                    if (!replacedCS) {
                        replacedCS = true;
                        EntityAITaskEntry replacement;
                        if (ESM_Settings.CenaCreeper && (ESM_Settings.CenaCreeperRarity <= 0
                            || entityLiving.worldObj.rand.nextInt(ESM_Settings.CenaCreeperRarity) == 0)) {
                            replacement = entityLiving.tasks.new EntityAITaskEntry(task.priority,
                                new ESM_EntityAIJohnCena((EntityCreeper) entityLiving));
                        } else {
                            replacement = entityLiving.tasks.new EntityAITaskEntry(task.priority,
                                new ESM_EntityAICreeperSwell((EntityCreeper) entityLiving));
                        }
                        entityLiving.tasks.taskEntries.set(i, replacement);
                    } else {
                        entityLiving.tasks.taskEntries.remove(i);
                    }
                }
                continue;
            }

            if (task.action.getClass() == EntityAIAvoidEntity.class && entityLiving instanceof EntityVillager) {
                if (!replacedAE) {
                    replacedAE = true;
                    EntityAIAvoidEntity avoid = new EntityAIAvoidEntity(
                        (EntityVillager) entityLiving,
                        net.minecraft.entity.monster.EntityMob.class,
                        12.0F,
                        0.6D,
                        0.6D);
                    entityLiving.tasks.taskEntries
                        .set(i, entityLiving.tasks.new EntityAITaskEntry(task.priority, avoid));
                } else {
                    entityLiving.tasks.taskEntries.remove(i);
                }
                continue;
            }

            if (task.action.getClass() == EntityAISwimming.class) {
                entityLiving.tasks.taskEntries.set(
                    i,
                    entityLiving.tasks.new EntityAITaskEntry(task.priority, new ESM_EntityAISwimming(entityLiving)));
                continue;
            }

            if (task.action.getClass() == EntityAIBreakDoor.class) {
                ESM_EntityAIBreakDoor_Proxy tmp = new ESM_EntityAIBreakDoor_Proxy(entityLiving);
                if (task.action == oldBD) cachedBD = tmp;
                entityLiving.tasks.taskEntries.set(i, entityLiving.tasks.new EntityAITaskEntry(task.priority, tmp));
                continue;
            }

            if (task.action.getClass() == EntityAIAttackOnCollide.class && entityLiving instanceof EntityCreature) {
                boolean longMemory = false;
                Class<?> targetType = EntityLivingBase.class;
                double speed = 1.0D;
                try {
                    longMemory = (Boolean) readObjectFieldBestEffort(
                        task.action,
                        EntityAIAttackOnCollide.class,
                        "field_75437_f",
                        "longMemory");
                    targetType = (Class<?>) readObjectFieldBestEffort(
                        task.action,
                        EntityAIAttackOnCollide.class,
                        "field_75444_h",
                        "classTarget");
                    speed = (Double) readObjectFieldBestEffort(
                        task.action,
                        EntityAIAttackOnCollide.class,
                        "field_75440_e",
                        "speedTowardsTarget");
                } catch (Throwable ignored) {}

                if (targetType == null) targetType = EntityLivingBase.class;
                if (speed <= 0) speed = 1.0D;

                ESM_EntityAIAttackOnCollide esmAOC = new ESM_EntityAIAttackOnCollide(
                    (EntityCreature) entityLiving,
                    targetType,
                    speed,
                    longMemory);
                if (task.action == oldAOC) cachedAOC = esmAOC;
                entityLiving.tasks.taskEntries.set(i, entityLiving.tasks.new EntityAITaskEntry(task.priority, esmAOC));
                continue;
            }

            if (task.action.getClass() == EntityAIArrowAttack.class && entityLiving instanceof IRangedAttackMob) {
                ESM_EntityAIArrowAttack tmp = new ESM_EntityAIArrowAttack(
                    (IRangedAttackMob) entityLiving,
                    1.0D,
                    20,
                    60,
                    (float) ESM_Settings.SkeletonDistance);
                if (task.action == oldAA) cachedAA = tmp;
                entityLiving.tasks.taskEntries.set(i, entityLiving.tasks.new EntityAITaskEntry(task.priority, tmp));
                continue;
            }

            if (ESM_Settings.animalsAttack && task.action.getClass() == EntityAIPanic.class
                && entityLiving instanceof IAnimals) {
                entityLiving.tasks.taskEntries.remove(i);
            }
        }

        // 4) Append New Tasks (Dupe Protected)
        if (!firstPass) {
            if (entityLiving instanceof EntityCreature) {
                if (!hasTask(entityLiving.targetTasks, ESM_EntityAIAvoidDetonations.class)) entityLiving.targetTasks
                    .addTask(1, new ESM_EntityAIAvoidDetonations((EntityCreature) entityLiving, 9F, 1.5D, 1.25D));

                if (entityLiving instanceof IMob && !(entityLiving instanceof EntityCreeper)) {
                    if (!hasTask(entityLiving.targetTasks, ESM_EntityAIAttackEvasion.class)) entityLiving.targetTasks
                        .addTask(2, new ESM_EntityAIAttackEvasion((EntityCreature) entityLiving, 5F, 1.5D, 1.25D));
                }

                if (entityLiving instanceof IAnimals && ESM_Settings.animalsAttack) {
                    if (!hasTask(entityLiving.tasks, ESM_EntityAIAttackOnCollide.class)) entityLiving.tasks
                        .addTask(4, new ESM_EntityAIAttackOnCollide((EntityCreature) entityLiving, 1.25D, true));
                    if (!hasTask(entityLiving.targetTasks, ESM_EntityAIHurtByTarget.class)) entityLiving.targetTasks
                        .addTask(3, new ESM_EntityAIHurtByTarget((EntityCreature) entityLiving, true));
                }
            }

            if (entityLiving instanceof EntitySkeleton) {
                ESM_EntityAIAttackOnCollide tmpAOC = (cachedAOC != null) ? cachedAOC
                    : new ESM_EntityAIAttackOnCollide((EntitySkeleton) entityLiving, EntityPlayer.class, 1.2D, false);
                try {
                    ObfuscationReflectionHelper.setPrivateValue(
                        EntitySkeleton.class,
                        (EntitySkeleton) entityLiving,
                        tmpAOC,
                        "field_85038_e",
                        "aiAttackOnCollide");
                } catch (Throwable ignored) {}

                ESM_EntityAIArrowAttack tmpAA = (cachedAA instanceof ESM_EntityAIArrowAttack)
                    ? (ESM_EntityAIArrowAttack) cachedAA
                    : new ESM_EntityAIArrowAttack(
                        (EntitySkeleton) entityLiving,
                        1.0D,
                        20,
                        60,
                        (float) ESM_Settings.SkeletonDistance);
                try {
                    ObfuscationReflectionHelper.setPrivateValue(
                        EntitySkeleton.class,
                        (EntitySkeleton) entityLiving,
                        tmpAA,
                        "field_85037_d",
                        "aiArrowAttack");
                } catch (Throwable ignored) {}

                if (ESM_Settings.mobBoating && !hasTask(entityLiving.tasks, ESM_EntityAIBoat.class))
                    entityLiving.tasks.addTask(1, new ESM_EntityAIBoat(entityLiving));
            }

            if (entityLiving instanceof EntityZombie) {
                ESM_EntityAIBreakDoor_Proxy tmp = (cachedBD != null) ? cachedBD
                    : new ESM_EntityAIBreakDoor_Proxy(entityLiving);
                try {
                    ObfuscationReflectionHelper
                        .setPrivateValue(EntityZombie.class, (EntityZombie) entityLiving, tmp, "field_146075_bs");
                } catch (Throwable ignored) {}

                ((EntityZombie) entityLiving).setCanPickUpLoot(true);
                ((EntityZombie) entityLiving).func_146070_a(true);

                if (ESM_Settings.ZombieDiggers) {
                    if (!hasTask(entityLiving.tasks, ESM_EntityAIDigging.class))
                        entityLiving.tasks.addTask(1, new ESM_EntityAIDigging((EntityZombie) entityLiving));
                    if (!hasTask(entityLiving.tasks, ESM_EntityAIGrief.class))
                        entityLiving.tasks.addTask(6, new ESM_EntityAIGrief((EntityZombie) entityLiving));
                    if (!hasTask(entityLiving.tasks, ESM_EntityAIPillarUp.class))
                        entityLiving.tasks.addTask(3, new ESM_EntityAIPillarUp(entityLiving));
                }

                if (ESM_Settings.DemolitionZombies && !hasTask(entityLiving.tasks, ESM_EntityAIDemolition.class))
                    entityLiving.tasks.addTask(3, new ESM_EntityAIDemolition(entityLiving));

                if (ESM_Settings.mobBoating && !hasTask(entityLiving.tasks, ESM_EntityAIBoat.class))
                    entityLiving.tasks.addTask(1, new ESM_EntityAIBoat(entityLiving));
            }
        }
    }

    public static EntityLivingBase GetNearestValidTarget(EntityLiving entityLiving) {
        return entityLiving.worldObj.getClosestVulnerablePlayerToEntity(entityLiving, ESM_Settings.Awareness);
    }

    public static void UpdateBiomeSpawns() {
        if (nativeBlazeBiomes == null || nativeGhastBiomes == null) {
            SetBiomeSpawnDefaults();
        }

        BiomeGenBase[] biomeList = BiomeGenBase.getBiomeGenArray();

        for (BiomeGenBase biome : biomeList) {
            if (biome == null) {
                continue;
            }

            if (!nativeBlazeBiomes.contains(biome)) {
                if (ESM_Settings.BlazeSpawn) {
                    EntityRegistry.removeSpawn(EntityBlaze.class, EnumCreatureType.monster, biome);
                    EntityRegistry.addSpawn(
                        EntityBlaze.class,
                        MathHelper.ceiling_float_int(100F / Math.max(1F, ESM_Settings.BlazeRarity)),
                        1,
                        1,
                        EnumCreatureType.monster,
                        biome);
                } else {
                    EntityRegistry.removeSpawn(EntityBlaze.class, EnumCreatureType.monster, biome);
                }
            }

            if (!nativeGhastBiomes.contains(biome)) {
                if (ESM_Settings.GhastSpawn) {
                    EntityRegistry.removeSpawn(EntityBlaze.class, EnumCreatureType.monster, biome);
                    EntityRegistry.addSpawn(
                        EntityGhast.class,
                        MathHelper.ceiling_float_int(100F / Math.max(1F, ESM_Settings.GhastRarity)),
                        1,
                        1,
                        EnumCreatureType.monster,
                        biome);
                } else {
                    EntityRegistry.removeSpawn(EntityGhast.class, EnumCreatureType.monster, biome);
                }
            }
        }
    }

    public static ArrayList<BiomeGenBase> nativeBlazeBiomes;
    public static ArrayList<BiomeGenBase> nativeGhastBiomes;

    public static void SetBiomeSpawnDefaults() {
        nativeBlazeBiomes = new ArrayList<BiomeGenBase>();
        nativeGhastBiomes = new ArrayList<BiomeGenBase>();

        BiomeGenBase[] biomeList = BiomeGenBase.getBiomeGenArray();

        for (BiomeGenBase biome : biomeList) {
            if (biome == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<SpawnListEntry> spawnList = biome.getSpawnableList(EnumCreatureType.monster);

            for (SpawnListEntry spawn : spawnList) {
                if (spawn.entityClass == EntityBlaze.class) {
                    nativeBlazeBiomes.add(biome);
                }

                if (spawn.entityClass == EntityGhast.class) {
                    nativeGhastBiomes.add(biome);
                }
            }
        }
    }

    private static long lastWorldtimeDayCheck = Long.MIN_VALUE;
    private static boolean isSiegeAllowedCached = false;

    public static boolean isSiegeAllowed(Long worldTime) {
        if ((ESM_Settings.SiegeWarmup > 0) && !isWarmupElapsed) {
            if ((worldTime / 24000L) < ESM_Settings.SiegeWarmup) return false; // still warming up
            isWarmupElapsed = true; // save unnecessary logic
        }

        if (ESM_Settings.SiegeFrequency == 1) return true;

        // only update the cached check value if the day has actually changed since last check
        if ((worldTime >= lastWorldtimeDayCheck + 24000L) || worldTime < lastWorldtimeDayCheck) {
            // update the last check time to the nearest 24000 ticks
            long worldDaysElapsed = worldTime / 24000L;
            lastWorldtimeDayCheck = worldDaysElapsed * 24000L;
            isSiegeAllowedCached = worldDaysElapsed % ESM_Settings.SiegeFrequency == 0;
        }

        return isSiegeAllowedCached;
    }

    private static boolean nerfedPick = !Items.iron_pickaxe
        .canHarvestBlock(Blocks.stone, new ItemStack(Items.iron_pickaxe));

    /**
     * Used by ESMPathFinder and ESM_EntityAIDoorInteract to check if a BlockDoor,
     * BlockFenceGate or BlockTrapDoor subclass should be considered griefable.
     *
     * @param block a block that subclasses either BlockDoor, BlockFenceGate or BlockTrapDoor (NB: A required
     *              precondition)
     */
    public static boolean isDoorOrGateGriefable(World world, Block block, int meta, Entity entity) {
        if (world.difficultySetting == EnumDifficulty.HARD)
            if (block == Blocks.wooden_door || block == Blocks.trapdoor || block == Blocks.fence_gate) return true;

        ItemStack item = null;
        if (entity instanceof EntityLiving) item = ((EntityLiving) entity).getEquipmentInSlot(0);

        if (!ESM_Settings.ZombieDiggerTools || ESM_Settings.ZombieGriefBlocksNoTool
            || block.getMaterial()
                .isToolNotRequired()
            || (item != null && (item.getItem()
                .canHarvestBlock(block, item)
                || (item.getItem() instanceof ItemPickaxe && nerfedPick && block.getMaterial() == Material.rock)))) {
            if (isSiegeAllowed(world.getWorldTime())) return true;

            if (BlockAndMeta.isInBlockAndMetaList(block, meta, ESM_Settings.getZombieGriefBlocks())) return true;
        }

        return false;

    }
}
