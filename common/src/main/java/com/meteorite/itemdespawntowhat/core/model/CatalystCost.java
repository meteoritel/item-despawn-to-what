package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * 规则级催化剂配置：同一组物品引用同时声明存在门槛与实际扣除量，并共享搜索半径。
 * counts 表示门槛，consume_counts 表示实际扣除量；两者不再互相替代。
 * 本类只承载数据与编解码；引用存在性校验在 {@link RuleValidation}，
 * 容量统计、预留与支付在运行时（core/runtime）。
 * 结构上不携带 chance / conditions / delay_ticks：这些字段由 RuleCodecs 的
 * catalyst_cost 对象未知字段检查按错误级拒绝。
 */
public record CatalystCost(
        List<TaggedId> items,
        int radius,
        Map<TaggedId, Integer> counts,
        Map<TaggedId, Integer> consumeCounts
) {

    /** 数量与半径的默认值与取值区间 */
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MIN_CONSUME_COUNT = 0;
    public static final int MAX_COUNT = 64;
    public static final int DEFAULT_RADIUS = 1;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 8;

    /** 紧凑构造器：列表做防御性拷贝，保持记录不可变（codec 层不产出 null） */
    public CatalystCost {
        items = items == null ? List.of() : List.copyOf(items);
        counts = Map.copyOf(counts);
        consumeCounts = Map.copyOf(consumeCounts);
    }

    /** 单个引用的存在门槛；未配置时按 1 件处理。 */
    public int countFor(TaggedId item) {
        return counts.getOrDefault(item, DEFAULT_COUNT);
    }

    /** 单个引用的实际扣除量；未配置时为 0，表示只检测、不消耗。 */
    public int consumeCountFor(TaggedId item) {
        return consumeCounts.getOrDefault(item, 0);
    }

    /** 全部引用（按引用去重后）的实际扣除数量合计。 */
    public int totalConsumeCount() {
        return items.stream().distinct().mapToInt(this::consumeCountFor).sum();
    }

    /** 将门槛数量投影为一次性支付配置，用于启动前的最低库存检查，不改变原声明。 */
    public CatalystCost thresholdPaymentProfile() {
        var thresholds = new java.util.LinkedHashMap<TaggedId, Integer>();
        items.forEach(item -> thresholds.put(item, countFor(item)));
        return new CatalystCost(items, radius, thresholds, thresholds);
    }

    /** 对象编解码：items 必填；门槛缺省为 1，消耗缺省为 0，半径缺省为 1。 */
    public static final MapCodec<CatalystCost> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TaggedId.CODEC.listOf().fieldOf(RuleFields.CATALYST_ITEMS).forGetter(CatalystCost::items),
            Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf(RuleFields.CATALYST_RADIUS, DEFAULT_RADIUS)
                    .forGetter(CatalystCost::radius),
            Codec.unboundedMap(TaggedId.CODEC, Codec.intRange(MIN_COUNT, MAX_COUNT))
                    .optionalFieldOf(RuleFields.ITEM_COUNTS, Map.of()).forGetter(CatalystCost::counts),
            Codec.unboundedMap(TaggedId.CODEC, Codec.intRange(MIN_CONSUME_COUNT, MAX_COUNT))
                    .optionalFieldOf(RuleFields.CONSUME_COUNTS, Map.of()).forGetter(CatalystCost::consumeCounts)
    ).apply(instance, CatalystCost::new));
    /** 保持对象 codec 出口，字段集合由 MAP_CODEC 派生。 */
    public static final Codec<CatalystCost> CODEC = MAP_CODEC.codec();

}
