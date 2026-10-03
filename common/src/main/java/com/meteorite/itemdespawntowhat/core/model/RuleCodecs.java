package com.meteorite.itemdespawntowhat.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeDispatch;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 规则及其条件树的编解码入口。
 * 条件表达式是一棵递归条件树：{"op":"all_of|any_of","terms":[...]} / {"op":"inverted","term":{...}} /
 * {"op":"leaf","condition":{...}}；无条件时由调用方省略 conditions 字段。
 * 效果与条件叶均通过注册表做扁平类型分发：类型专属字段与通用字段位于同一个 JSON 对象中。
 * 硬约束：codec 层不得产出 null（DFU 的 DataResult 内部使用 Optional.of，null 会在解码期抛 NPE）；
 * 可选字段一律以 Optional 承载，在记录构造的最后一步 orElse(null) 落回可空字段。
 * 旧格式（条件二维数组 / conditions.groups / 叶级 negated）一律明确报错，不做静默兼容。
 */
public final class RuleCodecs {

    // 触发时间默认值：对齐原版掉落物 300 秒的消失直觉
    public static final int DEFAULT_TRIGGER_AFTER_SECONDS = 300;

    // 当前规则契约结构版本；声明其它值一律拒绝
    public static final int DEFAULT_SCHEMA_VERSION = 1;

    // 规则文件允许出现的顶层字段（含覆盖层控制字段）
    public static final Set<String> KNOWN_RULE_FIELDS = Set.of(
            RuleFields.ID,
            RuleFields.ENABLED,
            RuleFields.PRIORITY,
            RuleFields.DISPLAY_NAME,
            RuleFields.NOTES,
            RuleFields.SOURCE,
            RuleFields.CONDITIONS,
            RuleFields.TRIGGER_AFTER_SECONDS,
            RuleFields.EFFECTS,
            RuleFields.TRIGGERS,
            RuleFields.SOURCE_COST,
            RuleFields.CATALYST_COST,
            RuleFields.COMBINATION,
            RuleFields.OUTCOMES,
            RuleFields.SCHEMA_VERSION,
            RuleFields.DISABLED,
            RuleFields.DELETE
    );

    // 候选结果对象允许出现的字段（未知字段与效果类型一致，采用错误级严格检查）
    private static final Set<String> OUTCOME_FIELDS = Set.of(
            RuleFields.CANDIDATE_ID,
            RuleFields.CANDIDATE_EFFECTS,
            RuleFields.SAFE_SPAWN,
            RuleFields.FILL_ORIGIN
    );

    // 催化剂固定成本对象允许出现的字段；未知字段（含 chance / conditions / delay_ticks）按错误级拒绝
    private static final Set<String> CATALYST_COST_FIELDS = Set.of(
            RuleFields.CATALYST_ITEMS,
            RuleFields.CATALYST_COUNT,
            RuleFields.CATALYST_RADIUS
    );

    // 旧版二维数组条件的分组字段名：仅用于识别旧格式并报错
    private static final String LEGACY_GROUPS = "groups";

    // 组合节点允许的节点级字段
    private static final Set<String> TERMS_FIELDS = Set.of(RuleFields.OP, RuleFields.TERMS);
    private static final Set<String> INVERTED_FIELDS = Set.of(RuleFields.OP, RuleFields.TERM);
    private static final Set<String> LEAF_FIELDS = Set.of(RuleFields.OP, RuleFields.CONDITION);

    // 允许的 op 取值，用于错误信息
    private static final String ALLOWED_OPS = RuleFields.OP_ALL_OF + " / " + RuleFields.OP_ANY_OF
            + " / " + RuleFields.OP_INVERTED + " / " + RuleFields.OP_LEAF;

    // 取反迁移提示：叶级 negated 已删除
    private static final String INVERT_HINT =
            "叶级 negated 已删除：取反请用 {\"op\":\"inverted\",\"term\":{\"op\":\"leaf\",\"condition\":{...}}}";

