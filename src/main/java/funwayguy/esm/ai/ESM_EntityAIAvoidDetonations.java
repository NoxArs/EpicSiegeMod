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

    private final EntityCreature theEntity;
    private final double farSpeed;
    private final double nearSpeed;
    private final float distanceFromEntity;

    private Entity closestLivingEntity;
    private PathEntity entityPathEntity;
    private final PathNavigate entityPathNavigate;

    // 扫描冷却：无目标更频繁，有目标也要定期更新威胁
    private int scanCooldown = 0;

    // 重新规划冷却：别每 tick 都算 path
    private int repathCooldown = 0;

    public ESM_EntityAIAvoidDetonations(EntityCreature host, float dist, double far, double near) {
        this.theEntity = host;
        this.distanceFromEntity = dist;
        this.farSpeed = far;
        this.nearSpeed = near;
        this.entityPathNavigate = host.getNavigator();
        this.setMutexBits(1);
    }

    private Entity findClosestThreat() {
        // Y 扩展：随距离变化，别太小也别太大
        double yExpand = Math.min(8.0D, Math.max(3.0D, this.distanceFromEntity * 0.5D));

        List<Entity> list = this.theEntity.worldObj.selectEntitiesWithinAABB(
            Entity.class,
            this.theEntity.boundingBox.expand(this.distanceFromEntity, yExpand, this.distanceFromEntity),
            selector);

        if (list == null || list.isEmpty()) return null;

        Entity best = null;
        double bestD2 = Double.MAX_VALUE;

        for (Entity e : list) {
            if (e == null || e.isDead) continue;
            double d2 = this.theEntity.getDistanceSqToEntity(e);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = e;
            }
        }
        return best;
    }

    private Vec3 pickBestEscapePoint(Entity threat) {
        if (threat == null) return null;

        Vec3 threatPos = Vec3.createVectorHelper(threat.posX, threat.posY, threat.posZ);

        // 多采样几次，选“最远离威胁且可达”的
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;

        int horiz = MathHelper.ceiling_double_int(this.distanceFromEntity + 4.0D);
        int vert = 4;

        for (int i = 0; i < 4; i++) {
            Vec3 v = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.theEntity, horiz, vert, threatPos);
            if (v == null) continue;

            // 可达性先粗判：path 不为空才算
            PathEntity p = this.entityPathNavigate.getPathToXYZ(v.xCoord, v.yCoord, v.zCoord);
            if (p == null) continue;

            // 分数：离威胁越远越好，同时也要“比当前更安全”
            double d2 = threat.getDistanceSq(v.xCoord, v.yCoord, v.zCoord);
            double cur2 = threat.getDistanceSqToEntity(this.theEntity);

            // 必须比当前更远，否则无意义
            if (d2 <= cur2) continue;

            // 额外给一点“路径长度”惩罚（太绕不如找别的）
            // PathEntity 在 1.7.10 没公开长度，简单用直线近似惩罚即可
            double pathPenalty = this.theEntity.getDistanceSq(v.xCoord, v.yCoord, v.zCoord) * 0.05D;

            double score = d2 - pathPenalty;
            if (score > bestScore) {
                bestScore = score;
                best = v;
                this.entityPathEntity = p; // 顺便缓存这条最优路径
            }
        }

        return best;
    }

    @Override
    public boolean shouldExecute() {
        // 苦力怕自己正在点火：不躲
        if (this.theEntity instanceof EntityCreeper) {
            EntityCreeper me = (EntityCreeper) this.theEntity;
            if (me.getCreeperState() == 1) return false;
        }

        // 冷却：无目标更频繁，有目标也更新
        if (--this.scanCooldown > 0) return false;
        this.scanCooldown = (this.closestLivingEntity == null ? 2 : 6);

        // 更新威胁
        Entity threat = findClosestThreat();
        if (threat == null) return false;

        this.closestLivingEntity = threat;

        // 选逃跑点 + 路径
        Vec3 best = pickBestEscapePoint(threat);
        return best != null && this.entityPathEntity != null;
    }

    @Override
    public boolean continueExecuting() {
        if (this.closestLivingEntity == null || this.closestLivingEntity.isDead) return false;
        double d2 = this.theEntity.getDistanceSqToEntity(this.closestLivingEntity);
        double stop = (double) this.distanceFromEntity * 1.2D;
        return !(d2 > stop * stop);
    }

    @Override
    public void startExecuting() {
        this.theEntity.setAttackTarget(null);
        if (this.entityPathEntity != null) {
            this.entityPathNavigate.setPath(this.entityPathEntity, this.farSpeed);
        }

        this.repathCooldown = 0;
    }

    @Override
    public void resetTask() {
        this.closestLivingEntity = null;
        this.entityPathEntity = null;
        this.scanCooldown = 0;
        this.repathCooldown = 0;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void updateTask() {
        if (this.closestLivingEntity == null || this.closestLivingEntity.isDead) return;

        double d2 = this.theEntity.getDistanceSqToEntity(this.closestLivingEntity);

        // 距离越近跑得越快
        if (d2 < 49.0D) this.entityPathNavigate.setSpeed(this.nearSpeed);
        else this.entityPathNavigate.setSpeed(this.farSpeed);

        // 定期重算路径（威胁会移动，随机点可能不再优）
        if (--this.repathCooldown <= 0) {
            this.repathCooldown = 10; // 0.5 秒重算一次

            // 如果没路了，或者威胁更近了，重新选点
            if (this.entityPathNavigate.noPath()
                || d2 < (double) this.distanceFromEntity * (double) this.distanceFromEntity) {
                Vec3 best = pickBestEscapePoint(this.closestLivingEntity);
                if (best != null && this.entityPathEntity != null) {
                    this.entityPathNavigate.setPath(this.entityPathEntity, this.farSpeed);
                }
            }
        }
    }
}
