package funwayguy.esm.ai;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;

import funwayguy.esm.core.ESM_Settings;

class ESM_EntityAINearestAttackableTargetSelector implements IEntitySelector {

    EntityLivingBase owner;
    final IEntitySelector field_111103_c;
    final List<Class<? extends EntityLivingBase>> targetable;
    final ESM_EntityAINearestAttackableTarget field_111102_d;

    public ESM_EntityAINearestAttackableTargetSelector(EntityLivingBase owner,
        ESM_EntityAINearestAttackableTarget targetAINearestAttackableTarget, IEntitySelector par2IEntitySelector,
        List<Class<? extends EntityLivingBase>> targetable) {
        this.owner = owner;
        this.field_111102_d = targetAINearestAttackableTarget;
        this.field_111103_c = par2IEntitySelector;
        this.targetable = (targetable == null) ? Collections.<Class<? extends EntityLivingBase>>emptyList()
            : targetable;
    }

    /**
     * Return whether the specified entity is applicable to this filter.
     */
    public boolean isEntityApplicable(Entity target) {
        if (target == null || target == owner) return false;

        if (!(target instanceof EntityLivingBase)) return false;
        final EntityLivingBase living = (EntityLivingBase) target;

        // friendly-fire / mob-vs-mob rule
        if (!ESM_Settings.friendlyFire && owner instanceof IMob) {
            if (ESM_Settings.Chaos) {
                if (owner.getClass() == target.getClass()) return false;
            } else {
                if (target instanceof IMob) return false;
            }
        }

        // pets rule (keep your existing semantics: allow pets to bypass whitelist when attackPets=true)
        boolean isPlayerPet = ESM_Settings.attackPets && (target instanceof IEntityOwnable)
            && (((IEntityOwnable) target).getOwner() instanceof EntityPlayer);

        if (!isPlayerPet && !ESM_Settings.Chaos) {
            // null-safe: if targetable is null/empty => reject all (same as your current intent, but no NPE)
            if (targetable == null || targetable.isEmpty()) return false;

            boolean allow = false;
            Class<?> tc = target.getClass();
            for (Class<? extends EntityLivingBase> c : targetable) {
                if (c.isAssignableFrom(tc)) {
                    allow = true;
                    break;
                }
            }
            if (!allow) return false;
        }

        if (field_111103_c != null && !field_111103_c.isEntityApplicable(target)) return false;
        return field_111102_d.isSuitableTarget(living, false);
    }
}
