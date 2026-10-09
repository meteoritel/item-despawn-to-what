package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.CatalystPresentEvaluator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 条件类型 catalyst_present：判断掉落物附近是否存在指定的催化剂物品。
 * 参数 items 为分别必需的物品引用（item id 或 #tag，至少一项）；counts 按引用配置门槛（1..64）。
 * 未配置的引用采用显式 count，count **允许留空**：
 * 留空表示门槛跟随同作用域的催化剂消耗量（规则级 catalyst_cost，或效果级 consume_catalyst），
 * 由 core/runtime/CatalystThresholdProjection 在构建规则索引时解析成有效值；
 * 找不到可匹配的消耗配置时按 DEFAULT_COUNT（1）处理。
 * 留空状态会被解码与编码原样保留，投影只影响运行期有效值，不反写用户声明。
 * 附近扫描范围见 CatalystPresentEvaluator：物品所在方块格的 1×1×1 范围，排除源物品自身与已死亡实体。
 * JSON 示例：{ "type": "itemdespawntowhat:catalyst_present", "items": ["minecraft:blaze_powder"] }
 */
public record CatalystPresentCondition(List<TaggedId> items, @Nullable Integer count,
                                      Map<TaggedId, Integer> counts) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "catalyst_present");

    // 参数 JSON 字段名
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_COUNT = "count";

    // 数量取值域；DEFAULT_COUNT 是「门槛留空且无匹配消耗配置」时的兜底值，不是解码默认值
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 64;
    public static final int DEFAULT_COUNT = 1;

    // 参数编解码器：items 缺省为空（由 validateParams 拒绝）；count 缺失即 null，编码时同样省略该字段
    public static final MapCodec<CatalystPresentCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TaggedId.CODEC.listOf().optionalFieldOf(FIELD_ITEMS, List.of()).forGetter(CatalystPresentCondition::items),
            Codec.INT.optionalFieldOf(FIELD_COUNT).forGetter(condition -> Optional.ofNullable(condition.count())),
            Codec.unboundedMap(TaggedId.CODEC, Codec.intRange(MIN_COUNT, MAX_COUNT))
                    .optionalFieldOf(RuleFields.ITEM_COUNTS, Map.of()).forGetter(CatalystPresentCondition::counts)
    ).apply(instance, (items, count, counts) -> new CatalystPresentCondition(items, count.orElse(null), counts)));

    // 参数列表不可变
    public CatalystPresentCondition {
        items = List.copyOf(items);
        counts = Map.copyOf(counts);
    }

    public int countFor(TaggedId item) {
        return counts.getOrDefault(item, count == null ? DEFAULT_COUNT : count);
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<CatalystPresentCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, CatalystPresentCondition::validateParams, CatalystPresentEvaluator::test);
    }

    // 参数语义校验：items 必填且逐项做注册表存在性校验；count 留空合法，填写时需落在 [1,64]
    public static boolean validateParams(CatalystPresentCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.notEmpty(params.items(), FIELD_ITEMS, issues,
                ParamChecks.child(fieldPath, FIELD_ITEMS));
        if (params.count() != null) {
            valid &= ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, FIELD_COUNT, issues,
                    ParamChecks.child(fieldPath, FIELD_COUNT));
        }
        valid &= RefChecks.checkAll(params.items(), BuiltInRegistries.ITEM, FIELD_ITEMS, issues,
                ParamChecks.child(fieldPath, FIELD_ITEMS));
        for (TaggedId item : params.counts().keySet()) {
            if (!params.items().contains(item)) {
                issues.error("数量配置引用了未选择的催化剂: " + item.serialized(), null,
                        ParamChecks.child(fieldPath, RuleFields.ITEM_COUNTS));
                valid = false;
            }
        }
        return valid;
    }
}
