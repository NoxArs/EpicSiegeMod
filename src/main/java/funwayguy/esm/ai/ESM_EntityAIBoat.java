package funwayguy.esm.ai;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidBlock;

import funwayguy.esm.entities.EntityZombieBoat;

public class ESM_EntityAIBoat extends EntityAIBase {

    private final EntityLiving host;
    private EntityZombieBoat mountedBoat;

    // 冷却（用 ticksExisted 简单够用）
    private int nextBoatTick = 0;

    // 刷船后保护期：不做“卡住下船”
    private int spawnGraceTicks = 0;

    // 卡住检测
    private int stuckTicks = 0;
    private double lastBoatX = 0.0D, lastBoatZ = 0.0D;

    // 下船平滑：先停控几 tick 再下
    private int dismountCooldownTicks = 0;

    public ESM_EntityAIBoat(EntityLiving host) {
        this.host = host;
        this.setMutexBits(1);
    }

    private boolean cooldownReady() {
        return host.ticksExisted >= nextBoatTick;
    }

    private void setCooldown(int ticks) {
        nextBoatTick = host.ticksExisted + ticks;
    }

    // =========================
    // Liquid helpers
    // =========================

    private static boolean isLiquidBlock(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b == null || b == Blocks.air) return false;

        Material m = b.getMaterial();
        if (m != null && m.isLiquid()) return true;

