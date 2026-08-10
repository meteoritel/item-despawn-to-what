package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExplosionConfig;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 执行物品到爆炸的转化。
 */
public final class ItemToExplosionExecutor extends AbstractWorldEffectExecutor<ItemToExplosionConfig> {
    @Override
    public boolean performConversion(ItemToExplosionConfig config, ItemEntity entity, ServerLevel level) {
        PreparedEffect prepared = prepare(config, entity, level,
                ConversionLimits.MAX_WORLD_EFFECT_EXECUTIONS / Math.max(1, config.getResultMultiple()));
        if (prepared == null) {
            return false;
        }
        int count = prepared.rounds() * config.getResultMultiple();
        LevelTaskManager.addTask(level, new ExplosionTask(entity.blockPosition(), config.getExplosionPower(),
                config.isExplosionFire(), Constants.explosionIntervalTicks, count,
                config.getExplosionDirectionType(), prepared.onFinish()));
        return true;
    }
}
