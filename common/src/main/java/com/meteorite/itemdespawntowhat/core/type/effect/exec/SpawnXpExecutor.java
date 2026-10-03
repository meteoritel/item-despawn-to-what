package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnXpEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;

/**
 * spawn_xp 执行器：在触发位置生成经验球。
 * rounds 语义：amount 按 rounds 缩放；per_source_item 表示"每个源物品一份"，
 * 倍率改用 coveredSourceItems()（= 本组实际扣减的源物品数）而不是叠加快照，二者不叠加。
 * 阶段 4 起每个经验批次通过 context.reportProgress 回执真实入账的点数。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class SpawnXpExecutor {

    private SpawnXpExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
    @SuppressWarnings("resource")
    public static EffectResult execute(SpawnXpEffect effect, EffectContext context) {
        // 单组一轮：默认按 rounds 倍率；per_source_item 按本组实际扣减的源物品数（精确值）
        int multiplier = effect.perSourceItem() ? context.coveredSourceItems() : context.rounds();
        int amount = EffectTargets.saturatedMultiply(effect.amount(), multiplier);
        if (amount <= 0) {
            return EffectResult.skipped("zero_amount");
        }
        ServerLevel level = context.level();
        Vec3 position = context.position();
        int batches = (int) (((long) amount + 4095) / 4096);
        EffectTargets.forEachStep(context, batches, 1, index -> {
            int points = (int) Math.min((long) amount - (long) index * 4096, 4096);
            ExperienceOrb.award(level, new Vec3(
                    position.x + (level.random.nextDouble() - 0.5) * 0.5,
                    position.y + 0.2,
                    position.z + (level.random.nextDouble() - 0.5) * 0.5), points);
            context.reportProgress(points);
        });
        return EffectResult.deferred(amount, "awarded");
    }
}
