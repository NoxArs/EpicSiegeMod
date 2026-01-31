package funwayguy.esm.ai;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.MathHelper;

/**
 * ESM_EntityAISwimming (1.7.10)
 *
 * Goals:
 * 1) Work with mod fluids: don't rely on isInWater() only.
 * 2) Avoid "shoreline jitter": don't trigger just because AABB barely touches liquid.
 * 3) Throttle decisions: recompute every N ticks, but with stable countdown logic.
 * 4) Preserve your intent:
 *    - Oxygen critical => force up
 *    - If path/target wants down => do NOT jump (optionally nudge downward)
 *    - Otherwise => try to float up
 */
public class ESM_EntityAISwimming extends EntityAIBase
{
    private final EntityLiving host;

    // --- Throttle ---
    private int checkDelay = 0;
    private boolean shouldJump = false;

    // --- Tunables ---
    // Decision refresh period
    private static final int DECISION_PERIOD_TICKS = 5; // 0.25s

    // Oxygen threshold: <100 => force up (you can tune)
    private static final int AIR_CRITICAL = 100;

    // Y tolerance to avoid flip-flop between "down" and "up" due to grid rounding
    private static final double Y_TOL = 0.25D;

    // "Near target" distance threshold (8 blocks)
    private static final double NEAR_DIST_SQ = 64.0D;

    // Optional: when we decide "do not jump" (pursue downward), lightly bias motion downward.
    // This helps actually "dive" instead of just "not rising".
    private static final boolean ENABLE_DIVE_NUDGE = true;
    private static final double DIVE_NUDGE = 0.02D; // small, safe

    public ESM_EntityAISwimming(EntityLiving host)
    {
        this.host = host;
        // 4 = Jumping mutex; prevents conflict with other jump tasks
        this.setMutexBits(4);

        // Allow navigator to consider swimming paths
        host.getNavigator().setCanSwim(true);
    }

    @Override
    public boolean shouldExecute()
    {
        if (host == null || host.worldObj == null) return false;

        // Strong but stable liquid detection: sample at feet + mid-body
        if (!isInAnyLiquid(host)) return false;

        refreshDecisionIfNeeded();
        return shouldJump;
    }

    @Override
    public boolean continueExecuting()
    {
        if (host == null || host.worldObj == null) return false;

        // Must still be in liquid to keep running
        if (!isInAnyLiquid(host)) return false;

        refreshDecisionIfNeeded();
        return shouldJump;
    }

    @Override
    public void startExecuting()
    {
        // force immediate recompute when task starts
        checkDelay = 0;
        refreshDecisionIfNeeded();
    }

    @Override
    public void resetTask()
    {
        shouldJump = false;
        checkDelay = 0;
    }

    @Override
    public void updateTask()
    {
        if (shouldJump)
        {
            host.getJumpHelper().setJumping();
        }
        else if (ENABLE_DIVE_NUDGE)
        {
            // Only nudge down while in liquid; avoid pushing into ground when leaving water
            if (isInAnyLiquid(host))
            {
                // If already rising fast (e.g., water current), don't fight too hard
                if (host.motionY > -0.1D) host.motionY -= DIVE_NUDGE;
            }
        }
    }

    // =========================================================
    // Internals
    // =========================================================

    private void refreshDecisionIfNeeded()
    {
        if (checkDelay > 0)
        {
            checkDelay--;
            return;
        }

        checkDelay = DECISION_PERIOD_TICKS;
        shouldJump = computeJumpDecision();
    }

    /**
     * Robust liquid detection without shoreline jitter.
     * Instead of AABB-any-liquid, we sample two points:
     *  - feet (minY + 0.05)
     *  - mid body (minY + height*0.5)
     *
     * This avoids "touching water with a corner" triggering swim AI on land.
     */
    private static boolean isInAnyLiquid(EntityLiving e)
    {
        // Use bounding box minY for stable "foot level"
        double footY = e.boundingBox.minY + 0.05D;
        double midY  = e.boundingBox.minY + (e.height * 0.5D);

        int x = MathHelper.floor_double(e.posX);
        int z = MathHelper.floor_double(e.posZ);

        int yFoot = MathHelper.floor_double(footY);
        int yMid  = MathHelper.floor_double(midY);

        // Any liquid material at these samples -> treat as swimming
        return e.worldObj.getBlock(x, yFoot, z).getMaterial().isLiquid()
            || e.worldObj.getBlock(x, yMid,  z).getMaterial().isLiquid();
    }

    /**
     * Decision: true => jump/up, false => do not jump (allow dive / pursue down)
     */
    private boolean computeJumpDecision()
    {
        // Priority 0: oxygen critical
        if (host.getAir() < AIR_CRITICAL) return true;

        // Stable foot Y
        double hostFootY = host.boundingBox.minY;

        // Priority 1: path navigation wants down
        PathEntity path = host.getNavigator().getPath();
        if (path != null && !path.isFinished())
        {
            // Final point check
            PathPoint finalPoint = path.getFinalPathPoint();
            if (finalPoint != null)
            {
                // PathPoint yCoord is a block grid coordinate; use tolerance
                if (finalPoint.yCoord + Y_TOL < hostFootY)
                {
                    return false;
                }
            }

            // Next point check (more immediate)
            int idx = path.getCurrentPathIndex();
            if (idx >= 0 && idx < path.getCurrentPathLength())
            {
                PathPoint next = path.getPathPointFromIndex(idx);
                if (next != null && next.yCoord + Y_TOL < hostFootY)
                {
                    return false;
                }
            }
        }

        // Priority 2: combat pursuit wants down (only when close)
        EntityLivingBase target = host.getAttackTarget();
        if (target != null && target.isEntityAlive())
        {
            double distSq = host.getDistanceSqToEntity(target);
            if (distSq < NEAR_DIST_SQ)
            {
                // If target clearly below me, prefer dive (do not jump)
                if (target.boundingBox.minY + 0.5D < hostFootY)
                {
                    return false;
                }
            }
        }

        // Priority 3: default float up
        return true;
    }
}
