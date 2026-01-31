package funwayguy.esm.ai;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

/**
 * A class made for some additional functions used and shared among the new AI systems
 */
public class AIUtils {

    public static float getBreakSpeed(EntityLiving entityLiving, ItemStack stack, Block block, int meta) {
        float f = (stack == null ? 1.0F
            : stack.getItem()
                .getDigSpeed(stack, block, meta));

        if (f > 1.0F) {
            int i = EnchantmentHelper.getEfficiencyModifier(entityLiving);
            ItemStack itemstack = entityLiving.getEquipmentInSlot(0);

            if (i > 0 && itemstack != null) {
                float f1 = (float) (i * i + 1);

                boolean canHarvest = ForgeHooks.canToolHarvestBlock(block, meta, itemstack);

                if (!canHarvest && f <= 1.0F) {
                    f += f1 * 0.08F;
                } else {
                    f += f1;
                }
            }
        }

        if (entityLiving.isPotionActive(Potion.digSpeed)) {
            f *= 1.0F + (float) (entityLiving.getActivePotionEffect(Potion.digSpeed)
                .getAmplifier() + 1) * 0.2F;
        }

        if (entityLiving.isPotionActive(Potion.digSlowdown)) {
            f *= 1.0F - (float) (entityLiving.getActivePotionEffect(Potion.digSlowdown)
                .getAmplifier() + 1) * 0.2F;
        }

        if (entityLiving.isInsideOfMaterial(Material.water)
            && !EnchantmentHelper.getAquaAffinityModifier(entityLiving)) {
            f /= 5.0F;
        }

        if (!entityLiving.onGround) {
            f /= 5.0F;
        }

        return (f < 0 ? 0 : f);
    }

    public static float getBlockStrength(EntityLiving entityLiving, Block block, World world, int x, int y, int z,
        boolean ignoreTool) {
        // Returns true if something like Iguana Tweaks is nerfing the vanilla picks. This will then cause zombies to
        // ignore the harvestability of blocks when holding picks
        boolean nerfedPick = !Items.iron_pickaxe.canHarvestBlock(Blocks.stone, new ItemStack(Items.iron_pickaxe));
        int metadata = world.getBlockMetadata(x, y, z);
        float hardness = block.getBlockHardness(world, x, y, z);

        if (hardness < 0.0F) {
            return 0.0F;
        }

        ItemStack item = entityLiving.getEquipmentInSlot(0);
        boolean canHarvest = ignoreTool || (item != null && (item.getItem()
            .canHarvestBlock(block, item) || (item.getItem() instanceof ItemPickaxe && nerfedPick)))
            || block.getMaterial()
                .isToolNotRequired();

        if (!canHarvest) {
            return getBreakSpeed(entityLiving, item, block, metadata) / hardness / 100F;
        } else {
            return getBreakSpeed(entityLiving, item, block, metadata) / hardness / 30F;
        }
    }

    public static Entity RayCastEntities(World world, double x, double y, double z, float yaw, float pitch, double dist,
        EntityLivingBase source) {
        Vec3 vec3 = Vec3.createVectorHelper(x, y, z);
        float f3 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f4 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f5 = -MathHelper.cos(-pitch * 0.017453292F);
        float f6 = MathHelper.sin(-pitch * 0.017453292F);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        double d3 = dist; // Ray Distance
        Vec3 vec31 = vec3.addVector((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
        return RayCastEntities(world, vec3, vec31, source);
    }

    public static Entity RayCastEntities(World world, double x, double y, double z, float yaw, float pitch,
        double dist) {
        Vec3 vec3 = Vec3.createVectorHelper(x, y, z);
        float f3 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f4 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f5 = -MathHelper.cos(-pitch * 0.017453292F);
        float f6 = MathHelper.sin(-pitch * 0.017453292F);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        double d3 = dist; // Ray Distance
        Vec3 vec31 = vec3.addVector((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
        return RayCastEntities(world, vec3, vec31, null);
    }

    public static Entity RayCastEntities(World world, Vec3 start, Vec3 end, EntityLivingBase source) {
        double d0 = start.distanceTo(end);
        double d1 = d0;
        Vec3 vec3 = start;
        Vec3 vec32 = end;
        Entity pointedEntity = null;
        @SuppressWarnings("unchecked")
        List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(
            source,
            AxisAlignedBB
                .getBoundingBox(start.xCoord, start.yCoord, start.zCoord, start.xCoord, start.yCoord, start.zCoord)
                .expand(d0, d0, d0));
        double d2 = d1;

        for (int i = 0; i < list.size(); ++i) {
            Entity entity = (Entity) list.get(i);

            if (entity.canBeCollidedWith()) {
                float f2 = entity.getCollisionBorderSize();
                AxisAlignedBB axisalignedbb = entity.boundingBox.expand((double) f2, (double) f2, (double) f2);
                MovingObjectPosition movingobjectposition = axisalignedbb.calculateIntercept(vec3, vec32);

                if (axisalignedbb.isVecInside(vec3)) {
                    if (0.0D < d2 || d2 == 0.0D) {
                        pointedEntity = entity;
                        d2 = 0.0D;
                    }
                } else if (movingobjectposition != null) {
                    double d3 = vec3.distanceTo(movingobjectposition.hitVec);

                    if (d3 < d2 || d2 == 0.0D) {
                        if (source != null && entity == source.ridingEntity && !entity.canRiderInteract()) {
                            if (d2 == 0.0D) {
                                pointedEntity = entity;
                            }
                        } else {
                            pointedEntity = entity;
                            d2 = d3;
                        }
                    }
                }
            }
        }

        return pointedEntity;
    }

    public static MovingObjectPosition RayCastBlocks(World world, double x, double y, double z, float yaw, float pitch,
        double dist, boolean liquids) {
        Vec3 vec3 = Vec3.createVectorHelper(x, y, z);
        float f3 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f4 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f5 = -MathHelper.cos(-pitch * 0.017453292F);
        float f6 = MathHelper.sin(-pitch * 0.017453292F);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        double d3 = dist; // Ray Distance
        Vec3 vec31 = vec3.addVector((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
        return RayCastBlocks(world, vec3, vec31, liquids);
    }

    public static MovingObjectPosition RayCastBlocks(World world, Vec3 vector1, Vec3 vector2, boolean liquids) {
        return world.func_147447_a(vector1, vector2, liquids, !liquids, false);
    }
}
