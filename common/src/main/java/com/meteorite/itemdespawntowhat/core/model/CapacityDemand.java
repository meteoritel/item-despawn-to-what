package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;

import java.util.Map;

/**
 * 效果类型声明的「每轮一组最小产出需求」。
 *
 * <p>结算的计划期容量承诺与开组前容量复核据此估算一组产出会占用的邻近数量上限：
 * 具体物品与通用实体按声明的件数累计；产物类型未知的产出（战利品表）按 {@link #unspecifiedItems}
 * 计入区域内的物品总量。声明只是下界，实际产出可能更多，因此它是规划口径，不是硬截断。</p>
 *
 * @param items            一组产出要求的各物品件数
 * @param entities         一组产出要求的各通用实体个数
 * @param unspecifiedItems 产物类型未知时按物品总量占用的件数下界
 */
public record CapacityDemand(Map<TaggedId, Integer> items, Map<TaggedId, Integer> entities, int unspecifiedItems) {

    // 不占容量的产出（一次性效果、经验等）复用该常量，避免每次分配空表
    public static final CapacityDemand NONE = new CapacityDemand(Map.of(), Map.of(), 0);

    public CapacityDemand {
        items = items == null ? Map.of() : Map.copyOf(items);
        entities = entities == null ? Map.of() : Map.copyOf(entities);
        unspecifiedItems = Math.max(0, unspecifiedItems);
    }

    /**
     * 单物品产出的需求声明。
     *
     * @param item  物品引用（支持具体物品与标签）
     * @param count 每轮一组的件数
     * @return 只占物品容量的需求声明
     */
    public static CapacityDemand item(TaggedId item, int count) {
        return new CapacityDemand(Map.of(item, count), Map.of(), 0);
    }

    /**
     * 单通用实体产出的需求声明。
     *
     * @param entity 实体类型引用（支持具体类型与标签）
     * @param count  每轮一组的个数
     * @return 只占实体容量的需求声明
     */
    public static CapacityDemand entity(TaggedId entity, int count) {
        return new CapacityDemand(Map.of(), Map.of(entity, count), 0);
    }

    /**
     * 产物类型未知的产出需求声明：按区域物品总量保守占用容量。
     *
     * @param count 每轮一组至少产出的物品件数
     * @return 只占物品总量的需求声明
     */
    public static CapacityDemand unspecified(int count) {
        return new CapacityDemand(Map.of(), Map.of(), count);
    }

    /**
     * 是否不占用任何容量。
     *
     * @return 三类需求都为 0 时为 true
     */
    public boolean isEmpty() {
        return items.isEmpty() && entities.isEmpty() && unspecifiedItems == 0;
    }
}
