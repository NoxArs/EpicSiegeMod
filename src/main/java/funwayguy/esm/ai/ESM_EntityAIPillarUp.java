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

/**
 * EpicSiege 1.7.10 - Pillar Up AI (hardened)
 *
 * Key behavior:
 * - Only triggers on server
 * - Requires target above by >= 2 blocks (grid-based)
 * - Requires stuck state (two-stage: path/collision failure + stagnation)
 * - Limits blocks placed per run using ESM_Settings.ZombiePillaring
 * - Validates placement again at update time to avoid desync/race
 * - Avoids "face-hug jitter" by refusing when too close
 */
public class ESM_EntityAIPillarUp extends EntityAIBase {

    /** Potential surfaces zombies can initialise pillaring on */
    static final ForgeDirection[] placeSurface = new ForgeDirection[] { ForgeDirection.DOWN, ForgeDirection.NORTH,
        ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST };

    public int placeDelay = 15;
    public EntityLiving builder;
    public EntityLivingBase target;

    private int blockX = 0;
    private int blockY = 0;
    private int blockZ = 0;

    // --- anti-jitter / anti-frenzy state ---
    private double lastX, lastZ;
    private int stagnantTicks; // ticks with near-zero horizontal movement
    private int failPathTicks; // ticks where nav is failing or colliding
    private int runTicks; // ticks since startExecuting
    private int placed; // blocks placed in this run

    public ESM_EntityAIPillarUp(EntityLiving entity) {
        this.builder = entity;
        // movement-ish AI; adjust if you have your own mutex scheme
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        // Server-only. (1.7.10 AI generally server-side, but keep it explicit)
        if (builder.worldObj == null || builder.worldObj.isRemote) return false;

        // Feature off
        if (ESM_Settings.ZombiePillaring <= 0) return false;

        // Siege window gate
        if (ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed) {
            if (!ESM_Utils.isSiegeAllowed(builder.worldObj.getWorldTime())) return false;
        }

        // Acquire target
        target = builder.getAttackTarget();
        if (target == null || !target.isEntityAlive()) return false;

        // Height gating (use bbox.minY for better ground truth)
        int by = MathHelper.floor_double(builder.boundingBox.minY);
        int ty = MathHelper.floor_double(target.boundingBox.minY);
        int dy = ty - by;

        // Require meaningful vertical advantage (prevents stair/half-slab spam)
        if (dy < 2) return false;

        // Optional: don't try to pillar absurd heights
        if (dy > 16) return false;

        // Horizontal distance gating (2D)
        double dx = target.posX - builder.posX;
        double dz = target.posZ - builder.posZ;
        double dist2 = dx * dx + dz * dz;

        // Must be within 8 blocks (64 = 8^2)
        if (dist2 >= 64.0D) return false;

        // Too close causes "face-hug jitter" and collision false positives
        if (dist2 <= 2.25D) return false; // ~1.5 blocks

        // Stuck detection (two-stage)
        if (!isStuck(builder)) return false;

        // Determine candidate placement position
        int i = MathHelper.floor_double(builder.posX);
        int j = MathHelper.floor_double(builder.posY);
        int k = MathHelper.floor_double(builder.posZ);

        int origI = i, origJ = j, origK = k;

        int xOff = (int) Math.signum(MathHelper.floor_double(target.posX) - origI);
        int zOff = (int) Math.signum(MathHelper.floor_double(target.posZ) - origK);

        // If target aligns perfectly in grid terms, sideways logic degenerates
        if (xOff == 0 && zOff == 0) return false;

        // Sideways pillaring: step sideways at y-1 if supported
        if ((target.posY - builder.posY) < 16.0D && isNormalCube(origI, origJ - 2, origK)
            && isNormalCube(origI, origJ - 1, origK)) {
            if (isReplaceable(origI + xOff, origJ - 1, origK)) {
                i = origI + xOff;
                j = origJ - 1;
                k = origK;
            } else if (isReplaceable(origI, origJ - 1, origK + zOff)) {
                i = origI;
                j = origJ - 1;
                k = origK + zOff;
            } else {
                return false;
            }
        }

        // Placement feasibility at chosen i/j/k
        if (!canPlaceAt(i, j, k, origI, origJ, origK)) return false;

        blockX = i;
        blockY = j;
        blockZ = k;

        return true;
    }

