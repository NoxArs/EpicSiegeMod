package funwayguy.esm.ai;

import net.minecraft.block.Block;
import net.minecraft.block.material.*;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import funwayguy.esm.core.BlockAndMeta;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAIGrief extends EntityAIBase {

    EntityLiving entityLiving;
    int markedX, markedY, markedZ;
    boolean hasMarked;
    int digTick = 0;
    double reachDistSq = 16.0D; // distance squared (I think?) of reach distance
    int pathRetry = 0;
    final boolean TOOLS_NERFED;

    public ESM_EntityAIGrief(EntityLiving entity) {
        this.entityLiving = entity;
        TOOLS_NERFED = !Items.iron_pickaxe.canHarvestBlock(Blocks.stone, new ItemStack(Items.iron_pickaxe));

    }

    @Override
    public boolean shouldExecute() {
        /*
         * if(this.entityLiving.getRNG().nextInt(4) != 0) // Severely nerfs how many time the next part of the script
         * can run
         * {
         * return false;
         * }
         */

        if (!ESM_Settings.ZombieGriefBlocksAnyTime) if (ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed)
            if (!ESM_Utils.isSiegeAllowed(entityLiving.worldObj.getWorldTime())) return false;

        // Returns true if something like Iguana Tweaks is nerfing the vanilla picks. This will then cause zombies to
        // ignore the harvestability of blocks when holding picks
        int i = MathHelper.floor_double(entityLiving.posX);
        int j = MathHelper.floor_double(entityLiving.posY);
        int k = MathHelper.floor_double(entityLiving.posZ);

        if (entityLiving.getAttackTarget() != null) {
            return false;
        }

        ItemStack item = entityLiving.getEquipmentInSlot(0);

        if ((entityLiving.ticksExisted % 5) != 0) return false;
        int checks = 0;
        while (checks < ESM_Settings.ZombieGriefBlocksTriesPerTick) {
            int checkX = i + entityLiving.getRNG()
                .nextInt(ESM_Settings.ZombieGriefBlocksRangeXZ * 2) - ESM_Settings.ZombieGriefBlocksRangeXZ;
            int checkY = j + entityLiving.getRNG()
                .nextInt(ESM_Settings.ZombieGriefBlocksRangeY * 2) - ESM_Settings.ZombieGriefBlocksRangeY;
            int checkZ = k + entityLiving.getRNG()
                .nextInt(ESM_Settings.ZombieGriefBlocksRangeXZ * 2) - ESM_Settings.ZombieGriefBlocksRangeXZ;

            Block block = entityLiving.worldObj.getBlock(checkX, checkY, checkZ);
            if (block.getMaterial() instanceof MaterialLiquid || block.getMaterial() instanceof MaterialTransparent) {
                checks++;
                continue;
            }
            int meta = entityLiving.worldObj.getBlockMetadata(checkX, checkY, checkZ);

            if ((BlockAndMeta.isInBlockAndMetaList(block, meta, ESM_Settings.getZombieGriefBlocks())
                || (ESM_Settings.ZombieGriefBlocksLightSources && block.getLightValue() > 0))
                && block.getBlockHardness(entityLiving.worldObj, checkX, checkY, checkZ) >= 0) {
                if (!ESM_Settings.ZombieDiggerTools || ESM_Settings.ZombieGriefBlocksNoTool
                    || (item != null && (item.getItem()
                        .canHarvestBlock(block, item)
                        || (item.getItem() instanceof ItemPickaxe && TOOLS_NERFED
                            && block.getMaterial() == Material.rock)))
                    || block.getMaterial()
                        .isToolNotRequired()) {
                    markedX = checkX;
                    markedY = checkY;
                    markedZ = checkZ;
                    pathRetry = 0;
                    hasMarked = true;
                    entityLiving.getNavigator()
                        .tryMoveToXYZ(markedX, markedY, markedZ, 1D);
                    digTick = 0;
                    return true;
                }
            }
            checks++;
        }
        return false;
    }

    @Override
    public boolean continueExecuting() {
        // Returns true if something like Iguana Tweaks is nerfing the vanilla picks. This will then cause zombies to
        // ignore the harvestability of blocks when holding picks

        if (!hasMarked || !entityLiving.isEntityAlive() || entityLiving.getAttackTarget() != null) {
            hasMarked = false;
            return false;
        }

        Block block = entityLiving.worldObj.getBlock(markedX, markedY, markedZ);
        int meta = entityLiving.worldObj.getBlockMetadata(markedX, markedY, markedZ);
        boolean inList = BlockAndMeta.isInBlockAndMetaList(block, meta, ESM_Settings.getZombieGriefBlocks());
        boolean allowed = inList || (ESM_Settings.ZombieGriefBlocksLightSources && block.getLightValue() > 0);
        if (block == Blocks.air || !allowed) {
            hasMarked = false;
            return false;
        }

        ItemStack item = entityLiving.getEquipmentInSlot(0);
        return !ESM_Settings.ZombieDiggerTools || ESM_Settings.ZombieGriefBlocksNoTool
            || (item != null && (item.getItem()
                .canHarvestBlock(block, item)
                || (item.getItem() instanceof ItemPickaxe && TOOLS_NERFED && block.getMaterial() == Material.rock)))
            || block.getMaterial()
                .isToolNotRequired();

    }

    @Override
    public void updateTask() {
        // Returns true if something like Iguana Tweaks is nerfing the vanilla picks. This will then cause zombies to
        // ignore the harvestability of blocks when holding picks

        if (!hasMarked || !entityLiving.isEntityAlive() || entityLiving.getAttackTarget() != null) {
            digTick = 0;
            hasMarked = false;
            return;
        }

        Block block = entityLiving.worldObj.getBlock(markedX, markedY, markedZ);
        if (block == Blocks.air) {
            digTick = 0;
            hasMarked = false;
            return;
        }

        if (entityLiving.getDistanceSq(markedX + 0.5D, markedY + 0.5D, markedZ + 0.5D) >= reachDistSq) {
            // griefable object is too far, need to move closer
            if (entityLiving.getNavigator()
                .noPath()) {
                // too far AND can't get a valid path, try and path again
                entityLiving.getNavigator()
                    .tryMoveToXYZ(markedX, markedY, markedZ, 1D);
                pathRetry++;
            }
            if (pathRetry >= 40) // (╯°□°）╯︵ ┻━┻
                hasMarked = false;
            digTick = 0;
            return;
        }

        if ((entityLiving.ticksExisted % 10) == 0) {
            if (!canSeeBlockAt(markedX, markedY, markedZ)) {
                hasMarked = false;
                digTick = 0;
                return;
            }
        }

        digTick++;

        float str = AIUtils.getBlockStrength(
            entityLiving,
            block,
            entityLiving.worldObj,
            markedX,
            markedY,
            markedZ,
            !ESM_Settings.ZombieDiggerTools) * (digTick + 1);

        if (str >= 1F) {
            digTick = 0;

            if (hasMarked) {
                ItemStack item = entityLiving.getEquipmentInSlot(0);
                boolean canHarvest = !ESM_Settings.ZombieDiggerTools || (item != null && (item.getItem()
                    .canHarvestBlock(block, item)
                    || (item.getItem() instanceof ItemPickaxe && TOOLS_NERFED && block.getMaterial() == Material.rock)))
                    || block.getMaterial()
                        .isToolNotRequired();
                entityLiving.worldObj.func_147480_a(markedX, markedY, markedZ, canHarvest);
                hasMarked = false;
            } else {
                hasMarked = false;
            }
        } else {
            if (digTick % 5 == 0) {
                entityLiving.worldObj.playSoundAtEntity(
                    entityLiving,
                    block.stepSound.getStepResourcePath(),
                    block.stepSound.getVolume() + 1F,
                    block.stepSound.getPitch());
                entityLiving.swingItem();
            }
        }
    }

    private boolean canSeeBlockAt(int posX, int posY, int posZ) {

        if (isCollidingWithPotentialTarget(posX, posY, posZ)) return true;

        Vec3 thisEntity = Vec3.createVectorHelper(
            entityLiving.posX,
            entityLiving.posY + (double) entityLiving.getEyeHeight(),
            entityLiving.posZ);
        Vec3 target = Vec3.createVectorHelper(posX, posY, posZ);
        MovingObjectPosition mop = entityLiving.worldObj.func_147447_a(thisEntity, target, false, false, true);

        if (mop == null) return false;

        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
            if (mop.blockX == posX && mop.blockY == posY && mop.blockZ == posZ) return true;

        return false;
    }

    private boolean isCollidingWithPotentialTarget(int posX, int posY, int posZ) {
        // entity might be colliding (or arrived) already....
        // ...no need to ray-trace. Allow grief if target is no more than 1 block away from the mob.
        if (isWithinRangeOf(posX, MathHelper.floor_double(entityLiving.posX), 1)) {
            if (isWithinRangeOf(posZ, MathHelper.floor_double(entityLiving.posZ), 1)) {
                if (isWithinRangeOf(
                    posY,
                    MathHelper.floor_double(entityLiving.posY + (double) entityLiving.getEyeHeight()),
                    1)) return true;
                // also check from feet-level. I've heard Zombies can kick.
                if (isWithinRangeOf(posY, MathHelper.floor_double(entityLiving.posY), 1)) return true;
            }
        }
        return false;
    }

    private boolean isWithinRangeOf(int from, int to, int range) {
        return (Math.abs(from - to) <= range) ? true : false;
    }
}
