package funwayguy.esm.handlers.entities;

import net.minecraft.entity.monster.EntityBlaze;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import funwayguy.esm.core.ESM_Settings;

public class ESM_BlazeHandler
{
    private static final String NBT_FIREBALLS = "ESM_FIREBALLS";
    private static final String SRG_FIREBALLS = "field_70846_g"; // 1.7.10 SRG

    public static void onEntityJoinWorld(EntityBlaze blaze)
    {
        if (blaze == null || blaze.worldObj == null || blaze.worldObj.isRemote) return;

        // 不要每次都清零：只在不存在时初始化
        if (!blaze.getEntityData().hasKey(NBT_FIREBALLS))
        {
            blaze.getEntityData().setInteger(NBT_FIREBALLS, 0);
        }
    }

    public static void onLivingUpdate(EntityBlaze blaze)
    {
        if (blaze == null || blaze.worldObj == null || blaze.worldObj.isRemote) return;

        if (blaze.getEntityToAttack() == null) return;

        // magic number 仍然保留，但至少不会炸
        if (blaze.attackTime != 6) return;

        int fireballs;
        try
        {
            fireballs = ObfuscationReflectionHelper.getPrivateValue(EntityBlaze.class, blaze, SRG_FIREBALLS);
        }
        catch (Throwable t)
        {
            return; // 字段不匹配就放弃，不要影响 tick
        }

        if (fireballs > 1)
        {
            int extra = blaze.getEntityData().getInteger(NBT_FIREBALLS);

            // 2..4 且未超上限：延长齐射
            if (fireballs < 5 && extra < ESM_Settings.BlazeFireballs)
            {
                try
                {
                    ObfuscationReflectionHelper.setPrivateValue(EntityBlaze.class, blaze, 2, SRG_FIREBALLS);
                    blaze.getEntityData().setInteger(NBT_FIREBALLS, extra + 1);
                }
                catch (Throwable t)
                {
                    // ignore
                }
            }
            else
            {
                // 否则强制结束本轮齐射，并重置计数
                try
                {
                    ObfuscationReflectionHelper.setPrivateValue(EntityBlaze.class, blaze, 5, SRG_FIREBALLS);
                }
                catch (Throwable t)
                {
                    // ignore
                }
                blaze.getEntityData().setInteger(NBT_FIREBALLS, 0);
            }
        }
    }
}

