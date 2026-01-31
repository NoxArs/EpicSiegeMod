package funwayguy.esm.block;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class BlockZombieStep extends Block {

    public BlockZombieStep() {
        super(Material.rock);
        setHardness(0.2F);
        setResistance(5.0F);
        setStepSound(soundTypePiston);
        setTickRandomly(true); // 让它能收到 updateTick
        setBlockName("esm_zombie_step");
        setBlockTextureName("minecraft:cobblestone"); // 外观用圆石
        // 建议：不要放到创造物品栏，避免玩家拿到
        // setCreativeTab(null);
    }

    /** 不掉落任何东西 */
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

    /** 放置后安排第一次 tick */
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        if (!world.isRemote) {
            world.scheduleBlockUpdate(x, y, z, this, 10); // 每 10 tick 走一次寿命
        }
    }

    /** 每次 tick 让 meta++，到 15 消失 */
    @Override
    public void updateTick(World world, int x, int y, int z, Random rand) {
        if (world.isRemote) return;

        int meta = world.getBlockMetadata(x, y, z);

        // 如果上面压着实体/方块你想延寿，可以在这里做检查（可选）
        // 但建议别太复杂，免得引发奇怪行为

        if (meta >= 15) {
            world.setBlockToAir(x, y, z);
            return;
        }

        world.setBlockMetadataWithNotify(x, y, z, meta + 1, 2);
        world.scheduleBlockUpdate(x, y, z, this, 10);
    }

    /** 避免被活塞搬运（可选但建议） */
    @Override
    public int getMobilityFlag() {
        return 2; // 2 = 不可被活塞推动/拉动（更稳）
    }
}
