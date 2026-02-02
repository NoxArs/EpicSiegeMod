package funwayguy.esm.ai;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.entity.EntityLiving;
import net.minecraft.world.EnumDifficulty;

import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAIBreakDoor extends ESM_EntityAIDoorInteract {

    private int breakingTime;
    private int field_75358_j = -1;

    public ESM_EntityAIBreakDoor(EntityLiving p_i1618_1_) {
        super(p_i1618_1_);
    }

    public boolean shouldExecute() {
        boolean flag = super.shouldExecute() && (this.theEntity.worldObj.getGameRules()
            .getGameRuleBooleanValue("mobGriefing"));
        if (flag) return this.isValidDoor();
        else return false;
    }

    public boolean isValidDoor() {
        if (ESM_Utils.isDoorOrGateGriefable(
            this.theEntity.worldObj,
            this.field_151504_e,
            this.theEntity.worldObj.getBlockMetadata(this.entityPosX, this.entityPosY, this.entityPosZ),
            this.theEntity)) return true;

        if (this.field_151504_e instanceof BlockDoor) {
            return !((BlockDoor) this.field_151504_e)
                .func_150015_f(this.theEntity.worldObj, this.entityPosX, this.entityPosY, this.entityPosZ);
        }
        if (this.field_151504_e instanceof BlockTrapDoor) {
            return !BlockTrapDoor.func_150118_d(
                this.theEntity.worldObj.getBlockMetadata(this.entityPosX, this.entityPosY, this.entityPosZ));
        }
        if (this.field_151504_e instanceof BlockFenceGate) {
            return !BlockFenceGate.isFenceGateOpen(
                this.theEntity.worldObj.getBlockMetadata(this.entityPosX, this.entityPosY, this.entityPosZ));
        }
        return false;
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting() {
        super.startExecuting();
        this.breakingTime = 0;
        this.field_75358_j = -1; // NEW
    }

    public boolean continueExecuting() {
        if (!this.theEntity.worldObj.getGameRules()
            .getGameRuleBooleanValue("mobGriefing")) return false;

        double d0 = this.theEntity.getDistanceSq(this.entityPosX, this.entityPosY, this.entityPosZ);
        return this.breakingTime <= 240 && this.isValidDoor() && d0 < 9.0D;
    }

    public void resetTask() {
        super.resetTask();
        this.theEntity.worldObj.destroyBlockInWorldPartially(
            this.theEntity.getEntityId(),
            this.entityPosX,
            this.entityPosY,
            this.entityPosZ,
            -1);
        this.field_75358_j = -1; // NEW
    }

    public void updateTask() {
        super.updateTask();

        if (this.theEntity.getRNG()
            .nextInt(20) == 0) {
            this.theEntity.worldObj.playAuxSFX(1010, this.entityPosX, this.entityPosY, this.entityPosZ, 0);
        }

        ++this.breakingTime;
        int i = (int) ((float) this.breakingTime / 240.0F * 10.0F);

        if (i != this.field_75358_j) {
            this.theEntity.worldObj.destroyBlockInWorldPartially(
                this.theEntity.getEntityId(),
                this.entityPosX,
                this.entityPosY,
                this.entityPosZ,
                i);
            this.field_75358_j = i;
        }

        if (this.breakingTime == 240
            && (this.theEntity.worldObj.difficultySetting == EnumDifficulty.HARD || ESM_Settings.ZombieDiggers)) {
            this.theEntity.worldObj.setBlockToAir(this.entityPosX, this.entityPosY, this.entityPosZ);
            this.theEntity.worldObj.playAuxSFX(1012, this.entityPosX, this.entityPosY, this.entityPosZ, 0);
            this.theEntity.worldObj.playAuxSFX(
                2001,
                this.entityPosX,
                this.entityPosY,
                this.entityPosZ,
                Block.getIdFromBlock(this.field_151504_e));
        }
    }
}
