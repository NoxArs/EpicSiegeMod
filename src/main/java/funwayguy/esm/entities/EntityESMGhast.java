package funwayguy.esm.entities;

import java.util.ArrayList;
import java.util.Collections;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S28PacketEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

import funwayguy.esm.ai.GenericEntitySelector;
import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;
import funwayguy.esm.handlers.ESM_PathCapHandler;

public class EntityESMGhast extends EntityGhast {

    private int explosionStrength = 1;
    public Entity targetedEntity;
    /** Cooldown time between target loss and new target aquirement. */
    private int aggroCooldown;

    public EntityESMGhast(World world) {
        super(world);
    }

    @Override
    protected void updateEntityActionState() {
        if (!this.worldObj.isRemote && this.worldObj.difficultySetting == EnumDifficulty.PEACEFUL) {
            this.setDead();
        }

        this.despawnEntity();
        this.prevAttackCounter = this.attackCounter;
        double d0 = this.waypointX - this.posX;
        double d1 = this.waypointY - this.posY;
        double d2 = this.waypointZ - this.posZ;
        double d3 = d0 * d0 + d1 * d1 + d2 * d2;

        if (d3 < 1.0D || d3 > 3600.0D) {
            this.waypointX = this.posX + (double) ((this.rand.nextFloat() * 2.0F - 1.0F) * 16.0F);
            this.waypointY = this.posY + (double) ((this.rand.nextFloat() * 2.0F - 1.0F) * 16.0F);
            this.waypointZ = this.posZ + (double) ((this.rand.nextFloat() * 2.0F - 1.0F) * 16.0F);
        }

        if (this.courseChangeCooldown-- <= 0) {
            this.courseChangeCooldown += this.rand.nextInt(5) + 2;
            d3 = (double) MathHelper.sqrt_double(d3);

            if (this.isCourseTraversable(this.waypointX, this.waypointY, this.waypointZ, d3)) {
                this.motionX += d0 / d3 * 0.1D;
                this.motionY += d1 / d3 * 0.1D;
                this.motionZ += d2 / d3 * 0.1D;
            } else {
                this.waypointX = this.posX;
                this.waypointY = this.posY;
                this.waypointZ = this.posZ;
            }
        }

        if (this.targetedEntity != null && this.targetedEntity.isDead) {
            this.targetedEntity = null;
        }

        if (this.targetedEntity == null || this.aggroCooldown-- <= 0) {
            this.searchForTarget(this);

            if (this.targetedEntity != null) {
                this.aggroCooldown = 20;
            }
        }

        double d4 = ESM_Settings.GhastFireDist;

        if (this.targetedEntity != null && this.targetedEntity.getDistanceSqToEntity(this) < d4 * d4) {
            double d5 = this.targetedEntity.posX - this.posX;
            double d6 = this.targetedEntity.posY - (this.posY + (double) (this.height / 2.0F) + 0.5D);// this.targetedEntity.boundingBox.minY
                                                                                                      // +
                                                                                                      // (double)(this.targetedEntity.height
                                                                                                      // / 2.0F) -
                                                                                                      // (this.posY +
                                                                                                      // (double)(this.height
                                                                                                      // / 2.0F));
            double d7 = this.targetedEntity.posZ - this.posZ;
            this.renderYawOffset = this.rotationYaw = -((float) Math.atan2(d5, d7)) * 180.0F / (float) Math.PI;

            if (this.canEntityBeSeen(this.targetedEntity) || ESM_Settings.GhastBreaching) {
                if (this.attackCounter == 10 * ESM_Settings.GhastFireDelay) {
                    // this.worldObj.playAuxSFXAtEntity((EntityPlayer)null, 1007, (int)this.posX, (int)this.posY,
                    // (int)this.posZ, 0);
                    this.PlaySound(1007);
                }

                ++this.attackCounter;

                if (this.attackCounter == 20 * ESM_Settings.GhastFireDelay) {
                    // this.worldObj.playAuxSFXAtEntity((EntityPlayer)null, 1008, (int)this.posX, (int)this.posY,
                    // (int)this.posZ, 0);
                    this.PlaySound(1008);
                    EntityLargeFireball entitylargefireball = new EntityLargeFireball(this.worldObj, this, d5, d6, d7);
                    entitylargefireball.field_92057_e = ESM_Settings.GhastBreaching ? 3 : this.explosionStrength;
                    double d8 = 4.0D;
                    Vec3 vec3 = this.getLook(1.0F);
                    entitylargefireball.posX = this.posX + vec3.xCoord * d8;
                    entitylargefireball.posY = this.posY + (double) (this.height / 2.0F) + 0.5D;
                    entitylargefireball.posZ = this.posZ + vec3.zCoord * d8;
                    this.worldObj.spawnEntityInWorld(entitylargefireball);
                    this.attackCounter = (int) (-40 * ESM_Settings.GhastFireDelay);
                }
            } else if (this.attackCounter > 0) {
                --this.attackCounter;
            }
        } else {
            this.renderYawOffset = this.rotationYaw = -((float) Math.atan2(this.motionX, this.motionZ)) * 180.0F
                / (float) Math.PI;

            if (this.attackCounter > 0) {
                --this.attackCounter;
            }
        }

        if (!this.worldObj.isRemote) {
            byte b1 = this.dataWatcher.getWatchableObjectByte(16);
            byte b0 = (byte) (this.attackCounter > 10 ? 1 : 0);

            if (b1 != b0) {
                this.dataWatcher.updateObject(16, Byte.valueOf(b0));
            }
        }
    }

