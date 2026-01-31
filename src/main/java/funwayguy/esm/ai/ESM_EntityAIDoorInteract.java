package funwayguy.esm.ai;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.MathHelper;

import funwayguy.esm.core.ESM_Utils;

public abstract class ESM_EntityAIDoorInteract extends EntityAIBase {

    protected EntityLiving theEntity;
    protected int entityPosX;
    protected int entityPosY;
    protected int entityPosZ;
    protected Block field_151504_e;
    /** If is true then the Entity has stopped Door Interaction and compoleted the task. */
    boolean hasStoppedDoorInteraction;
    float entityPositionX;
    float entityPositionZ;

    public ESM_EntityAIDoorInteract(EntityLiving p_i1621_1_) {
        this.theEntity = p_i1621_1_;
    }

    /**
     * Returns whether the EntityAIBase should begin execution.
     */
    public boolean shouldExecute() {
        PathNavigate pathnavigate = this.theEntity.getNavigator();
        PathEntity pathentity = pathnavigate.getPath();

        if (pathentity != null && !pathentity.isFinished() && pathnavigate.getCanBreakDoors()) {
            for (int i = 0; i
                < Math.min(pathentity.getCurrentPathIndex() + 2, pathentity.getCurrentPathLength()); ++i) {
                PathPoint pathpoint = pathentity.getPathPointFromIndex(i);
                this.entityPosX = pathpoint.xCoord;
                this.entityPosY = pathpoint.yCoord + 1;
                this.entityPosZ = pathpoint.zCoord;

                if (this.theEntity.getDistanceSq((double) this.entityPosX, this.entityPosY, (double) this.entityPosZ)
                    <= 9D) {
                    this.field_151504_e = this.func_151503_a(this.entityPosX, this.entityPosY, this.entityPosZ);

                    if (this.field_151504_e != null) {
                        return true;
                    }
                }

                this.entityPosY = pathpoint.yCoord;

                if (this.theEntity.getDistanceSq((double) this.entityPosX, this.entityPosY, (double) this.entityPosZ)
                    <= 9D) {
                    this.field_151504_e = this.func_151503_a(this.entityPosX, this.entityPosY, this.entityPosZ);

                    if (this.field_151504_e != null) {
                        return true;
                    }
                }
            }

            this.entityPosX = MathHelper.floor_double(this.theEntity.posX);
            this.entityPosY = MathHelper.floor_double(this.theEntity.posY);
            this.entityPosZ = MathHelper.floor_double(this.theEntity.posZ);
            this.field_151504_e = this.func_151503_a(this.entityPosX, this.entityPosY, this.entityPosZ);
            return this.field_151504_e != null;
        } else {
            return false;
        }
    }

    /**
     * Returns whether an in-progress EntityAIBase should continue executing
     */

    @Override
    public boolean continueExecuting() {
        if (this.hasStoppedDoorInteraction) return false;
        if (this.field_151504_e == null) return false;
        if (this.theEntity.getDistanceSq(this.entityPosX + 0.5D, this.entityPosY + 0.5D, this.entityPosZ + 0.5D)
            > 16.0D) return false;

        Block b = this.theEntity.worldObj.getBlock(this.entityPosX, this.entityPosY, this.entityPosZ);
        return (b instanceof BlockDoor) || (b instanceof BlockFenceGate) || (b instanceof BlockTrapDoor);
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting() {
        this.hasStoppedDoorInteraction = false;
        this.entityPositionX = (float) ((double) ((float) this.entityPosX + 0.5F) - this.theEntity.posX);
        this.entityPositionZ = (float) ((double) ((float) this.entityPosZ + 0.5F) - this.theEntity.posZ);
    }

    /**
     * Updates the task
     */
    public void updateTask() {
        float f = (float) ((double) ((float) this.entityPosX + 0.5F) - this.theEntity.posX);
        float f1 = (float) ((double) ((float) this.entityPosZ + 0.5F) - this.theEntity.posZ);
        float f2 = this.entityPositionX * f + this.entityPositionZ * f1;

        if (f2 < 0.0F) {
            this.hasStoppedDoorInteraction = true;
        }
    }

    private Block func_151503_a(int posX, int posY, int posZ) {
        Block block = this.theEntity.worldObj.getBlock(posX, posY, posZ);
        if (block instanceof BlockDoor || block instanceof BlockFenceGate || block instanceof BlockTrapDoor) {
            if (ESM_Utils.isDoorOrGateGriefable(
                this.theEntity.worldObj,
                block,
                this.theEntity.worldObj.getBlockMetadata(posX, posY, posZ),
                this.theEntity)) return block;
        }
        return null;

        // return !(block == Blocks.wooden_door || block == Blocks.trapdoor || block == Blocks.fence_gate) ? null :
        // block;
    }
}
