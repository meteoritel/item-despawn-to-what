package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 条件求值上下文：由运行时构造，条件求值器只依赖本接口，便于脱离游戏做单元验证。
 */
public interface ConditionContext {

    // 掉落物所在维度
    ServerLevel level();

    // 被检查的掉落物
    ItemEntity source();

    // 掉落物所在方块位置
    BlockPos pos();

    // 随机源（供概率类条件使用）
    RandomSource random();

    // 标签查询（带缓存，按 reload 失效）
    TagLookup tags();

    // 原版气候参数采样（按位置缓存）
    ClimateSampler climate();
}
