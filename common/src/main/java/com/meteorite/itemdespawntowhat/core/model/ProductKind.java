package com.meteorite.itemdespawntowhat.core.model;

import org.jetbrains.annotations.Nullable;

/**
 * 效果类型的产出类别：规则分类器据此判断一条规则应归入哪个分类组。
 *
 * <p>封闭枚举覆盖客户端分类骨架的叶子分类；{@link #NONE} 表示非产出（如消耗类效果），
 * 不参与分类判定。产出类别的分类标识与快照下发的分类值一致。</p>
 */
public enum ProductKind {

    // 非产出：不改变规则分类
    NONE(null),
    // 物品产出
    ENTITY_ITEM("entity.item"),
    // 通用实体产出
    ENTITY_ENTITY("entity.entity"),
    // 经验产出
    ENTITY_EXPERIENCE("entity.experience"),
    // 方块产出
    BLOCK("block"),
    // 战利品产出
    LOOT("loot"),
    // 世界事件产出
    WORLD("world"),
    // 未知产出：含该产出的规则整体归入扩展分组
    EXTENSION("extension");

    private final String category;

    ProductKind(@Nullable String category) {
        this.category = category;
    }

    /**
     * 该产出类别对应的分类标识。
     *
     * @return 分类标识；{@link #NONE} 返回 null
     */
    public @Nullable String category() {
        return category;
    }
}
