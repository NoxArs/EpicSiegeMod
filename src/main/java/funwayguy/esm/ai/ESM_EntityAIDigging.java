package funwayguy.esm.ai;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import funwayguy.esm.core.BlockAndMeta;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAIDigging extends EntityAIBase {

    EntityLivingBase target;
    // int[] markedLoc;
    int markedX, markedY, markedZ;
    boolean hasMarked = false;
    EntityLiving entityDigger;
    int digTick = 0;
    int scanTick = 0;
    final boolean TOOLS_NERFED;

    public ESM_EntityAIDigging(EntityLiving entity) {
        this.entityDigger = entity;
        // Returns true if something like Iguana Tweaks is nerfing the vanilla picks. This will then cause zombies to
        // ignore the harvestability of blocks when holding picks
        TOOLS_NERFED = !Items.iron_pickaxe.canHarvestBlock(Blocks.stone, new ItemStack(Items.iron_pickaxe));
    }

    private void refreshTarget() {
        EntityLivingBase cur = entityDigger.getAttackTarget();
        if (cur != null && cur.isEntityAlive()) {
            target = cur;
        } else if (target != null && !target.isEntityAlive()) {
            target = null;
        }
    }

    @Override
    public boolean shouldExecute() {
        if (ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed)
            if (!ESM_Utils.isSiegeAllowed(entityDigger.worldObj.getWorldTime())) return false;

        target = entityDigger.getAttackTarget();

        if (entityDigger.ticksExisted % 10 == 0 && target != null
            && entityDigger.getNavigator()
                .noPath()
            && entityDigger.getDistanceSqToEntity(target) > 1.0D
            && (target.onGround || !entityDigger.canEntityBeSeen(target))) {
            MovingObjectPosition mop = GetNextObstical(entityDigger, 2D, 4);

            if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) {
                return false;
            }
            Block block = entityDigger.worldObj.getBlock(mop.blockX, mop.blockY, mop.blockZ);

            if (canHarvestBlock(block)) {
                markedX = mop.blockX;
                markedY = mop.blockY;
                markedZ = mop.blockZ;
                hasMarked = true;
                // ESM.log.warn("Zombie digger with ID " + entityDigger.getEntityId() + " has set target to entity '" +
                // target.getCommandSenderName() + "', ID " + target.getEntityId());
                return true;
            } else {
                // ESM.log.warn("Zombie digger with ID can NOT REACH ENTITY!");
                return false;
            }
        }

        return false;
    }

    @Override
    public boolean continueExecuting() {
        refreshTarget(); // <- 新增

        return target != null && entityDigger != null
            && entityDigger.isEntityAlive()
            && hasMarked
            && entityDigger.getNavigator()
                .noPath()
            && entityDigger.getDistanceToEntity(target) > 1D
            && (target.onGround || !entityDigger.canEntityBeSeen(target));
    }

    @Override
    public void updateTask() {
        // Returns true if something like Iguana Tweaks is nerfing the vanilla picks. This will then cause zombies to
        // ignore the harvestability of blocks when holding picks
        refreshTarget(); // <- 新增
        if (target == null) { // <- 新增：目标没了立刻停
            resetTask();
            return;
        }
        MovingObjectPosition mop = null;
        if (entityDigger.ticksExisted % 5 == 0) {
            mop = GetNextObstical(entityDigger, 2D, 4);
        }

        if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
            markedX = mop.blockX;
            markedY = mop.blockY;
            markedZ = mop.blockZ;
            hasMarked = true;
        }

        if (!isMarkedBlockValid()) {
            if (hasMarked) {
                entityDigger.worldObj
                    .destroyBlockInWorldPartially(entityDigger.getEntityId(), markedX, markedY, markedZ, -1);
            }

            digTick = 0;
            hasMarked = false;
            return;
        }

        Block block = entityDigger.worldObj.getBlock(markedX, markedY, markedZ);
        digTick++;

        float str = AIUtils.getBlockStrength(
            this.entityDigger,
            block,
            entityDigger.worldObj,
            markedX,
            markedY,
            markedZ,
            !ESM_Settings.ZombieDiggerTools) * (digTick + 1);

        if (str >= 1F) {
            digTick = 0;
            entityDigger.worldObj.func_147480_a(markedX, markedY, markedZ, canHarvestBlock(block));
            hasMarked = false;
            entityDigger.getNavigator()
                .setPath(
                    entityDigger.getNavigator()
                        .getPathToEntityLiving(target),
                    1D);
        } else {
            if (digTick % 5 == 0) {
                entityDigger.worldObj.playSoundAtEntity(
                    entityDigger,
                    block.stepSound.getStepResourcePath(),
                    block.stepSound.getVolume() + 1F,
                    block.stepSound.getPitch());
                entityDigger.swingItem();
                entityDigger.worldObj.destroyBlockInWorldPartially(
                    entityDigger.getEntityId(),
                    markedX,
                    markedY,
                    markedZ,
                    (int) (str * 10F));
            }
        }
    }

    private boolean canHarvestBlock(Block block) {
        if (!ESM_Settings.ZombieDiggerTools) return true;
        if (block.getMaterial()
            .isToolNotRequired()) return true;
        ItemStack item = entityDigger.getEquipmentInSlot(0);
        if (item != null) {
            if (item.getItem()
                .canHarvestBlock(block, item)) return true;
            if (item.getItem() instanceof ItemPickaxe)
                if (TOOLS_NERFED && block.getMaterial() == Material.rock) return true;
        }

        return false;
    }

    @Override
    public void resetTask() {
        hasMarked = false;
        digTick = 0;
    }

    /**
     * Rolls through all the points in the bounding box of the entity and raycasts them toward it's current heading to
     * return any blocks that may be obstructing it's path.
     * The bigger the entity the longer this calculation will take due to the increased number of points (Generic bipeds
     * should only need 2)
     */
    public MovingObjectPosition GetNextObstical(EntityLivingBase entityLiving, double dist, int attempts) {
        float f = 1.0F;
        float f1 = entityLiving.prevRotationPitch + (entityLiving.rotationPitch - entityLiving.prevRotationPitch) * f;
        float f2 = entityLiving.prevRotationYaw + (entityLiving.rotationYaw - entityLiving.prevRotationYaw) * f;

        int digWidth = MathHelper.ceiling_double_int(entityLiving.width);
        int digHeight = MathHelper.ceiling_double_int(entityLiving.height);
        int passMax = digWidth * digWidth * digHeight;

        // 防御：避免 0
        if (passMax <= 0) passMax = 1;

        for (int i = 0; i < attempts; i++) {
            int x = scanTick % digWidth - (digWidth / 2);
            int y = scanTick / (digWidth * digWidth);
            int z = (scanTick % (digWidth * digWidth)) / digWidth - (digWidth / 2);

            double rayX = x + entityLiving.posX;
            double rayY = y + entityLiving.posY;
            double rayZ = z + entityLiving.posZ;

            MovingObjectPosition mop = AIUtils
                .RayCastBlocks(entityLiving.worldObj, rayX, rayY, rayZ, f2, f1, dist, false);

            // 默认：本次没成功就推进 scanTick，尝试下一个点
            scanTick = (scanTick + 1) % passMax;

            if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) continue;

            Block block = entityLiving.worldObj.getBlock(mop.blockX, mop.blockY, mop.blockZ);
            int meta = entityLiving.worldObj.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ);
            ItemStack item = entityLiving.getEquipmentInSlot(0);

            boolean inList = BlockAndMeta.isInBlockAndMetaList(block, meta, ESM_Settings.getZombieDigBlacklist());
            if (!ESM_Settings.ZombieSwapList) {
                if (inList) continue; // 黑名单：在表里 -> 跳过
            } else {
                if (!inList) continue; // 白名单：不在表 -> 跳过
            }

            boolean toolOk = !ESM_Settings.ZombieDiggerTools || (item != null && (item.getItem()
                .canHarvestBlock(block, item)
                || (item.getItem() instanceof ItemPickaxe && TOOLS_NERFED && block.getMaterial() == Material.rock)))
                || block.getMaterial()
                    .isToolNotRequired();

            if (toolOk) {
                scanTick = 0; // 你原本命中后会归零
                return mop;
            }
        }
        return null;
    }

    private boolean isMarkedBlockValid() {
        if (!hasMarked) return false;
        if (markedY < 0 || markedY >= 256) return false; // 1.7.10 高度上限通常 256

        // blockExists 在 1.7.10 常见可用；如果你环境没有，就用 checkChunksExist
        if (!entityDigger.worldObj.blockExists(markedX, markedY, markedZ)) return false;

        return true;
    }
}
