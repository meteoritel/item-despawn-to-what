package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.param.BoolSpec;
import com.meteorite.itemdespawntowhat.core.api.param.EnumSpec;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectOffset;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.WeatherExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * weather：切换触发维度（或其所在区域）的天气。
 * mode 必填（rain / clear）；duration_ticks 为持续刻数（上限对齐原版一天的 24000 刻）。
 * 注册为一次性效果（oneShot）：不参与候选容量计算，对同一源最多尝试一组。本类只承载参数模型与参数校验。
 */
public record WeatherEffect(
        Mode mode,
        int durationTicks,
        boolean thundering,
        int delayTicks,
        double chance,
        EffectOffset offset
) implements Effect {

    /** 效果类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "weather");

    // 类型专属参数字段名
    public static final String MODE_FIELD = "mode";
    public static final String DURATION_TICKS_FIELD = "duration_ticks";
    public static final String THUNDERING_FIELD = "thundering";

    // 参数默认值与取值区间
    public static final int DEFAULT_DURATION_TICKS = 6000;
    public static final int MIN_DURATION_TICKS = 1;
    public static final int MAX_DURATION_TICKS = 24000;
    public static final boolean DEFAULT_THUNDERING = false;

    // 天气模式
    public enum Mode {
        RAIN,
        CLEAR;

        /** JSON 取值：小写下划线，解析大小写不敏感 */
        public static final Codec<Mode> CODEC = EnumCodecs.lowerCase(Mode.class);
    }

    // 参数声明：类型专属字段在前，通用字段复用 CommonFields 的声明
    public static final EnumSpec<Mode> MODE = EnumSpec
            .required(MODE_FIELD, Mode.CODEC, "weather_effect_mode", Mode.values())
            .initial(Mode.RAIN);
    public static final IntSpec DURATION_TICKS = IntSpec
            .defaulted(DURATION_TICKS_FIELD, DEFAULT_DURATION_TICKS, MIN_DURATION_TICKS, MAX_DURATION_TICKS)
            .initial(DEFAULT_DURATION_TICKS);
    public static final BoolSpec THUNDERING = BoolSpec.defaulted(THUNDERING_FIELD, DEFAULT_THUNDERING)
            .initial(DEFAULT_THUNDERING);
    public static final ParamList<WeatherEffect> PARAMS = ParamList.of(MODE.bind(WeatherEffect::mode), DURATION_TICKS.bind(WeatherEffect::durationTicks), THUNDERING.bind(WeatherEffect::thundering)).plus(CommonFields.EFFECT_PARAMS);

    /** 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null） */
    public static MapCodec<WeatherEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(MODE),
                PARAMS.field(DURATION_TICKS),
                PARAMS.field(THUNDERING),
                PARAMS.field(CommonFields.DELAY_TICKS),
                PARAMS.field(CommonFields.CHANCE),
                PARAMS.field(CommonFields.OFFSET)
        ).apply(instance, WeatherEffect::new));
    }

    /** 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。；显式实现准备入口：概率在成功转化当刻判定，天气世界写延后到到期时按固定输入执行；；需要准备的类型用显式实现覆盖 prepare 与按准备创建执行状态的重载（lambda 只能表达 execute）。 */
    public static EffectType<WeatherEffect> effectType() {
        EffectExecutor<WeatherEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(WeatherEffect effect, EffectContext context) {
                return WeatherExecutor.execute(effect, context);
            }

            @Override
            public EffectPreparation prepare(WeatherEffect effect, ServerLevel level, Vec3 position,
                                             ItemEntity sourceEntity, ItemStack sourceStack, boolean fillOrigin) {
                // 采用成功转化当刻固定输入的路径：概率在此后同刻判定并随任务携带；
                // 天气世界状态（天空光、当前天气）不在此刻冻结，留到执行时实时读取
                return EffectPreparation.ready();
            }

            @Override
            public EffectExecution start(WeatherEffect effect, EffectContext context, @Nullable EffectPreparation preparation) {
                return WeatherExecutor.start(effect, context, preparation);
            }
        };
        return new SimpleEffectType<>(ID, codec(), executor, true,
                ProductKind.WORLD, PARAMS);
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

}