    public void PlaySound(int soundID) {
        if (this.worldObj.isRemote) {
            return;
        }

        MinecraftServer.getServer()
            .getConfigurationManager()
            .sendToAllNearExcept(
                null,
                posX,
                posY,
                posZ,
                128D,
                dimension,
                new S28PacketEffect(soundID, (int) posX, (int) posY, (int) posZ, 0, false));
    }

    /**
     * True if the ghast has an unobstructed line of travel to the waypoint.
     */
    private boolean isCourseTraversable(double p_70790_1_, double p_70790_3_, double p_70790_5_, double p_70790_7_) {
        double d4 = (this.waypointX - this.posX) / p_70790_7_;
        double d5 = (this.waypointY - this.posY) / p_70790_7_;
        double d6 = (this.waypointZ - this.posZ) / p_70790_7_;
        AxisAlignedBB axisalignedbb = this.boundingBox.copy();

        for (int i = 1; (double) i < p_70790_7_; ++i) {
            axisalignedbb.offset(d4, d5, d6);

            if (!this.worldObj.getCollidingBoundingBoxes(this, axisalignedbb)
                .isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * (abstract) Protected helper method to write subclass entity data to NBT.
     */
    public void writeEntityToNBT(NBTTagCompound p_70014_1_) {
        super.writeEntityToNBT(p_70014_1_);
        p_70014_1_.setInteger("ExplosionPower", this.explosionStrength);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readEntityFromNBT(NBTTagCompound p_70037_1_) {
        super.readEntityFromNBT(p_70037_1_);

        if (p_70037_1_.hasKey("ExplosionPower", 99)) {
            this.explosionStrength = p_70037_1_.getInteger("ExplosionPower");
        }
    }

    @SuppressWarnings("unchecked")
    private void searchForTarget(EntityGhast entity) {
        if (entity.targetTasks.taskEntries.size() >= 1) {
            entity.getEntityData()
                .setInteger("ESM_TARGET_COOLDOWN", 0);
            return;
        }

        if (targetedEntity != null && ESM_Settings.Awareness > 100) {
            entity.getEntityData()
                .setInteger("ESM_TARGET_COOLDOWN", 0);
            return;
        } else if (targetedEntity != null) {
            if (entity.getDistanceToEntity(targetedEntity) < ESM_Settings.Awareness) {
                entity.getEntityData()
                    .setInteger("ESM_TARGET_COOLDOWN", 0);
                return;
            }

            if (ESM_PathCapHandler.attackMap.get(targetedEntity) != null
                && ESM_PathCapHandler.attackMap.get(targetedEntity)
                    .size() >= ESM_Settings.TargetCap
                && ESM_Settings.TargetCap != -1
                && (targetedEntity instanceof EntityLivingBase
                    ? !ESM_Utils.isCloserThanOtherAttackers(entity.worldObj, entity, (EntityLivingBase) targetedEntity)
                    : true)) {
                if (ESM_PathCapHandler.attackMap.get(targetedEntity)
                    .size() > ESM_Settings.TargetCap) {
                    targetedEntity = null;
                }
                entity.getEntityData()
                    .setInteger("ESM_TARGET_COOLDOWN", 0);
                return;
            }
        }

        if (entity.getEntityData()
            .getInteger("ESM_TARGET_COOLDOWN") > 0 && targetedEntity != null) {
            entity.getEntityData()
                .setInteger(
                    "ESM_TARGET_COOLDOWN",
                    entity.getEntityData()
                        .getInteger("ESM_TARGET_COOLDOWN") - 1);
            return;
        } else {
            entity.getEntityData()
                .setInteger("ESM_TARGET_COOLDOWN", 30);
        }

        double dist = Math.max(ESM_Settings.Awareness, ESM_Settings.GhastFireDist);
        ArrayList<EntityLivingBase> targets = (ArrayList<EntityLivingBase>) entity.worldObj.selectEntitiesWithinAABB(
            EntityLivingBase.class,
            entity.boundingBox.expand(dist, dist, dist),
            new GenericEntitySelector(this));
        Collections.sort(targets, new EntityAINearestAttackableTarget.Sorter(entity));

        targetedEntity = targets.size() > 0 ? targets.get(0) : null;
    }
}
