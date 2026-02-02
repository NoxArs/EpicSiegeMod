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

public class AIUtils {
    private static final boolean NERFED_PICKAXES = !Items.iron_pickaxe
        .canHarvestBlock(Blocks.stone, new ItemStack(Items.iron_pickaxe));
    private static final float RADIAN = 0.017453292F;

    public static float getBreakSpeed(EntityLiving entityLiving, ItemStack stack, Block block, int meta) {
        // Use the passed stack if possible, otherwise fallback to held item
        if (stack == null) stack = entityLiving.getEquipmentInSlot(0);

        float speed = (stack == null ? 1.0F
            : stack.getItem()
                .getDigSpeed(stack, block, meta));

        // Efficiency handling:
        // Apply only when base speed > 1.0F (vanilla-style gate)
        if (speed > 1.0F) {
            int eff = EnchantmentHelper.getEfficiencyModifier(entityLiving);
            if (eff > 0 && stack != null) {
                float bonus = (float) (eff * eff + 1);
                boolean canHarvest = ForgeHooks.canToolHarvestBlock(block, meta, stack);
                speed += canHarvest ? bonus : bonus * 0.08F;
            }
        }

        // Potions
        if (entityLiving.isPotionActive(Potion.digSpeed)) {
            speed *= 1.0F + (float) (entityLiving.getActivePotionEffect(Potion.digSpeed)
                .getAmplifier() + 1) * 0.2F;
        }

        if (entityLiving.isPotionActive(Potion.digSlowdown)) {
            speed *= 1.0F - (float) (entityLiving.getActivePotionEffect(Potion.digSlowdown)
                .getAmplifier() + 1) * 0.2F;
        }
        if (entityLiving.isInsideOfMaterial(Material.water)
            && !EnchantmentHelper.getAquaAffinityModifier(entityLiving)) {
            speed /= 5.0F;
        }

        // Airborne penalty
        if (!entityLiving.onGround) {
            speed /= 5.0F;
        }

        return Math.max(speed, 0.0F);
    }

    public static float getBlockStrength(EntityLiving entityLiving, Block block, World world, int x, int y, int z,
        boolean ignoreTool) {
        float hardness = block.getBlockHardness(world, x, y, z);
        if (hardness <= 0.0F) {
            return 0.0F;
        }

        int meta = world.getBlockMetadata(x, y, z);
        ItemStack stack = entityLiving.getEquipmentInSlot(0);
        Material mat = block.getMaterial();

        // Determine "can harvest" (affects divisor 30 vs 100)
        boolean canHarvest = false;

        if (ignoreTool) {
            canHarvest = true;
        } else if (mat != null && mat.isToolNotRequired()) {
            canHarvest = true;
        } else if (stack != null && stack.getItem() != null) {
            boolean forgeHarvest = ForgeHooks.canToolHarvestBlock(block, meta, stack);
            boolean vanillaHarvest = stack.getItem()
                .canHarvestBlock(block, stack);

            boolean pickaxeException = (NERFED_PICKAXES && stack.getItem() instanceof ItemPickaxe
                && mat == Material.rock);

            canHarvest = forgeHarvest || vanillaHarvest || pickaxeException;
        }

        // Calculate speed
        float speed = getBreakSpeed(entityLiving, stack, block, meta);
        if (speed <= 0.0F || Float.isNaN(speed) || Float.isInfinite(speed)) return 0.0F;
        // Vanilla-ish divisor scheme (30 if harvestable, 100 otherwise)
        return canHarvest ? (speed / hardness / 30F) : (speed / hardness / 100F);
    }

    private static Vec3 getVectorFromRotation(float yaw, float pitch) {
        float f3 = MathHelper.cos(-yaw * RADIAN - (float) Math.PI);
        float f4 = MathHelper.sin(-yaw * RADIAN - (float) Math.PI);
        float f5 = -MathHelper.cos(-pitch * RADIAN);
        float f6 = MathHelper.sin(-pitch * RADIAN);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        return Vec3.createVectorHelper(f7, f6, f8);
    }

    public static Entity RayCastEntities(World world, double x, double y, double z, float yaw, float pitch, double dist,
        EntityLivingBase source) {
        Vec3 start = Vec3.createVectorHelper(x, y, z);
        Vec3 lookVec = getVectorFromRotation(yaw, pitch);
        Vec3 end = start.addVector(lookVec.xCoord * dist, lookVec.yCoord * dist, lookVec.zCoord * dist);

        return RayCastEntities(world, start, end, source);
    }

    public static Entity RayCastEntities(World world, double x, double y, double z, float yaw, float pitch,
        double dist) {
        return RayCastEntities(world, x, y, z, yaw, pitch, dist, null);
    }

    public static Entity RayCastEntities(World world, Vec3 start, Vec3 end, EntityLivingBase source) {
        double dist = start.distanceTo(end);
        Entity pointedEntity = null;

        List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(
            source,
            AxisAlignedBB
                .getBoundingBox(start.xCoord, start.yCoord, start.zCoord, start.xCoord, start.yCoord, start.zCoord)
                .expand(dist, dist, dist));

        double closest = dist;

        for (int i = 0; i < list.size(); ++i) {
            Entity entity = list.get(i);

            if (entity.canBeCollidedWith()) {
                float border = entity.getCollisionBorderSize();
                AxisAlignedBB bb = entity.boundingBox.expand(border, border, border);
                MovingObjectPosition hit = bb.calculateIntercept(start, end);

                if (bb.isVecInside(start)) {
                    if (0.0D < closest || closest == 0.0D) {
                        pointedEntity = entity;
                        closest = 0.0D;
                    }
                } else if (hit != null) {
                    double d = start.distanceTo(hit.hitVec);

                    if (d < closest || closest == 0.0D) {
                        if (source != null && entity == source.ridingEntity && !entity.canRiderInteract()) {
                            if (closest == 0.0D) {
                                pointedEntity = entity;
                            }
                        } else {
                            pointedEntity = entity;
                            closest = d;
                        }
                    }
                }
            }
        }

        return pointedEntity;
    }

    public static MovingObjectPosition RayCastBlocks(World world, double x, double y, double z, float yaw, float pitch,
        double dist, boolean liquids) {
        Vec3 start = Vec3.createVectorHelper(x, y, z);
        Vec3 lookVec = getVectorFromRotation(yaw, pitch);
        Vec3 end = start.addVector(lookVec.xCoord * dist, lookVec.yCoord * dist, lookVec.zCoord * dist);

        return RayCastBlocks(world, start, end, liquids);
    }

    public static MovingObjectPosition RayCastBlocks(World world, Vec3 vector1, Vec3 vector2, boolean liquids) {
        // world.rayTraceBlocks equivalent in 1.7.10
        return world.func_147447_a(vector1, vector2, liquids, !liquids, false);
    }
}
