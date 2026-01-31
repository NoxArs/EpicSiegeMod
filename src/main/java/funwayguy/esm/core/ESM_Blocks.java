package funwayguy.esm.core;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;
import funwayguy.esm.block.BlockZombieStep;

public class ESM_Blocks {

    public static Block zombieStep;

    public static void init() {
        zombieStep = new BlockZombieStep();
        GameRegistry.registerBlock(zombieStep, "zombie_step");
    }
}
