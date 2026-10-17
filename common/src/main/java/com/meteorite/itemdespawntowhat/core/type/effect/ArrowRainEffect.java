package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ArrowRainExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * arrow_rain：在触发位置上方生成一阵箭雨。
 * potion_effects 可空，元素为箭矢携带的药水效果（效果 id + 持续刻数 + 等级）。
 * 注册为一次性效果（oneShot）：不参与候选容量计算，对同一源最多尝试一组。本类只承载参数模型与参数校验。
 */
public record ArrowRainEffect(
        int count,
        Pickup pickup,
        @Nullable List<PotionEffectSpec> potionEffects,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "arrow_rain");

    // 类型专属参数字段名
    public static final String COUNT_FIELD = "count";
    public static final String PICKUP_FIELD = "pickup";
    public static final String POTION_EFFECTS_FIELD = "potion_effects";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 16;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 256;
    public static final Pickup DEFAULT_PICKUP = Pickup.DISALLOWED;

    // 紧凑构造器：列表字段做防御性拷贝，保持记录不可变
    public ArrowRainEffect {
        potionEffects = potionEffects == null ? null : List.copyOf(potionEffects);
    }

    // 箭矢拾取模式
    public enum Pickup {
        DISALLOWED,
        ALLOWED,
        CREATIVE_ONLY;

        // JSON 取值：小写下划线，解析大小写不敏感
        public static final Codec<Pickup> CODEC = EnumCodecs.lowerCase(Pickup.class);
    }

    /**
     * 箭矢携带的一项药水效果。
     * effect 为 TaggedId（支持 #tag 形式的药水效果标签）。
     */
    public record PotionEffectSpec(
            TaggedId effect,
            int durationTicks,
            int amplifier
    ) {

        // 参数字段名
        public static final String EFFECT_FIELD = "effect";
        public static final String DURATION_TICKS_FIELD = "duration_ticks";
        public static final String AMPLIFIER_FIELD = "amplifier";

        // 参数默认值与取值区间
        public static final int DEFAULT_DURATION_TICKS = 100;
        public static final int MIN_DURATION_TICKS = 1;
        public static final int MAX_DURATION_TICKS = 1000000;
        public static final int DEFAULT_AMPLIFIER = 0;
        public static final int MIN_AMPLIFIER = 0;
        public static final int MAX_AMPLIFIER = 255;

        // 药水效果编解码器
        public static final MapCodec<PotionEffectSpec> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.fieldOf(EFFECT_FIELD).forGetter(PotionEffectSpec::effect),
                Codec.intRange(MIN_DURATION_TICKS, MAX_DURATION_TICKS)
                        .optionalFieldOf(DURATION_TICKS_FIELD, DEFAULT_DURATION_TICKS)
                        .forGetter(PotionEffectSpec::durationTicks),
                Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER)
                        .optionalFieldOf(AMPLIFIER_FIELD, DEFAULT_AMPLIFIER)
                        .forGetter(PotionEffectSpec::amplifier)
        ).apply(instance, PotionEffectSpec::new));

        // 单项药水效果的语义校验；问题写入 issues，不抛异常
        public static boolean validateParams(PotionEffectSpec params, IssueCollector issues, String fieldPath) {
            String effectPath = ParamChecks.child(fieldPath, EFFECT_FIELD);
            boolean valid = ParamChecks.required(params.effect(), EFFECT_FIELD, issues, effectPath);
            valid &= RefChecks.check(params.effect(), BuiltInRegistries.MOB_EFFECT, EFFECT_FIELD,
                    issues, effectPath);
            valid &= ParamChecks.inRange(params.durationTicks(), MIN_DURATION_TICKS, MAX_DURATION_TICKS,
                    DURATION_TICKS_FIELD, issues, ParamChecks.child(fieldPath, DURATION_TICKS_FIELD));
            valid &= ParamChecks.inRange(params.amplifier(), MIN_AMPLIFIER, MAX_AMPLIFIER, AMPLIFIER_FIELD,
                    issues, ParamChecks.child(fieldPath, AMPLIFIER_FIELD));
            return valid;
        }
    }

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ArrowRainEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(ArrowRainEffect::count),
                Pickup.CODEC.optionalFieldOf(PICKUP_FIELD, DEFAULT_PICKUP).forGetter(ArrowRainEffect::pickup),
                PotionEffectSpec.CODEC.codec().listOf().optionalFieldOf(POTION_EFFECTS_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.potionEffects())),
                CommonFields.delayTicks(ArrowRainEffect::delayTicks),
                CommonFields.chance(ArrowRainEffect::chance),
                CommonFields.optionalConditions(ArrowRainEffect::conditions, expressionCodec)
        ).apply(instance, (count, pickup, potionEffects, delayTicks, chance, conditions) ->
                new ArrowRainEffect(count, pickup, potionEffects.orElse(null), delayTicks, chance,
                        conditions.orElse(null))));
    }

    /**
     * 本次箭雨在成功转化当刻固定的输入：只放已经抽定的药水效果，
     * 或无法解析药水引用时固定下来的失败原因；不冻结任何执行期世界状态。
     * @param potionEffects 已抽定的药水效果；列表只读、整场序列共用，套用到箭矢时再逐个复制
     * @param failure 成功转化当刻就无法解析的失败原因；null 表示照常执行
     */
    public record Prepared(List<MobEffectInstance> potionEffects, @Nullable String failure)
            implements EffectPreparation {

        public Prepared {
            potionEffects = List.copyOf(potionEffects);
        }
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。
    // 显式实现执行器契约：箭雨序列只能经预算内的执行状态推进，直接 execute 一律拒绝；
    // 药水效果属标签随机抽取，须在成功转化当刻抽定并随准备结果携带，执行期不再抽取。
    public static EffectType<ArrowRainEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        EffectExecutor<ArrowRainEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(ArrowRainEffect effect, EffectContext context) {
                return ArrowRainExecutor.execute(effect, context);
            }

            @Override
            public EffectPreparation prepare(ArrowRainEffect effect, ServerLevel level, Vec3 position,
                                             ItemEntity sourceEntity, ItemStack sourceStack, boolean fillOrigin) {
                // 只抽取药水引用：次数、拾取模式与基准位置来自效果参数与触发点，执行期世界状态不在此时冻结
                try {
                    return new Prepared(ArrowRainExecutor.resolvePotionEffects(effect.potionEffects(), level.random), null);
                } catch (IllegalStateException failure) {
                    // 加载期已校验引用，运行期无法解析属可见错误：固定下来按失败回执，不在执行期重试改写结果。
                    // 缺失消息时退回异常描述，避免与「照常执行」的空原因混同
                    String reason = failure.getMessage();
                    return new Prepared(List.of(), reason == null ? failure.toString() : reason);
                }
            }

            @Override
            public EffectExecution start(ArrowRainEffect effect, EffectContext context, @Nullable EffectPreparation preparation) {
                return ArrowRainExecutor.start(effect, context, preparation);
            }
        };
        return new SimpleEffectType<>(ID, codec(expressionCodec), ArrowRainEffect::validateParams, executor, true);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(ArrowRainEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
        valid &= ParamChecks.required(params.pickup(), PICKUP_FIELD, issues,
                ParamChecks.child(fieldPath, PICKUP_FIELD));
        if (params.potionEffects() != null) {
            String effectsPath = ParamChecks.child(fieldPath, POTION_EFFECTS_FIELD);
            for (int index = 0; index < params.potionEffects().size(); index++) {
                PotionEffectSpec spec = params.potionEffects().get(index);
                String specPath = ParamChecks.index(effectsPath, index);
                valid &= ParamChecks.required(spec, POTION_EFFECTS_FIELD, issues, specPath);
                if (spec != null) {
                    valid &= PotionEffectSpec.validateParams(spec, issues, specPath);
                }
            }
        }
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 效果级条件替换：其余参数原样保留，供运行期门槛投影重建真实效果类型
    @Override
    public ArrowRainEffect withConditions(@Nullable ConditionExpression conditions) {
        return new ArrowRainEffect(count, pickup, potionEffects, delayTicks, chance, conditions);
    }
}
