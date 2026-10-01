package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * 条件类型 weather：按掉落物所在维度当前的天气匹配。
 * 参数 weather 必填，取值为 clear / rain / thunder（解析大小写不敏感，输出统一小写）。
 * 说明：本阶段只做参数模型与校验，实际天气读取属阶段③ 的求值器。
 * JSON 示例：{ "type": "itemdespawntowhat:weather", "weather": "thunder" }
 */
public record WeatherCondition(boolean negated, Kind weather) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "weather");

    // 参数 JSON 字段名
    private static final String FIELD_WEATHER = "weather";

    // 参数编解码器：weather 必填
    public static final MapCodec<WeatherCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CommonFields.negated(WeatherCondition::negated),
            Kind.CODEC.fieldOf(FIELD_WEATHER).forGetter(WeatherCondition::weather)
    ).apply(instance, WeatherCondition::new));

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<WeatherCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, WeatherCondition::validateParams);
    }

    // 参数语义校验：天气取值必填（codec 已拦截缺失，这里兜底程序化构造）
    public static boolean validateParams(WeatherCondition params, IssueCollector issues, String fieldPath) {
        return ParamChecks.required(params.weather(), FIELD_WEATHER, issues,
                ParamChecks.child(fieldPath, FIELD_WEATHER));
    }

    /**
     * 天气取值。
     */
    public enum Kind {

        // 晴朗
        CLEAR("clear"),
        // 下雨
        RAIN("rain"),
        // 雷暴
        THUNDER("thunder");

        // JSON 取值（小写下划线）
        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        // 字符串编解码：解析失败给出可读错误
        public static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(Kind::parse, Kind::serializedName);

        // JSON 取值
        public String serializedName() {
            return serializedName;
        }

        // 大小写不敏感解析；非法取值返回空
        public static Optional<Kind> byName(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (Kind kind : values()) {
                if (kind.serializedName.equals(normalized)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }

        // 解析入口：错误信息列出全部合法取值
        private static DataResult<Kind> parse(String raw) {
            Optional<Kind> parsed = byName(raw);
            if (parsed.isPresent()) {
                return DataResult.success(parsed.get());
            }
            return DataResult.error(() -> "未知的天气取值: " + raw + "，可选: " + names());
        }

        // 合法取值文本
        private static String names() {
            StringBuilder builder = new StringBuilder();
            for (Kind kind : values()) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                builder.append(kind.serializedName);
            }
            return builder.toString();
        }
    }
}
