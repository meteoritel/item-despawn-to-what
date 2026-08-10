package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToArrowRainConfig;
import com.meteorite.itemdespawntowhat.server.task.ArrowRainTask;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 执行物品到箭雨的转化。
 */
public final class ItemToArrowRainExecutor extends AbstractWorldEffectExecutor<ItemToArrowRainConfig> {
    @Override
    public boolean performConversion(ItemToArrowRainConfig config, ItemEntity entity, ServerLevel level) {
        PreparedEffect prepared = prepare(config, entity, level,
                ConversionLimits.MAX_WORLD_EFFECT_EXECUTIONS / Math.max(1, config.getResultMultiple()));
        if (prepared == null) {
            return false;
        }
        int count = prepared.rounds() * config.getResultMultiple();
        LevelTaskManager.addTask(level, new ArrowRainTask(entity.blockPosition(), Constants.arrowIntervalTicks,
                count, config.getArrowPotionEffects(), config.getArrowPickupStatus(), prepared.onFinish()));
        return true;
    }
}
