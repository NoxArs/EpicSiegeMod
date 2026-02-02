package funwayguy.esm.ai;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

import funwayguy.esm.core.ESM_Blocks;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAIPillarUp extends EntityAIBase {

    private final EntityLiving builder;
    private EntityLivingBase target;

    private int placeDelay = 0;

    private int targetX, targetY, targetZ;

    private static final ForgeDirection[] PLACE_SURFACE = new ForgeDirection[] { ForgeDirection.DOWN,
        ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST };

    private static final int PLACE_INTERVAL = 15; // 原版节奏
    private static final double CLOSE_DIST_SQ = 16.0D; // 4格水平距离平方
    private static final double MAX_Y_DIFF = 20.0D; // 你原来的上限

    public ESM_EntityAIPillarUp(EntityLiving entity) {
        this.builder = entity;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (ESM_Settings.ZombiePillaring <= 0) return false;
        if (builder.worldObj == null || builder.worldObj.isRemote) return false;
        if (ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed
            && !ESM_Utils.isSiegeAllowed(builder.worldObj.getWorldTime())) return false;

        target = builder.getAttackTarget();
        if (target == null || !target.isEntityAlive()) return false;

        // 目标必须更高，否则不需要搭
        double yDiff = target.posY - builder.posY;
        if (yDiff <= 0.5D) return false;
        if (yDiff > MAX_Y_DIFF) return false;

        // 只看水平距离，避免忽略 targetY 带来的误判
        double dx = target.posX - builder.posX;
        double dz = target.posZ - builder.posZ;
        double horizSq = dx * dx + dz * dz;

        boolean isClose = horizSq < CLOSE_DIST_SQ;
        boolean noPath = builder.getNavigator()
            .noPath();
        boolean stable = builder.onGround || builder.handleWaterMovement() || builder.handleLavaMovement();

        if (!noPath || !isClose || !stable) return false;

        return computePlacement();
    }

    @Override
    public void startExecuting() {
        placeDelay = PLACE_INTERVAL;
    }

    @Override
    public boolean continueExecuting() {
        if (builder.worldObj == null || builder.worldObj.isRemote) return false;
        if (target == null || !target.isEntityAlive()) return false;

        // 轻量维持条件：仍然无路且接近
        double dx = target.posX - builder.posX;
        double dz = target.posZ - builder.posZ;
        double horizSq = dx * dx + dz * dz;

        if (!builder.getNavigator()
            .noPath()) return false;
        if (horizSq >= CLOSE_DIST_SQ) return false;
        if (target.posY <= builder.posY + 0.5D) return false;

        return true;
    }

    @Override
    public void resetTask() {
        target = null;
        placeDelay = 0;
    }

    @Override
    public void updateTask() {
        if (builder.worldObj == null || builder.worldObj.isRemote) return;
        if (target == null || !target.isEntityAlive()) return;

        if (placeDelay > 0) {
            placeDelay--;
            return;
        }

        placeDelay = PLACE_INTERVAL;
        if (!computePlacement()) return;
        if (!isReplaceable(targetX, targetY, targetZ)) return;

        Block blockToPlace = (ESM_Blocks.zombieStep != null) ? ESM_Blocks.zombieStep : Blocks.cobblestone;
        builder.setPositionAndUpdate(targetX + 0.5D, targetY + 1.0D, targetZ + 0.5D);
        builder.motionY = 0.0D;
        if (isReplaceable(targetX, targetY, targetZ)) {
            builder.worldObj.setBlock(targetX, targetY, targetZ, blockToPlace);
        }

        // 重新寻路（原语义）
        builder.getNavigator()
            .setPath(
                builder.getNavigator()
                    .getPathToEntityLiving(target),
                1.0D);
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    // =========================================================
    // Placement compute (ported semantics, fixed)
    // =========================================================

    private boolean computePlacement() {
        int bx = MathHelper.floor_double(builder.posX);
        int by = MathHelper.floor_double(builder.posY);
        int bz = MathHelper.floor_double(builder.posZ);

        int tmpX = bx;
        int tmpY = by;
        int tmpZ = bz;

        int xOff = (int) Math.signum(MathHelper.floor_double(target.posX) - bx);
        int zOff = (int) Math.signum(MathHelper.floor_double(target.posZ) - bz);

        // sideways pillaring gate (same as you)
        if ((target.posY - builder.posY < 16.0D) && isNormalCube(tmpX, tmpY - 2, tmpZ)
            && isNormalCube(tmpX, tmpY - 1, tmpZ)) {
            if (xOff != 0 && isReplaceable(tmpX + xOff, tmpY - 1, tmpZ)) {
                tmpX += xOff;
                tmpY -= 1;
            } else if (zOff != 0 && isReplaceable(tmpX, tmpY - 1, tmpZ + zOff)) {
                tmpZ += zOff;
                tmpY -= 1;
            } else if (target.posY <= builder.posY) {
                return false;
            }
        } else if (target.posY <= builder.posY) {
            return false;
        }

        // FIX: canPlace must be checked at FINAL tmpX/tmpY/tmpZ
        if (!hasSupport(tmpX, tmpY, tmpZ)) return false;

        // headroom checks (keep your intent)
        if (blocksMovement(bx, by + 2, bz)) return false;
        if (blocksMovement(tmpX, tmpY + 2, tmpZ)) return false;

        targetX = tmpX;
        targetY = tmpY;
        targetZ = tmpZ;
        return true;
    }

    private boolean hasSupport(int x, int y, int z) {
        for (ForgeDirection dir : PLACE_SURFACE) {
            if (isNormalCube(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNormalCube(int x, int y, int z) {
        return builder.worldObj.getBlock(x, y, z)
            .isNormalCube();
    }

    private boolean blocksMovement(int x, int y, int z) {
        Material m = builder.worldObj.getBlock(x, y, z)
            .getMaterial();
        return m != null && m.blocksMovement();
    }

    private boolean isReplaceable(int x, int y, int z) {
        Block b = builder.worldObj.getBlock(x, y, z);
        if (b == null || b == Blocks.air) return true;

        Material m = b.getMaterial();
        return m != null && m.isReplaceable();
    }
}