    @Override
    public void startExecuting() {
        placeDelay = 15;

        runTicks = 0;
        placed = 0;

        stagnantTicks = 0;
        failPathTicks = 0;

        lastX = builder.posX;
        lastZ = builder.posZ;
    }

    @Override
    public boolean continueExecuting() {
        if (builder.worldObj == null || builder.worldObj.isRemote) return false;
        if (target == null || !target.isEntityAlive()) return false;

        // Hard stops to prevent endless world-staircase and infinite AI loops
        if (placed >= ESM_Settings.ZombiePillaring) return false;
        if (runTicks >= 200) return false; // ~10 seconds

        return true;
    }

    @Override
    public void resetTask() {
        target = null;

        runTicks = 0;
        placed = 0;

        stagnantTicks = 0;
        failPathTicks = 0;
    }

    @Override
    public void updateTask() {
        runTicks++;

        if (target == null || !target.isEntityAlive()) return;
        if (placed >= ESM_Settings.ZombiePillaring) return;

        if (placeDelay > 0) {
            placeDelay--;
            return;
        }
        placeDelay = 15;

        // Re-validate placement now (world may have changed since shouldExecute)
        if (!canStillPlace(blockX, blockY, blockZ)) {
            // End this run cleanly
            placed = ESM_Settings.ZombiePillaring;
            return;
        }

        // Place block (prefer zombieStep, fallback to cobble if null/unavailable)
        Block placeBlock = (ESM_Blocks.zombieStep != null) ? ESM_Blocks.zombieStep : Blocks.cobblestone;

        builder.worldObj.setBlock(blockX, blockY, blockZ, placeBlock);
        placed++;

        // Only teleport if we're actually below the step; reduces "blink spam"
        if (builder.posY < (blockY + 0.9D)) {
            builder.setPositionAndUpdate(blockX + 0.5D, blockY + 1D, blockZ + 0.5D);
        }

        // Try re-pathing to target
        builder.getNavigator()
            .setPath(
                builder.getNavigator()
                    .getPathToEntityLiving(target),
                1D);
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    // ---------------- helpers ----------------

    private boolean isStuck(EntityLiving e) {
        // Horizontal movement delta
        double dxm = e.posX - lastX;
        double dzm = e.posZ - lastZ;

        // ~0.02 blocks/tick threshold squared (0.02^2 = 0.0004)
        if (dxm * dxm + dzm * dzm < 0.0004D) stagnantTicks++;
        else stagnantTicks = 0;

        lastX = e.posX;
        lastZ = e.posZ;

        boolean navNoPath = e.getNavigator()
            .noPath();
        boolean collided = e.isCollidedHorizontally;

        // Fail-path ticks only accumulate when navigation is not progressing
        if (navNoPath || collided) failPathTicks++;
        else failPathTicks = 0;

        // Two-stage: must be failing for ~1s AND not moving for ~1.5s
        return (failPathTicks >= 20) && (stagnantTicks >= 30);
    }

    private boolean canPlaceAt(int i, int j, int k, int origI, int origJ, int origK) {
        // must not suffocate at current or original position when we step up
        if (blocksMovement(origI, origJ + 2, origK)) return false;
        if (blocksMovement(i, j + 2, k)) return false;

        // must have some supporting normal cube adjacent (or below)
        for (ForgeDirection dir : placeSurface) {
            if (isNormalCube(i + dir.offsetX, j + dir.offsetY, k + dir.offsetZ)) {
                return true;
            }
        }
        return false;
    }

    private boolean canStillPlace(int x, int y, int z) {
        // target block must be replaceable
        if (!isReplaceable(x, y, z)) return false;

        // ensure headroom above the placed block (avoid teleport into block)
        if (blocksMovement(x, y + 1, z)) return false;
        if (blocksMovement(x, y + 2, z)) return false;

        // must still have a supporting face
        for (ForgeDirection dir : placeSurface) {
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
        Material m = builder.worldObj.getBlock(x, y, z)
            .getMaterial();
        return m != null && m.isReplaceable();
    }
}
