package funwayguy.esm.block;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class BlockZombieStep extends Block {

    private static final int CHECK_NEAR_DELAY = 20; // 1 秒
    private static final int CHECK_FAR_DELAY = 40; // 2 秒
    private static final int DECAY_DELAY = 20; // 腐烂节奏
    private static final int MAX_META = 15;

    public BlockZombieStep() {
        super(Material.rock);
        setHardness(0.2F);
        setResistance(5.0F);
        setStepSound(soundTypePiston);

        setBlockName("esm_zombie_step");
        setBlockTextureName("minecraft:cobblestone");
        setCreativeTab(null);
        setTickRandomly(false);
    }

    /* ================= 掉落控制 ================= */

    @Override
    public int quantityDropped(Random rand) {
        return 0;
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return null;
    }

    @Override
    protected boolean canSilkHarvest() {
        return false;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        if (!world.isRemote) {
            world.scheduleBlockUpdate(x, y, z, this, CHECK_NEAR_DELAY);
        }
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random rand) {
        if (world.isRemote) return;

        int meta = world.getBlockMetadata(x, y, z);
        if (hasZombieUser(world, x, y, z)) {
            if (meta > 0) {
                world.setBlockMetadataWithNotify(x, y, z, meta - 1, 2);
            }

            world.scheduleBlockUpdate(x, y, z, this, CHECK_NEAR_DELAY);
            return;
        }
        if (hasZombieNearby(world, x, y, z)) {
            world.scheduleBlockUpdate(x, y, z, this, CHECK_FAR_DELAY);
            return;
        }
        if (meta >= MAX_META) {
            world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(this) + (meta << 12));
            world.setBlockToAir(x, y, z);
            return;
        }

        world.setBlockMetadataWithNotify(x, y, z, meta + 1, 2);
        world.scheduleBlockUpdate(x, y, z, this, DECAY_DELAY);
    }

    private boolean hasZombieUser(World world, int x, int y, int z) {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 2, z + 1)
            .expand(0.2D, 0.2D, 0.2D);

        return !world.getEntitiesWithinAABB(EntityMob.class, box)
            .isEmpty();
    }

    private boolean hasZombieNearby(World world, int x, int y, int z) {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - 4, y - 4, z - 4, x + 5, y + 5, z + 5);

        return !world.getEntitiesWithinAABB(EntityZombie.class, box)
            .isEmpty();
    }

    /* ================= 物理属性 ================= */

    @Override
    public int getMobilityFlag() {
        return 2;
    }
}
