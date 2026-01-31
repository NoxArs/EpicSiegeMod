package funwayguy.esm.ai;

import java.util.List;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;

import funwayguy.esm.core.ESM_Settings;
import funwayguy.esm.core.ESM_Utils;

public class ESM_EntityAINearestAttackableTarget extends ESM_EntityAITarget
{
    public final List<Class<? extends EntityLivingBase>> targetClass;
    private final int targetChance;

    // 搜索冷却：避免每 tick 扫描大范围实体
    private int searchDelay = 0;

    // 原版字段保留（你现在不排序就可以不使用它）
    @SuppressWarnings("unused")
    private final EntityAINearestAttackableTarget.Sorter theNearestAttackableTargetSorter;

    private final IEntitySelector targetEntitySelector;

    // 缓存目标（复用以减少扫描）
    private EntityLivingBase targetEntity;

    private int calcSearchDelay()
    {
        // 下限 5tick=0.25s，避免 Awareness 太小导致疯狂扫描
        // 你也可以把下限调成 10/20 来更保守
        return Math.max(5, ESM_Settings.Awareness / 2);
    }

    public ESM_EntityAINearestAttackableTarget(EntityCreature owner,
                                               List<Class<? extends EntityLivingBase>> targetClasses, int chance, boolean checkSight)
    {
        this(owner, targetClasses, chance, checkSight, false);
    }

    public ESM_EntityAINearestAttackableTarget(EntityCreature owner,
                                               List<Class<? extends EntityLivingBase>> targetClasses, int chance, boolean checkSight, boolean nearbyOnly)
    {
        this(owner, targetClasses, chance, checkSight, nearbyOnly, (IEntitySelector)null);
    }

    public ESM_EntityAINearestAttackableTarget(EntityCreature owner,
                                               List<Class<? extends EntityLivingBase>> targetClasses, int chance, boolean checkSight, boolean nearbyOnly,
                                               IEntitySelector extraSelector)
    {
        super(owner, !(targetClasses.contains(EntityVillager.class) && owner instanceof EntityZombie), nearbyOnly);
        this.targetClass = targetClasses;
        this.targetChance = chance;
        this.theNearestAttackableTargetSorter = new EntityAINearestAttackableTarget.Sorter(owner);
        this.setMutexBits(1);

        // 选择器用于 AABB 查询阶段的“便宜过滤”（类型/友伤/宠物等）
        this.targetEntitySelector = new ESM_EntityAINearestAttackableTargetSelector(owner, this, extraSelector, this.targetClass);
    }

    @Override
    public void resetTask()
    {
        super.resetTask();
        this.targetEntity = null;
        // 不强行清 searchDelay 也行；这里保守清掉，避免 AI 复位后还要等
        this.searchDelay = 0;
    }

    @Override
    public boolean shouldExecute()
    {
        // --- 0) 冷却：只负责节流扫描（缓存复用仍然允许快速通过） ---
        if (searchDelay > 0)
        {
            searchDelay--;
            // 注意：这里不要 return true，因为这个 AI 负责“寻找新目标”，冷却期间不做事
            return false;
        }

        final double range = this.getTargetDistance();
        final double maxDistSq = range * range;

        // --- 1) 复用缓存目标：大幅减少 selectEntitiesWithinAABB 的次数 ---
        // 这里不需要再跑 selector（保持“原版黏着语义”），但要做必要合法性检查
        if (targetEntity != null)
        {
            if (targetEntity.isEntityAlive() && taskOwner.getDistanceSqToEntity(targetEntity) <= maxDistSq)
            {
                // 保留语义：仍然要是 suitable（避免 selector 与 suitable 语义漂移）
                if (isSuitableTarget(targetEntity, false))
                {
                    // 昂贵检查：只对这个缓存目标做一次
                    int pathCount = ESM_Utils.getAIPathCount(this.taskOwner.worldObj, targetEntity);
                    if (pathCount < ESM_Settings.TargetCap || ESM_Settings.TargetCap == -1
                        || ESM_Utils.isCloserThanOtherAttackers(this.taskOwner.worldObj, this.taskOwner, targetEntity))
                    {
                        return true;
                    }
                }
            }

            // 缓存失效：清掉，并进入短冷却（避免每 tick 重复对同一失效目标做昂贵检查）
            targetEntity = null;
            searchDelay = calcSearchDelay();
            return false;
        }

        // --- 2) RNG：失败不吃冷却，避免“怪发呆” ---
        if (this.targetChance > 0 && this.taskOwner.getRNG().nextInt(this.targetChance) != 0)
        {
            return false;
        }

        // --- 3) 扫描候选（只有到这里才是真正扫描） ---
        List<EntityLivingBase> list = this.taskOwner.worldObj.selectEntitiesWithinAABB(
            EntityLivingBase.class,
            this.taskOwner.boundingBox.expand(range, range, range),
            this.targetEntitySelector
        );

        // 扫描发生了：无论成功与否，都进入冷却，避免每 tick 扫描
        // 这里是“省 TPS”的关键点
        searchDelay = calcSearchDelay();

        if (list == null || list.isEmpty())
        {
            targetEntity = null;
            return false;
        }

        EntityLivingBase best = null;
        double bestDistSq = maxDistSq; // 直接用 maxDistSq 起步，剪枝更有效

        for (EntityLivingBase e : list)
        {
            if (e == null || !e.isEntityAlive()) continue;

            double dsq = this.taskOwner.getDistanceSqToEntity(e);
            if (dsq >= bestDistSq) continue; // 越来越紧的剪枝

            // 保留原版/ESM 的语义检查（重要）
            if (!isSuitableTarget(e, false)) continue;

            // 昂贵 cap 检查放最后
            int pathCount = ESM_Utils.getAIPathCount(this.taskOwner.worldObj, e);
            if (pathCount < ESM_Settings.TargetCap || ESM_Settings.TargetCap == -1
                || ESM_Utils.isCloserThanOtherAttackers(this.taskOwner.worldObj, this.taskOwner, e))
            {
                best = e;
                bestDistSq = dsq;
            }
        }

        this.targetEntity = best;
        return best != null;
    }

    @Override
    public void startExecuting()
    {
        this.taskOwner.setAttackTarget(this.targetEntity);
        // 允许下一 tick 继续复用缓存（不强行清 delay）
        // 如果你希望更“黏”，可以把 searchDelay 保留；如果希望更“敏捷”，可设 0
        // 这里选择不改，让节流逻辑自然运行
        super.startExecuting();
    }
}
