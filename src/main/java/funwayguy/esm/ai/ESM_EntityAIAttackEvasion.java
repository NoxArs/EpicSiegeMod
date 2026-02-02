package funwayguy.esm.ai;

import java.util.Iterator;
import java.util.List;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.util.Vec3;

import funwayguy.esm.core.ESM_Settings;

public class ESM_EntityAIAttackEvasion extends EntityAIBase {

    public final IEntitySelector field_98218_a = new IEntitySelector() {

        /**
         * Return whether the specified entity is applicable to this filter.
         */
        public boolean isEntityApplicable(Entity p_82704_1_) {
            return p_82704_1_.isEntityAlive() && ESM_EntityAIAttackEvasion.this.theEntity.getEntitySenses()
                .canSee(p_82704_1_);
        }
    };
    /** The entity we are attached to */
    private final EntityCreature theEntity;
    private final double farSpeed;
    private final double nearSpeed;
    private EntityPlayer closestLivingEntity;
    private final float distanceFromEntity;
    /** The PathEntity of our entity */
    private PathEntity entityPathEntity;

    public ESM_EntityAIAttackEvasion(EntityCreature creature, float distance, double farSpeed, double nearSpeed) {
        this.theEntity = creature;
        this.distanceFromEntity = distance;
        this.farSpeed = farSpeed;
        this.nearSpeed = nearSpeed;
        this.setMutexBits(1);
    }

    /**
     * Returns whether the EntityAIBase should begin execution.
     */
    public boolean shouldExecute() {
        if (!ESM_Settings.attackEvasion || theEntity.handleWaterMovement()
            || theEntity.getHealth() / theEntity.getMaxHealth() > 0.25F) {
            return false;
        }

        List<EntityMob> attackers = this.theEntity.worldObj.selectEntitiesWithinAABB(
            EntityMob.class,
            this.theEntity.boundingBox.expand(16D, 16D, 16D),
            this.field_98218_a);
        if (attackers.size() > 1) {
            return false;
        }

        List<EntityPlayer> list = this.theEntity.worldObj.selectEntitiesWithinAABB(
            EntityPlayer.class,
            this.theEntity.boundingBox
                .expand(this.distanceFromEntity, this.distanceFromEntity, this.distanceFromEntity),
            this.field_98218_a);

        if (list.isEmpty()) {
            return false;
        }

        Iterator<EntityPlayer> iterator = list.iterator();

        this.closestLivingEntity = null;

        while (iterator.hasNext()) {
            EntityPlayer player = iterator.next();

            if (player != null && player.isEntityAlive()
                && !player.capabilities.disableDamage
                && player.canEntityBeSeen(this.theEntity)) {
                if (player.getDistanceToEntity(this.theEntity) <= 2D && this.theEntity.getAttackTarget() == player) {
                    // Too close to target to warrant a retreat
                    return false;
                }

                Vec3 vec3 = player.getLook(1.0F)
                    .normalize();
                Vec3 vec31 = Vec3.createVectorHelper(
                    theEntity.posX - player.posX,
                    theEntity.boundingBox.minY + (double) (theEntity.height / 2.0F)
                        - (player.posY + (double) player.getEyeHeight()),
                    theEntity.posZ - player.posZ);
                double d0 = vec31.lengthVector();
                if (d0 < 0.0001D) d0 = 0.0001D;
                vec31 = vec31.normalize();
                double d1 = vec3.dotProduct(vec31);

                if (d1 > 0.5D - 0.025D / d0) {
                    closestLivingEntity = player;
                    break;
                }
            }
        }

        if (this.closestLivingEntity == null) {
            return false;
        }

        Vec3 vec3 = RandomPositionGenerator.findRandomTargetBlockAwayFrom(
            this.theEntity,
            16,
            8,
            Vec3.createVectorHelper(
                this.closestLivingEntity.posX,
                this.closestLivingEntity.posY,
                this.closestLivingEntity.posZ));

        if (vec3 == null) {
            return false;
        } else if (this.closestLivingEntity.getDistanceSq(vec3.xCoord, vec3.yCoord, vec3.zCoord)
            < this.closestLivingEntity.getDistanceSqToEntity(this.theEntity)) {
                return false;
            } else {
                this.entityPathEntity = this.theEntity.getNavigator()
                    .getPathToXYZ(vec3.xCoord, vec3.yCoord, vec3.zCoord);
                return this.entityPathEntity != null && this.entityPathEntity.isDestinationSame(vec3);
            }
    }

    /**
     * Returns whether an in-progress EntityAIBase should continue executing
     */
    public boolean continueExecuting() {
        if (this.entityPathEntity == null) return false;
        if (this.closestLivingEntity == null || !this.closestLivingEntity.isEntityAlive()
            || this.theEntity.getAttackTarget() != null) {
            return false;
        } else if (this.theEntity.getNavigator()
            .noPath() || entityPathEntity.isFinished()
            || this.theEntity.getDistanceToEntity(this.closestLivingEntity) <= 2D) {
                return false;
            } else {
                Vec3 vec3 = this.closestLivingEntity.getLook(1.0F)
                    .normalize();
                Vec3 vec31 = Vec3.createVectorHelper(
                    theEntity.posX - this.closestLivingEntity.posX,
                    theEntity.boundingBox.minY + (double) (theEntity.height / 2.0F)
                        - (this.closestLivingEntity.posY + (double) this.closestLivingEntity.getEyeHeight()),
                    theEntity.posZ - this.closestLivingEntity.posZ);
                double d0 = vec31.lengthVector();
                if (d0 < 0.0001D) d0 = 0.0001D;
                vec31 = vec31.normalize();
                double d1 = vec3.dotProduct(vec31);
                return d1 > 0.5D - 0.025D / d0;
            }
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting() {
        this.theEntity.setAttackTarget(null);
        this.theEntity.setTarget(null);
        this.theEntity.getNavigator()
            .setPath(this.entityPathEntity, this.farSpeed);
    }

    /**
     * Resets the task
     */
    public void resetTask() {
        this.closestLivingEntity = null;
        this.entityPathEntity = null;
    }

    /**
     * Determine if this AI Task is interruptible by a higher (= lower value) priority task.
     */
    public boolean isInterruptible() {
        return false;
    }

    /**
     * Updates the task
     */
    public void updateTask() {
        if (this.closestLivingEntity == null || !this.closestLivingEntity.isEntityAlive()) return;
        if (this.theEntity.getDistanceSqToEntity(this.closestLivingEntity) < 49.0D) {
            this.theEntity.getNavigator()
                .setSpeed(this.nearSpeed);
        } else {
            this.theEntity.getNavigator()
                .setSpeed(this.farSpeed);
        }
    }
}
