package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToLightningConfig;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import com.meteorite.itemdespawntowhat.server.task.LightningTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 执行物品到闪电的转化。
 */
public final class ItemToLightningExecutor extends AbstractWorldEffectExecutor<ItemToLightningConfig> {
    @Override
    public boolean performConversion(ItemToLightningConfig config, ItemEntity entity, ServerLevel level) {
        PreparedEffect prepared = prepare(config, entity, level,
                ConversionLimits.MAX_WORLD_EFFECT_EXECUTIONS / Math.max(1, config.getResultMultiple()));
        if (prepared == null) {
            return false;
        }
        int count = prepared.rounds() * config.getResultMultiple();
        LevelTaskManager.addTask(level, new LightningTask(entity.blockPosition(), config.isVisualOnly(),
                Constants.lightningIntervalTicks, count, prepared.onFinish()));
        return true;
    }
}
