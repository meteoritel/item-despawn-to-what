package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.BaseWorldEffectConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 世界效果执行器共享的源物品消耗和完成回调骨架。
 */
abstract class AbstractWorldEffectExecutor<C extends BaseWorldEffectConfig>
        extends AbstractConversionExecutor<C> {

    protected @Nullable PreparedEffect prepare(
            C config,
            ItemEntity entity,
            ServerLevel level,
            int resultCapacityInRounds
    ) {
        int size = entity.getItem().getCount();
        int rounds = computeActualRounds(config, entity, size, resultCapacityInRounds);
        if (rounds <= 0) {
            return null;
        }
        int consumed = rounds * config.getSourceMultiple();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        return new PreparedEffect(rounds, () -> addRemainingItems(
                config, entity, level, size - consumed));
    }

    protected record PreparedEffect(int rounds, Runnable onFinish) {
    }
}
