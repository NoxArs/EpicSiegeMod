package funwayguy.esm.ai;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import funwayguy.esm.AnimalDamageSource;
import funwayguy.esm.core.ESM_Settings;

public class ESM_EntityAIAttackOnCollide extends EntityAIAttackOnCollide {

    World worldObj;
    EntityCreature attacker;
    /** An amount of decrementing ticks that allows the entity to attack once the tick reaches 0. */
    int attackTick;
    /** The speed with which the mob will approach the target */
    double speedTowardsTarget;
    /** When true, the mob will continue chasing its target, even if it can't find a path to them right now. */
    boolean longMemory;
    /** The PathEntity of our entity. */
    PathEntity entityPathEntity;
    Class<?> classTarget;
    private int field_75445_i;
    private double field_151497_i;
    private double field_151495_j;
    private double field_151496_k;

    private int failedPathFindingPenalty;

    public ESM_EntityAIAttackOnCollide(EntityCreature attacker, Class<?> target, double speed, boolean longMemory) {
        this(attacker, speed, longMemory);
        this.classTarget = target;
    }

    public ESM_EntityAIAttackOnCollide(EntityCreature attacker, double speed, boolean longMemory) {
        super(attacker, speed, longMemory);
        this.attacker = attacker;
        this.worldObj = attacker.worldObj;
        this.speedTowardsTarget = speed;
        this.longMemory = longMemory;
        this.setMutexBits(3);
    }

    /**
     * Returns whether the EntityAIBase should begin execution.
     */
    public boolean shouldExecute() {
        EntityLivingBase entitylivingbase = this.attacker.getAttackTarget();

        if (entitylivingbase == null) {
            return false;
        } else if (!entitylivingbase.isEntityAlive()) {
            return false;
        } else if (this.classTarget != null && !this.classTarget.isAssignableFrom(entitylivingbase.getClass())) {
            return false;
        } else {
            if (--this.field_75445_i <= 0) {
                this.entityPathEntity = this.attacker.getNavigator()
                    .getPathToEntityLiving(entitylivingbase);
                this.field_75445_i = MathHelper
                    .ceiling_float_int(this.attacker.getDistanceToEntity(entitylivingbase) / 4F)
                    + this.attacker.getRNG()
                        .nextInt(7);
                return this.entityPathEntity != null;
            } else {
                return this.entityPathEntity != null || !this.attacker.getNavigator()
                    .noPath();
            }
        }
    }

    /**
     * Returns whether an in-progress EntityAIBase should continue executing
     */
    public boolean continueExecuting() {
        EntityLivingBase entitylivingbase = this.attacker.getAttackTarget();
        if (entitylivingbase == null || !entitylivingbase.isEntityAlive()) return false;

        if (!this.longMemory || ESM_Settings.isXrayAllowed(this.worldObj.getWorldTime())) {
            return !this.attacker.getNavigator()
                .noPath();
        } else {
            return this.attacker.isWithinHomeDistance(
                MathHelper.floor_double(entitylivingbase.posX),
                MathHelper.floor_double(entitylivingbase.posY),
                MathHelper.floor_double(entitylivingbase.posZ));
        }
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting() {
        this.attacker.getNavigator()
            .setPath(this.entityPathEntity, this.speedTowardsTarget);
        this.field_75445_i = 0;
    }

    /**
     * Resets the task
     */
    public void resetTask() {
        this.attacker.getNavigator()
            .clearPathEntity();
    }

    /**
     * Updates the task
     */
    public void updateTask() {
        EntityLivingBase entitylivingbase = this.attacker.getAttackTarget();

        if (entitylivingbase == null) {
            this.resetTask();
            return;
        }

        this.attacker.getLookHelper()
            .setLookPositionWithEntity(entitylivingbase, 30.0F, 30.0F);
        double d0 = this.attacker
            .getDistanceSq(entitylivingbase.posX, entitylivingbase.boundingBox.minY, entitylivingbase.posZ);
        double d1 = this.attacker.width * 2.0F * this.attacker.width * 2.0F + entitylivingbase.width;
        --this.field_75445_i;

        if ((this.longMemory || this.attacker.getEntitySenses()
            .canSee(entitylivingbase))
            && this.field_75445_i <= 0
            && (this.field_151497_i == 0.0D && this.field_151495_j == 0.0D && this.field_151496_k == 0.0D
                || entitylivingbase.getDistanceSq(this.field_151497_i, this.field_151495_j, this.field_151496_k) >= 1.0D
                || this.attacker.getRNG()
                    .nextFloat() < 0.05F)) {
            this.field_151497_i = entitylivingbase.posX;
            this.field_151495_j = entitylivingbase.boundingBox.minY;
            this.field_151496_k = entitylivingbase.posZ;
            this.field_75445_i = failedPathFindingPenalty
                + MathHelper.ceiling_float_int(this.attacker.getDistanceToEntity(entitylivingbase) / 4F)
                + this.attacker.getRNG()
                    .nextInt(7);

            if (this.attacker.getNavigator()
                .getPath() != null) {
                PathPoint finalPathPoint = this.attacker.getNavigator()
                    .getPath()
                    .getFinalPathPoint();
                if (finalPathPoint != null && entitylivingbase
                    .getDistanceSq(finalPathPoint.xCoord, finalPathPoint.yCoord, finalPathPoint.zCoord) < 1) {
                    failedPathFindingPenalty = 0;
                } else {
                    failedPathFindingPenalty = Math.min(failedPathFindingPenalty + 10, 100);
                }
            } else {
                failedPathFindingPenalty = Math.min(failedPathFindingPenalty + 10, 100);
            }

            this.field_75445_i += MathHelper
                .ceiling_float_int(this.attacker.getDistanceToEntity(entitylivingbase) / 16F * 5F);

            if (!this.attacker.getNavigator()
                .tryMoveToEntityLiving(entitylivingbase, this.speedTowardsTarget)) {
                this.field_75445_i += 15;
            }
        }

        this.attackTick = Math.max(this.attackTick - 1, 0);

        if (d0 <= d1 && this.attackTick <= 20) {
            this.attackTick = 20;

            if (this.attacker.getHeldItem() != null) {
                this.attacker.swingItem();
            }

            if (this.attacker instanceof IAnimals && ESM_Settings.animalsAttack) {
                entitylivingbase.attackEntityFrom(new AnimalDamageSource("mob", this.attacker), 2F);
            } else {
                this.attacker.attackEntityAsMob(entitylivingbase);
            }
        }
    }
}
