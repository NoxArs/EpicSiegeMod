package funwayguy.esm.handlers.entities;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

/**
 * ESM Enderman Handler (Optimized & Hardened) - FINAL
 *
 * Properties:
 * 1) Server-authoritative only (no client desync).
 * 2) Attribute modifiers are applied/remain idempotent (no spam sync).
 * 3) Look-away hysteresis prevents jitter between "stare" and "punish".
 * 4) Teleport uses pre-collision check (reduces rubber-banding) + safe player teleport.
 * 5) Vertical ground search is bounded (CPU safe in void / skyblock).
 * 6) Reflection is guarded (won't crash if field name differs).
 */
public class ESM_EndermanHandler {

    // ---- NBT Keys ----
    private static final String NBT_LOOKED_AWAY = "ESM_LOOKED_AWAY";
    private static final String NBT_STARE_TIMER = "ESM_STARETIMER";
    private static final String NBT_AWAY_TICKS = "ESM_AWAY_TICKS";
    private static final String NBT_TELEPORT_CD = "ESM_TELEPORT_CD";
    private static final String NBT_SLENDER_CD = "ESM_SLENDER_CD";

    // ---- Constants ----
    private static final int STARE_TRIGGER_TICKS = 6;
    private static final int STARE_SOUND_PERIOD = 20;

    private static final int LOOK_AWAY_HYSTERESIS = 10;

    private static final int SLENDER_PUNISH_CD = 40;
    private static final int TELEPORT_CD_TICKS = 40;

    private static final int TELEPORT_RANGE_H = 64;
    private static final int TELEPORT_RANGE_V = 32;

    private static final int MAX_GROUND_SEARCH = 32; // limit downward scan
    private static final double Y_EPS = 0.01D;

    // ---- Attributes ----
    // Operation 2: final *= (1 + amount)
    // -0.80 => 20% speed (very slow, not hard-freeze)
    private static final AttributeModifier SPEED_HALT_MOD = new AttributeModifier(
        UUID.fromString("020E0DFB-87AE-4653-9556-831010E291A2"),
        "esm_staring_speed_slow",
        -0.80D,
        2).setSaved(false);

    // +0.35 => 135% speed
    private static final AttributeModifier SPEED_BOOST_MOD = new AttributeModifier(
        UUID.fromString("020E0DFB-87AE-4653-9556-831010E291A0"),
        "esm_attack_speed_boost",
        0.35D,
        2).setSaved(false);

    // SRG + MCP
    private static final String[] FIELD_IS_AGGRESSIVE = new String[] { "field_104003_g", "isAggressive" };

    public static void onEntityJoinWorld(EntityEnderman enderman) {
        // No-op: keep hook for future init if needed
    }

    public static void onLivingUpdate(EntityEnderman enderman) {
        if (enderman == null || enderman.worldObj == null || enderman.worldObj.isRemote) return;

        // Tick cooldowns
        decNBT(enderman, NBT_TELEPORT_CD);
        decNBT(enderman, NBT_SLENDER_CD);

        EntityLivingBase curTarget = getAttackTarget(enderman);

        // ---- Has target ----
        if (curTarget != null && curTarget.isEntityAlive()) {
            if (!ESM_Settings.EndermanSlender) return;

            boolean isStaring = shouldAttackTarget(enderman, curTarget);

            if (isStaring) {
                // Player staring: "fear freeze"
                setBool(enderman, NBT_LOOKED_AWAY, false);
                setInt(enderman, NBT_AWAY_TICKS, 0);

                applySpeedMod(enderman, SPEED_HALT_MOD, true);
                applySpeedMod(enderman, SPEED_BOOST_MOD, false);

                setAggressiveSafe(enderman, true);
            } else {
                // Player not staring: build hysteresis before switching modes
                applySpeedMod(enderman, SPEED_HALT_MOD, false);

                int awayTicks = getInt(enderman, NBT_AWAY_TICKS);
                if (awayTicks < LOOK_AWAY_HYSTERESIS) setInt(enderman, NBT_AWAY_TICKS, awayTicks + 1);

                if (awayTicks + 1 >= LOOK_AWAY_HYSTERESIS) {
                    setBool(enderman, NBT_LOOKED_AWAY, true);
                }
            }

            // ---- Looked-away mode ----
            if (getBool(enderman, NBT_LOOKED_AWAY)) {
                // Chase boost
                applySpeedMod(enderman, SPEED_BOOST_MOD, true);

                // Debuffs on cooldown
                if (getInt(enderman, NBT_SLENDER_CD) <= 0) {
                    setInt(enderman, NBT_SLENDER_CD, SLENDER_PUNISH_CD);
                    safeAddDebuffs(curTarget);
                }

                // Teleport logic on cooldown
                double distSq = enderman.getDistanceSqToEntity(curTarget);
                if (distSq <= 64.0D && getInt(enderman, NBT_TELEPORT_CD) <= 0) {
                    // One roll: teleport triggers at most 5% per tick when close and cooldown ready
                    if (enderman.getRNG()
                        .nextInt(20) == 0) {
                        setInt(enderman, NBT_TELEPORT_CD, TELEPORT_CD_TICKS);

                        // Secondary roll decides WHO to teleport (player tele is rarer)
                        boolean didTeleport = false;
                        if (ESM_Settings.EndermanPlayerTele && enderman.getRNG()
                            .nextInt(4) == 0) {
                            didTeleport = teleportTargetRandomly(curTarget);
                        } else {
                            didTeleport = teleportTargetRandomly(enderman);
                        }

                        if (didTeleport) {
                            enderman.worldObj.playSoundAtEntity(enderman, "ambient.cave.cave", 1.0F, 0.5F);
                        }
                    }
                } else if (enderman.getRNG()
                    .nextInt(100) == 0) {
                        enderman.worldObj.playSoundAtEntity(enderman, "ambient.cave.cave", 1.0F, 0.5F);
                    }
            } else {
                // Ensure boost off if not in looked-away mode
                applySpeedMod(enderman, SPEED_BOOST_MOD, false);
            }

            return;
        }

        // ---- No target: cleanup + reacquire ----
        setBool(enderman, NBT_LOOKED_AWAY, false);
        setInt(enderman, NBT_AWAY_TICKS, 0);

        applySpeedMod(enderman, SPEED_HALT_MOD, false);
        applySpeedMod(enderman, SPEED_BOOST_MOD, false);

        EntityLivingBase newTarget = getValidTarget(enderman);
        if (newTarget != null) {
            enderman.setTarget(newTarget);
        }
    }

