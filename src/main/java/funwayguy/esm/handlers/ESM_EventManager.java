package funwayguy.esm.handlers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayer.EnumStatus;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.event.world.WorldEvent.Save;
import net.minecraftforge.event.world.WorldEvent.Unload;

import com.google.common.base.Stopwatch;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import funwayguy.esm.ai.GenericEntitySelector;
import funwayguy.esm.client.gui.ESMGuiConfig;
import funwayguy.esm.core.AttrUtil;
import funwayguy.esm.core.DimSettings;
import funwayguy.esm.core.ESM;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;
import funwayguy.esm.entities.EntityESMGhast;
import funwayguy.esm.handlers.entities.ESM_BlazeHandler;
import funwayguy.esm.handlers.entities.ESM_CreeperHandler;
import funwayguy.esm.handlers.entities.ESM_EndermanHandler;
import funwayguy.esm.handlers.entities.ESM_SkeletonHandler;
import funwayguy.esm.handlers.entities.ESM_ZombieHandler;

public class ESM_EventManager {

    static float curBossMod = 0F;

    private static final UUID UUID_HP = UUID.fromString("8cbf8b3d-6db1-42ea-8a53-ff1ef6a5293f");
    private static final UUID UUID_SPD = UUID.fromString("f6126165-cabf-4041-9510-dae783fd86ad");
    private static final UUID UUID_DMG = UUID.fromString("fc62a932-f911-48ef-95e6-af9b356911f2");
    private static final UUID UUID_KBR = UUID.fromString("82d629b8-529c-479b-ab7a-e9b7265f816f");

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || event.entity instanceof EntityPlayer) {
            return;
        }

        if (ESM_Settings.Apocalypse && (event.entity instanceof IMob || event.entity instanceof EntityMob)
            && !(event.entity instanceof IBossDisplayData || event.entity instanceof EntityZombie
                || event.entity instanceof EntityPlayer
                || (event.entity instanceof EntityEnderman && ESM_Settings.EndermanSlender))) {
            event.entity.setDead();
            event.setCanceled(true);
            return;
        }

        // ----- Compute list gating once -----
        String entId = EntityList.getEntityString(event.entity);
        boolean inAIList = (entId != null && ESM_Settings.AIExempt.contains(entId));
        boolean allowedByList = (inAIList == ESM_Settings.flipBlacklist);

        // ----- AI replacement (same gating as the rest) -----
        if (event.entity instanceof EntityLiving && (event.entity instanceof IMob || ESM_Settings.ambiguous_AI)
            && allowedByList) {
            ESM_Utils.replaceAI((EntityLiving) event.entity, true);

            if (event.entity instanceof EntityMob
                || (event.entity instanceof EntitySpider && !event.world.isDaytime())) {
                searchForTarget((EntityCreature) event.entity);
            }
        }

        // ----- Dimension attribute tweaks (UUID replace, no stacking) -----
        if (event.entity instanceof EntityLivingBase && (event.entity instanceof IMob || ESM_Settings.ambiguous_AI)
            && allowedByList) {
            EntityLivingBase living = (EntityLivingBase) event.entity;
            DimSettings dimSet = ESM_Settings.dimSettings.get(event.world.provider.dimensionId);

            // delta = (mult - 1), because AttributeModifier op=1 expects delta
            double hpDelta = (dimSet != null ? dimSet.hpMult : 0.0D) + curBossMod;
            double spdDelta = (dimSet != null ? dimSet.spdMult : 0.0D) + curBossMod;
            double dmgDelta = (dimSet != null ? dimSet.dmgMult : 0.0D) + curBossMod;
            double kbrDelta = (dimSet != null ? dimSet.knockResist : 0.0D) + curBossMod;

            // Apply only if there's something to apply (either dim config or boss mod active)
            if (dimSet != null || (curBossMod > 0F && ESM_Settings.bossModifier != 0F)) {

                AttrUtil.applyOrReplace(
                    living.getEntityAttribute(SharedMonsterAttributes.maxHealth),
                    UUID_HP,
                    "ESM_TWEAK_HP",
                    hpDelta,
                    1);
                living.setHealth(living.getMaxHealth());

                AttrUtil.applyOrReplace(
                    living.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
                    UUID_SPD,
                    "ESM_TWEAK_SPD",
                    spdDelta,
                    1);

                AttrUtil.applyOrReplace(
                    living.getEntityAttribute(SharedMonsterAttributes.attackDamage),
                    UUID_DMG,
                    "ESM_TWEAK_DMG",
                    dmgDelta,
                    1);

                // FIXED: knockback uses knockResist delta (not dmgMult)
                AttrUtil.applyOrReplace(
                    living.getEntityAttribute(SharedMonsterAttributes.knockbackResistance),
                    UUID_KBR,
                    "ESM_TWEAK_KBR",
                    kbrDelta,
                    1);
            }
        }

        if (event.entity.getClass() == EntityGhast.class) {
            // 如果这个 ghast 已经是我们替换出来的，就别再替换
            if (event.entity.getEntityData()
                .getBoolean("ESM_MODIFIED")) return;

            event.setCanceled(true);

            EntityESMGhast newGhast = new EntityESMGhast(event.world);
            newGhast.setLocationAndAngles(
                event.entity.posX,
                event.entity.posY,
                event.entity.posZ,
                event.entity.rotationYaw,
                0.0F);

            NBTTagCompound oldTags = new NBTTagCompound();
            event.entity.writeToNBT(oldTags);
            newGhast.readFromNBT(oldTags);

            // ★关键：标记写在“新实体”上
            newGhast.getEntityData()
                .setBoolean("ESM_MODIFIED", true);

            event.world.spawnEntityInWorld(newGhast);

            newGhast.riddenByEntity = event.entity.riddenByEntity;
            if (newGhast.riddenByEntity != null) {
                newGhast.riddenByEntity.mountEntity(newGhast);
            }

            event.entity.setDead();
            event.setCanceled(true);
            return;
        } else if (event.entity instanceof EntityCreeper) {
            ESM_CreeperHandler.onEntityJoinWorld((EntityCreeper) event.entity);
        } else if (event.entity instanceof EntitySpider) {} else if (event.entity instanceof EntitySkeleton) {
            ESM_SkeletonHandler.onEntityJoinWorld((EntitySkeleton) event.entity);
        } else if (event.entity instanceof EntityZombie) {
            if (ESM_Settings.ZombieDiggers && event.world.rand.nextFloat() < 0.1F) {
                ((EntityZombie) event.entity).setCanPickUpLoot(true);

                if (event.world.rand.nextFloat() < 0.1F) {
                    ((EntityZombie) event.entity).setCurrentItemOrArmor(0, new ItemStack(Items.diamond_pickaxe));
                } else {
                    ((EntityZombie) event.entity).setCurrentItemOrArmor(0, new ItemStack(Items.iron_pickaxe));
                }
            } else if (ESM_Settings.DemolitionZombies && event.world.rand.nextFloat() < 0.1F
                && (ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed
                    ? ESM_Utils.isSiegeAllowed(event.world.getWorldTime())
                    : true)) {
                        ((EntityZombie) event.entity).setCurrentItemOrArmor(0, new ItemStack(Blocks.tnt));
                    }
        } else if (event.entity.getClass() == EntityArrow.class) // Changed because other people like replacing arrows
                                                                 // and not saying they did
        {
            final EntityArrow arrow = (EntityArrow) event.entity;
            if (arrow.getEntityData()
                .getBoolean("ESM_MODIFIED")) {
                return;
            }
            if (!(arrow.shootingEntity instanceof EntityLiving) || !(arrow.shootingEntity instanceof IMob)) {
                return;
            }

            final EntityLiving shooter = (EntityLiving) arrow.shootingEntity;
            final EntityLivingBase target = shooter.getAttackTarget();
            if (target == null || target.isDead || target.getHealth() <= 0.0F) {
                return;
            }
            replaceArrowAttack(shooter, target, arrow.getDamage());
            arrow.getEntityData()
                .setBoolean("ESM_MODIFIED", true);
            event.setCanceled(true);
            event.entity.setDead();
            return;
        } else if (event.entity instanceof EntityPotion) {
            EntityPotion potion = (EntityPotion) event.entity;

            PotionEffect effect = null;

            if (ESM_Settings.customPotions != null && ESM_Settings.customPotions.length > 0) {
                String[] type = ESM_Settings.customPotions[event.world.rand.nextInt(ESM_Settings.customPotions.length)]
                    .split(":");

                if (type.length == 3) {
                    try {
                        effect = new PotionEffect(
                            Integer.parseInt(type[0]),
                            Integer.parseInt(type[1]),
                            Integer.parseInt(type[2]));
                    } catch (Exception e) {
                        effect = null;
                    }
                }
            }

            if (potion.getThrower() instanceof EntityWitch && effect != null) {
                NBTTagList nbtList = new NBTTagList();
                nbtList.appendTag(effect.writeCustomPotionEffectToNBT(new NBTTagCompound()));

                ItemStack effectStack = new ItemStack(Items.potionitem);
                NBTTagCompound itemTags = new NBTTagCompound();
                itemTags.setTag("CustomPotionEffects", nbtList);
                effectStack.setTagCompound(itemTags);

                ObfuscationReflectionHelper
                    .setPrivateValue(EntityPotion.class, potion, effectStack, "field_70197_d", "potionDamage");
            }
        } else if (event.entity instanceof EntityBlaze) {
            ESM_BlazeHandler.onEntityJoinWorld((EntityBlaze) event.entity);
        } else if (event.entity instanceof EntitySmallFireball) {
            EntitySmallFireball fireball = (EntitySmallFireball) event.entity;
            if (fireball.shootingEntity instanceof EntityBlaze) {
                fireball.shootingEntity.getEntityData()
                    .setInteger(
                        "ESM_FIREBALLS",
                        fireball.shootingEntity.getEntityData()
                            .getInteger("ESM_FIREBALLS") + 1);
            }
        } else if (event.entity instanceof EntityEnderman) {
            ESM_EndermanHandler.onEntityJoinWorld((EntityEnderman) event.entity);
        }

        boolean mobBombMatch = ESM_Settings.MobBombAll || (ESM_Settings.MobBombs != null && entId != null
            && (ESM_Settings.MobBombs.contains(entId) != ESM_Settings.MobBombInvert));

        if (mobBombMatch && event.entity.riddenByEntity == null
            && event.entity instanceof IMob
            && !event.isCanceled()
            && !event.entity.isDead
            && event.world.loadedEntityList.size() < 512) {
            if (ESM_Settings.MobBombRarity <= 0 || event.world.rand.nextInt(ESM_Settings.MobBombRarity) == 0) {
                event.entity.getEntityData()
                    .setBoolean("ESM_MODIFIED", true);

                Entity passenger = !ESM_Settings.CrystalBombs ? new EntityCreeper(event.entity.worldObj)
                    : new EntityEnderCrystal(event.entity.worldObj);

                passenger.setLocationAndAngles(
                    event.entity.posX,
                    event.entity.posY,
                    event.entity.posZ,
                    event.entity.rotationYaw,
                    0.0F);

                if (passenger instanceof EntityLiving) {
                    ((EntityLiving) passenger).onSpawnWithEgg((IEntityLivingData) null);
                }

                event.entity.worldObj.spawnEntityInWorld(passenger);
                passenger.mountEntity(event.entity);
            }
        }

        if (event.entity instanceof IMob && !(event.entity instanceof IBossDisplayData)
            && event.entity instanceof EntityLivingBase
            && ESM_Settings.PotionMobs > event.world.rand.nextInt(100)
            && ESM_Settings.PotionMobEffects != null
            && ESM_Settings.PotionMobEffects.length > 0) {
            int id = ESM_Settings.PotionMobEffects[event.world.rand.nextInt(ESM_Settings.PotionMobEffects.length)];
            if (Potion.potionTypes[id] != null) {
                ((EntityLivingBase) event.entity).addPotionEffect(new PotionEffect(id, 999999));
            }
        }
    }

    @SubscribeEvent
    public void onExplode(ExplosionEvent.Start event) {
        EntityLivingBase source = event.explosion.getExplosivePlacedBy();

        if (source instanceof EntityCreeper) {
            if (ESM_Settings.CreeperNapalm) {
                event.explosion.isFlaming = true;
            }

            if ("John Cena".equals(((EntityCreeper) source).getCustomNameTag())) {
                event.explosion.explosionSize *= 3F;
                event.world.playSoundAtEntity(source, "esm:cena_creeper.end", 1.0F, 1.0F);
            }
        }
    }

    public static void replaceArrowAttack(EntityLiving shooter, EntityLivingBase targetEntity, double baseDamage) {
        if (shooter == null || targetEntity == null || shooter.worldObj == null) return;

        // 预测目标位置用的距离（用 minY 会让瞄得更低；保留原作者意图）
        double targetDist = shooter.getDistance(
            targetEntity.posX + (targetEntity.posX - targetEntity.lastTickPosX),
            targetEntity.boundingBox.minY,
            targetEntity.posZ + (targetEntity.posZ - targetEntity.lastTickPosZ));

        // 原作者的“距离越远速度越快”曲线
        float calcSpeed = (float) (0.00013D * targetDist * targetDist + 0.02D * targetDist + 1.25D);

        // 最终用来发射/预判的速度：必须和后面的 lead 一致
        final float fireSpeed = (ESM_Settings.SkeletonDistance <= 0) ? 1.6F : calcSpeed;

        // 先构造箭（构造器内部也会做一次 heading，但我们会覆盖它）
        EntityArrow arrow = new EntityArrow(
            shooter.worldObj,
            shooter,
            targetEntity,
            fireSpeed,
            ESM_Settings.SkeletonAccuracy);

        // 用 fireSpeed 做 lead（现在和实际发射速度一致了）
        double leadFactor = (fireSpeed > 1.0E-6F) ? (targetDist / fireSpeed) : 0.0D;

        double d0 = (targetEntity.posX + (targetEntity.posX - targetEntity.lastTickPosX) * leadFactor) - shooter.posX;
        double d1 = targetEntity.boundingBox.minY + (double) (targetEntity.height / 3.0F) - arrow.posY;
        double d2 = (targetEntity.posZ + (targetEntity.posZ - targetEntity.lastTickPosZ) * leadFactor) - shooter.posZ;
        double d3 = (double) MathHelper.sqrt_double(d0 * d0 + d2 * d2);

        if (d3 >= 1.0E-7D) {
            float f4 = (float) d3 * 0.2F; // 原作者的抬高量
            arrow.setThrowableHeading(d0, d1 + (double) f4, d2, fireSpeed, ESM_Settings.SkeletonAccuracy);
        }

        // --- 伤害 / 附魔继承 ---
        ItemStack held = shooter.getHeldItem();
        int power = (held != null) ? EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, held) : 0;
        int punch = (held != null) ? EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, held) : 0;
        int flame = (held != null) ? EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, held) : 0;

        arrow.setDamage(baseDamage);

        if (power > 0) {
            arrow.setDamage(arrow.getDamage() + (double) power * 0.5D + 0.5D);
        }

        if (punch > 0) {
            arrow.setKnockbackStrength(punch);
        }

        // 火焰：武器 flame 或凋零骷髅（原作者逻辑）
        if (flame > 0 || (shooter instanceof EntitySkeleton && ((EntitySkeleton) shooter).getSkeletonType() == 1)) {
            arrow.setFire(100);
        }

        shooter.playSound(
            "random.bow",
            1.0F,
            1.0F / (shooter.getRNG()
                .nextFloat() * 0.4F + 0.8F));

        // 标记：防止 JoinWorld 里再次被你劫持
        arrow.getEntityData()
            .setBoolean("ESM_MODIFIED", true);

        shooter.worldObj.spawnEntityInWorld(arrow);
    }

    @SubscribeEvent
    public void onEntityAttacked(LivingHurtEvent event) {
        if (event.entity.worldObj.isRemote) {
            return;
        }
        /*
         * if(!ESM_Settings.friendlyFire && event.source != null && event.source.getEntity() != null &&
         * event.entityLiving instanceof IMob && (ESM_Settings.Chaos? event.entityLiving.getClass() ==
         * event.source.getEntity().getClass() : event.source.getEntity() instanceof IMob))
         * {
         * event.setCanceled(true);
         * return;
         * }
         */
        if (!(event.entityLiving instanceof EntityPlayer) && event.entityLiving.ridingEntity != null
            && event.source == DamageSource.inWall) {
            event.entityLiving.dismountEntity(event.entityLiving.ridingEntity);
            event.entityLiving.ridingEntity.riddenByEntity = null;
            event.entityLiving.ridingEntity = null;
        }

        if (event.entityLiving instanceof EntityPlayer && event.source.getEntity() instanceof IMob) {
            int day = (int) (event.entityLiving.worldObj.getWorldTime() / 24000);

            if (ESM_Settings.hardDay != 0 && day != 0 && day % ESM_Settings.hardDay == 0 && ESM_Settings.hardDamage) {
                event.ammount *= 2F;
            }
        }

        if (event.source != null && event.source.getEntity() instanceof EntitySpider
            && ESM_Settings.SpiderWebChance > event.entityLiving.getRNG()
                .nextInt(100)) {
            int i = MathHelper.floor_double(event.entityLiving.posX);
            int j = MathHelper.floor_double(event.entityLiving.posY);
            int k = MathHelper.floor_double(event.entityLiving.posZ);

            Block b = event.entityLiving.worldObj.getBlock(i, j, k);

            if (b.getMaterial()
                .isReplaceable()) {
                event.entityLiving.worldObj.setBlock(i, j, k, Blocks.web);
            }
        }
    }

    private static boolean isBossForScaling(EntityLivingBase e) {
        if (e == null) return false;
        if (!(e instanceof IBossDisplayData)) return false;
        return e.getMaxHealth() >= 200.0F;
    }

    private static float advanceBossMod(float cur, float k) {
        if (k <= 0F) return cur;
        if (k > 1F) k = 1F;

        if (cur < 0F) cur = 0F;
        if (cur > 1F) cur = 1F;

        return cur + (1F - cur) * k;
    }

    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        if (event.entity.worldObj.isRemote) {
            return;
        }

        ESM_PathCapHandler.RemoveTarget(event.entityLiving);

        if (event.entityLiving instanceof EntityLiving) {
            EntityLivingBase target = ((EntityLiving) event.entityLiving).getAttackTarget();

            if (target != null) {
                ESM_PathCapHandler.UpdateAttackers(target);
            }
        }

        if (event.entity instanceof EntityPlayer) {
            if (event.source.getSourceOfDamage() instanceof EntityZombie && ESM_Settings.ZombieInfectious) {
                EntityZombie zombie = new EntityZombie(event.entity.worldObj);
                zombie.setPosition(event.entity.posX, event.entity.posY, event.entity.posZ);
                zombie.setCanPickUpLoot(true);
                zombie.setCustomNameTag(
                    event.entity.getCommandSenderName() + " ("
                        + StatCollector.translateToLocal("entity.Zombie.name")
                        + ")");
                zombie.getEntityData()
                    .setBoolean("ESM_MODIFIED", true);
                event.entity.worldObj.spawnEntityInWorld(zombie);
            }
        }

        if (isBossForScaling(event.entityLiving)) curBossMod = advanceBossMod(curBossMod, ESM_Settings.bossModifier);
    }

    static Method methodAI;

    public static boolean usesAI(EntityLivingBase entityLiving) {
        if (!(entityLiving instanceof EntityLiving)) {
            return false;
        }

        EntityLiving living = (EntityLiving) entityLiving;

        // 懒加载反射 Method
        if (methodAI == null) {
            try {
                // 混淆环境（生产）
                methodAI = EntityLivingBase.class.getDeclaredMethod("func_70650_aV");
                methodAI.setAccessible(true);
            } catch (Exception e1) {
                try {
                    // MCP / 开发环境
                    methodAI = EntityLivingBase.class.getDeclaredMethod("isAIEnabled");
                    methodAI.setAccessible(true);
                } catch (Exception e2) {
                    // 两个都拿不到，降级策略见下
                    methodAI = null;
                }
            }
        }

        // 如果成功拿到方法，直接用
        if (methodAI != null) {
            try {
                return (Boolean) methodAI.invoke(living);
            } catch (Exception e) {
                // invoke 失败 → 降级
            }
        }

        // ===== 降级策略 =====
        // simple 模式：认为 EntityLiving 默认“有 AI”
        if (ESM_Settings.simpleAiCheck) {
            return living.tasks != null;
        }

        // 非 simple 模式：退回到最保守判断
        return living.tasks != null;
    }

    @SuppressWarnings("unchecked")
    public static void searchForTarget(EntityCreature entity) {
        if (ESM_Settings.neutralMobs || usesAI(entity)
            || (entity instanceof EntityEnderman)
            || (entity instanceof EntityTameable && ((EntityTameable) entity).isTamed())) {
            entity.getEntityData()
                .setInteger("ESM_TARGET_COOLDOWN", 0);
            return;
        }

        int cooldown = entity.getEntityData()
            .getInteger("ESM_TARGET_COOLDOWN");

        if (cooldown > 0) {
            entity.getEntityData()
                .setInteger("ESM_TARGET_COOLDOWN", cooldown - 1);
            return;
        } else {
            entity.getEntityData()
                .setInteger("ESM_TARGET_COOLDOWN", 60);
        }

        EntityLivingBase target = entity.getAITarget();
        target = target != null && target.isEntityAlive() ? target : entity.getAttackTarget();
        target = target != null && target.isEntityAlive() ? target
            : (entity.getEntityToAttack() instanceof EntityLivingBase ? (EntityLivingBase) entity.getEntityToAttack()
                : target);

        if (target != null && target.isEntityAlive()) {
            if (ESM_Settings.TargetCap >= 0 && ESM_PathCapHandler.attackMap.get(target) != null
                && ESM_PathCapHandler.attackMap.get(target)
                    .size() > ESM_Settings.TargetCap
                && !ESM_Utils.isCloserThanOtherAttackers(entity.worldObj, entity, target)) {
                entity.getNavigator()
                    .clearPathEntity();
                entity.setAttackTarget(null);
                entity.setTarget(null);
                entity.setRevengeTarget(null);
                return;
            }

            if (entity.getDistanceToEntity(target) > ESM_Settings.Awareness) {
                entity.getNavigator()
                    .clearPathEntity();
                entity.setAttackTarget(null);
                entity.setTarget(null);
                entity.setRevengeTarget(null);
                return;
            }

            if (!entity.hasPath() && ESM_Settings.forcePath) {
                // This entity may be auto-invalidating its current target/path
                entity.setPathToEntity(
                    entity.worldObj
                        .getPathEntityToEntity(entity, target, ESM_Settings.Awareness, true, false, false, true));
                entity.getEntityData()
                    .setInteger("ESM_TARGET_COOLDOWN", 10);
            }

            // In case the target hasn't been applied to both variables yet we just re-set both of them.
            entity.setTarget(target);
            entity.setAttackTarget(target);
            entity.setRevengeTarget(target);
            return;
        }

        EntityLivingBase closestTarget = null;

        List<EntityLivingBase> targets = entity.worldObj.selectEntitiesWithinAABB(
            EntityLivingBase.class,
            entity.boundingBox.expand(ESM_Settings.Awareness, ESM_Settings.Awareness, ESM_Settings.Awareness),
            new GenericEntitySelector(entity));

        Iterator<EntityLivingBase> it = targets.iterator();
        while (it.hasNext()) {
            if (it.next() instanceof EntityPlayer) {
                it.remove();
            }
        }

        if (targets == null || targets.isEmpty()) {
            entity.setTarget(null);
            entity.setAttackTarget(null);
            entity.setRevengeTarget(null);
            return;
        }

        // 需要排序的话，用能处理 EntityLivingBase 的 comparator
        Collections.sort(targets, new EntityAINearestAttackableTarget.Sorter(entity));

        for (int i = 0; i < targets.size(); i++) {
            EntityLivingBase subject = targets.get(i);

            if (ESM_Settings.TargetCap < 0 || ESM_PathCapHandler.attackMap.get(subject) == null
                || ESM_PathCapHandler.attackMap.get(subject)
                    .size() < ESM_Settings.TargetCap
                || ESM_Utils.isCloserThanOtherAttackers(entity.worldObj, entity, subject)) {
                closestTarget = subject;
                break;
            }
        }

        entity.setTarget(closestTarget);
        entity.setAttackTarget(closestTarget);
        entity.setRevengeTarget(closestTarget);

        if (closestTarget != null) {
            entity.setPathToEntity(
                entity.worldObj
                    .getPathEntityToEntity(entity, closestTarget, ESM_Settings.Awareness, true, false, false, true));
            ESM_PathCapHandler.AddNewAttack(entity, closestTarget);
        }
    }

    Stopwatch timer = Stopwatch.createUnstarted();
    int ticks = 0;
    float TPS = 0;

    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (!timer.isRunning()) {
                timer.start();
            }

            ticks++;

            if (ticks >= 100) {
                timer.stop();

                long ms = timer.elapsed(TimeUnit.MILLISECONDS);
                if (ms > 0) TPS = ticks * 1000F / ms;

                // Debugging TPS counter
                // ESM.log.log(Level.INFO, "TPms: " + TPS + " (" + ticks + "/" +
                // (timer.elapsed(TimeUnit.MILLISECONDS)/1000F) + ")");
                ticks = 0;

                timer.reset();
                timer.start();
            }
        }
    }

    private void handleHardModeExtraSpawns(LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;

        World world = event.entityLiving.worldObj;
        if (!(world instanceof WorldServer)) return;
        if (world.difficultySetting == EnumDifficulty.PEACEFUL) return;
        if (!world.getGameRules()
            .getGameRuleBooleanValue("doMobSpawning")) return;
        if (world.loadedEntityList.size() >= 512) return;

        boolean hard = ESM_Settings.moreSpawning;
        int day = (int) (world.getWorldTime() / 24000);

        if (!hard && ESM_Settings.hardDay != 0 && day != 0 && day % ESM_Settings.hardDay == 0) {
            hard = true;
        }
        if (!hard) return;

        if (event.entityLiving.getRNG()
            .nextInt(10) != 0) return;

        int x = MathHelper.floor_double(event.entityLiving.posX) + event.entityLiving.getRNG()
            .nextInt(48) - 24;
        int z = MathHelper.floor_double(event.entityLiving.posZ) + event.entityLiving.getRNG()
            .nextInt(48) - 24;

        // 用玩家当前高度（比随机 y 稳定得多）
        int y = MathHelper.floor_double(event.entityLiving.posY);
        if (y < 1) y = 1;
        if (y > 255) y = 255;

        if (world.getClosestPlayer(x, y, z, 8D) != null) return;
        if (!SpawnerAnimals.canCreatureTypeSpawnAtLocation(EnumCreatureType.monster, world, x, y, z)) return;

        SpawnListEntry entry = ((WorldServer) world).spawnRandomCreature(EnumCreatureType.monster, x, y, z);
        if (entry == null) return;

        try {
            EntityLiving mob = (EntityLiving) entry.entityClass.getConstructor(new Class[] { World.class })
                .newInstance(new Object[] { world });

            mob.setLocationAndAngles(
                x + 0.5D,
                y,
                z + 0.5D,
                event.entityLiving.getRNG()
                    .nextFloat() * 360.0F,
                0.0F);

            Result canSpawn = ForgeEventFactory.canEntitySpawn(mob, world, x, y, z);
            if (canSpawn == Result.ALLOW || (canSpawn == Result.DEFAULT && mob.getCanSpawnHere())) {
                world.spawnEntityInWorld(mob);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (event.entity.worldObj.isRemote) return;

        // ---- 玩家刷怪功能如果你要保留：必须在这里处理（因为下面会过滤玩家）----
        if (event.entityLiving instanceof EntityPlayer) {
            handleHardModeExtraSpawns(event); // 你把原来的那坨 else-if 挪进这个函数
            return;
        }

        // ---- Apocalypse 清理（原逻辑）----
        if (ESM_Settings.Apocalypse && (event.entity instanceof IMob || event.entity instanceof EntityMob)
            && !(event.entity instanceof IBossDisplayData || event.entity instanceof EntityPlayer
                || event.entity instanceof EntityZombie
                || (event.entity instanceof EntityEnderman && ESM_Settings.EndermanSlender))) {
            event.entityLiving.setDead();
            return;
        }

        // ---- 统一名单 gating（防 null，可读）----
        String entId = EntityList.getEntityString(event.entity);
        boolean inAIList = (entId != null && ESM_Settings.AIExempt.contains(entId));
        boolean allowedByList = (inAIList == ESM_Settings.flipBlacklist);
        if (!allowedByList) return;

        // ---- AI replace：只补漏，不每 tick 重复 ----
        if (event.entityLiving instanceof EntityLiving) {
            NBTTagCompound data = event.entityLiving.getEntityData();
            if (!data.getBoolean("ESM_AI_REPLACED")) {
                ESM_Utils.replaceAI((EntityLiving) event.entityLiving);
                data.setBoolean("ESM_AI_REPLACED", true);
            }
        }

        // ---- 攻击者统计 ----
        if (event.entityLiving instanceof EntityLiving
            && ((EntityLiving) event.entityLiving).getAttackTarget() != null) {
            ESM_PathCapHandler.AddNewAttack(event.entityLiving, ((EntityLiving) event.entityLiving).getAttackTarget());
        } else if (event.entityLiving.getAITarget() != null) {
            ESM_PathCapHandler.AddNewAttack(event.entityLiving, event.entityLiving.getAITarget());
        } else if (event.entityLiving instanceof EntityCreature) {
            Entity toAttack = ((EntityCreature) event.entityLiving).getEntityToAttack();
            if (toAttack instanceof EntityLivingBase) {
                ESM_PathCapHandler.AddNewAttack(event.entityLiving, (EntityLivingBase) toAttack);
            }
        }

        // ---- 强制找目标（保留你原条件）----
        if (ESM_Settings.Awareness != 16 && event.entityLiving instanceof IMob
            && event.entityLiving instanceof EntityCreature
            && !(event.entityLiving instanceof EntitySpider && event.entityLiving.worldObj.isDaytime())) {
            searchForTarget((EntityCreature) event.entityLiving);
        }

        if (event.entityLiving instanceof EntityZombie) {
            ESM_ZombieHandler.onLivingUpdate((EntityZombie) event.entityLiving);
        } else if (event.entityLiving instanceof EntityCreeper) {
            ESM_CreeperHandler.onLivingUpdate((EntityCreeper) event.entityLiving);
        } else if (event.entityLiving instanceof EntitySkeleton) {
            ESM_SkeletonHandler.onLivingUpdate((EntitySkeleton) event.entityLiving);
        } else if (event.entityLiving instanceof EntityBlaze) {
            ESM_BlazeHandler.onLivingUpdate((EntityBlaze) event.entityLiving);
        } else if (event.entityLiving instanceof EntityEnderman) {
            ESM_EndermanHandler.onLivingUpdate((EntityEnderman) event.entityLiving);
        }
    }

    @SubscribeEvent
    public void onEnderTeleport(EnderTeleportEvent event) {
        AxisAlignedBB bounds = event.entityLiving.getCollisionBox(event.entityLiving);
        bounds = bounds != null ? bounds : event.entityLiving.getBoundingBox();

        if (bounds != null
            && !event.entityLiving.worldObj.getEntitiesWithinAABB(EntityPlayer.class, bounds.expand(5D, 5D, 5D))
                .isEmpty()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerChangedDimensionEvent event) {
        if (ESM_Settings.ResistanceCoolDown > 0) {
            event.player.addPotionEffect(new PotionEffect(Potion.resistance.id, ESM_Settings.ResistanceCoolDown, 5));
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerRespawnEvent event) {
        if (ESM_Settings.ResistanceCoolDown > 0) {
            event.player.addPotionEffect(new PotionEffect(Potion.resistance.id, ESM_Settings.ResistanceCoolDown, 5));
        }
    }

    @SubscribeEvent
    public void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (event.entityPlayer.worldObj.isRemote) return;
        if (ESM_Settings.AllowSleep) return;

        EntityPlayer player = event.entityPlayer;
        World world = player.worldObj;

        // 基础状态检查
        if (player.isPlayerSleeping() || !player.isEntityAlive()) return;
        if (!world.provider.canRespawnHere()) return;
        if (world.isDaytime()) return;

        // 距离检查（原版逻辑）
        if (Math.abs(player.posX - event.x) > 3.0D || Math.abs(player.posY - event.y) > 2.0D
            || Math.abs(player.posZ - event.z) > 3.0D) {
            return;
        }

        // 周围怪物检查（沿用你原范围）
        double dx = 8.0D, dy = 5.0D;
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(event.x - dx, event.y - dy, event.z - dx, event.x + dx, event.y + dy, event.z + dx);

        // 你原来用 EntityMob；想更强可以换成 IMob.class（需要 selector 或别的查法）
        List<?> mobs = world.getEntitiesWithinAABB(EntityMob.class, box);
        if (!mobs.isEmpty()) return;

        // 明确：不允许睡觉
        event.setResult(Result.DENY);
        event.result = EnumStatus.OTHER_PROBLEM; // 保持你原行为（不进入睡眠）

        // 设重生点
        if (player.isRiding()) player.mountEntity((Entity) null);
        player.setSpawnChunk(new ChunkCoordinates(event.x, event.y, event.z), false);
        player.addChatMessage(new ChatComponentText("Spawnpoint set"));
    }

    @SubscribeEvent
    public void onWorldLoad(Load event) {
        if (!event.world.isRemote && (ESM_Settings.currentWorlds == null || ESM_Settings.worldDir == null)) {
            MinecraftServer server = MinecraftServer.getServer();

            if (server.isServerRunning()) {
                ESM_Settings.currentWorlds = server.worldServers;
                if (ESM.proxy.isClient()) {
                    ESM_Settings.worldDir = server.getFile("saves/" + server.getFolderName());
                } else {
                    ESM_Settings.worldDir = server.getFile(server.getFolderName());
                }
                ESM_Settings.LoadWorldConfig();
                try {
                    NBTTagCompound wmTag = CompressedStreamTools.read(new File(ESM_Settings.worldDir, "ESM.dat"));
                    if (wmTag != null) {
                        curBossMod = wmTag.getFloat("BossModifier");
                    } else {
                        curBossMod = 0F;
                    }
                } catch (IOException e) {
                    curBossMod = 0F;
                    ESM.log.warn("[WorldLoad] Failed to read ESM.dat, default = 0", e);
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(Unload event) {
        if (!event.world.isRemote) {
            MinecraftServer mc = MinecraftServer.getServer();

            if (!mc.isServerRunning()) {
                ESM_PathCapHandler.attackMap.clear();
                ESM_Settings.currentWorlds = null;
                ESM_Settings.worldDir = null;
                curBossMod = 0F;
            }
        }
        ESM_Utils.isWarmupElapsed = false; // reset the warmup flag, check again on next world load
    }

    @SubscribeEvent
    public void onWorldSave(Save event) {
        try {
            NBTTagCompound wmTag = new NBTTagCompound();
            wmTag.setFloat("BossModifier", curBossMod);
            CompressedStreamTools.write(wmTag, new File(ESM_Settings.worldDir, "ESM.dat"));
        } catch (Exception e) {
            ESM.log.warn("[WorldSave] Failed to save ESM.dat", e);
        }
        /*
         * if(ESM_EntityAIBrainController.brain != null)
         * {
         * ESM_EntityAIBrainController.brain.Save();
         * }
         */
    }

    public static int getPortalTime(Entity entity) {
        return ObfuscationReflectionHelper.getPrivateValue(Entity.class, entity, "field_82153_h", "portalCounter");
    }

    public static boolean getInPortal(Entity entity) {
        return ObfuscationReflectionHelper.getPrivateValue(Entity.class, entity, "field_71087_bX", "inPortal");
    }

    public static void setInPortal(Entity entity, boolean value) {
        ObfuscationReflectionHelper.setPrivateValue(Entity.class, entity, value, "field_71087_bX", "inPortal");
    }

    public static boolean isNearSpawner(World world, int x, int y, int z) {
        for (int i = x - 5; i <= x + 5; i++) {
            for (int j = y - 5; j <= y + 5; j++) {
                for (int k = z - 5; k <= z + 5; k++) {
                    if (!world.getChunkProvider()
                        .chunkExists(i >> 4, k >> 4)) {
                        continue;
                    }
                    if (j < 0 || j > 255) continue;
                    if (world.getBlock(i, j, k) == Blocks.mob_spawner) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID.equals(ESM_Settings.ID)) {
            for (Configuration config : ESMGuiConfig.tempConfigs) {
                config.save();
            }

            ESM_Utils.UpdateBiomeSpawns();
        }
    }

    @SubscribeEvent
    public void allowDespawn(LivingSpawnEvent.AllowDespawn event) {
        if (!ESM_Settings.keepLoaded) return;

        // 64 格内锁定玩家 -> 不允许 despawn
        final double keepDistSq = 64D * 64D;

        if (event.entityLiving instanceof EntityCreature) {
            EntityCreature creature = (EntityCreature) event.entityLiving;
            Entity target = creature.getEntityToAttack();

            if (target instanceof EntityPlayer && target.isEntityAlive()) {
                if (creature.getDistanceSqToEntity(target) < keepDistSq) {
                    event.setResult(Result.DENY);
                    return;
                }
            }
        } else if (event.entityLiving instanceof EntityESMGhast) {
            EntityESMGhast ghast = (EntityESMGhast) event.entityLiving;
            Entity target = ghast.targetedEntity;

            if (target instanceof EntityPlayer && target.isEntityAlive()) {
                if (ghast.getDistanceSqToEntity(target) < keepDistSq) {
                    event.setResult(Result.DENY);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public void allowSpawn(LivingSpawnEvent.CheckSpawn event) {
        BiomeGenBase biome = event.world.getBiomeGenForCoords((int) event.x, (int) event.z);

        if (event.entityLiving instanceof EntityGhast
            && ESM_Settings.GhastDimensionBlacklist.contains(event.world.provider.dimensionId)
            && !ESM_Utils.nativeGhastBiomes.contains(biome)) {
            event.setResult(Result.DENY);
            return;
        } else if (event.entityLiving instanceof EntityBlaze
            && ESM_Settings.BlazeDimensionBlacklist.contains(event.world.provider.dimensionId)
            && !ESM_Utils.nativeBlazeBiomes.contains(biome)) {
                event.setResult(Result.DENY);
                return;
            }

        if (!(event.entityLiving instanceof EntityMob)) return;
        if (event.getResult() == Result.DENY) return;

        boolean hardDayFlag = false;
        int day = (int) (event.world.getWorldTime() / 24000);

        if (ESM_Settings.hardDay != 0 && day != 0 && day % ESM_Settings.hardDay == 0) {
            hardDayFlag = true;
        }

        if (!hardDayFlag && ESM_Settings.timedDifficulty > 0) {
            double p = event.world.getTotalWorldTime() / (ESM_Settings.timedDifficulty * 24000D);
            if (p < event.world.rand.nextFloat()) {
                event.setResult(Result.DENY);
                return;
            }
        } else if (!ESM_Settings.moreSpawning && !hardDayFlag) {
            return; // 不 вмеш动 vanilla
        }

        int i = MathHelper.floor_double(event.entityLiving.posX);
        int j = MathHelper.floor_double(event.entityLiving.boundingBox.minY);
        int k = MathHelper.floor_double(event.entityLiving.posZ);

        int l = event.world.getBlockLightValue(i, j, k);

        if (event.world.isThundering()) {
            int old = event.world.skylightSubtracted;
            try {
                event.world.skylightSubtracted = 10;
                l = event.world.getBlockLightValue(i, j, k);
            } finally {
                event.world.skylightSubtracted = old;
            }
        }

        if (hardDayFlag) l = 0;

        boolean ok = event.world.checkNoEntityCollision(event.entityLiving.boundingBox)
            && event.world.getCollidingBoundingBoxes(event.entityLiving, event.entityLiving.boundingBox)
                .isEmpty()
            && !event.world.isAnyLiquid(event.entityLiving.boundingBox)
            && ((EntityMob) event.entityLiving).getBlockPathWeight(i, j, k) >= 0.0F
            && l <= 7;

        // 关键：只在 DEFAULT 时提升为 ALLOW，避免抢其他 mod 的决定权
        if (ok && event.getResult() == Result.DEFAULT) {
            event.setResult(Result.ALLOW);
        }
    }

}
