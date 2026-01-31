package funwayguy.esm.ai;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;

import funwayguy.esm.core.ESM_Settings;

public class GenericEntitySelector implements IEntitySelector {

    private final EntityLivingBase host;

    public GenericEntitySelector(EntityLivingBase host) {
        this.host = host;
    }

    @Override
    public boolean isEntityApplicable(Entity subject) {
        // 1) cheapest guards
        if (subject == null) return false;
        if (subject == host) return false;
        if (!(subject instanceof EntityLivingBase)) return false;

        EntityLivingBase living = (EntityLivingBase) subject;
        if (living.isDead || living.getHealth() <= 0.0F) return false;

        // 2) friendly-fire / mob-vs-mob rule
        if (!ESM_Settings.friendlyFire && host instanceof IMob) {
            if (ESM_Settings.Chaos) {
                // chaos: block only same exact class (keep your original semantics)
                if (host.getClass() == subject.getClass()) return false;
            } else {
                // normal: mobs don't target mobs
                if (subject instanceof IMob) return false;
            }
        }

        // 3) target type policy
        if (subject instanceof IEntityOwnable) {
            if (!ESM_Settings.attackPets) return false;

            IEntityOwnable ownable = (IEntityOwnable) subject;
            if (!(ownable.getOwner() instanceof EntityPlayer)) return false;
        } else if (subject instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) subject;
            if (p.capabilities.disableDamage) return false;
        } else if (subject instanceof EntityVillager) {
            if (!ESM_Settings.VillagerTarget) return false;
        } else {
            // original behavior: if not Chaos, reject everything else
            if (!ESM_Settings.Chaos) return false;
        }

        // 4) sight / xray
        if (ESM_Settings.isXrayAllowed(host.worldObj.getWorldTime())) return true;
        if (host instanceof EntitySpider) return true;

        return host.canEntityBeSeen(subject);
    }
}