    // =========================================================
    // Targeting
    // =========================================================

    private static EntityLivingBase getAttackTarget(EntityEnderman e) {
        Entity ent = e.getEntityToAttack();
        return (ent instanceof EntityLivingBase) ? (EntityLivingBase) ent : null;
    }

    public static EntityLivingBase getValidTarget(EntityEnderman enderman) {
        EntityLivingBase target = ESM_Utils.GetNearestValidTarget(enderman);
        if (target == null || !target.isEntityAlive()) return null;

        if (!shouldAttackTarget(enderman, target)) {
            setInt(enderman, NBT_STARE_TIMER, 0);
            return null;
        }

        int stare = getInt(enderman, NBT_STARE_TIMER);

        if (stare == 0 || (stare % STARE_SOUND_PERIOD) == 0) {
            enderman.worldObj.playSoundAtEntity(target, "mob.endermen.stare", 1.0F, 1.0F);
        }

        stare++;
        if (stare >= STARE_TRIGGER_TICKS) {
            setInt(enderman, NBT_STARE_TIMER, 0);
            enderman.setScreaming(true);
            setAggressiveSafe(enderman, true);
            return target;
        }

        setInt(enderman, NBT_STARE_TIMER, stare);
        return null;
    }

    public static boolean shouldAttackTarget(EntityEnderman enderman, EntityLivingBase target) {
        // Pumpkin helmet blocks stare aggression
        ItemStack helmet = target.getEquipmentInSlot(4);
        if (helmet != null && helmet.getItem() == Item.getItemFromBlock(Blocks.pumpkin)) return false;

        Vec3 look = target.getLook(1.0F)
            .normalize();

        Vec3 toEnderman = Vec3.createVectorHelper(
            enderman.posX - target.posX,
            enderman.boundingBox.minY + (enderman.height * 0.5F) - (target.posY + target.getEyeHeight()),
            enderman.posZ - target.posZ);

        double dist = toEnderman.lengthVector();
        if (dist < 1.0E-4D) return false; // prevent divide-by-zero / NaN

        toEnderman = toEnderman.normalize();
        double dot = look.dotProduct(toEnderman);

        double threshold = 1.0D - 0.025D / dist;

        // Slender mode: easier to trigger when close (< 32 blocks)
        if (ESM_Settings.EndermanSlender && enderman.getDistanceSqToEntity(target) < 1024.0D) {
            threshold = 0.85D - 0.025D / dist;
        }

        return dot > threshold && enderman.canEntityBeSeen(target);
    }

    // =========================================================
    // Teleport Logic (Pre-check + bounded ground search)
    // =========================================================

    public static boolean teleportTargetRandomly(EntityLivingBase target) {
        double x = target.posX + (target.getRNG()
            .nextDouble() - 0.5D) * TELEPORT_RANGE_H;
        double z = target.posZ + (target.getRNG()
            .nextDouble() - 0.5D) * TELEPORT_RANGE_H;
        double yBase = target.posY + (target.getRNG()
            .nextInt(TELEPORT_RANGE_V * 2) - TELEPORT_RANGE_V);

        return teleportTargetTo(target, x, yBase, z);
    }

