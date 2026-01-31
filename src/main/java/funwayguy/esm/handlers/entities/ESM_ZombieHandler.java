package funwayguy.esm.handlers.entities;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;

import funwayguy.esm.ai.AIUtils;
import funwayguy.esm.core.ESM_Settings;

public class ESM_ZombieHandler {

    // 扫描地面物品的频率：20 tick = 1秒
    private static final String NBT_TOOL_SCAN_CD = "ESM_TOOL_SCAN_CD";
    private static final int TOOL_SCAN_INTERVAL = 20;

    // 手持槽在 1.7.10 是 0
    private static final int SLOT_HELD = 0;

    public static void onLivingUpdate(EntityZombie zombie) {
        // ---- cheapest guards ----
        if (zombie == null) return;
        if (zombie.worldObj == null || zombie.worldObj.isRemote) return;
        if (zombie.isDead) return;

        if (!zombie.canPickUpLoot()) return;

        if (!zombie.worldObj.getGameRules()
            .getGameRuleBooleanValue("mobGriefing")) return;
        if (!ESM_Settings.ZombieDiggers) return;

        // ---- per-zombie cooldown ----
        final NBTTagCompound tag = zombie.getEntityData();
        int cd = tag.getInteger(NBT_TOOL_SCAN_CD);
        if (cd > 0) {
            tag.setInteger(NBT_TOOL_SCAN_CD, cd - 1);
            return;
        }
        tag.setInteger(NBT_TOOL_SCAN_CD, TOOL_SCAN_INTERVAL);

        // ---- if holding sword: keep combat threat (original behavior) ----
        ItemStack cur = zombie.getEquipmentInSlot(SLOT_HELD);
        if (cur != null) {
            Item curItem = cur.getItem();
            if (curItem instanceof ItemSword) return;
        }

        // ---- scan nearby items (1 block horizontally, 0 vertically as original) ----
        @SuppressWarnings("unchecked")
        List<EntityItem> list = zombie.worldObj
            .getEntitiesWithinAABB(EntityItem.class, zombie.boundingBox.expand(1.0D, 0.0D, 1.0D));

        if (list == null || list.isEmpty()) return;

        // 当前挖石头速度（如果手里有东西）
        float curSpeed = -1.0F;
        if (cur != null && cur.getItem() != null) {
            curSpeed = AIUtils.getBreakSpeed(zombie, cur, Blocks.stone, 0);
        }

        EntityItem bestEntity = null;
        ItemStack bestStack = null;
        float bestSpeed = curSpeed;

        for (int idx = 0; idx < list.size(); idx++) {
            EntityItem ei = list.get(idx);
            if (ei == null || ei.isDead) continue;

            ItemStack st = ei.getEntityItem();
            if (st == null) continue;

            Item it = st.getItem();
            if (it == null) continue;

            // 只考虑“能挖石头”的工具
            // 注意：canHarvestBlock 在部分 mod 工具上可能返回 true，但 breakSpeed 很低
            if (!it.canHarvestBlock(Blocks.stone, st)) continue;

            // 原代码用了 getArmorPosition(itemstack)==0 来筛掉盔甲，这里更直接：
            // 我们只关心手持工具，盔甲掉地上也不会被当作“挖掘工具”通过 canHarvestBlock
            float sp = AIUtils.getBreakSpeed(zombie, st, Blocks.stone, 0);
            if (sp > bestSpeed) {
                bestSpeed = sp;
                bestEntity = ei;
                bestStack = st;
            }
        }

        // 没有更好的工具
        if (bestEntity == null || bestStack == null) return;

        // ---- equip best tool ----
        // 丢掉当前手持（原行为）
        if (cur != null) {
            zombie.entityDropItem(cur, 0.0F);
        }

        // 装备用 copy() 更安全
        ItemStack equip = bestStack.copy();
        zombie.setCurrentItemOrArmor(SLOT_HELD, equip);

        // 掉落概率：1.0F 表示必掉（原代码 2.0F 不规范但等效更“必掉”）
        zombie.setEquipmentDropChance(SLOT_HELD, 1.0F);

        zombie.onItemPickup(bestEntity, 1);
        bestEntity.setDead();
    }
}
