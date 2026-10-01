package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.resources.ResourceLocation;

/**
 * 标签成员查询：由运行时按维度/服务端注册表实现并缓存，条件求值器不直接访问注册表。
 * 全部方法在标签不存在时返回 false（不抛异常）。
 */
public interface TagLookup {

    // 物品标签是否包含指定物品
    boolean itemInTag(ResourceLocation tagId, ResourceLocation itemId);

    // 方块标签是否包含指定方块
    boolean blockInTag(ResourceLocation tagId, ResourceLocation blockId);

    // 实体类型标签是否包含指定实体类型
    boolean entityInTag(ResourceLocation tagId, ResourceLocation entityTypeId);

    // 生物群系标签是否包含指定生物群系
    boolean biomeInTag(ResourceLocation tagId, ResourceLocation biomeId);

    // 流体标签是否包含指定流体
    boolean fluidInTag(ResourceLocation tagId, ResourceLocation fluidId);

    // 药水效果标签是否包含指定效果
    boolean mobEffectInTag(ResourceLocation tagId, ResourceLocation effectId);
}
