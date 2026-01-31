package funwayguy.esm.handlers.entities;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_SkeletonHandler {

    public static void onEntityJoinWorld(EntitySkeleton skeleton) {
        skeleton.getEntityData()
            .setBoolean(
                "ESM_SKELETON_SETUP",
                skeleton.getEntityData()
                    .getBoolean("ESM_MODIFIED"));
        skeleton.getEntityData()
            .setBoolean("ESM_MODIFIED", true);
    }

    public static void onLivingUpdate(EntitySkeleton skeleton)
    {
        if (skeleton.worldObj == null || skeleton.worldObj.isRemote) return;

        // 在前 1 秒内尝试一次即可；NBT gate 保证只做一次
        if (skeleton.ticksExisted > 20) return;

        if (skeleton.getEntityData().getBoolean("ESM_SKELETON_SETUP")) return;
        skeleton.getEntityData().setBoolean("ESM_SKELETON_SETUP", true);

        if (!ESM_Settings.WitherSkeletons) return;

        // 只对普通骷髅做变身
        if (skeleton.getSkeletonType() != 0) return;

        int rarity = ESM_Settings.WitherSkeletonRarity;
        if (rarity > 0 && skeleton.getRNG().nextInt(rarity) != 0) return;

        // 1) 设置类型（会同步 datawatcher，客户端会变黑）
        skeleton.setSkeletonType(1);

        // 2) 确保手持是剑（避免弓导致 combatTask 切回远程）
        ItemStack held = skeleton.getHeldItem();
        if (held == null || held.getItem() == Items.bow)
        {
            skeleton.setCurrentItemOrArmor(0, new ItemStack(Items.stone_sword));
        }

        // 3) （可选但推荐）补基础属性：至少攻击伤害更像凋灵骷髅
        // 1.7.10: SharedMonsterAttributes.attackDamage 通常只在 EntityMob 有
        // 骷髅一般也有；如果没有 getEntityAttribute 会返回 null
        IAttributeInstance atk = skeleton.getEntityAttribute(SharedMonsterAttributes.attackDamage);
        if (atk != null)
        {
            // 原版凋灵骷髅近战伤害大概 4~7（难度相关），这里给一个中位数
            atk.setBaseValue(Math.max(atk.getBaseValue(), 5.0D));
        }

        // 4) 注入 ESM AI（先注入，再 setCombatTask，确保最终战斗 AI 贴合装备）
        ESM_Utils.replaceAI(skeleton, true);

        // 5) 设定最终战斗任务
        skeleton.setCombatTask();

        // 6) （可选）打标：后续事件里给其近战命中附带凋灵效果
        skeleton.getEntityData().setBoolean("ESM_IS_PSEUDO_WITHER_SKELETON", true);
    }
}
