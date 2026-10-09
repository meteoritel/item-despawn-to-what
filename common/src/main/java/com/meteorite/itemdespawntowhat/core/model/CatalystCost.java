package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * 规则级催化剂固定成本：一组结算所需的催化剂物品/标签、数量与可搜索半径。
 * 字段名与取值域与 consume_catalyst 效果（{@code core/type/effect/ConsumeCatalystEffect}）完全一致：
 * items 必填非空且分别支付；counts 为各引用的 1..64 数量，未配置的引用采用 count（默认 1）。
 * radius 可选默认 1（1..8）。
 * 本类只承载数据与编解码；引用存在性校验在 {@link RuleValidation}，
 * 容量统计、预留与支付在运行时（core/runtime）。
 * 结构上不携带 chance / conditions / delay_ticks：这些字段由 RuleCodecs 的
 * catalyst_cost 对象未知字段检查按错误级拒绝。
 */
public record CatalystCost(
        List<TaggedId> items,
        int count,
        int radius,
        Map<TaggedId, Integer> counts
) {

    // 数量与半径的默认值与取值区间，与 consume_catalyst 效果保持一致
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int DEFAULT_RADIUS = 1;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 8;

    // 紧凑构造器：列表做防御性拷贝，保持记录不可变（codec 层不产出 null）
    public CatalystCost {
        items = items == null ? List.of() : List.copyOf(items);
        counts = Map.copyOf(counts);
    }

    public int countFor(TaggedId item) {
        return counts.getOrDefault(item, count);
    }

    public int totalCount() {
        return items.stream().distinct().mapToInt(this::countFor).sum();
    }

    // 对象编解码：items 必填（缺失由解码报错、空列表由 RuleValidation 报错），count / radius 缺省省略不写
    public static final Codec<CatalystCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TaggedId.CODEC.listOf().fieldOf(RuleFields.CATALYST_ITEMS).forGetter(CatalystCost::items),
            Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(RuleFields.CATALYST_COUNT, DEFAULT_COUNT)
                    .forGetter(CatalystCost::count),
            Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf(RuleFields.CATALYST_RADIUS, DEFAULT_RADIUS)
                    .forGetter(CatalystCost::radius),
            Codec.unboundedMap(TaggedId.CODEC, Codec.intRange(MIN_COUNT, MAX_COUNT))
                    .optionalFieldOf(RuleFields.ITEM_COUNTS, Map.of()).forGetter(CatalystCost::counts)
    ).apply(instance, CatalystCost::new));
}
