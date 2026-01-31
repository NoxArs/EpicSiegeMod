package funwayguy.esm.ai;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAIDemolition extends EntityAIBase {

    public final EntityLiving host;

    public ESM_EntityAIDemolition(EntityLiving host) {
        this.host = host;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (host.worldObj.isRemote) return false;

        if (ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed)
            if (!ESM_Utils.isSiegeAllowed(host.worldObj.getWorldTime())) return false;

        // cooldown (2s)
        long now = host.worldObj.getTotalWorldTime();
        if (host.getEntityData()
            .getLong("ESM_DEMO_CD") > now) return false;

        EntityLivingBase t = host.getAttackTarget();
        if (t == null) return false;
        if (host.getDistanceSqToEntity(t) >= 9.0D) return false;

        ItemStack held = host.getHeldItem();
        if (held == null) return false;

        return held.getItem() == Item.getItemFromBlock(Blocks.tnt);
    }

    @Override
    public boolean continueExecuting() {
        return false;
    }

    @Override
    public void startExecuting() {
        if (host.worldObj.isRemote) return;

        // consume 1 TNT from held stack
        ItemStack held = host.getHeldItem();
        if (held != null && held.getItem() == Item.getItemFromBlock(Blocks.tnt)) {
            held.stackSize--;
            if (held.stackSize <= 0) {
                host.setCurrentItemOrArmor(0, null);
            }
        }

        // set cooldown
        host.getEntityData()
            .setLong("ESM_DEMO_CD", host.worldObj.getTotalWorldTime() + 40);

        EntityTNTPrimed tnt = new EntityTNTPrimed(host.worldObj, host.posX, host.posY + 0.5D, host.posZ, host);
        host.worldObj.spawnEntityInWorld(tnt);
    }
}
