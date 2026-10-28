package com.meteorite.itemdespawntowhat.core.type;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.extension.RuleTypeProvider;
import java.util.ServiceLoader;
import java.util.Comparator;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.registry.SimpleTypeRegistry;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;

/**
 * 内置与 SPI 扩展类型注册表的规范装配点，供服务端与客户端各自装配。
 * 装配顺序固定：条件类型冻结 → 条件表达式编解码器 → 效果类型冻结（规则级条件树需要该编解码器）。
 * 运行时引导层直接使用本记录，避免各处重复拼装。
 */
public record BuiltinTypeRegistries(
        TypeRegistry<ConditionType<?>> conditionTypes,
        TypeRegistry<EffectType<?>> effectTypes,
        Codec<ConditionExpression> conditionExpressionCodec
) {

    /**
     * 该规则每轮消耗的源物品数量（固定成本优先）：
     * - 显式声明 source_cost → 取当前源物品对应数量（直接物品优先，其次源列表中的首个命中标签，缺省 1）；
     * - 未声明 source_cost → 隐式消耗 1。
     */
    public int perRoundSourceConsumption(Rule rule, ItemStack source) {
        if (rule.sourceCost() != null) {
            return rule.sourceCost().forStack(rule.source(), source);
        }
        return 1;
    }

    /** 按 provider 类名稳定排序，构建并冻结内置与 SPI 扩展类型表。 */
    public static BuiltinTypeRegistries create() {
        var providers = ServiceLoader.load(RuleTypeProvider.class).stream()
                .sorted(Comparator.comparing(provider -> provider.type().getName()))
                .map(ServiceLoader.Provider::get).toList();
        var conditions = new SimpleTypeRegistry<ConditionType<?>>();
        BuiltinConditionTypes.registerInto(conditions);
        providers.forEach(provider -> provider.registerConditions(conditions));
        TypeRegistry<ConditionType<?>> conditionTypes = conditions.freeze();
        Codec<ConditionExpression> expressionCodec = RuleCodecs.conditionExpressionCodec(conditionTypes);
        var effects = new SimpleTypeRegistry<EffectType<?>>();
        BuiltinEffectTypes.registerInto(effects);
        providers.forEach(provider -> provider.registerEffects(effects));
        effects.all().forEach(BuiltinTypeRegistries::validateCommonEffectFields);
        TypeRegistry<EffectType<?>> effectTypes = effects.freeze();
        return new BuiltinTypeRegistries(conditionTypes, effectTypes, expressionCodec);
    }

    private static void validateCommonEffectFields(EffectType<?> type) {
        if (type.params().get(CommonFields.DELAY_TICKS.name()) != CommonFields.DELAY_TICKS) {
            throw new IllegalStateException("效果类型 " + type.id() + " 未声明通用字段 " + CommonFields.DELAY_TICKS.name());
        }
        if (type.params().get(CommonFields.CHANCE.name()) != CommonFields.CHANCE) {
            throw new IllegalStateException("效果类型 " + type.id() + " 未声明通用字段 " + CommonFields.CHANCE.name());
        }
    }
}
