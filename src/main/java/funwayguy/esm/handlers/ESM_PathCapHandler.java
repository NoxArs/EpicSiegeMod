package funwayguy.esm.handlers;

import java.util.*;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.apache.logging.log4j.Level;

import funwayguy.esm.core.ESM;
import funwayguy.esm.core.ESM_Settings;

public class ESM_PathCapHandler {

    public static final HashMap<EntityLivingBase, ArrayList<EntityLivingBase>> attackMap = new HashMap<EntityLivingBase, ArrayList<EntityLivingBase>>();

    public static void AddNewAttack(EntityLivingBase source, EntityLivingBase target) {
        if (source == target || source == null || target == null) return;
        if (!source.isEntityAlive() || !target.isEntityAlive()) return;

        ArrayList<EntityLivingBase> attackers = attackMap.get(target);

        // 只有“新增 target”才受 128 限制；已有 target 继续允许更新
        if (attackers == null) {
            // 先清一遍死 target，避免 128 被空壳占满
            if (attackMap.size() >= 100) { // 提前清理阈值
                cleanupDeadTargets();
            }

            if (attackMap.size() >= 128) {
                ESM.log.log(Level.WARN, "Hard limit of 128 active targets has been reached!");
                return;
            }

            attackers = new ArrayList<EntityLivingBase>();
            attackMap.put(target, attackers);
        }

        if (attackers.contains(source)) return;

        attackers.add(source);
        UpdateAttackers(target);
    }

    /** 轻量全局清理：把死掉/不存活的 target 从 map 里移除（只在需要时调用） */
    private static void cleanupDeadTargets() {
        if (attackMap.isEmpty()) return;

        Iterator<EntityLivingBase> it = attackMap.keySet()
            .iterator();
        while (it.hasNext()) {
            EntityLivingBase t = it.next();
            if (t == null || !t.isEntityAlive() || t.getHealth() <= 0F) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void UpdateAttackers(final EntityLivingBase target) {
        if (target == null) return;

        ArrayList<EntityLivingBase> attackers = attackMap.get(target);
        if (attackers == null) return; // 关键：不存在就不更新，更不能递归创建

        // target 死了才 remove
        if (!target.isEntityAlive() || target.getHealth() <= 0F) {
            attackMap.remove(target);
            return;
        }

        // 清理无效 attacker（倒序删安全）
        for (int i = attackers.size() - 1; i >= 0; i--) {
            EntityLivingBase subject = attackers.get(i);

            if (subject == null || !subject.isEntityAlive()
                || subject.getHealth() <= 0F
                || subject.dimension != target.dimension
                || (subject instanceof EntityPlayer && ((EntityPlayer) subject).capabilities.disableDamage)) {
                attackers.remove(i);
                continue;
            }

            // 仍然以 target 为目标才保留，否则移除
            if (subject.getAITarget() == target) continue;
            if (subject instanceof EntityLiving && ((EntityLiving) subject).getAttackTarget() == target) continue;
            if (subject instanceof EntityCreature && ((EntityCreature) subject).getEntityToAttack() == target) continue;

            attackers.remove(i);
        }

        // 如果清理完没人了：直接移除这个 target，避免空壳占位
        if (attackers.isEmpty()) {
            attackMap.remove(target);
            return;
        }

        // 超过 TargetCap：踢掉更“远”的攻击者
        if (ESM_Settings.TargetCap >= 0 && attackers.size() > ESM_Settings.TargetCap) {
            // 按距离从近到远排序
            Collections.sort(attackers, new Comparator<EntityLivingBase>() {

                @Override
                public int compare(EntityLivingBase a, EntityLivingBase b) {
                    return Double.compare(a.getDistanceSqToEntity(target), b.getDistanceSqToEntity(target));
                }
            });

            // 从最远开始踢
            for (int i = attackers.size() - 1; i >= 0 && attackers.size() > ESM_Settings.TargetCap; i--) {
                EntityLivingBase entity = attackers.remove(i);

                if (entity instanceof EntityLiving) {
                    ((EntityLiving) entity).setAttackTarget(null);

                    if (entity instanceof EntityCreature) {
                        ((EntityCreature) entity).setTarget(null);
                    }

                    ((EntityLiving) entity).getNavigator()
                        .clearPathEntity();
                }
            }

            // 踢完如果空了也移除
            if (attackers.isEmpty()) {
                attackMap.remove(target);
                return;
            }
        }

        // warn 只作为提示（不影响逻辑）
        if (attackMap.size() >= 128) {
            ESM.log.log(Level.WARN, "Hard limit of 128 active targets has been reached!");
        }
    }

    public static void RemoveTarget(EntityLivingBase target) {
        if (target != null) attackMap.remove(target);
    }
}
