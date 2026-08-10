package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/** 物品到世界效果转换执行器。 */
public final class ItemToWorldEffectExecutor extends AbstractConversionExecutor<ItemToWorldEffectConfig> {
    @Override public boolean performConversion(ItemToWorldEffectConfig config, ItemEntity entity, ServerLevel level) {
        WorldEffectType effect = config.getWorldEffect();
        if (effect == null || !effect.canExecute(level)) return false;
        int size = entity.getItem().getCount();
        boolean weather = effect == WorldEffectType.RAIN || effect == WorldEffectType.CLEAR;
        int rounds = computeActualRounds(config, entity, size,
                weather ? 1 : ConversionLimits.MAX_WORLD_EFFECT_EXECUTIONS / Math.max(1, config.getResultMultiple()));
        if (rounds <= 0) return false;
        int consumed = (weather ? config.getSourceMultiple() : rounds * config.getSourceMultiple());
        int executionCount = weather ? 1 : rounds * config.getResultMultiple();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        int remaining = size - consumed;
        effect.getExecutor().execute(entity, level, config, executionCount,
                () -> addRemainingItems(config, entity, level, remaining));
        return true;
    }
}
