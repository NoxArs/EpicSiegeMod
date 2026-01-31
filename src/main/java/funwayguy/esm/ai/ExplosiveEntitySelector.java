package funwayguy.esm.ai;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.projectile.EntityLargeFireball;

public class ExplosiveEntitySelector implements IEntitySelector {

    @Override
    public boolean isEntityApplicable(Entity entity) {
        if (entity == null) return false;

        if (entity instanceof EntityCreeper) {
            return ((EntityCreeper) entity).getCreeperState() == 1;
        }

        if (entity instanceof EntityTNTPrimed) {
            return ((EntityTNTPrimed) entity).fuse > 0;
        }

        return entity instanceof EntityLargeFireball;
    }
}