        return (b instanceof IFluidBlock);
    }

    /**
     * 更稳的“在液体里”判定：优先用 BB 材质检测。
     * 同时做岩浆平衡：非免疫火焰的不允许在岩浆启用。
     */
    private boolean isEntityInLiquidSafe(EntityLiving e) {
        World w = e.worldObj;
        if (w == null) return false;

        boolean inWater = e.isInWater();
        boolean inLava = w.isMaterialInBB(e.boundingBox, Material.lava);

        if (!inWater && !inLava) return false;
        if (inLava && !e.isImmuneToFire()) return false;

        // 额外：脚下确实是液体（避免站在岸边水面外侧误触发）
        int x = MathHelper.floor_double(e.posX);
        int z = MathHelper.floor_double(e.posZ);
        int yFeet = MathHelper.floor_double(e.boundingBox.minY + 0.01D);

        // 常见情况：实体浮在水面，脚下是水；若脚下是空气，则再探一格
        if (isLiquidBlock(w, x, yFeet, z)) return true;
        if (isLiquidBlock(w, x, yFeet - 1, z)) return true;

        return false;
    }

    @Override
    public boolean shouldExecute() {
        if (host.worldObj == null || host.worldObj.isRemote) return false;
        if (host.isDead) return false;
        if (!cooldownReady()) return false;
        if (host.hurtTime > 0) return false;
        EntityLivingBase target = host.getAttackTarget();
        if (target == null || target.isDead) return false;
        if (host.ridingEntity instanceof EntityZombieBoat) return true;
        if (!isEntityInLiquidSafe(host)) return false;
        double distSq = host.getDistanceSqToEntity(target);
        if (distSq < 49.0D) return false;

        return true;
    }

    @Override
    public void startExecuting() {
        if (host.worldObj == null || host.worldObj.isRemote) return;

        if (host.hurtTime > 0) {
            setCooldown(20);
            return;
        }

        if (host.ridingEntity instanceof EntityZombieBoat) {
            mountedBoat = (EntityZombieBoat) host.ridingEntity;
            return;
        }

        EntityZombieBoat boat = new EntityZombieBoat(host.worldObj);
        boat.setBoatHealth(10.0F);
        boat.setEmptyLifeTicks(60);
        boat.setPosition(host.posX, host.posY + 0.1D, host.posZ);

        if (host.worldObj.spawnEntityInWorld(boat)) {
            host.mountEntity(boat);
            mountedBoat = boat;
            spawnGraceTicks = 20;
            setCooldown(80);
        } else {
            setCooldown(40);
        }

        lastBoatX = boat.posX;
        lastBoatZ = boat.posZ;
        stuckTicks = 0;
        dismountCooldownTicks = 0;
    }

    @Override
    public boolean continueExecuting() {
        if (host.worldObj == null || host.worldObj.isRemote) return false;
        if (host.isDead) return false;

        EntityLivingBase target = host.getAttackTarget();
        if (target == null || target.isDead) return false;

        return (host.ridingEntity instanceof EntityZombieBoat);
    }

    @Override
    public void resetTask() {
        if (host.worldObj == null || host.worldObj.isRemote) return;
        if (mountedBoat != null) {
            mountedBoat.setAIControls(0.0F, 0.0F);
        }
        boolean isSuccessDismount = false;
        if (mountedBoat != null) {
            boolean boatIsDead = mountedBoat.isDead || mountedBoat.getBoatHealth() <= 0.0F;
            boolean stillRiding = (host.ridingEntity == mountedBoat);
            if (boatIsDead) {
                isSuccessDismount = false;
            } else if (stillRiding) {
                isSuccessDismount = true;
            } else {
                isSuccessDismount = false;
                mountedBoat.setEmptyLifeTicks(1);
            }
        }
        if (isSuccessDismount) {
            if (host.ridingEntity instanceof EntityZombieBoat) {
                host.mountEntity(null);
            }
            if (mountedBoat != null) mountedBoat.setEmptyLifeTicks(1);
            setCooldown(60);
        } else {
            setCooldown(1000);
        }
        host.moveForward = 0.0F;
        host.moveStrafing = 0.0F;
        stuckTicks = 0;
        spawnGraceTicks = 0;
        dismountCooldownTicks = 0;
        mountedBoat = null;
    }

    @Override
    public void updateTask() {
        if (host.worldObj == null || host.worldObj.isRemote) {
            return;
        }

        if (!(host.ridingEntity instanceof EntityZombieBoat)) {
            resetTask();
            return;
        }

        EntityZombieBoat boat = (EntityZombieBoat) host.ridingEntity;
        mountedBoat = boat;

        EntityLivingBase target = host.getAttackTarget();
        if (target == null || target.isDead) {
            resetTask();
            return;
        }

        if (spawnGraceTicks > 0) spawnGraceTicks--;

        // ============================================================
        // 下船策略
        // ============================================================
        boolean shouldDismount = false;

        // 1) 距离近：靠近后下船继续近战
        double distSq = host.getDistanceSqToEntity(target);
        if (distSq < 16.0D) shouldDismount = true;

        // 2) 到岸 / 卡住检测
        if (!shouldDismount && spawnGraceTicks <= 0) {
            if (boat.onGround) shouldDismount = true;
            double dxp = boat.posX - lastBoatX;
            double dzp = boat.posZ - lastBoatZ;
            double movedSq = dxp * dxp + dzp * dzp;
            lastBoatX = boat.posX;
            lastBoatZ = boat.posZ;

            // “速度低 +（撞墙或在地上）+移动很小” => 卡住
            double speedSq = boat.motionX * boat.motionX + boat.motionZ * boat.motionZ;
            boolean lowSpeed = speedSq < 0.0016D; // ~0.04 m/tick
            boolean lowMove = movedSq < 0.0009D; // ~0.03 block/tick
            boolean badContact = boat.isCollidedHorizontally || boat.onGround;

            if (lowSpeed && lowMove && badContact) stuckTicks++;
            else if (movedSq > 0.01D) stuckTicks = 0; // 明显移动就清零
            else stuckTicks = Math.max(0, stuckTicks - 1);

            // 卡住一段时间：判断目标是否已经上岸，若上岸则下船
            if (stuckTicks > 20) {
                int tx = MathHelper.floor_double(target.posX);
                int tz = MathHelper.floor_double(target.posZ);
                int tyFeet = MathHelper.floor_double(target.boundingBox.minY + 0.01D);

                boolean targetInLiquid = isLiquidBlock(host.worldObj, tx, tyFeet, tz)
                    || isLiquidBlock(host.worldObj, tx, tyFeet - 1, tz);

                if (!targetInLiquid) shouldDismount = true;
                else if (stuckTicks > 80) shouldDismount = true; // 真卡死：强制结束
            }
        }

        if (shouldDismount) {
            // 可选：先停控 2 tick 再下船，减少抖动
            if (dismountCooldownTicks <= 0) {
                dismountCooldownTicks = 2;
                boat.setAIControls(0.0F, 0.0F);
                return;
            }
            dismountCooldownTicks--;
            if (dismountCooldownTicks <= 0) {
                resetTask();
            }
            return;
        }

        // ============================================================
        // 控制船：更稳的“先对准再加速”
        // ============================================================

        double dx = target.posX - boat.posX;
        double dz = target.posZ - boat.posZ;

        // 目标方位
        float desiredYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

        // 用 host yaw 做操控主体，但减少与 boat yaw 的“打架”：
        // 先把 host yaw 缓慢拉向 boat yaw（船头），再拉向 desiredYaw
        float boatYaw = boat.rotationYaw;
        float pullToBoat = MathHelper.wrapAngleTo180_float(boatYaw - host.rotationYaw);
        host.rotationYaw += MathHelper.clamp_float(pullToBoat, -5.0F, 5.0F);

        float delta = MathHelper.wrapAngleTo180_float(desiredYaw - host.rotationYaw);

        // 转向（限制每 tick 转速）
        float turn = MathHelper.clamp_float(delta, -10.0F, 10.0F);
        host.rotationYaw += turn;
        host.rotationYawHead = host.rotationYaw;

        // strafe：过弯辅助
        float strafe = MathHelper.clamp_float(delta / 45.0F, -1.0F, 1.0F);

        // 油门：角度越大越收油，避免撞岸/甩尾
        float absDelta = Math.abs(delta);
        float forward;
        if (absDelta < 15.0F) forward = 1.0F;
        else if (absDelta < 45.0F) forward = 0.7F;
        else if (absDelta < 90.0F) forward = 0.4F;
        else forward = 0.2F;

        boat.setAIControls(forward, strafe);
    }
}
