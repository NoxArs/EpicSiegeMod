package funwayguy.esm.ai;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBreakDoor;

/**
 * Proxy AI between modified AI and vanilla
 */
public class ESM_EntityAIBreakDoor_Proxy extends EntityAIBreakDoor {

    ESM_EntityAIBreakDoor proxyInstance;

    public ESM_EntityAIBreakDoor_Proxy(EntityLiving entity) {
        super(entity);
        proxyInstance = new ESM_EntityAIBreakDoor(entity);
    }

    @Override
    public boolean shouldExecute() {
        return proxyInstance.shouldExecute();
    }

    @Override
    public void startExecuting() {
        proxyInstance.startExecuting();
    }

    @Override
    public boolean continueExecuting() {
        return proxyInstance.continueExecuting();
    }

    @Override
    public void resetTask() {
        proxyInstance.resetTask();
    }

    @Override
    public void updateTask() {
        proxyInstance.updateTask();
    }
}
