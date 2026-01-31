package funwayguy.esm.ai;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.MathHelper;

import org.apache.commons.lang3.StringUtils;

import funwayguy.esm.core.ESM_Settings;

public abstract class ESM_EntityAITarget extends EntityAIBase {

    private int reachCacheTargetId = -1;
    private int reachCacheStatus = 0; // 0 unknown, 1 ok, 2 fail
    private long reachCacheUntil = 0;

    /** The entity that this task belongs to */
    protected EntityCreature taskOwner;
    /** If true, EntityAI targets must be able to be seen (cannot be blocked by walls) to be suitable targets. */
    protected boolean shouldCheckSight;
    /** When true, only entities that can be reached with minimal effort will be targetted. */
    private boolean nearbyOnly;
    /** When nearbyOnly is true: 0 -> No target, but OK to search; 1 -> Nearby target found; 2 -> Target too far. */
    private int field_75298_g;

    public ESM_EntityAITarget(EntityCreature p_i1669_1_, boolean p_i1669_2_) {
        this(p_i1669_1_, p_i1669_2_, false);
    }

    public ESM_EntityAITarget(EntityCreature p_i1670_1_, boolean p_i1670_2_, boolean p_i1670_3_) {
        this.taskOwner = p_i1670_1_;
        this.shouldCheckSight = p_i1670_2_;
        this.nearbyOnly = p_i1670_3_;
    }

    /**
     * Returns whether an in-progress EntityAIBase should continue executing
     */
    public boolean continueExecuting() {
        EntityLivingBase entitylivingbase = this.taskOwner.getAttackTarget();

        if (entitylivingbase == null) {
            return false;
        } else if (!entitylivingbase.isEntityAlive()) {
            return false;
        } else {
            double d0 = this.getTargetDistance();

            if (this.taskOwner.getDistanceSqToEntity(entitylivingbase) > d0 * d0) {
                return false;
            } else {
                if (this.shouldCheckSight && !ESM_Settings.isXrayAllowed(this.taskOwner.worldObj.getWorldTime())) {
                    if (this.taskOwner.getEntitySenses()
                        .canSee(entitylivingbase)) {
                        this.field_75298_g = 0;
                    } else if (++this.field_75298_g > 60) {
                        return false;
                    }
                }

                return !(entitylivingbase instanceof EntityPlayerMP)
                    || !((EntityPlayerMP) entitylivingbase).theItemInWorldManager.isCreative();
            }
        }
    }

    protected double getTargetDistance() {
        IAttributeInstance iattributeinstance = this.taskOwner.getEntityAttribute(SharedMonsterAttributes.followRange);
        return iattributeinstance == null ? 16.0D : iattributeinstance.getAttributeValue();
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting() {
        this.field_75298_g = 0;
    }

    /**
     * Resets the task
     */
    public void resetTask() {
        reachCacheTargetId = -1;
        reachCacheStatus = 0;
        reachCacheUntil = 0;
        this.taskOwner.setAttackTarget((EntityLivingBase) null);
    }

    /**
     * A method used to see if an entity is a suitable target through a number of checks.
     */
    protected boolean isSuitableTarget(EntityLivingBase p_75296_1_, boolean p_75296_2_) {
        if (p_75296_1_ == null) {
            return false;
        } else if (p_75296_1_ == this.taskOwner) {
            return false;
        } else if (!p_75296_1_.isEntityAlive()) {
            return false;
        } else if (!this.taskOwner.canAttackClass(p_75296_1_.getClass())) {
            return false;
        } else {
            if (this.taskOwner instanceof IEntityOwnable
                && StringUtils.isNotEmpty(((IEntityOwnable) this.taskOwner).func_152113_b())) {
                if (p_75296_1_ instanceof IEntityOwnable && ((IEntityOwnable) this.taskOwner).func_152113_b()
                    .equals(((IEntityOwnable) p_75296_1_).func_152113_b())) {
                    return false;
                }

                if (p_75296_1_ == ((IEntityOwnable) this.taskOwner).getOwner()) {
                    return false;
                }
            } else if (p_75296_1_ instanceof EntityPlayer && !p_75296_2_
                && ((EntityPlayer) p_75296_1_).capabilities.disableDamage) {
                    return false;
                }

            if (!this.taskOwner.isWithinHomeDistance(
                MathHelper.floor_double(p_75296_1_.posX),
                MathHelper.floor_double(p_75296_1_.posY),
                MathHelper.floor_double(p_75296_1_.posZ))) {
                return false;
            } else if (this.shouldCheckSight && !this.taskOwner.getEntitySenses()
                .canSee(p_75296_1_) && !ESM_Settings.isXrayAllowed(this.taskOwner.worldObj.getWorldTime())) {
                    return false;
                } else {
                    if ((this.nearbyOnly || ESM_Settings.QuickPathing)
                        && !(this.taskOwner instanceof EntityCreeper && ESM_Settings.CreeperBreaching)
                        && !(this.taskOwner instanceof EntityZombie && ESM_Settings.ZombieDiggers)) {
                        long now = this.taskOwner.worldObj.getTotalWorldTime();
                        int tid = p_75296_1_.getEntityId();

                        // 缓存命中且未过期：直接用
                        if (tid == reachCacheTargetId && now < reachCacheUntil) {
                            if (reachCacheStatus == 2) return false;
                        } else {
                            // 重新计算（昂贵）
                            boolean ok = this.canEasilyReach(p_75296_1_);
                            reachCacheTargetId = tid;
                            reachCacheStatus = ok ? 1 : 2;

                            // 过期时间：近的短一点，远的长一点（避免频繁寻路）
                            // 你也可以固定 10~20 tick
                            int dist = (int) this.taskOwner.getDistanceToEntity(p_75296_1_);
                            int ttl = ok ? (10 + Math.min(30, dist)) : 5; // 失败只缓存 5 tick
                            reachCacheUntil = now + ttl;

                            if (!ok) return false;
                        }
                    }

                    return true;
                }
        }
    }

    /**
     * Checks to see if this entity can find a short path to the given target.
     */
    private boolean canEasilyReach(EntityLivingBase target) {
        // 近距离别寻路否决，否则“贴脸也不主动打”
        if (this.taskOwner.getDistanceSqToEntity(target) <= 64.0D) // 8格
            return true;

        PathEntity path = this.taskOwner.getNavigator()
            .getPathToEntityLiving(target);
        if (path == null) return false;
        PathPoint end = path.getFinalPathPoint();
        if (end == null) return false;

        int dx = end.xCoord - MathHelper.floor_double(target.posX);
        int dz = end.zCoord - MathHelper.floor_double(target.posZ);
        return (dx * dx + dz * dz) <= 2.25D;
    }
}
