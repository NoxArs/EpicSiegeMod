package funwayguy.esm.handlers.entities;

import net.minecraft.entity.monster.EntityCreeper;
import funwayguy.esm.core.ESM_Settings;

/**
 * ESM Creeper handler (plain / vanilla-style)
 *
 * - On join world (server side): randomly make creeper powered based on settings.
 * - On living update (server side): if creeper starts swelling while riding, force dismount safely.
 *
 * No NBT persistence / no watchdog maintenance.
 */
public class ESM_CreeperHandler
{
    // 1.7.10 DataWatcher ID for creeper powered flag (Byte: 0/1)
    private static final int DW_POWERED_ID = 17;

    public static void onEntityJoinWorld(EntityCreeper creeper)
    {
        if (creeper == null || creeper.worldObj == null || creeper.worldObj.isRemote) return;
        if (creeper.getDataWatcher().getWatchableObjectByte(DW_POWERED_ID) == 1) return;
        if (!ESM_Settings.CreeperPowered) return;

        int r = ESM_Settings.CreeperPoweredRarity;
        // r <= 0 => always powered
        if (r > 0 && creeper.getRNG().nextInt(r) != 0) return;

        // Set powered = true
        creeper.getDataWatcher().updateObject(DW_POWERED_ID, Byte.valueOf((byte)1));
    }

    public static void onLivingUpdate(EntityCreeper creeper)
    {
        if (creeper == null || creeper.worldObj == null || creeper.worldObj.isRemote) return;

        // state 1 == swelling (about to explode)
        if (creeper.getCreeperState() == 1 && creeper.ridingEntity != null)
        {
            // safest way: let vanilla handle both sides + sync
            creeper.mountEntity(null);
        }
    }
}
