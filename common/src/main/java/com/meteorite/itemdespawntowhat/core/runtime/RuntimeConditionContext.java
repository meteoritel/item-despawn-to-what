package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ClimateSampler;
import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.TagLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 条件求值上下文的服务端实现。
 * 标签查询与气候采样对象由运行时按维度持有（带缓存），本记录只做参数聚合。
 */
public record RuntimeConditionContext(
        ServerLevel level,
        ItemEntity source,
        BlockPos pos,
        RandomSource random,
        TagLookup tags,
        ClimateSampler climate
) implements ConditionContext {
}
