package com.meteorite.itemdespawntowhat.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 规则分类器：按生效效果类型声明的产出类别派生规则分类组。
 *
 * <p>纯函数，只依赖已解码规则与效果类型注册表，不读取世界状态。组合语义与既有分组保持一致：
 * 单一产出类别直接归类；多个根类别归 {@link #MIXED}；实体下多个子类别归 {@link #ENTITY_MIXED}；
 * 无产出或含 {@link ProductKind#EXTENSION} 归 {@link #EXTENSION}。未声明产出类别的效果
 * （默认 {@link ProductKind#NONE}）不参与归类，只有这类效果的规则按无产出处理。</p>
 */
public final class RuleCategoryClassifier {

    // 组合分类标识：多个根类别
    public static final String MIXED = "mixed";
    // 组合分类标识：实体下的多个子类别
    public static final String ENTITY_MIXED = "entity.mixed";
    // 扩展分类标识：无产出或含未知产出
    public static final String EXTENSION = "extension";
    // 实体根类别标识，用于识别实体子类别的共同父级
    private static final String ENTITY_ROOT = "entity";

    private RuleCategoryClassifier() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按生效效果派生规则分类。
     *
     * @param rule 已解码规则，允许为 null
     * @param effectTypes 效果类型注册表，允许为 null（缺失时按未知产出处理）
     * @return 分类标识；规则为空、无产出或含未知产出时为 {@link #EXTENSION}
     */
    public static String classify(@Nullable Rule rule, @Nullable TypeRegistry<EffectType<?>> effectTypes) {
        if (rule == null) {
            return EXTENSION;
        }
        Set<ProductKind> kinds = new LinkedHashSet<>();
        for (Effect effect : rule.allEffects()) {
            ProductKind kind = productKindOf(effect, effectTypes);
            if (kind != ProductKind.NONE) {
                kinds.add(kind);
            }
        }
        return combine(kinds);
    }

    /**
     * 按草稿 JSON 派生规则分类：客户端编辑产出结构时据此判断分类应跟随到哪个值。
     *
     * <p>草稿经常处于不完整状态（效果只填了一半参数），整体解码成 {@link Rule} 会失败，
     * 因此逐条效果按类型注册表解码后再读取产出类别；类型未注册或参数无法解码时按
     * {@link ProductKind#EXTENSION} 处理，与「含未知产出的规则归入扩展分组」保持一致。
     * 组合语义与 {@link #classify} 共用同一实现，不产生第二套判定。</p>
     *
     * @param body 规则草稿的 JSON 对象，允许为 null
     * @param effectTypes 效果类型注册表，允许为 null（缺失时按未知产出处理）
     * @return 分类标识；草稿为空时为 {@link #EXTENSION}
     */
    public static String classifyJson(@Nullable JsonObject body, @Nullable TypeRegistry<EffectType<?>> effectTypes) {
        if (body == null || effectTypes == null) {
            return EXTENSION;
        }
        Codec<Effect> effectCodec = RuleCodecs.effectCodec(effectTypes);
        Set<ProductKind> kinds = new LinkedHashSet<>();
        collectProductKinds(body.get(RuleFields.EFFECTS), effectCodec, effectTypes, kinds);
        JsonElement outcomes = body.get(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isJsonArray()) {
            for (JsonElement candidate : outcomes.getAsJsonArray()) {
                if (candidate.isJsonObject()) {
                    collectProductKinds(candidate.getAsJsonObject().get(RuleFields.CANDIDATE_EFFECTS),
                            effectCodec, effectTypes, kinds);
                }
            }
        }
        return combine(kinds);
    }

    // 收集一个效果列表 JSON 的产出类别：非对象元素与解码失败的效果按未知产出计
    private static void collectProductKinds(@Nullable JsonElement effects, Codec<Effect> effectCodec,
                                            TypeRegistry<EffectType<?>> effectTypes, Set<ProductKind> kinds) {
        if (effects == null || !effects.isJsonArray()) {
            return;
        }
        for (JsonElement element : effects.getAsJsonArray()) {
            ProductKind kind = element.isJsonObject() ? productKindOf(decodeEffect(effectCodec, element), effectTypes)
                    : ProductKind.EXTENSION;
            if (kind != ProductKind.NONE) {
                kinds.add(kind);
            }
        }
    }

    // 单条效果 JSON 按类型分发解码；失败时返回 null（调用方按未知产出处理）
    private static @Nullable Effect decodeEffect(Codec<Effect> effectCodec, JsonElement element) {
        return effectCodec.decode(JsonOps.INSTANCE, element).result().map(Pair::getFirst).orElse(null);
    }

    /**
     * 单条已解码效果的产出类别。
     *
     * <p>列表预览判断某条效果是否产出时共用本入口，避免展示层按效果类型字符串自行判定。
     * 未注册类型或无法解码的效果按未知产出处理，与「含未知产出的规则归入扩展分组」一致。</p>
     *
     * @param effect      已解码效果，允许为 null（草稿参数不完整导致解码失败）
     * @param effectTypes 效果类型注册表，允许为 null（缺失时按未知产出处理）
     * @return 产出类别；无法解码或未注册的类型返回 {@link ProductKind#EXTENSION}
     */
    public static ProductKind productKindOf(@Nullable Effect effect, @Nullable TypeRegistry<EffectType<?>> effectTypes) {
        if (effect == null || effectTypes == null) {
            return ProductKind.EXTENSION;
        }
        EffectType<?> type = effectTypes.getOrNull(effect.type());
        return type == null ? ProductKind.EXTENSION : productKind(type, effect);
    }

    // 效果实例的运行时类型由分发注册表保证与类型参数一致，这里的收窄只用于读取实例参数
    @SuppressWarnings("unchecked")
    private static <P extends Effect> ProductKind productKind(EffectType<P> type, Effect effect) {
        return type.productKind((P) effect);
    }

    // 组合语义：根类别数量与实体子类别数量决定最终分类标识
    private static String combine(Set<ProductKind> kinds) {
        if (kinds.isEmpty() || kinds.contains(ProductKind.EXTENSION)) {
            return EXTENSION;
        }
        Set<String> roots = new LinkedHashSet<>();
        for (ProductKind kind : kinds) {
            String category = kind.category();
            if (category == null) {
                continue;
            }
            roots.add(category.startsWith(ENTITY_ROOT + ".") ? ENTITY_ROOT : category);
        }
        if (roots.size() > 1) {
            return MIXED;
        }
        if (roots.contains(ENTITY_ROOT)) {
            return kinds.size() > 1 ? ENTITY_MIXED : kinds.iterator().next().category();
        }
        return roots.iterator().next();
    }
}
