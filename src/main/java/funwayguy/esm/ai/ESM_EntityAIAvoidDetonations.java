package funwayguy.esm.ai;

import java.util.List;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class ESM_EntityAIAvoidDetonations extends EntityAIBase {

    public final IEntitySelector selector = new ExplosiveEntitySelector();

    /** The entity we are attached to */
    private final EntityCreature theEntity;
    private final double farSpeed;
    private final double nearSpeed;
    private final float distanceFromEntity;

    /** Current threat */
    private Entity closestLivingEntity;

    /** The PathEntity of our entity */
    private PathEntity entityPathEntity;
    /** The PathNavigate of our entity */
    private final PathNavigate entityPathNavigate;

    // NEW: refresh cooldown so we don't scan/sort every tick
    private int refreshCooldown = 0;

    public ESM_EntityAIAvoidDetonations(EntityCreature entity, float distance, double farSpeed, double nearSpeed) {
        this.theEntity = entity;
        this.distanceFromEntity = distance;
        this.farSpeed = farSpeed;
        this.nearSpeed = nearSpeed;
        this.entityPathNavigate = entity.getNavigator();
        this.setMutexBits(1);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean shouldExecute() {
        // Keep original creeper special-case
        if (this.theEntity instanceof EntityCreeper) {
            EntityCreeper me = (EntityCreeper) this.theEntity;
            if (me.getCreeperState() == 1) {
                return false;
            }
        }

        // NEW: Refresh threat periodically or when missing/invalid
        if (this.closestLivingEntity == null || !this.closestLivingEntity.isEntityAlive()
            || --this.refreshCooldown <= 0) {
            this.refreshCooldown = 10; // tweak: 5/10/20 ticks depending on perf

            List<Entity> list = this.theEntity.worldObj.selectEntitiesWithinAABB(
                Entity.class,
                this.theEntity.boundingBox
                    .expand((double) this.distanceFromEntity, 3.0D, (double) this.distanceFromEntity),
                selector);

            if (list.isEmpty()) {
                this.closestLivingEntity = null;
                return false;
            }

            // NEW: O(n) pick nearest instead of sorting whole list
            Entity best = null;
            double bestDistSq = Double.MAX_VALUE;

            for (int i = 0; i < list.size(); i++) {
                Entity e = list.get(i);
                if (e == null || e.isDead) continue;

                double dsq = this.theEntity.getDistanceSqToEntity(e);
                if (dsq < bestDistSq) {
                    bestDistSq = dsq;
                    best = e;
                }
            }

            this.closestLivingEntity = best;

            if (this.closestLivingEntity == null) {
                return false;
            }
        }

        // At this point, closestLivingEntity should be valid, but still guard anyway
        if (this.closestLivingEntity == null || !this.closestLivingEntity.isEntityAlive()) {
            return false;
        }

        Vec3 vec3 = RandomPositionGenerator.findRandomTargetBlockAwayFrom(
            this.theEntity,
            MathHelper.ceiling_double_int(this.distanceFromEntity + 4D),
            4,
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
                this.entityPathEntity = this.entityPathNavigate.getPathToXYZ(vec3.xCoord, vec3.yCoord, vec3.zCoord);
                return this.entityPathEntity != null;
            }
    }

    @Override
    public boolean continueExecuting() {
        return !this.entityPathNavigate.noPath() && this.closestLivingEntity != null
            && this.closestLivingEntity.isEntityAlive();
    }

    @Override
    public void startExecuting() {
        this.theEntity.setAttackTarget(null);
        this.entityPathNavigate.setPath(this.entityPathEntity, this.farSpeed);
    }

    @Override
    public void resetTask() {
        this.closestLivingEntity = null;
        this.entityPathEntity = null; // NEW: clear stale path
        // leave refreshCooldown as-is or reset to 0; resetting helps reacquire faster
        this.refreshCooldown = 0;
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public void updateTask() {
        // NEW: hard guard against NPE / dead threat
        if (this.closestLivingEntity == null || !this.closestLivingEntity.isEntityAlive()) {
            this.resetTask();
            return;
        }

        if (this.theEntity.getDistanceToEntity(this.closestLivingEntity) < this.distanceFromEntity / 2D) {
            this.theEntity.getNavigator()
                .setSpeed(this.nearSpeed);
        } else {
            this.theEntity.getNavigator()
                .setSpeed(this.farSpeed);
        }
    }
}
