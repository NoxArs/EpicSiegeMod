package funwayguy.esm.ai;

import java.util.List;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;

public class ESM_EntityAIHurtByTarget extends ESM_EntityAITarget {

    boolean entityCallsForHelp;
    private int field_142052_b;

    public ESM_EntityAIHurtByTarget(EntityCreature creature, boolean callsForHelp) {
        super(creature, false);
        this.entityCallsForHelp = callsForHelp;
        this.setMutexBits(1);
    }

    /**
     * Returns whether the EntityAIBase should begin execution.
     */
    @Override
    public boolean shouldExecute() {
        int i = this.taskOwner.func_142015_aE();
        EntityLivingBase t = this.taskOwner.getAITarget();
        return i != this.field_142052_b && t != null && this.isSuitableTarget(t, false);
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    @Override
    public void startExecuting() {
        this.field_142052_b = this.taskOwner.func_142015_aE();
        EntityLivingBase target = this.taskOwner.getAITarget();
        if (target == null) {
            super.startExecuting();
            return;
        }

        // 如果目标是友军/同队，直接不扩散仇恨（防止误伤链）
        if (this.taskOwner.getTeam() != null && this.taskOwner.isOnSameTeam(target)) {
            super.startExecuting();
            return;
        }

        this.taskOwner.setAttackTarget(target);
        this.field_142052_b = this.taskOwner.func_142015_aE();

        if (this.entityCallsForHelp) {
            double d0 = this.getTargetDistance();

            @SuppressWarnings("unchecked")
            List<EntityCreature> list = this.taskOwner.worldObj.getEntitiesWithinAABB(
                (Class<EntityCreature>) (Class<?>) this.taskOwner.getClass(),
                this.taskOwner.boundingBox.expand(d0, 10.0D, d0));

            for (EntityCreature ally : list) {
                if (ally == null || !ally.isEntityAlive()) continue;
                if (ally == this.taskOwner) continue;
                if (ally.getAttackTarget() != null) continue;
                if (ally.getTeam() != null && ally.isOnSameTeam(target)) continue;
                ally.setAttackTarget(target);
            }
        }

        super.startExecuting();
    }
}
