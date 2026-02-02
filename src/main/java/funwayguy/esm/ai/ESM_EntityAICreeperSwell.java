package funwayguy.esm.ai;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import funwayguy.esm.core.ESM_Settings;

public class ESM_EntityAICreeperSwell extends EntityAIBase {

    private final EntityCreeper swellingCreeper;
    private EntityLivingBase creeperAttackTarget;
    private final double triggerDistSqBase;
    private int detLockTicks = 0;
    private int diggerCheckCooldown = 0;
    private boolean cachedHasDigger = false;

    // ---- tunables ----
    private static final int LOCK_TICKS_PROX = 15; // proximity ignite lock
    private static final int LOCK_TICKS_BREACH = 8; // breaching lock to avoid flicker
    private static final double BREACH_MAX_Y_DIFF = 4.0D;
    private static final double RAYTRACE_DIST = 1.8D;

    // wall thickness policy
    private static final int WALL_MAX_CHECK = 4; // how far to probe
    private static final int WALL_MAX_THICKNESS = 3; // >3 blocks thick => don't breach

    public ESM_EntityAICreeperSwell(EntityCreeper creeper) {
        this.swellingCreeper = creeper;
        double base = (double) getCreeperRadius(creeper) + 0.5D;
        this.triggerDistSqBase = base * base;
        if (!ESM_Settings.CreeperChargers) {
            this.setMutexBits(1);
        }
    }

    public static int getCreeperRadius(EntityCreeper creeper) {
        int radius = 3;
        if (creeper.getEntityData()
            .hasKey("ExplosionRadius")) {
            radius = creeper.getEntityData()
                .getByte("ExplosionRadius");
        }
        return radius;
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = this.swellingCreeper.getAttackTarget();
        return checkBreachOrIgnite(target);
    }

    @Override
    public boolean continueExecuting() {
        EntityLivingBase target = this.swellingCreeper.getAttackTarget();
        return this.swellingCreeper.getCreeperState() > 0 || detLockTicks > 0 || checkBreachOrIgnite(target);
    }

    @Override
    public void startExecuting() {
        this.creeperAttackTarget = this.swellingCreeper.getAttackTarget();
        this.detLockTicks = 0;
    }

    @Override
    public void resetTask() {
        this.creeperAttackTarget = null;
        this.detLockTicks = 0;
        this.swellingCreeper.setCreeperState(-1);
    }

