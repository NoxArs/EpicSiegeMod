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
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

import funwayguy.esm.core.BlockAndMeta;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAIDigging extends EntityAIBase {

    private final EntityLiving digger;
    private EntityLivingBase target;

    // Current block being mined
    private int markedX, markedY, markedZ;
    private boolean hasMarked = false;

    // Mining progress: 0..1+
    private float progress = 0.0F;
    private int digTicks = 0; // ✅真实 tick 计数

    // Throttling
    private int scanCooldown = 0;
    private int scanTick = 0; // rolling scan index over full space
    private int progressCooldown = 0;

    // Tool nerf detection (GTNH/Iguana Tweaks)
    private final boolean TOOLS_NERFED;

    // Constants
    private static final int SCAN_INTERVAL = 10;
    private static final int PROGRESS_INTERVAL = 10;

    // ✅MAX_SCAN_POINTS：每次扫描最多尝试几条射线（不是截断空间）
    private static final int MAX_SCAN_POINTS = 16;
    private static final double SCAN_DISTANCE = 2.0D;

    public ESM_EntityAIDigging(EntityLiving entity) {
        this.digger = entity;
        this.setMutexBits(1);

        this.TOOLS_NERFED = !Items.iron_pickaxe.canHarvestBlock(Blocks.stone, new ItemStack(Items.iron_pickaxe));
    }

    // =========================================================
    // Life Cycle
    // =========================================================

    @Override
    public boolean shouldExecute() {
        if (ESM_Settings.ZombieEnhancementsOnlyWhenSiegeAllowed) {
            if (!ESM_Utils.isSiegeAllowed(digger.worldObj.getWorldTime())) return false;
        }

        target = digger.getAttackTarget();
        if (target == null || !target.isEntityAlive()) return false;

        // If we already have a valid marked block, keep going
        if (hasMarked && isMarkedBlockValid()) return true;

        // Don't dig if currently pathing or too close
        if (!digger.getNavigator()
            .noPath()) return false;
        if (digger.getDistanceSqToEntity(target) < 1.0D) return false;

        // Throttling
        if (--scanCooldown > 0) return false;
        scanCooldown = SCAN_INTERVAL;

        MovingObjectPosition mop = findNextObstacle(SCAN_DISTANCE);
        if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
            lockBlock(mop.blockX, mop.blockY, mop.blockZ);
            return true;
        }

        return false;
    }

    @Override
    public boolean continueExecuting() {
        if (digger == null || digger.isDead) return false;
        if (target == null || !target.isEntityAlive()) return false;
        return hasMarked && isMarkedBlockValid();
    }

    @Override
    public void startExecuting() {
        digger.getNavigator()
            .clearPathEntity();
    }

    @Override
    public void resetTask() {
        clearProgressVisual();
        hasMarked = false;
        progress = 0.0F;
        digTicks = 0;
        progressCooldown = 0;
        target = null;
    }

    @Override
    public void updateTask() {
        if (target == null || !target.isEntityAlive()) {
            resetTask();
            return;
        }

        // Keep still while digging
        digger.getNavigator()
            .clearPathEntity();

        // Look at block (visual)
        if (hasMarked) {
            digger.getLookHelper()
                .setLookPosition(markedX + 0.5, markedY + 0.5, markedZ + 0.5, 30F, 30F);
        }

        if (!isMarkedBlockValid()) {
            clearProgressVisual();
            hasMarked = false;
            progress = 0.0F;
            digTicks = 0;
            progressCooldown = 0;
            return;
        }

        World world = digger.worldObj;
        Block block = world.getBlock(markedX, markedY, markedZ);

        // unbreakable safety
        float hardness = block.getBlockHardness(world, markedX, markedY, markedZ);
        if (hardness < 0.0F) {
            clearProgressVisual();
            hasMarked = false;
            progress = 0.0F;
            digTicks = 0;
            return;
        }

        digTicks++;

        // per-tick progress fraction
        float perTick = AIUtils
            .getBlockStrength(digger, block, world, markedX, markedY, markedZ, !ESM_Settings.ZombieDiggerTools);

        if (perTick <= 0.0F || Float.isNaN(perTick) || Float.isInfinite(perTick)) {
            clearProgressVisual();
            hasMarked = false;
            progress = 0.0F;
            digTicks = 0;
            return;
        }

        progress += perTick;

        if (progress >= 1.0F) {
            String bn = String.valueOf(Block.blockRegistry.getNameForObject(block));
            int meta = world.getBlockMetadata(markedX, markedY, markedZ);

            EntityLivingBase tgt = target;

            world.func_147480_a(
                markedX,
                markedY,
                markedZ,
                canHarvestBlockAligned(block, world, markedX, markedY, markedZ));

            clearProgressVisual();
            hasMarked = false;
            progress = 0.0F;
            digTicks = 0;
            progressCooldown = 0;

            if (tgt != null && tgt.isEntityAlive()) {
                digger.getNavigator()
                    .setPath(
                        digger.getNavigator()
                            .getPathToEntityLiving(tgt),
                        1.0D);
            }
            return;
        }

        // Visual feedback throttled
        if (--progressCooldown <= 0) {
            progressCooldown = PROGRESS_INTERVAL;
            digger.swingItem();
            world.destroyBlockInWorldPartially(
                digger.getEntityId(),
                markedX,
                markedY,
                markedZ,
                Math.min(9, (int) (progress * 10.0F)));
        }
    }

    // =========================================================
    // Core Logic: ballistic raytrace + anti pillar/hole
    // =========================================================

    private MovingObjectPosition findNextObstacle(double dist) {
        // --- sampling grid around the digger's body ---
        int w = MathHelper.ceiling_double_int(digger.width);
        int h = MathHelper.ceiling_double_int(digger.height);

        int maxPoints = w * w * h;
        if (maxPoints <= 0) maxPoints = 1;

        World world = digger.worldObj;

        final double tx = target.posX;
        final double ty = target.posY + target.getEyeHeight();
        final double tz = target.posZ;

        final double ddx = digger.posX - target.posX;
        final double ddz = digger.posZ - target.posZ;
        final double hDistSq = ddx * ddx + ddz * ddz;

        for (int attempt = 0; attempt < MAX_SCAN_POINTS; attempt++) {
            int idx = (scanTick++ % maxPoints);

            int y = idx % h;
            int x = (idx / h) % w;
            int z = (idx / (h * w)) % w;

            double ox = digger.posX + x - (w / 2.0D);
            double oy = digger.posY + y + 0.5D;
            double oz = digger.posZ + z - (w / 2.0D);

            Vec3 origin = Vec3.createVectorHelper(ox, oy, oz);
            double dx = tx - ox;
            double dy = ty - oy;
            double dz = tz - oz;

            double lenSq = dx * dx + dy * dy + dz * dz;
            if (lenSq < 1.0e-12) continue;

            double invLen = 1.0D / MathHelper.sqrt_double(lenSq);
            double dirX = dx * invLen;
            double dirY = dy * invLen;
            double dirZ = dz * invLen;
            double ex = ox + dirX * dist;
            double ey = oy + dirY * dist;
            double ez = oz + dirZ * dist;
            if (hDistSq < 16.0D) {
                double ddy = target.posY - digger.posY;
                if (ddy > 2.0D) {
                    ex = ox;
                    ey = oy + dist;
                    ez = oz;
                } else if (ddy < -2.0D) {
                    ex = ox;
                    ey = oy - dist;
                    ez = oz;
                }
            }
            Vec3 end = Vec3.createVectorHelper(ex, ey, ez);
            MovingObjectPosition mop = world.func_147447_a(origin, end, false, true, false);
            if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) continue;
            Block block = world.getBlock(mop.blockX, mop.blockY, mop.blockZ);
            if (block == null || block == Blocks.air) continue;
            if (block.getBlockHardness(world, mop.blockX, mop.blockY, mop.blockZ) < 0.0F) continue;
            if (isTooTough(block, world, mop.blockX, mop.blockY, mop.blockZ)) continue;
            if (!canHarvestBlockAligned(block, world, mop.blockX, mop.blockY, mop.blockZ)) continue;
            int meta = world.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ);
            boolean inList = BlockAndMeta.isInBlockAndMetaList(block, meta, ESM_Settings.getZombieDigBlacklist());
            if (ESM_Settings.ZombieSwapList == inList) return mop;
        }
        return null;
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void lockBlock(int x, int y, int z) {
        markedX = x;
        markedY = y;
        markedZ = z;
        hasMarked = true;
        progress = 0.0F;
        digTicks = 0;
        progressCooldown = 0;
    }

    private void clearProgressVisual() {
        if (hasMarked) {
            digger.worldObj.destroyBlockInWorldPartially(digger.getEntityId(), markedX, markedY, markedZ, -1);
        }
    }

    private boolean isMarkedBlockValid() {
        if (!hasMarked) return false;
        if (markedY < 0 || markedY >= 256) return false;
        if (!digger.worldObj.blockExists(markedX, markedY, markedZ)) return false;

        World world = digger.worldObj;
        Block block = world.getBlock(markedX, markedY, markedZ);
        if (block == null || block == Blocks.air) return false;

        if (block.getBlockHardness(world, markedX, markedY, markedZ) < 0.0F) return false;
        if (isTooTough(block, world, markedX, markedY, markedZ)) return false;

        int meta = world.getBlockMetadata(markedX, markedY, markedZ);
        boolean inList = BlockAndMeta.isInBlockAndMetaList(block, meta, ESM_Settings.getZombieDigBlacklist());
        if (ESM_Settings.ZombieSwapList != inList) return false;

        return canHarvestBlockAligned(block, world, markedX, markedY, markedZ);
    }

    /**
     * Align harvest logic with your getBlockStrength():
     * - material tool-not-required
     * - ForgeHooks.canToolHarvestBlock + Item.canHarvestBlock
     * - pickaxeException only for rock when tools nerfed
     */

    private boolean isTooTough(Block block, World world, int x, int y, int z) {
        float h = block.getBlockHardness(world, x, y, z);
        if (h < 0.0F) return true; // unbreakable
        if (h >= 24) return true; // 你自己加个配置，先从 5~10 试
        float r = block.getExplosionResistance(digger);
        if (r >= 24) return true; // 先从 30~60 试

        return false;
    }

    private boolean canHarvestBlockAligned(Block block, World world, int x, int y, int z) {
        if (!ESM_Settings.ZombieDiggerTools) return true;

        Material mat = block.getMaterial();
        if (mat != null && mat.isToolNotRequired()) return true;

        ItemStack stack = digger.getEquipmentInSlot(0);
        if (stack == null || stack.getItem() == null) return false;

        int meta = world.getBlockMetadata(x, y, z);

        boolean forgeHarvest = ForgeHooks.canToolHarvestBlock(block, meta, stack);
        boolean vanillaHarvest = stack.getItem()
            .canHarvestBlock(block, stack);

        boolean pickaxeException = TOOLS_NERFED && stack.getItem() instanceof ItemPickaxe && mat == Material.rock;

        return forgeHarvest || vanillaHarvest || pickaxeException;
    }
}