    private RuleCodecs() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 条件树编解码器（递归结构；空表达式编码为空 all_of，解码不接受空对象）
    public static Codec<ConditionExpression> conditionExpressionCodec(TypeRegistry<ConditionType<?>> conditionTypes) {
        Codec<Condition> leafCodec = TypeDispatch.flat(conditionTypes, Condition::type).codec();
        NodeCodecHolder holder = new NodeCodecHolder();
        MapCodec<ConditionNode> nodeCodec = nodeMapCodec(leafCodec, holder);
        // 递归自引用在构造完成后回填，避免构造期无限递归
        holder.ref = nodeCodec.codec();
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<ConditionExpression, T>> decode(DynamicOps<T> ops, T input) {
                var asMap = ops.getMap(input);
                if (asMap.result().isEmpty()) {
                    // 数组即旧版「外层 OR / 内层 AND」二维数组格式，必须明确拒绝而不是给出泛化的类型错误
                    return DataResult.error(() -> "条件表达式必须是条件树对象，允许的 op: " + ALLOWED_OPS
                            + "；旧格式（条件数组 / groups 字段 / 叶级 negated）已废弃");
                }
                return asMap.flatMap(map -> nodeCodec.decode(ops, map))
                        .map(node -> Pair.of(new ConditionExpression(node), input));
            }

            @Override
            public <T> DataResult<T> encode(@Nullable ConditionExpression input, DynamicOps<T> ops, T prefix) {
                return encodeExpression(input, ops, ops.mapBuilder(), nodeCodec).build(prefix);
            }
        };
    }

    // 效果列表编解码器
    public static Codec<Effect> effectCodec(TypeRegistry<EffectType<?>> effectTypes) {
        return TypeDispatch.flat(effectTypes, Effect::type).codec();
    }

    // 规则编解码器
    public static Codec<Rule> codec(TypeRegistry<EffectType<?>> effectTypes,
                                    TypeRegistry<ConditionType<?>> conditionTypes) {
        Codec<ConditionExpression> expressionCodec = conditionExpressionCodec(conditionTypes);
        Codec<Effect> effectCodec = effectCodec(effectTypes);
        Codec<OutcomeCandidate> outcomeCodec = OutcomeCandidate.codec(effectCodec);
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf(RuleFields.ID).forGetter(Rule::id),
                Codec.BOOL.optionalFieldOf(RuleFields.ENABLED, Boolean.TRUE).forGetter(Rule::enabled),
                Codec.INT.optionalFieldOf(RuleFields.PRIORITY, 0).forGetter(Rule::priority),
                Codec.STRING.optionalFieldOf(RuleFields.DISPLAY_NAME)
                        .forGetter(rule -> Optional.ofNullable(rule.displayName())),
                Codec.STRING.optionalFieldOf(RuleFields.NOTES)
                        .forGetter(rule -> Optional.ofNullable(rule.notes())),
                SourceMatcher.CODEC.fieldOf(RuleFields.SOURCE).forGetter(Rule::source),
                expressionCodec.optionalFieldOf(RuleFields.CONDITIONS, ConditionExpression.EMPTY)
                        .forGetter(Rule::conditions),
                Codec.INT.optionalFieldOf(RuleFields.TRIGGER_AFTER_SECONDS, DEFAULT_TRIGGER_AFTER_SECONDS)
                        .forGetter(Rule::triggerAfterSeconds),
                TriggerKind.CODEC.listOf().optionalFieldOf(RuleFields.TRIGGERS, List.of())
                        .forGetter(rule -> List.copyOf(rule.triggers())),
                Codec.INT.optionalFieldOf(RuleFields.SOURCE_COST)
                        .forGetter(rule -> Optional.ofNullable(rule.sourceCost())),
                CatalystCost.CODEC.optionalFieldOf(RuleFields.CATALYST_COST)
                        .forGetter(rule -> Optional.ofNullable(rule.catalystCost())),
                CombinationMode.CODEC.optionalFieldOf(RuleFields.COMBINATION, CombinationMode.ROUND_ROBIN)
                        .forGetter(Rule::combination),
                outcomeCodec.listOf().optionalFieldOf(RuleFields.OUTCOMES, List.of())
                        .forGetter(Rule::outcomes),
                effectCodec.listOf().optionalFieldOf(RuleFields.EFFECTS, List.of()).forGetter(Rule::effects),
                Codec.INT.optionalFieldOf(RuleFields.SCHEMA_VERSION, DEFAULT_SCHEMA_VERSION)
                        .forGetter(Rule::schemaVersion)
        ).apply(instance, (id, enabled, priority, displayName, notes, source, conditions, triggerAfterSeconds,
                           triggers, sourceCost, catalystCost, combination, outcomes, effects, schemaVersion) ->
                new Rule(id, enabled, priority, displayName.orElse(null), notes.orElse(null), source, conditions,
                        triggerAfterSeconds, effects, new LinkedHashSet<>(triggers), sourceCost.orElse(null),
                        catalystCost.orElse(null), combination, outcomes, schemaVersion)));
    }

    // 构造加载层可用的解码器；未知顶层字段不阻断加载，但会作为 WARN 进入 IssueCollector
    public static RuleDecoder<Rule> decoder(RegistryAccess registryAccess,
                                            TypeRegistry<EffectType<?>> effectTypes,
                                            TypeRegistry<ConditionType<?>> conditionTypes) {
        Codec<Rule> ruleCodec = codec(effectTypes, conditionTypes);
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        return (body, issues) -> {
            warnUnknownFields(body, issues);
            checkOutcomeFields(body, issues);
            checkCatalystCostFields(body, issues);
            return ruleCodec.decode(ops, body).map(Pair::getFirst);
        };
    }

    // 顶层未知字段告警
    private static void warnUnknownFields(JsonObject body, IssueCollector issues) {
        for (String key : body.keySet()) {
            if (!KNOWN_RULE_FIELDS.contains(key)) {
                issues.warn("未知字段: " + key, null, key);
            }
        }
    }

    // 候选结果对象的未知字段：与效果类型一致采用错误级严格检查
    private static void checkOutcomeFields(JsonObject body, IssueCollector issues) {
        JsonElement raw = body.get(RuleFields.OUTCOMES);
        if (raw == null || !raw.isJsonArray()) {
            return;
        }
        int index = 0;
        for (JsonElement element : raw.getAsJsonArray()) {
            if (element.isJsonObject()) {
                for (String key : element.getAsJsonObject().keySet()) {
                    if (!OUTCOME_FIELDS.contains(key)) {
                        issues.error("候选结果存在未知字段: " + key, null,
                                RuleFields.OUTCOMES + "[" + index + "]." + key);
                    }
                }
            }
            index++;
        }
    }

    // 催化剂固定成本：整数写法与对象内未知字段都按错误级拒绝（不做静默兼容）
    private static void checkCatalystCostFields(JsonObject body, IssueCollector issues) {
        JsonElement raw = body.get(RuleFields.CATALYST_COST);
        if (raw == null) {
            return;
        }
        if (!raw.isJsonObject()) {
            issues.error("catalyst_cost 必须是对象 {\"" + RuleFields.CATALYST_ITEMS + "\":[...],\""
                    + RuleFields.CATALYST_COUNT + "\":1,\"" + RuleFields.CATALYST_RADIUS + "\":1}，"
                    + "整数写法已废弃", null, RuleFields.CATALYST_COST);
            return;
        }
        for (String key : raw.getAsJsonObject().keySet()) {
            if (!CATALYST_COST_FIELDS.contains(key)) {
                issues.error("catalyst_cost 存在未知字段: " + key, null, RuleFields.CATALYST_COST + "." + key);
            }
        }
    }

    // 表达式编码：空表达式写成恒真的空 all_of，保证编码结果始终可回读且不产出 null
    private static <T> RecordBuilder<T> encodeExpression(@Nullable ConditionExpression expression,
                                                         DynamicOps<T> ops,
                                                         RecordBuilder<T> prefix,
                                                         MapCodec<ConditionNode> nodeCodec) {
        if (expression == null || expression.isEmpty()) {
            return prefix.add(RuleFields.OP, ops.createString(RuleFields.OP_ALL_OF))
                    .add(RuleFields.TERMS, List.of(), nodeCodec.codec().listOf());
        }
        return nodeCodec.encode(expression.root(), ops, prefix);
    }

    // 条件节点编解码器：按 op 字段分派到组合节点或条件叶
    private static MapCodec<ConditionNode> nodeMapCodec(Codec<Condition> leafCodec, NodeCodecHolder holder) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<ConditionNode> decode(DynamicOps<T> ops, MapLike<T> input) {
                T rawOp = input.get(RuleFields.OP);
                if (rawOp == null) {
                    return DataResult.error(() -> missingOpMessage(input));
                }
                return ops.getStringValue(rawOp).flatMap(op -> switch (op) {
                    case RuleFields.OP_ALL_OF -> decodeTerms(ops, input, true, holder.ref);
                    case RuleFields.OP_ANY_OF -> decodeTerms(ops, input, false, holder.ref);
                    case RuleFields.OP_INVERTED -> decodeInverted(ops, input, holder.ref);
                    case RuleFields.OP_LEAF -> decodeLeaf(ops, input, leafCodec);
                    default -> DataResult.error(() -> "未知的条件节点 op: " + op + "，允许值: " + ALLOWED_OPS);
                });
            }

            @Override
            public <T> RecordBuilder<T> encode(@Nullable ConditionNode input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                if (input instanceof ConditionNode.AllOf(var allOfTerms)) {
                    return prefix.add(RuleFields.OP, ops.createString(RuleFields.OP_ALL_OF))
                            .add(RuleFields.TERMS, allOfTerms, holder.ref.listOf());
                }
                if (input instanceof ConditionNode.AnyOf(var anyOfTerms)) {
                    return prefix.add(RuleFields.OP, ops.createString(RuleFields.OP_ANY_OF))
                            .add(RuleFields.TERMS, anyOfTerms, holder.ref.listOf());
                }
                if (input instanceof ConditionNode.Inverted(var invertedTerm)) {
                    return prefix.add(RuleFields.OP, ops.createString(RuleFields.OP_INVERTED))
                            .add(RuleFields.TERM, invertedTerm, holder.ref);
                }
                if (input instanceof ConditionNode.Leaf(var leafCondition)) {
                    return prefix.add(RuleFields.OP, ops.createString(RuleFields.OP_LEAF))
                            .add(RuleFields.CONDITION, leafCondition, leafCodec);
                }
                // 未知节点实现属于编程错误：静默半写会产出无法回读的 JSON
                throw new IllegalStateException("编码失败：未知的条件节点类型 " + input);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(RuleFields.OP, RuleFields.TERMS, RuleFields.TERM, RuleFields.CONDITION)
                        .map(ops::createString);
            }
        };
    }

    // all_of / any_of：terms 必填且非空；空组只是编辑中间态，JSON 里不合法
    private static <T> DataResult<ConditionNode> decodeTerms(DynamicOps<T> ops, MapLike<T> input,
                                                             boolean allOf, Codec<ConditionNode> nodeCodec) {
        String op = allOf ? RuleFields.OP_ALL_OF : RuleFields.OP_ANY_OF;
        String unknown = firstUnknownField(ops, input, TERMS_FIELDS);
        if (unknown != null) {
            return DataResult.error(() -> "op=" + op + " 存在未知字段: " + unknown + "，允许字段: op / terms");
        }
        T rawTerms = input.get(RuleFields.TERMS);
        if (rawTerms == null) {
            return DataResult.error(() -> "op=" + op + " 缺少 terms 字段（应为子节点数组）");
        }
        return nodeCodec.listOf().parse(ops, rawTerms).flatMap(terms -> {
            if (terms.isEmpty()) {
                return DataResult.error(() -> "op=" + op + " 的 terms 不能为空：空条件组请直接省略 conditions 字段");
            }
            return DataResult.success(allOf ? new ConditionNode.AllOf(terms) : new ConditionNode.AnyOf(terms));
        });
    }

    // inverted：恰好一个 term
    private static <T> DataResult<ConditionNode> decodeInverted(DynamicOps<T> ops, MapLike<T> input,
                                                                Codec<ConditionNode> nodeCodec) {
        String unknown = firstUnknownField(ops, input, INVERTED_FIELDS);
        if (unknown != null) {
            return DataResult.error(() -> "op=inverted 存在未知字段: " + unknown + "，允许字段: op / term");
        }
        T rawTerm = input.get(RuleFields.TERM);
        if (rawTerm == null) {
            return DataResult.error(() -> "op=inverted 缺少 term 字段（应为单个子节点）");
        }
        return nodeCodec.parse(ops, rawTerm).map(ConditionNode.Inverted::new);
    }

    // leaf：condition 字段内是扁平的「type + 类型专属字段」对象
    private static <T> DataResult<ConditionNode> decodeLeaf(DynamicOps<T> ops, MapLike<T> input, Codec<Condition> leafCodec) {
        String unknown = firstUnknownField(ops, input, LEAF_FIELDS);
        if (unknown != null) {
            String detail = RuleFields.NEGATED.equals(unknown)
                    ? "：" + INVERT_HINT
                    : "，允许字段: op / condition";
            return DataResult.error(() -> "op=leaf 存在未知字段: " + unknown + detail);
        }
        T rawCondition = input.get(RuleFields.CONDITION);
        if (rawCondition == null) {
            return DataResult.error(() -> "op=leaf 缺少 condition 字段（条件对象应位于 condition 字段内部）");
        }
        // 条件对象内部的 negated 单独识别，给出迁移提示（TypeDispatch 的未知字段检查也会兜底拦截）
        if (ops.get(rawCondition, RuleFields.NEGATED).result().isPresent()) {
            return DataResult.error(() -> INVERT_HINT);
        }
        return leafCodec.parse(ops, rawCondition).map(ConditionNode.Leaf::new);
    }

    // 缺少 op 时的分类报错：区分旧格式（groups / 直接内联的叶）与书写遗漏
    private static <T> String missingOpMessage(MapLike<T> input) {
        if (input.get(RuleFields.TERMS) != null || input.get(RuleFields.TERM) != null) {
            return "条件节点缺少 op 字段：组合节点须写成 {\"op\":\"all_of\",\"terms\":[...]} / "
                    + "{\"op\":\"any_of\",\"terms\":[...]} / {\"op\":\"inverted\",\"term\":{...}}";
        }
        if (input.get(RuleFields.CONDITION) != null) {
            return "条件节点缺少 op 字段：条件叶须写成 {\"op\":\"leaf\",\"condition\":{...}}";
        }
        if (input.get(LEGACY_GROUPS) != null) {
            return "检测到旧格式 " + RuleFields.CONDITIONS + "." + LEGACY_GROUPS
                    + "：条件表达式已改为条件树，请改用 all_of / any_of / inverted / leaf 节点";
        }
        if (input.get(RuleFields.TYPE) != null) {
            return "检测到旧格式条件叶（type 直接内联）：须写成 {\"op\":\"leaf\",\"condition\":{\"type\":...}}；"
                    + INVERT_HINT;
        }
        return "条件节点缺少 op 字段，允许值: " + ALLOWED_OPS;
    }

    // 首个不在允许集合内的字段名；没有则返回 null
    private static <T> String firstUnknownField(DynamicOps<T> ops, MapLike<T> input, Set<String> allowed) {
        for (Pair<T, T> entry : input.entries().toList()) {
            String key = ops.getStringValue(entry.getFirst()).result().orElse(null);
            if (key == null) {
                return "<非字符串键>";
            }
            if (!allowed.contains(key)) {
                return key;
            }
        }
        return null;
    }

    // 递归自引用持有者：条件节点 codec 需要引用自身来解析 terms / term
    private static final class NodeCodecHolder {
        private Codec<ConditionNode> ref;
    }
}