    @Override
    public void updateTask() {
        this.creeperAttackTarget = this.swellingCreeper.getAttackTarget();
        if (--diggerCheckCooldown <= 0) {
            diggerCheckCooldown = 20;
            cachedHasDigger = CheckForDiggers();
        }
        if (detLockTicks > 0) {
            detLockTicks--;
            if (ESM_Settings.CreeperChargers) {
                this.swellingCreeper.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 20, 1));
            }
            this.swellingCreeper.setCreeperState(1);
            return;
        }
        boolean ignite = checkBreachOrIgnite(this.creeperAttackTarget);
        if (ignite) {
            boolean breaching = (ESM_Settings.CreeperBreaching && isBreachingCondition(this.creeperAttackTarget));
            if (detLockTicks == 0) {
                detLockTicks = breaching ? LOCK_TICKS_BREACH : LOCK_TICKS_PROX;
            }
            this.swellingCreeper.setCreeperState(1);
        } else {
            this.swellingCreeper.setCreeperState(-1);
        }
    }

    private boolean checkBreachOrIgnite(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive()) return false;
        if (this.swellingCreeper.getCreeperState() > 0) return true;
        double distSq = this.swellingCreeper.getDistanceSqToEntity(target);
        double triggerSq = this.triggerDistSqBase * (ESM_Settings.CreeperChargers ? 2.0D : 1.0D);
        if (distSq <= triggerSq) return true;
        return ESM_Settings.CreeperBreaching && isBreachingCondition(target);
    }

    /**
     * Strict breaching:
     * - not newborn
     * - not mounted
     * - no digger zombie nearby
     * - close range only
     * - roughly same Y-level
     * - MUST be "noPath && cannotSee"
     * - raytrace hits a SIDE face block
     * - wall thickness check (along face normal into the wall)
     */
    private boolean isBreachingCondition(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive()) return false;

        if (swellingCreeper.ticksExisted < 60) return false;
        if (swellingCreeper.ridingEntity != null) return false;
        if (cachedHasDigger) return false;

        if (Math.abs(swellingCreeper.posY - target.posY) > BREACH_MAX_Y_DIFF) return false;
        if (!swellingCreeper.getNavigator()
            .noPath()) return false;
        if (swellingCreeper.getEntitySenses()
            .canSee(target)) return false;

        MovingObjectPosition mop = GetMovingObjectPosition(swellingCreeper, RAYTRACE_DIST, false);
        if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) return false;
        if (mop.sideHit == 0 || mop.sideHit == 1) return false;
        int thickness = getWallThicknessFromSideHit(swellingCreeper.worldObj, mop, WALL_MAX_CHECK);
        return thickness <= WALL_MAX_THICKNESS;
    }

    public boolean CheckForDiggers() {
        if (!ESM_Settings.ZombieDiggers) return false;

        List<EntityZombie> zombieList = this.swellingCreeper.worldObj
            .getEntitiesWithinAABB(EntityZombie.class, this.swellingCreeper.boundingBox.expand(10D, 10D, 10D));

        if (zombieList == null || zombieList.isEmpty()) return false;

        for (EntityZombie z : zombieList) {
            if (z == null || z.isDead) continue;

            ItemStack stack = z.getEquipmentInSlot(0);

            if (!ESM_Settings.ZombieDiggerTools) {
                return true;
            }

            if (stack != null && stack.getItem() != null) {
                if (stack.getItem() instanceof ItemPickaxe) return true;
                if (stack.getItem()
                    .canHarvestBlock(Blocks.stone, stack)) return true;
            }
        }

        return false;
    }

    public static MovingObjectPosition GetMovingObjectPosition(EntityLivingBase entity, double distance,
        boolean liquids) {
        float f = 1.0F;

        float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * f;
        float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * f;

        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * f;
        double y = entity.prevPosY + (entity.posY - entity.prevPosY) * f + entity.getEyeHeight();
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * f;

        Vec3 src = Vec3.createVectorHelper(x, y, z);

        float f3 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f4 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f5 = -MathHelper.cos(-pitch * 0.017453292F);
        float f6 = MathHelper.sin(-pitch * 0.017453292F);

        float dx = f4 * f5;
        float dz = f3 * f5;

        Vec3 dst = src.addVector(dx * distance, f6 * distance, dz * distance);

        return entity.worldObj.func_147447_a(src, dst, liquids, !liquids, false);
    }

    private static int getWallThicknessFromSideHit(World world, MovingObjectPosition hit, int maxCheck) {
        if (world == null || hit == null || hit.hitVec == null) return Integer.MAX_VALUE;

        int stepX = 0, stepY = 0, stepZ = 0;
        switch (hit.sideHit) {
            case 2:
                stepZ = -1;
                break;
            case 3:
                stepZ = 1;
                break;
            case 4:
                stepX = -1;
                break;
            case 5:
                stepX = 1;
                break;
            default:
                return Integer.MAX_VALUE;
        }

        int bx = hit.blockX;
        int by = hit.blockY;
        int bz = hit.blockZ;

        int thickness = 1;

        for (int i = 0; i < maxCheck; i++) {
            bx += stepX;
            by += stepY;
            bz += stepZ;

            Block block = world.getBlock(bx, by, bz);
            if (block == null) return thickness;
            if (block.isAir(world, bx, by, bz)) return thickness;
            Material m = block.getMaterial();
            if (m == null) return thickness;
            if (m.isReplaceable()) return thickness;
            if (!block.isOpaqueCube()) return thickness;
            thickness++;
        }

        return Integer.MAX_VALUE; // still solid after maxCheck => too thick
    }
}
