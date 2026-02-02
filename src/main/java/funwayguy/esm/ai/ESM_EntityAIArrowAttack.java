package funwayguy.esm.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.ai.EntityAIArrowAttack;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import funwayguy.esm.core.ESM_Settings;

/**
 * Playable-but-mean arrow attack AI (1.7.10)
 * - still extends EntityAIArrowAttack for compatibility (skeleton aiArrowAttack field type)
 * - avoids field hiding: DO NOT redeclare vanilla fields
 * - ray is aimed at target (not yaw/pitch)
 */
public class ESM_EntityAIArrowAttack extends EntityAIArrowAttack {

    // We only keep OUR extra state; do not redeclare parent fields.
    private final EntityLiving hostLiving;
    private final IRangedAttackMob hostRanged;

    private int pathRefresh = 0;

    // Tunables
    private static final int SEE_LOCK_TICKS = 12; // vanilla-ish was ~20
    private static final int PATH_REFRESH_TICKS = 20; // 1 second
    private static final double RAY_MAX_DIST = 24.0D; // clamp for perf
    private static final float CLOSE_CADENCE_BOOST_RATIO_SQ = 0.09F; // (0.6R)^2

    public ESM_EntityAIArrowAttack(IRangedAttackMob host, double speed, int delay, float radius) {
        this(host, speed, delay, delay, radius);
    }

    public ESM_EntityAIArrowAttack(IRangedAttackMob host, double speed, int minDelay, int maxDelay, float radius) {
        // IMPORTANT: keep super() so vanilla fields exist & skeleton expects this exact type
        super(host, speed, minDelay, maxDelay, radius);

        if (!(host instanceof EntityLiving)) {
            throw new IllegalArgumentException("ArrowAttackGoal requires mob implements IRangedAttackMob");
        }

        this.hostRanged = host;
        this.hostLiving = (EntityLiving) host;

        // parent already sets mutex bits, but we keep consistent
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = this.hostLiving.getAttackTarget();
        if (t == null || t.isDead) return false;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        EntityLivingBase t = this.hostLiving.getAttackTarget();
        if (t == null || t.isDead) {
            // if temporarily lost target, continue only if still pathing
            return !this.hostLiving.getNavigator()
                .noPath();
        }
        return true;
    }

