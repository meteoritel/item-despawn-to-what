package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;

/**
 * 实体产出经验子类执行器：在触发位置生成经验球。
 * rounds 语义：amount 按 rounds 缩放；per_source_item 表示"每个源物品一份"，
 * 倍率改用 coveredSourceItems()（= 本组实际扣减的源物品数）而不是叠加快照，二者不叠加。
 * 每个经验批次通过 context.reportProgress 回执真实入账的点数。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class SpawnXpExecutor {

    private SpawnXpExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按经验面额分批交付经验，首批实际交付后播放一次生成反馈。
     * @param effect 经验点数与倍率策略
     * @param context 规则执行上下文
     * @return 经验交付的待完成回执，点数为零时返回跳过
     */
    public static EffectResult execute(EntityProduct.Experience effect, EffectContext context) {
        // 单组一轮：默认按 rounds 倍率；per_source_item 按本组实际扣减的源物品数（精确值）
        int multiplier = effect.perSourceItem() ? context.coveredSourceItems() : context.rounds();
        int amount = EffectTargets.saturatedMultiply(effect.amount(), multiplier);
        if (amount <= 0) {
            return EffectResult.skipped("zero_amount");
        }
        ServerLevel level = context.level();
        Vec3 position = context.position();
        int[] remaining = {amount};
        boolean[] feedbackSent = {false};
        // 先按总点数决定原版面额，再按逻辑球份数调度；不按点数批次重新拆分。
        EffectTargets.forEachStep(context, orbCount(amount), 16, index -> {
            int points = ExperienceOrb.getExperienceValue(remaining[0]);
            ExperienceOrb.award(level, new Vec3(
                    position.x + (level.random.nextDouble() - 0.5) * 0.5,
                    position.y + 0.2,
                    position.z + (level.random.nextDouble() - 0.5) * 0.5), points);
            if (!feedbackSent[0]) {
                VisualFeedback.entitySpawn(level, position);
                feedbackSent[0] = true;
            }
            remaining[0] -= points;
            context.reportProgress(points);
        });
        return EffectResult.deferred(amount, "awarded");
    }
    // 原版贪心面额的逻辑份数；按同面额整除计数，避免预检逐球循环。
    public static int orbCount(int points) {
        int count = 0;
        while (points > 0) {
            int value = ExperienceOrb.getExperienceValue(points);
            count += points / value;
            points %= value;
        }
        return count;
    }

}
