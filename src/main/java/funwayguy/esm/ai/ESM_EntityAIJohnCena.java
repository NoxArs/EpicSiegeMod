package funwayguy.esm.ai;

import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;

import funwayguy.esm.core.ESM_Settings;

public class ESM_EntityAIJohnCena extends EntityAIBase {

    /** The creeper that is swelling. */
    EntityCreeper swellingCreeper;

    /**
     * The creeper's attack target. This is used for the changing of the creeper's state.
     */
    EntityLivingBase creeperAttackTarget;

    double detDist = 9.0D;
    boolean detLock = false;

    public ESM_EntityAIJohnCena(EntityCreeper par1EntityCreeper) {
        this.swellingCreeper = par1EntityCreeper;
        detDist = (double) getCreeperRadius(swellingCreeper) + 0.5D;
        detDist = detDist * detDist;
        // John Cena always charges in
    }

    public static int getCreeperRadius(EntityCreeper creeper) {
        int radius = 3;

        NBTTagCompound data = creeper.getEntityData();

        if (data.hasKey("ExplosionRadius")) {
            radius = data.getByte("ExplosionRadius");
        }

        return radius;
    }

    /**
     * Returns whether the EntityAIBase should begin execution.
     */
    public boolean shouldExecute() {
        EntityLivingBase entitylivingbase = this.swellingCreeper.getAttackTarget();

        boolean enableBreach = this.swellingCreeper.ticksExisted >= 60 && entitylivingbase != null
            && swellingCreeper.ridingEntity == null
            && ESM_Settings.CreeperBreaching
            && !swellingCreeper.hasPath();

        if (enableBreach) {
            MovingObjectPosition mop = GetMovingObjectPosition(this.swellingCreeper, false);
            enableBreach = mop != null && mop.typeOfHit == MovingObjectType.BLOCK;
        }

        return this.swellingCreeper.getCreeperState() > 0 || enableBreach
            || (entitylivingbase != null && this.swellingCreeper.getDistanceSqToEntity(entitylivingbase)
                <= detDist * (ESM_Settings.CreeperChargers ? 2 : 1));
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting() {
        this.swellingCreeper.worldObj.playSoundAtEntity(swellingCreeper, "esm:cena_creeper.start", 1.0F, 1.0F);
        this.swellingCreeper.setCustomNameTag("John Cena");
    }

    /**
     * Resets the task
     */
    @Override
    public void resetTask() {
        EntityLivingBase t = this.swellingCreeper.getAttackTarget();
        if (t == null || !t.isEntityAlive()) this.swellingCreeper.setCreeperState(-1);
    }

    public boolean CheckForDiggers() {
        if (!ESM_Settings.ZombieDiggers) {
            return false;
        }

        @SuppressWarnings("unchecked")
        List<EntityZombie> zombieList = this.swellingCreeper.worldObj
            .getEntitiesWithinAABB(EntityZombie.class, this.swellingCreeper.boundingBox.expand(10D, 10D, 10D));
        Iterator<EntityZombie> iterator = zombieList.iterator();

        while (iterator.hasNext()) {
            EntityZombie zombie = iterator.next();

            if (zombie != null && zombie.isEntityAlive()) {
                ItemStack stack = zombie.getEquipmentInSlot(0);

                if (!ESM_Settings.ZombieDiggerTools || (stack != null && (stack.getItem()
                    .canHarvestBlock(Blocks.stone, stack) || stack.getItem() instanceof ItemPickaxe))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Updates the task
     */
    @Override
    public void updateTask() {
        EntityLivingBase t = this.swellingCreeper.getAttackTarget();
        if (t == null || !t.isEntityAlive()) {
            this.swellingCreeper.setCreeperState(-1);
            return;
        }

        // 一旦进入膨胀态，就锁死（保留原版疯狂）
        if (this.swellingCreeper.getCreeperState() > 0) {
            this.swellingCreeper.setCreeperState(1);
            return;
        }

        boolean enableBreach = this.swellingCreeper.ticksExisted >= 60 && t != null
            && this.swellingCreeper.ridingEntity == null
            && ESM_Settings.CreeperBreaching
            && !this.swellingCreeper.hasPath(); // 更贴近你原版 shouldExecute

        if (enableBreach) {
            MovingObjectPosition mop = GetMovingObjectPosition(this.swellingCreeper, false);
            enableBreach = mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        }

        double near = detDist * (ESM_Settings.CreeperChargers ? 2 : 1);
        if (this.swellingCreeper.getDistanceSqToEntity(t) <= near || enableBreach) {
            this.swellingCreeper.setCreeperState(1);
        }
        // 注意：这里不要 else -> -1。否则“没开始膨胀前”会抖动，且会削弱疯狂感
    }

    public static MovingObjectPosition GetMovingObjectPosition(EntityLivingBase entityLiving, boolean liquids) {
        float f = 1.0F;
        float f1 = entityLiving.prevRotationPitch + (entityLiving.rotationPitch - entityLiving.prevRotationPitch) * f;
        float f2 = entityLiving.prevRotationYaw + (entityLiving.rotationYaw - entityLiving.prevRotationYaw) * f;
        double d0 = entityLiving.prevPosX + (entityLiving.posX - entityLiving.prevPosX) * (double) f;
        double d1 = entityLiving.prevPosY + (entityLiving.posY - entityLiving.prevPosY) * (double) f
            + (double) entityLiving.getEyeHeight();
        double d2 = entityLiving.prevPosZ + (entityLiving.posZ - entityLiving.prevPosZ) * (double) f;
        Vec3 vec3 = Vec3.createVectorHelper(d0, d1, d2);
        float f3 = MathHelper.cos(-f2 * 0.017453292F - (float) Math.PI);
        float f4 = MathHelper.sin(-f2 * 0.017453292F - (float) Math.PI);
        float f5 = -MathHelper.cos(-f1 * 0.017453292F);
        float f6 = MathHelper.sin(-f1 * 0.017453292F);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        double d3 = 1.0D; // Ray Distance
        Vec3 vec31 = vec3.addVector((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
        return entityLiving.worldObj.func_147447_a(vec3, vec31, liquids, !liquids, false);
    }
}