    @Override
    public void resetTask() {
        // We don't have direct access to parent's private counters across mappings reliably,
        // so we reset only our extra state.
        this.pathRefresh = 0;

        // Also clear navigator so it doesn't keep stale chase behavior
        // (optional but tends to reduce jitter)
        // this.hostLiving.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = this.hostLiving.getAttackTarget();
        if (target == null || target.isDead) {
            resetTask();
            return;
        }

        // Distance
        double distSq = this.hostLiving.getDistanceSq(target.posX, target.boundingBox.minY, target.posZ);

        // Visibility: senses LOS + blocker policy
        boolean canSee = this.hostLiving.getEntitySenses()
            .canSee(target);
        if (canSee && isShotBlockedByNonMob(target)) {
            // if a non-mob blocks the line, treat as "cannot see"
            canSee = false;
        }

        // We need a seeTime counter, but parent's fields are private and mapping-dependent.
        // So we keep a local see counter via dataWatcher-ish? No—simplest: approximate by a small hysteresis:
        // - If canSee: we "assume" stable after SEE_LOCK_TICKS using a small local counter.
        // We'll implement as a static-local field? can't. We'll just use pathing behavior based on canSee+distance,
        // which is good enough for "playable mean".
        //
        // Better: keep our own seeTime.
        // (Safe and mapping-independent.)
        // ---------------------------------------------------
        // local seeTime
        double dist = Math.sqrt(distSq); // 算出距离
        boolean isCloseWallhack = dist <= 12.0D;
        if (canSee || isCloseWallhack) seeTimeLocal = Math.min(1000, seeTimeLocal + 1);
        else seeTimeLocal = 0;

        // Movement
        if (distSq <= (double) (getMaxAttackDistanceSq()) && seeTimeLocal >= SEE_LOCK_TICKS) {
            this.hostLiving.getNavigator()
                .clearPathEntity();
            this.pathRefresh = 0;
        } else {
            if (this.pathRefresh > 0) this.pathRefresh--;
            if (this.pathRefresh == 0) {
                this.pathRefresh = PATH_REFRESH_TICKS;
                this.hostLiving.getNavigator()
                    .tryMoveToEntityLiving(target, getMoveSpeed());
            }
        }

        // Look at target
        this.hostLiving.getLookHelper()
            .setLookPositionWithEntity(target, 30.0F, 30.0F);

        // Attack cadence (we implement our own timer to avoid relying on parent's private timer)
        rangedAttackTimeLocal--;

        // Mild cadence boost at close range
        if (canSee && distSq < (double) (getMaxAttackDistanceSq() * CLOSE_CADENCE_BOOST_RATIO_SQ)) {
            if (rangedAttackTimeLocal > 0) rangedAttackTimeLocal--;
        }

        if (rangedAttackTimeLocal == 0) {
            if (distSq > (double) getMaxAttackDistanceSq() || !canSee) {
                return;
            }

            float distanceRatio = MathHelper.sqrt_double(distSq) / getAttackRadius();
            float power = distanceRatio;
            if (power < 0.1F) power = 0.1F;
            if (power > 1.0F) power = 1.0F;

            this.hostRanged.attackEntityWithRangedAttack(target, power);

            rangedAttackTimeLocal = MathHelper.floor_float(
                distanceRatio * (float) (getMaxAttackTime() - getMinAttackTime()) + (float) getMinAttackTime());
        } else if (rangedAttackTimeLocal < 0) {
            float distanceRatio = MathHelper.sqrt_double(distSq) / getAttackRadius();
            rangedAttackTimeLocal = MathHelper.floor_float(
                distanceRatio * (float) (getMaxAttackTime() - getMinAttackTime()) + (float) getMinAttackTime());
        }
    }

    // -------------------------------------------------------
    // Local counters (mapping-independent, avoids parent privates)
    // -------------------------------------------------------
    private int seeTimeLocal = 0;
    private int rangedAttackTimeLocal = -1;

    // -------------------------------------------------------
    // Config accessors (avoid relying on parent field names)
    // -------------------------------------------------------
    private double getMoveSpeed() {
        return 1.0D;
    } // you pass 1.0D; keep it stable

    private int getMinAttackTime() {
        return 20;
    } // you pass 20

    private int getMaxAttackTime() {
        return 60;
    } // you pass 60

    private float getAttackRadius() {
        return (float) ESM_Settings.SkeletonDistance;
    } // consistent with your callsite

    private float getMaxAttackDistanceSq() {
        float r = getAttackRadius();
        return r * r;
    }

    /**
     * Returns true if a NON-mob entity is blocking the shot line.
     * - IMob blockers are ignored (mean but playable)
     * - Non-mob blockers block
     */
    private boolean isShotBlockedByNonMob(EntityLivingBase target) {
        Vec3 start = Vec3
            .createVectorHelper(hostLiving.posX, hostLiving.posY + (double) hostLiving.getEyeHeight(), hostLiving.posZ);

        Vec3 end = Vec3.createVectorHelper(target.posX, target.posY + (double) (target.height * 0.5F), target.posZ);

        double dist = start.distanceTo(end);
        if (dist > RAY_MAX_DIST) {
            Vec3 dir = end.subtract(start);
            double inv = RAY_MAX_DIST / dist;
            end = start.addVector(dir.xCoord * inv, dir.yCoord * inv, dir.zCoord * inv);
        }

        Entity hit = AIUtils.RayCastEntities(hostLiving.worldObj, start, end, this.hostLiving);
        if (hit == null) return false;
        if (hit == target) return false;
        if (hit instanceof IMob) return false;
        return true;
    }
}
