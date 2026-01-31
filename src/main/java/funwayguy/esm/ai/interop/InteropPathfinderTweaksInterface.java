package funwayguy.esm.ai.interop;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;

public interface InteropPathfinderTweaksInterface {

    public boolean isTallBlock(Entity entity, Block block, int posX, int posY, int posZ);
}
