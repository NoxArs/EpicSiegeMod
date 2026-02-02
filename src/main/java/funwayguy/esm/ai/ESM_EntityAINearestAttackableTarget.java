package funwayguy.esm.ai;

import java.util.List;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;

import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAINearestAttackableTarget extends ESM_EntityAITarget {

    public final List<Class<? extends EntityLivingBase>> targetClass;
    private final int targetChance;
    private int searchDelay = 0;
    private final EntityAINearestAttackableTarget.Sorter theNearestAttackableTargetSorter;

    private final IEntitySelector targetEntitySelector;

    private EntityLivingBase targetEntity;

    private int calcSearchDelay() {
        return Math.min(40, Math.max(5, ESM_Settings.Awareness / 2));
    }

    public ESM_EntityAINearestAttackableTarget(EntityCreature owner,
        List<Class<? extends EntityLivingBase>> targetClasses, int chance, boolean checkSight) {
        this(owner, targetClasses, chance, checkSight, false);
    }

    public ESM_EntityAINearestAttackableTarget(EntityCreature owner,
        List<Class<? extends EntityLivingBase>> targetClasses, int chance, boolean checkSight, boolean nearbyOnly) {
        this(owner, targetClasses, chance, checkSight, nearbyOnly, (IEntitySelector) null);
    }

    public ESM_EntityAINearestAttackableTarget(EntityCreature owner,
        List<Class<? extends EntityLivingBase>> targetClasses, int chance, boolean checkSight, boolean nearbyOnly,
        IEntitySelector extraSelector) {
        super(owner, !(targetClasses.contains(EntityVillager.class) && owner instanceof EntityZombie), nearbyOnly);
        this.targetClass = targetClasses;
        this.targetChance = chance;
        this.theNearestAttackableTargetSorter = new EntityAINearestAttackableTarget.Sorter(owner);
        this.setMutexBits(1);

        this.targetEntitySelector = new ESM_EntityAINearestAttackableTargetSelector(
            owner,
            this,
            extraSelector,
            this.targetClass);
    }

    @Override
    public void resetTask() {
        super.resetTask();
        this.targetEntity = null;
        this.searchDelay = 0;
    }

    @Override
    public boolean shouldExecute() {
        if (searchDelay > 0) {
            searchDelay--;
            return false;
        }

        final double range = this.getTargetDistance();
        final double maxDistSq = range * range;
        if (targetEntity != null) {
            if (targetEntity.isEntityAlive() && taskOwner.getDistanceSqToEntity(targetEntity) <= maxDistSq) {
                if (isSuitableTarget(targetEntity, false)) {
                    int pathCount = ESM_Utils.getAIPathCount(this.taskOwner.worldObj, targetEntity);
                    if (pathCount < ESM_Settings.TargetCap || ESM_Settings.TargetCap == -1
                        || ESM_Utils
                            .isCloserThanOtherAttackers(this.taskOwner.worldObj, this.taskOwner, targetEntity)) {
                        return true;
                    }
                }
            }
            targetEntity = null;
            searchDelay = calcSearchDelay();
            return false;
        }

        if (this.targetChance > 0 && this.taskOwner.getRNG()
            .nextInt(this.targetChance) != 0) {
            return false;
        }

        List<EntityLivingBase> list = this.taskOwner.worldObj.selectEntitiesWithinAABB(
            EntityLivingBase.class,
            this.taskOwner.boundingBox.expand(range, range, range),
            this.targetEntitySelector);

        searchDelay = calcSearchDelay();

        if (list == null || list.isEmpty()) {
            targetEntity = null;
            return false;
        }

        EntityLivingBase best = null;
        double bestDistSq = maxDistSq;

        for (EntityLivingBase e : list) {
            if (e == null || !e.isEntityAlive()) continue;

            double dsq = this.taskOwner.getDistanceSqToEntity(e);
            if (dsq >= bestDistSq) continue;

            if (!isSuitableTarget(e, false)) continue;

            int pathCount = ESM_Utils.getAIPathCount(this.taskOwner.worldObj, e);
            if (pathCount < ESM_Settings.TargetCap || ESM_Settings.TargetCap == -1
                || ESM_Utils.isCloserThanOtherAttackers(this.taskOwner.worldObj, this.taskOwner, e)) {
                best = e;
                bestDistSq = dsq;
            }
        }

        this.targetEntity = best;
        return best != null;
    }

    @Override
    public void startExecuting() {
        this.taskOwner.setAttackTarget(this.targetEntity);
        super.startExecuting();
    }
}
