package com.meteorite.itemdespawntowhat.core.network.protocol;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/***
 * 选择目录类型：物品/方块/实体/战利品表/群系/维度/标签/流体/状态效果。
 * P3 阶段只提供 DTO 与骨架收发，目录内容由 P4/P5 填充。
 */
public enum RuleCatalogType {
    ITEM,
    BLOCK,
    ENTITY,
    LOOT_TABLE,
    BIOME,
    DIMENSION,
    TAG,
    FLUID,
    MOB_EFFECT;

    // 线上传输用的类型名（枚举名，客户端按同名字符串比较）
    public String id() {
        return name();
    }

    // 宽松解析：未知类型返回 null，调用方回 INVALID_REQUEST
    public static @Nullable RuleCatalogType fromId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
