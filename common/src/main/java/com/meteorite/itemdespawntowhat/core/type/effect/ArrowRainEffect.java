package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.param.EnumSpec;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.NestedListSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ParamUnit;
import com.meteorite.itemdespawntowhat.core.api.param.RefSpec;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
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
        double chance
) implements Effect {

    /** 效果类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "arrow_rain");

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

        /** JSON 取值：小写下划线，解析大小写不敏感 */
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

        /** 单项药水效果的参数声明 */
        public static final RefSpec EFFECT = RefSpec.required(EFFECT_FIELD, "minecraft:mob_effect");
        public static final IntSpec DURATION_TICKS = IntSpec
                .defaulted(DURATION_TICKS_FIELD, DEFAULT_DURATION_TICKS, MIN_DURATION_TICKS, MAX_DURATION_TICKS);
        public static final IntSpec AMPLIFIER = IntSpec
                .defaulted(AMPLIFIER_FIELD, DEFAULT_AMPLIFIER, MIN_AMPLIFIER, MAX_AMPLIFIER)
                .unit(ParamUnit.LEVEL);
        public static final ParamList<PotionEffectSpec> PARAMS = ParamList.of(
                EFFECT.bind(PotionEffectSpec::effect),
                DURATION_TICKS.bind(PotionEffectSpec::durationTicks),
                AMPLIFIER.bind(PotionEffectSpec::amplifier));

        /** 药水效果编解码器 */
        public static final MapCodec<PotionEffectSpec> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(EFFECT),
                PARAMS.field(DURATION_TICKS),
                PARAMS.field(AMPLIFIER)
        ).apply(instance, PotionEffectSpec::new));

        /** 校验声明值域之外的状态效果引用；数值与存在性由嵌套声明检查。 */
        public static boolean validateReferences(PotionEffectSpec params, IssueCollector issues, String fieldPath) {
            String effectPath = ParamChecks.child(fieldPath, EFFECT_FIELD);
            return RefChecks.check(params.effect(), BuiltInRegistries.MOB_EFFECT, EFFECT_FIELD, issues, effectPath);
        }
    }

    /** 参数声明：类型专属字段在前，通用字段复用 CommonFields 的声明 */
    public static final IntSpec COUNT = IntSpec.defaulted(COUNT_FIELD, DEFAULT_COUNT, MIN_COUNT, MAX_COUNT)
            .initial(DEFAULT_COUNT);
    public static final EnumSpec<Pickup> PICKUP = EnumSpec
            .defaulted(PICKUP_FIELD, Pickup.CODEC, DEFAULT_PICKUP, "arrow_rain_pickup", Pickup.values())
            .editorRequired(false)
            .initial(DEFAULT_PICKUP);
    public static final NestedListSpec<PotionEffectSpec> POTION_EFFECTS = NestedListSpec
            .nullable(POTION_EFFECTS_FIELD, PotionEffectSpec.CODEC.codec(), PotionEffectSpec.PARAMS)
            .hint("arrow_rain.potion_effects.hint")
            .initial(List.of());
    public static final ParamList<ArrowRainEffect> PARAMS = ParamList.of(COUNT.bind(ArrowRainEffect::count), PICKUP.bind(ArrowRainEffect::pickup), POTION_EFFECTS.bind(ArrowRainEffect::potionEffects)).plus(CommonFields.EFFECT_PARAMS);

    /** 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null） */
    public static MapCodec<ArrowRainEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(COUNT),
                PARAMS.field(PICKUP),
                PARAMS.nullableField(POTION_EFFECTS),
                PARAMS.field(CommonFields.DELAY_TICKS),
                PARAMS.field(CommonFields.CHANCE)
        ).apply(instance, (count, pickup, potionEffects, delayTicks, chance) ->
                new ArrowRainEffect(count, pickup, potionEffects.orElse(null), delayTicks, chance)));
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

    /** 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。；显式实现执行器契约：箭雨序列只能经预算内的执行状态推进，直接 execute 一律拒绝；；药水效果属标签随机抽取，须在成功转化当刻抽定并随准备结果携带，执行期不再抽取。 */
    public static EffectType<ArrowRainEffect> effectType() {
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
        return new SimpleEffectType<>(ID, codec(), ArrowRainEffect::validateParams, executor, true,
                ProductKind.WORLD, PARAMS);
    }

    /** 校验药水效果的注册表引用；字段值域与必填由 PARAMS 统一检查。 */
    public static boolean validateParams(ArrowRainEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = true;
        if (params.potionEffects() != null) {
            String effectsPath = ParamChecks.child(fieldPath, POTION_EFFECTS_FIELD);
            for (int index = 0; index < params.potionEffects().size(); index++) {
                PotionEffectSpec spec = params.potionEffects().get(index);
                String specPath = ParamChecks.index(effectsPath, index);
                if (spec != null) {
                    valid &= PotionEffectSpec.validateReferences(spec, issues, specPath);
                }
            }
        }
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

}
