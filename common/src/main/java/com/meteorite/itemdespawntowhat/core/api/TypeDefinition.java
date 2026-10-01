package com.meteorite.itemdespawntowhat.core.api;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 可注册类型定义的公共契约（效果类型与条件类型共用）。
 * 参数对象 P 是该类型专属的不可变记录，其编解码由本定义自行提供。
 */
public interface TypeDefinition<P> {

    // 该类型的稳定标识，同时是 JSON 中 type 字段的取值
    ResourceLocation id();

    // 该类型专属参数的编解码器（扁平字段，与规则/效果通用字段处在同一对象中）
    MapCodec<P> codec();
}
