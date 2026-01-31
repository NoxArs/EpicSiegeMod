package funwayguy.esm.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.ai.EntityAIArrowAttack;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.MathHelper;

import funwayguy.esm.core.ESM_Settings;

public class ESM_EntityAIArrowAttack extends EntityAIArrowAttack {

    /** The entity the AI instance has been applied to */
    private final EntityLiving entityHost;
    /** The entity (as a RangedAttackMob) the AI instance has been applied to. */
    private final IRangedAttackMob rangedAttackEntityHost;
    private EntityLivingBase attackTarget;

    private int rangedAttackTime;
    private double entityMoveSpeed;
    private int field_75318_f;
    private int field_96561_g;
    private int maxRangedAttackTime;
    private float field_96562_i;
    private float field_82642_h;

    // Path refresh cooldown (ticks)
    private int pathRefresh = 0;

    public ESM_EntityAIArrowAttack(IRangedAttackMob p_i1649_1_, double p_i1649_2_, int p_i1649_4_, float p_i1649_5_) {
        this(p_i1649_1_, p_i1649_2_, p_i1649_4_, p_i1649_4_, p_i1649_5_);
    }

    public ESM_EntityAIArrowAttack(IRangedAttackMob p_i1650_1_, double p_i1650_2_, int p_i1650_4_, int p_i1650_5_,
        float p_i1650_6_) {
        super(p_i1650_1_, p_i1650_2_, p_i1650_4_, p_i1650_5_, p_i1650_6_);

        this.rangedAttackTime = -1;

        if (!(p_i1650_1_ instanceof EntityLivingBase)) {
            throw new IllegalArgumentException("ArrowAttackGoal requires Mob implements RangedAttackMob");
        } else {
            this.rangedAttackEntityHost = p_i1650_1_;
            this.entityHost = (EntityLiving) p_i1650_1_;
            this.entityMoveSpeed = p_i1650_2_;
            this.field_96561_g = p_i1650_4_;
            this.maxRangedAttackTime = p_i1650_5_;
            this.field_96562_i = p_i1650_6_;
            this.field_82642_h = p_i1650_6_ * p_i1650_6_;
            this.setMutexBits(3);
        }
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = this.entityHost.getAttackTarget();
        if (t == null || t.isDead) return false;
        this.attackTarget = t;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        // Avoid calling shouldExecute() here (it has side effects).
        // Continue if we still have a valid target OR we are still navigating a path to something.
        if (this.attackTarget != null && !this.attackTarget.isDead) return true;
        return !this.entityHost.getNavigator()
            .noPath();
    }

    @Override
    public void resetTask() {
        this.attackTarget = null;
        this.field_75318_f = 0;
        this.rangedAttackTime = -1;
        this.pathRefresh = 0; // important: reset our cooldown
    }

    @Override
    public void updateTask() {
        // ---- Hard safety: target may become invalid between ticks ----
        if (this.attackTarget == null || this.attackTarget.isDead) {
            // Try to reacquire from host; if still none, stop cleanly
            EntityLivingBase t = this.entityHost.getAttackTarget();
            if (t == null || t.isDead) {
                this.resetTask();
                return;
            }
            // Target changed / reacquired: reset soft state for stability
            this.attackTarget = t;
            this.field_75318_f = 0;
            this.rangedAttackTime = -1;
            this.pathRefresh = 0;
        }

        final double d0 = this.entityHost
            .getDistanceSq(this.attackTarget.posX, this.attackTarget.boundingBox.minY, this.attackTarget.posZ);

        boolean canSee = this.entityHost.getEntitySenses()
            .canSee(this.attackTarget);

        // "Close" should encourage chasing, not pretend we can see through walls.
        final boolean isClose = this.entityHost.getDistanceToEntity(this.attackTarget) <= 12.0F;

        // ---- Expensive LOS entity raycast: only do it when vanilla says we can see (best perf win) ----
        if (canSee) {
            Entity los = AIUtils.RayCastEntities(
                entityHost.worldObj,
                entityHost.posX,
                entityHost.posY + entityHost.getEyeHeight(),
                entityHost.posZ,
                entityHost.rotationYawHead,
                entityHost.rotationPitch,
                8F,
                this.entityHost);

            if (los != null && los != attackTarget && (ESM_Settings.ambiguous_AI || los instanceof IMob)) {
                canSee = false;
            }
        }

        // Track visibility time strictly by canSee (not isClose).
        if (canSee) {
            ++this.field_75318_f;
        } else {
            this.field_75318_f = 0;
        }

        // Stop moving only when in range AND have had stable sight for a short time.
        if (d0 <= (double) this.field_82642_h && this.field_75318_f >= 5) {
            this.entityHost.getNavigator()
                .clearPathEntity();
        } else {
            // Refresh path periodically (fixes original "only when getPath()==null" bug).
            if (--this.pathRefresh <= 0) {
                this.pathRefresh = 20;

                // If we can't see but we're close, still try to reposition towards target.
                // If we can't see and we're not close, also chase (vanilla-ish behavior).
                this.entityHost.getNavigator()
                    .tryMoveToEntityLiving(this.attackTarget, this.entityMoveSpeed);
            }
        }

        this.entityHost.getLookHelper()
            .setLookPositionWithEntity(this.attackTarget, 30.0F, 30.0F);

        // ---- Ranged attack timing ----
        if (--this.rangedAttackTime == 0) {
            if (d0 > (double) this.field_82642_h || !canSee) {
                return;
            }

            float f = MathHelper.sqrt_double(d0) / this.field_96562_i;
            float f1 = f;

            if (f1 < 0.1F) f1 = 0.1F;
            if (f1 > 1.0F) f1 = 1.0F;

            this.rangedAttackEntityHost.attackEntityWithRangedAttack(this.attackTarget, f1);
            this.rangedAttackTime = MathHelper
                .floor_float(f * (float) (this.maxRangedAttackTime - this.field_96561_g) + (float) this.field_96561_g);
        } else if (this.rangedAttackTime < 0) {
            float f = MathHelper.sqrt_double(d0) / this.field_96562_i;
            this.rangedAttackTime = MathHelper
                .floor_float(f * (float) (this.maxRangedAttackTime - this.field_96561_g) + (float) this.field_96561_g);
        }
    }
}
