package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ConsumeSourceExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.Map;

/**
 * consume_source：消耗来源掉落物的物品堆叠。
 * counts 按源物品引用（item id 或 #tag）分别配置消耗量，未配置的引用取 count。
 * 规则未显式声明任何 consume_* 效果时按隐式消耗处理（见 core/model/Rule#usesImplicitSourceConsumption）。本类只承载参数模型与参数校验。
 */
public record ConsumeSourceEffect(
        int count,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions,
        Map<TaggedId, Integer> counts
) implements Effect {

    public ConsumeSourceEffect {
        counts = Map.copyOf(counts);
    }

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "consume_source");

    // 类型专属参数字段名
    public static final String COUNT_FIELD = "count";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ConsumeSourceEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(ConsumeSourceEffect::count),
                CommonFields.delayTicks(ConsumeSourceEffect::delayTicks),
                CommonFields.chance(ConsumeSourceEffect::chance),
                CommonFields.optionalConditions(ConsumeSourceEffect::conditions, expressionCodec),
                Codec.unboundedMap(TaggedId.CODEC, Codec.intRange(MIN_COUNT, MAX_COUNT))
                        .optionalFieldOf(RuleFields.ITEM_COUNTS, Map.of()).forGetter(ConsumeSourceEffect::counts)
        ).apply(instance, (count, delayTicks, chance, conditions, counts) ->
                new ConsumeSourceEffect(count, delayTicks, chance, conditions.orElse(null), counts)));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。
    // 显式实现准备入口：效果级条件与概率在成功转化当刻判定并随任务携带，源实体清空后不再读取原掉落物；
    // 本类型的参数与所需数量都是静态的，固定输入只承载跳过判定，不复制由结算层持有的源库存。
    public static EffectType<ConsumeSourceEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        EffectExecutor<ConsumeSourceEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(ConsumeSourceEffect effect, EffectContext context) {
                return ConsumeSourceExecutor.execute(effect, context);
            }

            @Override
            public EffectPreparation prepare(ConsumeSourceEffect effect, ServerLevel level, Vec3 position,
                                             ItemStack sourceStack, boolean fillOrigin) {
                // 源件数由结算层开组时从持有库存扣减并记账，执行期只经上下文读本组已付成本；
                // 因此这里不冻结数量，也不保留对源实体的引用
                return EffectPreparation.ready();
            }
        };
        return new SimpleEffectType<>(ID, codec(expressionCodec), ConsumeSourceEffect::validateParams, executor);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(ConsumeSourceEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 效果级条件替换：其余参数原样保留，供运行期门槛投影重建真实效果类型
    @Override
    public ConsumeSourceEffect withConditions(@Nullable ConditionExpression conditions) {
        return new ConsumeSourceEffect(count, delayTicks, chance, conditions, counts);
    }
}
