package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnXpEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;

/**
 * spawn_xp 执行器：在触发位置生成经验球；per_source_item 为真时按源堆叠数量倍增。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class SpawnXpExecutor {

    private SpawnXpExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(SpawnXpEffect effect, EffectContext context) {
        int amount = effect.amount();
        if (effect.perSourceItem()) {
            amount = EffectTargets.saturatedMultiply(amount, Math.max(1, context.sourceStack().getCount()));
        }
        if (amount <= 0) {
            return;
        }
        ServerLevel level = context.level();
        Vec3 position = context.position();
        ExperienceOrb.award(level, new Vec3(
                position.x + (level.random.nextDouble() - 0.5) * 0.5,
                position.y + 0.2,
                position.z + (level.random.nextDouble() - 0.5) * 0.5), amount);
    }
}
