package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
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
            ProductKind kind = productKind(effect, effectTypes);
            if (kind != ProductKind.NONE) {
                kinds.add(kind);
            }
        }
        return combine(kinds);
    }

    // 单条效果的产出类别：未注册的类型视为未知产出，其余由效果类型按实例参数解析
    private static ProductKind productKind(Effect effect, @Nullable TypeRegistry<EffectType<?>> effectTypes) {
        EffectType<?> type = effectTypes == null ? null : effectTypes.getOrNull(effect.type());
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
