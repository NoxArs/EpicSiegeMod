package funwayguy.esm;

import net.minecraft.entity.Entity;
import net.minecraft.util.EntityDamageSource;

public class AnimalDamageSource extends EntityDamageSource {

    public AnimalDamageSource(String typeName, Entity entitySource) {
        super(typeName, entitySource);
    }

    @Override
    public boolean isDifficultyScaled() {
        return false;
    }
}
