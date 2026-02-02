package funwayguy.esm.entities;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntityZombieBoat extends EntityBoat {

    // ---- 授权死亡：空船自毁/虚空/血量打爆 ----
    private boolean authorizedDeath = false;

    // 空船寿命
    private int emptyLifeTicks = 60;
    private int emptyTimer;

    // 物理参数
    private double speedMultiplier = 0.07D;
    private boolean isBoatEmpty = true;

    // 船血量 (坦克模式：40点血 = 20颗心)
    private float boatHealth = 10.0F;
    private static final float BOAT_HEALTH_MAX = 10.0F;

    // AI 输入
    private float aiMoveForward = 0.0F;
    private float aiMoveStrafing = 0.0F;

    // 客户端插值
    private int boatPosRotationIncrements;
    private double boatX;
    private double boatY;
    private double boatZ;
    private double boatYaw;
    private double boatPitch;

    @SideOnly(Side.CLIENT)
    private double velocityX;
    @SideOnly(Side.CLIENT)
    private double velocityY;
    @SideOnly(Side.CLIENT)
    private double velocityZ;

    public EntityZombieBoat(World w) {
        super(w);
        this.isImmuneToFire = true;
        this.preventEntitySpawning = true;
        this.emptyTimer = this.emptyLifeTicks;
        this.boatHealth = BOAT_HEALTH_MAX; // 初始满血

        this.setSize(1.5F, 0.6F);
        this.yOffset = this.height / 2.0F;
    }

    public EntityZombieBoat(World w, double x, double y, double z) {
        this(w);
        this.setPosition(x, y + (double) this.yOffset, z);
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    public void setAIControls(float forward, float strafe) {
        this.aiMoveForward = forward;
        this.aiMoveStrafing = strafe;
    }

    public void setEmptyLifeTicks(int ticks) {
        this.emptyLifeTicks = Math.max(1, ticks);
        this.emptyTimer = this.emptyLifeTicks;
    }

    // ==========================================
    // 🛡️ 坦克防御逻辑 (核心修改)
    // ==========================================
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        // 1. 虚空清除：必须允许
        if (src == DamageSource.outOfWorld) {
            this.authorizedDeath = true;
            return super.attackEntityFrom(src, amount);
        }
        if (src == DamageSource.inWall || src == DamageSource.drown || src == DamageSource.fall) {
            return false;
        }
        Entity attacker = src.getEntity();
        boolean isValidAttacker = (attacker instanceof EntityPlayer) || src.isProjectile() || src.isExplosion();

        if (!isValidAttacker) {
            return false;
        }
        if (!this.worldObj.isRemote && !this.isDead) {
            this.setBeenAttacked();
            this.setTimeSinceHit(10);
            this.setDamageTaken(this.getDamageTaken() + amount * 10.0F);
            this.playSound("step.wood", 1.0F, 1.0F);
            if (attacker instanceof EntityPlayer && ((EntityPlayer) attacker).capabilities.isCreativeMode) {
                amount = 1000.0F;
            }

            // 扣血
            this.boatHealth -= amount;

            // 死亡判定
            if (this.boatHealth <= 0.0F) {
                this.boatHealth = 0.0F;
                this.playSound("random.break", 1.0F, 1.0F);
                this.authorizedDeath = true;
                super.setDead();
            }
        }

        return true;
    }

    @Override
    public void setDead() {
        if (this.worldObj != null && !this.worldObj.isRemote && !this.authorizedDeath) {
            return;
        }
        super.setDead();
    }

    @Override
    public EntityItem entityDropItem(ItemStack stack, float offsetY) {
        return null;
    }

    @Override
    public EntityItem func_145778_a(Item item, int count, float offset) {
        return null;
    }

    @Override
    protected void updateFallState(double dist, boolean onGround) {}

    // ==========================================
    // ⚙️ Update (物理 + 网络同步)
    // ==========================================
    @Override
    public void onUpdate() {
        this.onEntityUpdate();

        // 1) 客户端：插值木偶模式
        if (this.worldObj.isRemote) {
            boolean puppetMode = this.isBoatEmpty || !isLocalPlayerRidingClient();
            if (puppetMode) {
                if (this.boatPosRotationIncrements > 0) {
                    double x = this.posX + (this.boatX - this.posX) / (double) this.boatPosRotationIncrements;
                    double y = this.posY + (this.boatY - this.posY) / (double) this.boatPosRotationIncrements;
                    double z = this.posZ + (this.boatZ - this.posZ) / (double) this.boatPosRotationIncrements;
                    double ang = MathHelper.wrapAngleTo180_double(this.boatYaw - (double) this.rotationYaw);
                    this.rotationYaw = (float) ((double) this.rotationYaw
                        + ang / (double) this.boatPosRotationIncrements);
                    this.rotationPitch = (float) ((double) this.rotationPitch
                        + (this.boatPitch - (double) this.rotationPitch) / (double) this.boatPosRotationIncrements);
                    this.boatPosRotationIncrements--;
                    this.setPosition(x, y, z);
                    this.setRotation(this.rotationYaw, this.rotationPitch);
                } else {
                    this.setPosition(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
                    if (this.onGround) {
                        this.motionX *= 0.5D;
                        this.motionY *= 0.5D;
                        this.motionZ *= 0.5D;
                    }
                    this.motionX *= 0.99D;
                    this.motionY *= 0.95D;
                    this.motionZ *= 0.99D;
                }
                return;
            }
        }

        // 2) 服务端：寿命/乘员
        boolean hasRider = (this.riddenByEntity != null) && !this.riddenByEntity.isDead;
        if (hasRider) {
            this.emptyTimer = this.emptyLifeTicks;
            this.isBoatEmpty = false;
        } else {
            this.isBoatEmpty = true;
            if (!this.worldObj.isRemote && --this.emptyTimer <= 0) {
                this.authorizedDeath = true;
                super.setDead();
                return;
            }
        }

        // 视觉冷却 (红色闪烁恢复)
        if (this.getTimeSinceHit() > 0) this.setTimeSinceHit(this.getTimeSinceHit() - 1);
        if (this.getDamageTaken() > 0.0F) this.setDamageTaken(this.getDamageTaken() - 1.0F);

        // 浮力
        double waterRatio = 0.0D;
        for (int i = 0; i < 5; i++) {
            double minY = this.boundingBox.minY + (this.boundingBox.maxY - this.boundingBox.minY) * (double) i / 5.0D
                - 0.125D;
            double maxY = this.boundingBox.minY
                + (this.boundingBox.maxY - this.boundingBox.minY) * (double) (i + 1) / 5.0D
                - 0.125D;
            AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(
                this.boundingBox.minX,
                minY,
                this.boundingBox.minZ,
                this.boundingBox.maxX,
                maxY,
                this.boundingBox.maxZ);
            if (this.worldObj.isAABBInMaterial(aabb, Material.water)) {
                waterRatio += 1.0D / 5.0D;
            }
        }

        // double prevHoriz = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);

        if (hasRider && this.riddenByEntity instanceof EntityLivingBase) {
            EntityLivingBase rider = (EntityLivingBase) this.riddenByEntity;
            float moveF, moveS;

            if (rider instanceof EntityPlayer) {
                moveF = rider.moveForward;
                moveS = rider.moveStrafing;
            } else {
                moveF = this.aiMoveForward;
                moveS = this.aiMoveStrafing;
            }

            float len = MathHelper.sqrt_float(moveF * moveF + moveS * moveS);
            if (len > 1.0F) {
                moveF /= len;
                moveS /= len;
            }

            if (moveF != 0.0F || moveS != 0.0F) {
                float yawRad = rider.rotationYaw * (float) Math.PI / 180.0F;
                double ax = (-Math.sin(yawRad) * (double) moveF + Math.cos(yawRad) * (double) moveS);
                double az = (Math.cos(yawRad) * (double) moveF + Math.sin(yawRad) * (double) moveS);
                this.motionX += ax * this.speedMultiplier;
                this.motionZ += az * this.speedMultiplier;
            }
        }

        this.aiMoveForward = 0.0F;
        this.aiMoveStrafing = 0.0F;

        // 速度限制
        double currentSpeed = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        if (currentSpeed > 0.35D) {
            double scale = 0.35D / currentSpeed;
            this.motionX *= scale;
            this.motionZ *= scale;
        }

        // 浮力
        if (waterRatio < 1.0D) {
            double buoy = waterRatio * 2.0D - 1.0D;
            this.motionY += 0.04D * buoy;
        } else {
            if (this.motionY < 0.0D) this.motionY /= 2.0D;
            this.motionY += 0.007D;
        }

        // 睡莲粉碎
        int bx = MathHelper.floor_double(this.posX);
        int by = MathHelper.floor_double(this.posY);
        int bz = MathHelper.floor_double(this.posZ);
        Block b = this.worldObj.getBlock(bx, by, bz);
        if (b == Blocks.waterlily) {
            this.worldObj.func_147480_a(bx, by, bz, true);
        }

        // 移动
        if (this.onGround) {
            this.motionX *= 0.5D;
            this.motionY *= 0.5D;
            this.motionZ *= 0.5D;
        }
        this.moveEntity(this.motionX, this.motionY, this.motionZ);

        // 朝向
        double horiz = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        if (horiz > 0.05D) {
            double angle = Math.atan2(this.motionZ, this.motionX) * 180.0D / Math.PI;
            this.rotationYaw = (float) angle - 90.0F;
        }

        // 阻尼
        this.motionX *= 0.99D;
        this.motionY *= 0.95D;
        this.motionZ *= 0.99D;
    }

    @SideOnly(Side.CLIENT)
    private boolean isLocalPlayerRidingClient() {
        if (this.riddenByEntity == null) return false;
        return this.riddenByEntity == net.minecraft.client.Minecraft.getMinecraft().thePlayer;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int increments) {
        this.boatX = x;
        this.boatY = y;
        this.boatZ = z;
        this.boatYaw = yaw;
        this.boatPitch = pitch;
        this.boatPosRotationIncrements = increments + 5;
        this.motionX = this.velocityX;
        this.motionY = this.velocityY;
        this.motionZ = this.velocityZ;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setVelocity(double x, double y, double z) {
        this.velocityX = this.motionX = x;
        this.velocityY = this.motionY = y;
        this.velocityZ = this.motionZ = z;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setInteger("ESM_EmptyLife", this.emptyLifeTicks);
        nbt.setInteger("ESM_EmptyTimer", this.emptyTimer);
        nbt.setFloat("ESM_BoatHP", this.boatHealth);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        this.emptyLifeTicks = Math.max(1, nbt.getInteger("ESM_EmptyLife"));
        this.emptyTimer = Math.max(0, nbt.getInteger("ESM_EmptyTimer"));
        if (this.emptyTimer <= 0) this.emptyTimer = this.emptyLifeTicks;

        this.boatHealth = MathHelper.clamp_float(nbt.getFloat("ESM_BoatHP"), 0.0F, BOAT_HEALTH_MAX);
        if (this.boatHealth <= 0.0F) this.boatHealth = BOAT_HEALTH_MAX;

        this.authorizedDeath = false;
    }

    public void setBoatHealth(float v) {
        boatHealth = v;
    }

    public float getBoatHealth() {
        return boatHealth;
    }
}