    public static boolean teleportTargetTo(EntityLivingBase target, double x, double yBase, double z) {
        World w = target.worldObj;
        if (w == null) return false;

        int i = MathHelper.floor_double(x);
        int k = MathHelper.floor_double(z);
        int j = MathHelper.floor_double(yBase);

        // Find ground with bounded search
        double finalY = -1;
        boolean foundGround = false;

        for (int d = 0; d < MAX_GROUND_SEARCH && j > 0; d++) {
            Block below = w.getBlock(i, j - 1, k);
            if (below.getMaterial()
                .blocksMovement()) {
                foundGround = true;
                finalY = j + Y_EPS;
                break;
            }
            j--;
        }

        if (!foundGround) return false;

        // Pre-collision check using a dummy box before moving entity (reduces jitter)
        double halfW = target.width * 0.5D;
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(x - halfW, finalY, z - halfW, x + halfW, finalY + target.height, z + halfW);

        if (!w.getCollidingBoundingBoxes(target, box)
            .isEmpty()) return false;
        if (w.isAnyLiquid(box)) return false;

        // Execute teleport (server-safe)
        return setPositionServerSafe(target, x, finalY, z);
    }

    private static boolean setPositionServerSafe(EntityLivingBase e, double x, double y, double z) {
        // Dismount both directions to avoid sync weirdness
        try {
            if (e.ridingEntity != null) e.mountEntity(null);
            if (e.riddenByEntity != null) e.riddenByEntity.mountEntity(null);
        } catch (Throwable t) {
            // ignore
        }

        // Sound at old location
        e.worldObj.playSoundEffect(e.posX, e.posY, e.posZ, "mob.endermen.portal", 1.0F, 1.0F);

        if (e instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) e;
            if (p.playerNetServerHandler != null) {
                p.playerNetServerHandler.setPlayerLocation(x, y, z, p.rotationYaw, p.rotationPitch);
            } else {
                // fallback
                e.setPositionAndUpdate(x, y, z);
            }
        } else {
            e.setPositionAndUpdate(x, y, z);
        }

        // Sound at new location
        e.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 1.0F, 1.0F);

        // Particles (server-side)
        if (e.worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) e.worldObj;
            // func_147487_a = spawnParticle(String, x, y, z, count, dx, dy, dz, speed)
            ws.func_147487_a("portal", x, y + e.height * 0.5D, z, 32, 0.5D, 0.5D, 0.5D, 0.0D);
        }

        return true;
    }

    // =========================================================
    // Attribute Logic (Idempotent)
    // =========================================================

    private static void applySpeedMod(EntityLivingBase e, AttributeModifier mod, boolean enable) {
        IAttributeInstance attr = e.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (attr == null) return;

        boolean has = (attr.getModifier(mod.getID()) != null);

        if (enable && !has) {
            attr.applyModifier(mod);
        } else if (!enable && has) {
            attr.removeModifier(mod);
        }
    }

    // =========================================================
    // Debuffs (Safe)
    // =========================================================

    private static void safeAddDebuffs(EntityLivingBase target) {
        try {
            target.addPotionEffect(new PotionEffect(Potion.blindness.id, 100, 0));
            target.addPotionEffect(new PotionEffect(Potion.hunger.id, 100, 0));
        } catch (Throwable t) {
            // ignore
        }
    }

    // =========================================================
    // Reflection
    // =========================================================

    private static void setAggressiveSafe(EntityEnderman e, boolean aggressive) {
        try {
            // ObfuscationReflectionHelper supports varargs of names; try SRG then MCP.
            ObfuscationReflectionHelper.setPrivateValue(EntityEnderman.class, e, aggressive, FIELD_IS_AGGRESSIVE);
        } catch (Throwable t) {
            // ignore
        }
    }

    // =========================================================
    // NBT utils
    // =========================================================

    private static boolean getBool(Entity e, String key) {
        return e.getEntityData()
            .getBoolean(key);
    }

    private static void setBool(Entity e, String key, boolean v) {
        e.getEntityData()
            .setBoolean(key, v);
    }

    private static int getInt(Entity e, String key) {
        return e.getEntityData()
            .getInteger(key);
    }

    private static void setInt(Entity e, String key, int v) {
        e.getEntityData()
            .setInteger(key, v);
    }

    private static void decNBT(Entity e, String key) {
        int v = e.getEntityData()
            .getInteger(key);
        if (v > 0) e.getEntityData()
            .setInteger(key, v - 1);
    }
}
