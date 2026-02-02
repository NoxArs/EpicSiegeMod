package funwayguy.esm.ai;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import funwayguy.esm.core.BlockAndMeta;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAIGrief extends EntityAIBase {

    private final EntityLiving entityLiving;

    private int markedX, markedY, markedZ;
    private boolean hasMarked;

    private int digTick = 0;
    private int pathRetry = 0;

    /** reach distance squared */
    private final double reachDistSq = 16.0D;

    /** True if some mod nerfs vanilla pickaxe harvestability (e.g., Iguana Tweaks) */
    private final boolean TOOLS_NERFED;

    /** Throttle scanning; replaces ticksExisted % 5 gate */
    private int scanCooldown = 0;

    public ESM_EntityAIGrief(EntityLiving entity) {
        this.entityLiving = entity;
        this.TOOLS_NERFED = !Items.iron_pickaxe.canHarvestBlock(Blocks.stone, new ItemStack(Items.iron_pickaxe));
    }

    @Override
    public boolean shouldExecute() {
        if (!isTimeAllowed()) return false;
        if (entityLiving.getAttackTarget() != null) return false;

        // throttle
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }

        final int baseX = MathHelper.floor_double(entityLiving.posX);
        final int baseY = MathHelper.floor_double(entityLiving.posY);
        final int baseZ = MathHelper.floor_double(entityLiving.posZ);

        final ItemStack held = entityLiving.getEquipmentInSlot(0);
        final BlockAndMeta[] griefList = ESM_Settings.getZombieGriefBlocks();
        if (griefList == null) return false;
        final int tries = Math.max(0, ESM_Settings.ZombieGriefBlocksTriesPerTick);
        boolean didScan = false;

        for (int n = 0; n < tries; n++) {
            didScan = true;

            int x = baseX + randRange(ESM_Settings.ZombieGriefBlocksRangeXZ);
            int y = baseY + randRange(ESM_Settings.ZombieGriefBlocksRangeY);
            int z = baseZ + randRange(ESM_Settings.ZombieGriefBlocksRangeXZ);

            // Y clamp for 1.7.10 worlds (0..255)
            if (y <= 0 || y >= 255) continue;

            Block block = entityLiving.worldObj.getBlock(x, y, z);
            if (block == null || block == Blocks.air) continue;

            Material mat = block.getMaterial();
            if (mat instanceof MaterialLiquid) continue; // only liquids are force-skipped

            int meta = entityLiving.worldObj.getBlockMetadata(x, y, z);

            if (!isAllowedTargetBlock(block, meta, griefList)) continue;

            // hardness check last
            if (block.getBlockHardness(entityLiving.worldObj, x, y, z) < 0.0F) continue;

            if (!canDigBlock(block, held)) continue;

            markTarget(x, y, z);
            scanCooldown = 5; // set after a real scan attempt
            return true;
        }

        if (didScan) scanCooldown = 5;
        return false;
    }

    @Override
    public boolean continueExecuting() {
        if (!hasMarked) return false;
        if (!entityLiving.isEntityAlive()) return false;
        if (entityLiving.getAttackTarget() != null) {
            clearMark();
            return false;
        }

        Block block = entityLiving.worldObj.getBlock(markedX, markedY, markedZ);
        if (block == null || block == Blocks.air) {
            clearMark();
            return false;
        }

        final BlockAndMeta[] griefList = ESM_Settings.getZombieGriefBlocks();
        if (griefList == null) return false;
        int meta = entityLiving.worldObj.getBlockMetadata(markedX, markedY, markedZ);

        if (!isAllowedTargetBlock(block, meta, griefList)) {
            clearMark();
            return false;
        }

        ItemStack held = entityLiving.getEquipmentInSlot(0);
        if (!canDigBlock(block, held)) {
            clearMark();
            return false;
        }

        return true;
    }

    @Override
    public void updateTask() {
        if (!hasMarked || !entityLiving.isEntityAlive() || entityLiving.getAttackTarget() != null) {
            clearMark();
            return;
        }

        Block block = entityLiving.worldObj.getBlock(markedX, markedY, markedZ);
        if (block == null || block == Blocks.air) {
            clearMark();
            return;
        }

        // too far: move closer
        if (entityLiving.getDistanceSq(markedX + 0.5D, markedY + 0.5D, markedZ + 0.5D) >= reachDistSq) {
            if (entityLiving.getNavigator()
                .noPath()) {
                entityLiving.getNavigator()
                    .tryMoveToXYZ(markedX, markedY, markedZ, 1D);
                pathRetry++;
            }

            if (pathRetry >= 40) clearMark();
            digTick = 0;
            return;
        }

        // visibility check (throttled)
        if ((entityLiving.ticksExisted % 10) == 0) {
            if (!canSeeBlockAt(markedX, markedY, markedZ)) {
                clearMark();
                return;
            }
        }

        digTick++;

        float strength = AIUtils.getBlockStrength(
            entityLiving,
            block,
            entityLiving.worldObj,
            markedX,
            markedY,
            markedZ,
            !ESM_Settings.ZombieDiggerTools) * (digTick + 1);

        if (strength >= 1.0F) {
            // Re-validate target right before break (world may have changed)
            final BlockAndMeta[] griefList = ESM_Settings.getZombieGriefBlocks();
            if (griefList == null) return;
            int meta = entityLiving.worldObj.getBlockMetadata(markedX, markedY, markedZ);

            if (!isAllowedTargetBlock(block, meta, griefList)) {
                clearMark();
                return;
            }

            if (block.getBlockHardness(entityLiving.worldObj, markedX, markedY, markedZ) < 0.0F) {
                clearMark();
                return;
            }

            ItemStack held = entityLiving.getEquipmentInSlot(0);
            boolean drop = canDigBlock(block, held);
            entityLiving.worldObj.func_147480_a(markedX, markedY, markedZ, drop);

            clearMark();
            return;
        }

        // feedback
        if (digTick % 5 == 0) {
            if (block.stepSound != null) {
                entityLiving.worldObj.playSoundAtEntity(
                    entityLiving,
                    block.stepSound.getStepResourcePath(),
                    block.stepSound.getVolume() + 1F,
                    block.stepSound.getPitch());
            }
            entityLiving.swingItem();
        }
    }

    private boolean isTimeAllowed() {
        // Make semantics explicit (keeping YOUR current behavior):
        // AnyTime => allowed
        // Otherwise, if "only when siege allowed" is false => still allowed
        // Otherwise, require siege allowed.
        if (ESM_Settings.ZombieGriefBlocksAnyTime) return true;
        if (!ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed) return true;
        return ESM_Utils.isSiegeAllowed(entityLiving.worldObj.getWorldTime());
    }

    private boolean isAllowedTargetBlock(Block block, int meta, BlockAndMeta[] griefList) {
        if (BlockAndMeta.isInBlockAndMetaList(block, meta, griefList)) return true;
        return ESM_Settings.ZombieGriefBlocksLightSources && block.getLightValue() > 0;
    }

    private boolean canDigBlock(Block block, ItemStack held) {
        if (!ESM_Settings.ZombieDiggerTools) return true;
        if (ESM_Settings.ZombieGriefBlocksNoTool) return true;

        Material mat = block.getMaterial();
        if (mat != null && mat.isToolNotRequired()) return true;

        if (held == null) return false;

        Item item = held.getItem(); // 1.7.10: held != null => item should exist
        if (item == null) return false; // defensive for broken mod stacks

        if (item.canHarvestBlock(block, held)) return true;

        // legacy exception: if tools nerfed, let any pickaxe dig rock
        if (TOOLS_NERFED && item instanceof ItemPickaxe && mat == Material.rock) return true;

        return false;
    }

    private void markTarget(int x, int y, int z) {
        markedX = x;
        markedY = y;
        markedZ = z;

        hasMarked = true;
        digTick = 0;
        pathRetry = 0;

        entityLiving.getNavigator()
            .tryMoveToXYZ(markedX, markedY, markedZ, 1D);
    }

    private void clearMark() {
        hasMarked = false;
        digTick = 0;
        pathRetry = 0;
    }

    private int randRange(int range) {
        if (range <= 0) return 0;
        return entityLiving.getRNG()
            .nextInt(range * 2 + 1) - range;
    }

    private boolean canSeeBlockAt(int posX, int posY, int posZ) {
        if (isCollidingWithPotentialTarget(posX, posY, posZ)) return true;

        Vec3 eye = Vec3.createVectorHelper(
            entityLiving.posX,
            entityLiving.posY + (double) entityLiving.getEyeHeight(),
            entityLiving.posZ);

        // Aim at center
        Vec3 target = Vec3.createVectorHelper(posX + 0.5D, posY + 0.5D, posZ + 0.5D);

        MovingObjectPosition mop = entityLiving.worldObj.func_147447_a(eye, target, false, false, true);
        if (mop == null) return false;

        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return mop.blockX == posX && mop.blockY == posY && mop.blockZ == posZ;
        }
        return false;
    }

    private boolean isCollidingWithPotentialTarget(int posX, int posY, int posZ) {
        if (isWithinRangeOf(posX, MathHelper.floor_double(entityLiving.posX), 1)
            && isWithinRangeOf(posZ, MathHelper.floor_double(entityLiving.posZ), 1)) {
            int eyeY = MathHelper.floor_double(entityLiving.posY + (double) entityLiving.getEyeHeight());
            if (isWithinRangeOf(posY, eyeY, 1)) return true;

            int feetY = MathHelper.floor_double(entityLiving.posY);
            if (isWithinRangeOf(posY, feetY, 1)) return true;
        }
        return false;
    }

    private boolean isWithinRangeOf(int from, int to, int range) {
        return Math.abs(from - to) <= range;
    }
}
