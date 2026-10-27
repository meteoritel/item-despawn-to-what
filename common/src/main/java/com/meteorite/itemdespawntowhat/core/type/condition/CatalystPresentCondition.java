package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.MapSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.RefListSpec;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.CatalystPresentEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 条件类型 catalyst_present：判断掉落物附近是否存在指定的催化剂物品。
 * 参数 items 为分别必需的物品引用（item id 或 #tag，至少一项）；counts 按引用配置门槛（1..64）。
 * 未配置的引用采用显式 count，count **允许留空**：
 * 留空表示采用规则级 catalyst_cost.counts 的门槛，
 * 由 core/runtime/CatalystThresholdProjection 在构建规则索引时解析成有效值；
 * 找不到可匹配的门槛配置时按 DEFAULT_COUNT（1）处理。
 * 留空状态会被解码与编码原样保留，投影只影响运行期有效值，不反写用户声明。
 * 附近扫描范围见 CatalystPresentEvaluator：规则共享 radius 范围，排除源物品自身与已死亡实体。
 * JSON 示例：{ "type": "idtw:catalyst_present", "items": ["minecraft:blaze_powder"] }
 */
public record CatalystPresentCondition(List<TaggedId> items, @Nullable Integer count,
                                      Map<TaggedId, Integer> counts, int radius) implements Condition {

    /** 创建采用默认规则半径的声明；运行投影统一填入所属规则半径。 */
    public CatalystPresentCondition(List<TaggedId> items, @Nullable Integer count, Map<TaggedId, Integer> counts) {
        this(items, count, counts, com.meteorite.itemdespawntowhat.core.model.CatalystCost.DEFAULT_RADIUS);
    }

    /** 类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "catalyst_present");

    // 参数 JSON 字段名
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_COUNT = "count";

    // 数量取值域；DEFAULT_COUNT 是「门槛留空且无匹配规则配置」时的兜底值，不是解码默认值
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 64;
    public static final int DEFAULT_COUNT = 1;

    // 参数声明：items 至少一项；count 为可留空的单值门槛（解码宽、校验严）；counts 按引用配置门槛
    public static final RefListSpec ITEMS = RefListSpec
            .defaultedEmpty(FIELD_ITEMS, "minecraft:item")
            .requireNonEmpty()
            .initial(List.of());
    public static final IntSpec COUNT = IntSpec.nullable(FIELD_COUNT, MIN_COUNT, MAX_COUNT).widened().hidden();
    public static final MapSpec COUNTS = MapSpec
            .defaultedEmpty(RuleFields.ITEM_COUNTS, "minecraft:item", MIN_COUNT, MAX_COUNT)
            .label("catalyst_present.count");
    public static final ParamList<CatalystPresentCondition> PARAMS = ParamList.of(
            ITEMS.bind(CatalystPresentCondition::items),
            COUNT.bind(CatalystPresentCondition::count),
            COUNTS.bind(CatalystPresentCondition::counts));

    /** 参数编解码器：items 缺省为空（由 validateParams 拒绝）；count 缺失即 null，编码时同样省略该字段 */
    public static MapCodec<CatalystPresentCondition> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(ITEMS),
                PARAMS.nullableField(COUNT),
                PARAMS.field(COUNTS)
        ).apply(instance, (items, count, counts) -> new CatalystPresentCondition(items, count.orElse(null), counts)));
    }

    /** 参数列表不可变 */
    public CatalystPresentCondition {
        items = List.copyOf(items);
        counts = Map.copyOf(counts);
    }

    /** 单个催化剂的判定门槛：counts 未配置该引用时退回 count，count 留空则取 DEFAULT_COUNT */
    public int countFor(TaggedId item) {
        return counts.getOrDefault(item, count == null ? DEFAULT_COUNT : count);
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    /** 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突） */
    public static ConditionType<CatalystPresentCondition> conditionType() {
        return new SimpleConditionType<>(ID, codec(), CatalystPresentCondition::validateParams, CatalystPresentEvaluator::test,
                (condition, context) -> com.meteorite.itemdespawntowhat.core.api.ChunkPresence.containsArea(
                        context.level(), context.pos(), condition.radius())
                        ? com.meteorite.itemdespawntowhat.core.api.Evaluability.AVAILABLE
                        : com.meteorite.itemdespawntowhat.core.api.Evaluability.UNAVAILABLE,
                PARAMS);
    }

    /** 参数语义校验：items 必填且逐项做注册表存在性校验；count 留空合法，填写时需落在 [1,64] */
    public static boolean validateParams(CatalystPresentCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = RefChecks.checkAll(params.items(), BuiltInRegistries.ITEM, FIELD_ITEMS, issues,
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
