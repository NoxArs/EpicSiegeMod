package funwayguy.esm.core;

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
import net.minecraft.entity.ai.EntityAIArrowAttack;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIBreakDoor;
import net.minecraft.entity.ai.EntityAICreeperSwell;
import net.minecraft.entity.ai.EntityAIFollowOwner;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
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

import org.apache.logging.log4j.Level;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.registry.EntityRegistry;
import funwayguy.esm.ai.ESMPathNavigator;
import funwayguy.esm.ai.ESM_EntityAIArrowAttack;
import funwayguy.esm.ai.ESM_EntityAIAttackEvasion;
import funwayguy.esm.ai.ESM_EntityAIAttackOnCollide;
import funwayguy.esm.ai.ESM_EntityAIAvoidDetonations;
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

    public static int getSuitableSpawnHeight(World world, int par1, int par2) {
        int k;

        for (k = 63; !world.isAirBlock(par1, k, par2) || !world.isAirBlock(par1, k + 1, par2); ++k) {}

        ESM.log.log(Level.INFO, "Suitable spawn at " + par1 + ", " + k + ", " + par2);

        return k;
    }

    public static void resetPlayerMPStats(EntityPlayerMP player) {
        ObfuscationReflectionHelper
            .setPrivateValue(EntityPlayerMP.class, player, -1, "field_71144_ck", "lastExperience");
        ObfuscationReflectionHelper
            .setPrivateValue(EntityPlayerMP.class, player, -1.0F, "field_71149_ch", "lastHealth");
        ObfuscationReflectionHelper
            .setPrivateValue(EntityPlayerMP.class, player, -1, "field_71146_ci", "lastFoodLevel");
    }

    public static int getAIPathCount(World world, EntityLivingBase targetEntity) {
        List<EntityLivingBase> attackerList = ESM_PathCapHandler.attackMap.get(targetEntity);

        return attackerList == null ? 0 : attackerList.size();
    }

    public static boolean isCloserThanOtherAttackers(World world, EntityLivingBase attacker, EntityLivingBase target) {
        List<EntityLivingBase> attackerList = ESM_PathCapHandler.attackMap.get(target);

        if (attackerList == null || attackerList.size() <= 0) {
            return true;
        }

        float distance = attacker.getDistanceToEntity(target);

        EntityLivingBase subject = attackerList.get(attackerList.size() - 1); // We only need to check the farthest, the
                                                                              // rest are unnecessary to check

        if (subject != null && subject.getDistanceToEntity(target) > distance && subject != attacker) {
            return true;
        } else if (subject == null) // This is bad and needs to be resolved immediately
        {
            ESM_PathCapHandler.UpdateAttackers(target);
        }

        return false;
    }

    public static void replaceAI(EntityLiving entityLiving) {
        replaceAI(entityLiving, false);
    }

    @SuppressWarnings("unchecked")
    public static void replaceAI(EntityLiving entityLiving, boolean firstPass) {
        // TODO: Check if the PathNavigate replacement is compatible with PathfinderTweaks (it could be overwriting it,
        // somehow gate hopping has started again)
        PathNavigate oldNav = entityLiving.getNavigator();

        if (oldNav != null && oldNav.getClass() == PathNavigate.class) {
            ESMPathNavigator newNav = new ESMPathNavigator(entityLiving, entityLiving.worldObj);
            ObfuscationReflectionHelper
                .setPrivateValue(EntityLiving.class, entityLiving, newNav, "field_70699_by", "navigator");
            newNav.inherit(oldNav);
        }

        boolean replaceNAT = false;
        boolean replaceCS = false;
        boolean replaceAE = false;

        ESM_EntityAINearestAttackableTarget esmTargetAI = null;

        for (int i = entityLiving.targetTasks.taskEntries.size() - 1; i >= 0; i--) {
            EntityAITaskEntry task = (EntityAITaskEntry) entityLiving.targetTasks.taskEntries.get(i);

            if (task == null || task.action == null) {
                continue;
            }

            if (task.action.getClass() == EntityAINearestAttackableTarget.class
                && entityLiving instanceof EntityCreature) // Changed to allow other targeting based AI
            {
                if (esmTargetAI == null) {
                    esmTargetAI = new ESM_EntityAINearestAttackableTarget(
                        (EntityCreature) entityLiving,
                        new ArrayList<Class<? extends EntityLivingBase>>(),
                        0,
                        true);

                    if (ESM_Settings.Chaos) {
                        esmTargetAI.targetClass.add(EntityLivingBase.class);
                    } else {
                        if (ESM_Settings.ambiguous_AI) {
                            esmTargetAI.targetClass.add(EntityPlayer.class); // Attacking players is a must in ESM
                        }

                        if (ESM_Settings.VillagerTarget) {
                            esmTargetAI.targetClass.add(EntityVillager.class);
                        }
                    }
                }

                Class<?> targetType = ObfuscationReflectionHelper.getPrivateValue(
                    EntityAINearestAttackableTarget.class,
                    (EntityAINearestAttackableTarget) task.action,
                    "field_75307_b",
                    "targetClass");

                if (targetType != null && !esmTargetAI.targetClass.contains(targetType)) {
                    esmTargetAI.targetClass.add((Class<? extends EntityLivingBase>) targetType); // Accounts for custom
                                                                                                 // targets. Adds to
                                                                                                 // batched AI
                }

                if (!replaceNAT && !ESM_Settings.neutralMobs) {
                    replaceNAT = true;
                    EntityAITaskEntry replacement = entityLiving.targetTasks.new EntityAITaskEntry(task.priority,
                        esmTargetAI);
                    entityLiving.targetTasks.taskEntries.set(i, replacement);
                    entityLiving.getEntityAttribute(SharedMonsterAttributes.followRange)
                        .setBaseValue(ESM_Settings.Awareness);
                } else {
                    entityLiving.targetTasks.taskEntries.remove(i); // Remove redundant AI
                }
            } else if (task.action.getClass() == EntityAIHurtByTarget.class && entityLiving instanceof EntityCreature) {
                EntityAITaskEntry replacement = entityLiving.targetTasks.new EntityAITaskEntry(task.priority,
                    new ESM_EntityAIHurtByTarget((EntityCreature) entityLiving, true));
                entityLiving.targetTasks.taskEntries.set(i, replacement);
            }
        }

        // Cached for Zombies who switch out their AI dynamically
        ESM_EntityAIBreakDoor_Proxy cachedBD = null;
        EntityAIBreakDoor oldBD = entityLiving instanceof EntityZombie
            ? (EntityAIBreakDoor) ObfuscationReflectionHelper
                .getPrivateValue(EntityZombie.class, (EntityZombie) entityLiving, "field_146075_bs")
            : null;

        // Cached for Skeletons who switched out their AI dynamically
        ESM_EntityAIAttackOnCollide cachedAOC = null;
        EntityAIAttackOnCollide oldAOC = entityLiving instanceof EntitySkeleton
            ? (EntityAIAttackOnCollide) ObfuscationReflectionHelper.getPrivateValue(
                EntitySkeleton.class,
                (EntitySkeleton) entityLiving,
                "field_85038_e",
                "aiAttackOnCollide")
            : null;
        EntityAIArrowAttack cachedAA = null;
        EntityAIArrowAttack oldAA = entityLiving instanceof EntitySkeleton
            ? (EntityAIArrowAttack) ObfuscationReflectionHelper
                .getPrivateValue(EntitySkeleton.class, (EntitySkeleton) entityLiving, "field_85037_d", "aiArrowAttack")
            : null;

        for (int i = entityLiving.tasks.taskEntries.size() - 1; i >= 0; i--) {
            EntityAITaskEntry task = (EntityAITaskEntry) entityLiving.tasks.taskEntries.get(i);

            if (task == null || task.action == null) {
                continue;
            }

            if (task.action instanceof EntityAIFollowOwner) {
                ObfuscationReflectionHelper.setPrivateValue(
                    EntityAIFollowOwner.class,
                    (EntityAIFollowOwner) task.action,
                    entityLiving.getNavigator(),
                    "field_75337_g",
                    "petPathfinder"); // Minor fix to make sure the navigation is using the new one
            } else if (task.action.getClass() == EntityAICreeperSwell.class && entityLiving instanceof EntityCreeper) {
                if (ESM_Settings.CreeperEnhancementsOnlyWhenSiegeAllowed
                    && isSiegeAllowed(entityLiving.worldObj.getWorldTime())) {
                    if (!replaceCS) {
                        replaceCS = true;
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
            } else if (task.action.getClass() == EntityAIAvoidEntity.class && entityLiving instanceof EntityVillager) {
                if (!replaceAE) {
                    replaceAE = true;
                    // 确保 EntityAITaskEntry 的构造函数可见
                    EntityAITaskEntry replacement = entityLiving.tasks.new EntityAITaskEntry(task.priority,
                        new EntityAIAvoidEntity(
                            (EntityVillager) entityLiving,
                            (Class<? extends net.minecraft.entity.Entity>) (Object) IMob.class,
                            12.0F,
                            0.6D,
                            0.6D));
                    if (entityLiving.tasks.taskEntries instanceof List) {
                        ((List) entityLiving.tasks.taskEntries).set(i, replacement);
                    } else {
                        entityLiving.tasks.taskEntries.remove(task);
                        entityLiving.tasks.taskEntries.add(replacement);
                    }
                } else {
                    entityLiving.tasks.taskEntries.remove(task);
                }
            } else if (task.action.getClass() == EntityAISwimming.class) {
                EntityAITaskEntry replacement = entityLiving.tasks.new EntityAITaskEntry(task.priority,
                    new ESM_EntityAISwimming(entityLiving));
                entityLiving.tasks.taskEntries.set(i, replacement);
            } else if (task.action.getClass() == EntityAIBreakDoor.class) {
                ESM_EntityAIBreakDoor_Proxy tmp = new ESM_EntityAIBreakDoor_Proxy(entityLiving);
                if (task.action == oldBD) {
                    cachedBD = tmp;
                }
                EntityAITaskEntry replacement = entityLiving.tasks.new EntityAITaskEntry(task.priority, tmp);
                entityLiving.tasks.taskEntries.set(i, replacement);
            } else
                if (task.action.getClass() == EntityAIAttackOnCollide.class && entityLiving instanceof EntityCreature) {
                    boolean longMemory = ObfuscationReflectionHelper.getPrivateValue(
                        EntityAIAttackOnCollide.class,
                        (EntityAIAttackOnCollide) task.action,
                        "field_75437_f",
                        "longMemory");
                    Class<?> targetType = ObfuscationReflectionHelper.getPrivateValue(
                        EntityAIAttackOnCollide.class,
                        (EntityAIAttackOnCollide) task.action,
                        "field_75444_h",
                        "classTarget");
                    double speed = ObfuscationReflectionHelper.getPrivateValue(
                        EntityAIAttackOnCollide.class,
                        (EntityAIAttackOnCollide) task.action,
                        "field_75440_e",
                        "speedTowardsTarget");

                    ESM_EntityAIAttackOnCollide esmAOC = new ESM_EntityAIAttackOnCollide(
                        (EntityCreature) entityLiving,
                        targetType,
                        speed,
                        longMemory);
                    if (task.action == oldAOC) {
                        cachedAOC = esmAOC;
                    }
                    EntityAITaskEntry replacement = entityLiving.tasks.new EntityAITaskEntry(task.priority, esmAOC);
                    entityLiving.tasks.taskEntries.set(i, replacement);
                } else if (task.action.getClass() == EntityAIArrowAttack.class
                    && entityLiving instanceof IRangedAttackMob) {
                        ESM_EntityAIArrowAttack tmp = new ESM_EntityAIArrowAttack(
                            (IRangedAttackMob) entityLiving,
                            1.0D,
                            20,
                            60,
                            (float) ESM_Settings.SkeletonDistance);
                        if (task.action == oldAA) {
                            cachedAA = tmp;
                        }
                        EntityAITaskEntry replace = entityLiving.tasks.new EntityAITaskEntry(task.priority, tmp);
                        entityLiving.tasks.taskEntries.set(i, replace);
                    } else if (ESM_Settings.animalsAttack && task.action.getClass() == EntityAIPanic.class
                        && entityLiving instanceof IAnimals
                        && entityLiving instanceof EntityCreature) {
                            entityLiving.tasks.taskEntries.remove(i);
                        }
        }

        if (!firstPass) {
            if (entityLiving instanceof EntityCreature) {
                entityLiving.targetTasks
                    .addTask(0, new ESM_EntityAIAvoidDetonations((EntityCreature) entityLiving, 9F, 1.5D, 1.25D));
                if (entityLiving instanceof IMob && !(entityLiving instanceof EntityCreeper)) {
                    entityLiving.targetTasks
                        .addTask(0, new ESM_EntityAIAttackEvasion((EntityCreature) entityLiving, 5F, 1.5D, 1.25D));
                }

                if (entityLiving instanceof IAnimals && ESM_Settings.animalsAttack) {
                    entityLiving.tasks
                        .addTask(4, new ESM_EntityAIAttackOnCollide((EntityCreature) entityLiving, 1.25D, true));
                    entityLiving.targetTasks
                        .addTask(3, new ESM_EntityAIHurtByTarget((EntityCreature) entityLiving, true));
                }
            }

            if (entityLiving instanceof EntitySkeleton) {
                ESM_EntityAIAttackOnCollide tmpAOC = cachedAOC != null ? cachedAOC
                    : new ESM_EntityAIAttackOnCollide((EntitySkeleton) entityLiving, EntityPlayer.class, 1.2D, false);
                ObfuscationReflectionHelper.setPrivateValue(
                    EntitySkeleton.class,
                    (EntitySkeleton) entityLiving,
                    tmpAOC,
                    "field_85038_e",
                    "aiAttackOnCollide");
                EntityAIArrowAttack tmpAA = cachedAA != null ? cachedAA
                    : new ESM_EntityAIArrowAttack(
                        (EntitySkeleton) entityLiving,
                        1.0D,
                        20,
                        60,
                        ESM_Settings.SkeletonDistance);
                ObfuscationReflectionHelper.setPrivateValue(
                    EntitySkeleton.class,
                    (EntitySkeleton) entityLiving,
                    tmpAA,
                    "field_85037_d",
                    "aiArrowAttack");
            }

            if (entityLiving instanceof EntityZombie) {
                ESM_EntityAIBreakDoor_Proxy tmp = cachedBD != null ? cachedBD
                    : new ESM_EntityAIBreakDoor_Proxy(entityLiving);
                ObfuscationReflectionHelper
                    .setPrivateValue(EntityZombie.class, (EntityZombie) entityLiving, tmp, "field_146075_bs");

                ((EntityZombie) entityLiving).setCanPickUpLoot(true);
                ((EntityZombie) entityLiving).func_146070_a(true); // Break doors

                if (ESM_Settings.ZombieDiggers) {
                    entityLiving.tasks.addTask(1, new ESM_EntityAIDigging((EntityZombie) entityLiving));
                    entityLiving.tasks.addTask(6, new ESM_EntityAIGrief((EntityZombie) entityLiving));

                    entityLiving.tasks.addTask(3, new ESM_EntityAIPillarUp(entityLiving));
                }

                if (ESM_Settings.DemolitionZombies) {
                    entityLiving.tasks.addTask(3, new ESM_EntityAIDemolition(entityLiving));
                }
            }
        }
    }

    public static EntityLivingBase GetNearestValidTarget(EntityLiving entityLiving) {
        return entityLiving.worldObj.getClosestVulnerablePlayerToEntity(entityLiving, ESM_Settings.Awareness);
    }

    public static boolean isFortAt(World world, int x, int z, int size) {
        int dimension = world.provider.dimensionId;

        if (ESM_Settings.fortDistance - x % Math.abs(ESM_Settings.fortDistance) < size
            || ESM_Settings.fortDistance - z % Math.abs(ESM_Settings.fortDistance) < size) {
            return true;
        }

        int fGridX = x - (x % Math.abs(ESM_Settings.fortDistance));
        int fGridZ = z - (z % Math.abs(ESM_Settings.fortDistance));

        if (ESM_Settings.fortDB.contains(
            (new StringBuilder()).append(dimension)
                .append(",")
                .append(fGridX)
                .append(",")
                .append(fGridZ)
                .toString())) {
            return true;
        }
        return false;
    }

    public static void addFortToDB(World world, int x, int z) {
        int dimension = world.provider.dimensionId;

        int fGridX = x - (x % Math.abs(ESM_Settings.fortDistance));
        int fGridZ = z - (z % Math.abs(ESM_Settings.fortDistance));

        ESM_Settings.fortDB.add(
            (new StringBuilder()).append(dimension)
                .append(",")
                .append(fGridX)
                .append(",")
                .append(fGridZ)
                .toString());
        ESM_Settings.saveFortDB();
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
