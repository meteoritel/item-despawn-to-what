package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.param.ClimateDomain;
import com.meteorite.itemdespawntowhat.core.api.param.EnumSpec;
import com.meteorite.itemdespawntowhat.core.api.param.NestedSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.RefListSpec;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceCheck;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceContext;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.BiomeEvaluator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 条件类型 biome：按掉落物所在方块位置的生物群系匹配，支持 exact 与 climate 两种子模式。
 * - exact：按群系注册名或标签匹配，biomes 至少一项；
 * - climate：按原版 6 个气候参数（temperature / humidity / continentalness / erosion / depth / weirdness）
 *   的可选区间匹配，取值域 [-1,1]，两端可空表示该端不限制，至少需要给出一个区间。
 * 运行时判定见 BiomeEvaluator：exact 取当前位置群系逐个引用比对，climate 经 ClimateSampler 取 6 参数后逐区间比对；
 * 采样入口为 ServerChunkCache.randomState().sampler()，采样点为掉落物所在方块位置，采样结果按位置缓存。
 * 生物群系属于动态注册表（不在 BuiltInRegistries 中），validateParams 只做语法与模式相关校验，
 * 群系 id / tag 的存在性复核留给运行时/命令层（那里能拿到 RegistryAccess）。
 * JSON 示例（exact）：{ "type": "idtw:biome", "mode": "exact", "biomes": ["#minecraft:is_forest"] }
 * JSON 示例（climate）：{ "type": "idtw:biome", "mode": "climate", "temperature": { "min": 0.2 } }
 */
public record BiomeCondition(
        Mode mode,
        List<TaggedId> biomes,
        ClimateRange temperature,
        ClimateRange humidity,
        ClimateRange continentalness,
        ClimateRange erosion,
        ClimateRange depth,
        ClimateRange weirdness
) implements Condition, DynamicReferenceCheck {

    /** 类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "biome");

    // 参数 JSON 字段名
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_BIOMES = "biomes";
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_HUMIDITY = "humidity";
    private static final String FIELD_CONTINENTALNESS = "continentalness";
    private static final String FIELD_EROSION = "erosion";
    private static final String FIELD_DEPTH = "depth";
    private static final String FIELD_WEIRDNESS = "weirdness";

    // 参数声明：mode 决定哪部分参与匹配；biomes 的「非空」只在 exact 模式生效（模式相关校验留在 validateParams）
    public static final EnumSpec<Mode> MODE = EnumSpec
            .required(FIELD_MODE, EnumCodecs.lowerCase(Mode.class), "biome_mode", Mode.values())
            .initial(Mode.EXACT);
    public static final RefListSpec BIOMES = RefListSpec
            .defaultedEmpty(FIELD_BIOMES, "minecraft:worldgen/biome")
            .hint("biome.biomes.hint")
            .initial(List.of());
    public static final NestedSpec<ClimateRange> TEMPERATURE =
            NestedSpec.climate(FIELD_TEMPERATURE, ClimateRange.CODEC);
    public static final NestedSpec<ClimateRange> HUMIDITY =
            NestedSpec.climate(FIELD_HUMIDITY, ClimateRange.CODEC);
    public static final NestedSpec<ClimateRange> CONTINENTALNESS =
            NestedSpec.climate(FIELD_CONTINENTALNESS, ClimateRange.CODEC);
    public static final NestedSpec<ClimateRange> EROSION =
            NestedSpec.climate(FIELD_EROSION, ClimateRange.CODEC);
    public static final NestedSpec<ClimateRange> DEPTH =
            NestedSpec.climate(FIELD_DEPTH, ClimateRange.CODEC);
    public static final NestedSpec<ClimateRange> WEIRDNESS =
            NestedSpec.climate(FIELD_WEIRDNESS, ClimateRange.CODEC);
    public static final ParamList<BiomeCondition> PARAMS = ParamList.of(
            MODE.bind(BiomeCondition::mode),
            BIOMES.bind(BiomeCondition::biomes),
            TEMPERATURE.bind(BiomeCondition::temperature),
            HUMIDITY.bind(BiomeCondition::humidity),
            CONTINENTALNESS.bind(BiomeCondition::continentalness),
            EROSION.bind(BiomeCondition::erosion),
            DEPTH.bind(BiomeCondition::depth),
            WEIRDNESS.bind(BiomeCondition::weirdness));

    /** 参数编解码器：mode 必填，其余字段缺省为空，哪部分必填由 validateParams 判定 */
    public static MapCodec<BiomeCondition> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.field(MODE),
                PARAMS.field(BIOMES),
                PARAMS.nullableField(TEMPERATURE),
                PARAMS.nullableField(HUMIDITY),
                PARAMS.nullableField(CONTINENTALNESS),
                PARAMS.nullableField(EROSION),
                PARAMS.nullableField(DEPTH),
                PARAMS.nullableField(WEIRDNESS)
        ).apply(instance, (mode, biomes, temperature, humidity, continentalness, erosion, depth, weirdness) ->
                new BiomeCondition(mode, biomes, temperature.orElse(null), humidity.orElse(null),
                        continentalness.orElse(null), erosion.orElse(null), depth.orElse(null), weirdness.orElse(null))));
    }

    // 参数列表不可变
    public BiomeCondition {
        biomes = List.copyOf(biomes);
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    /** 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突） */
    public static ConditionType<BiomeCondition> conditionType() {
        return new SimpleConditionType<>(ID, codec(), BiomeCondition::validateParams, BiomeEvaluator::test, PARAMS);
    }

    /** 参数语义校验：mode 决定哪部分必填，未参与匹配的那部分给出告警而非错误 */
    public static boolean validateParams(BiomeCondition params, IssueCollector issues, String fieldPath) {
        if (params.mode() == Mode.EXACT) {
            boolean valid = ParamChecks.notEmpty(params.biomes(), FIELD_BIOMES, issues, ParamChecks.child(fieldPath, FIELD_BIOMES));
            if (hasClimateRange(params)) {
                issues.warn("mode=exact 时气候参数区间不参与匹配，已忽略", null, fieldPath);
            }
            return valid;
        }
        if (params.mode() == Mode.CLIMATE) {
            boolean valid = true;
            if (!hasClimateRange(params)) {
                issues.error("mode=climate 时 temperature/humidity/continentalness/erosion/depth/weirdness 至少需要一个区间",
                        null, fieldPath);
                valid = false;
            }
            if (!params.biomes().isEmpty()) {
                issues.warn("mode=climate 时 biomes 不参与匹配，已忽略", null, fieldPath);
            }
            valid &= validateRange(params.temperature(), FIELD_TEMPERATURE, issues, fieldPath);
            valid &= validateRange(params.humidity(), FIELD_HUMIDITY, issues, fieldPath);
            valid &= validateRange(params.continentalness(), FIELD_CONTINENTALNESS, issues, fieldPath);
            valid &= validateRange(params.erosion(), FIELD_EROSION, issues, fieldPath);
            valid &= validateRange(params.depth(), FIELD_DEPTH, issues, fieldPath);
            valid &= validateRange(params.weirdness(), FIELD_WEIRDNESS, issues, fieldPath);
            return valid;
        }
        return true;
    }

    /**
     * 动态引用校验：仅在 exact 模式下逐项校验群系引用是否存在；climate 模式不使用 biomes 列表。
     *
     * @param context 当前服务端、统一议题收集通道与当前条件叶的字段路径
     */
    @Override
    public void checkDynamicReferences(DynamicReferenceContext context) {
        if (mode() != Mode.EXACT) {
            return;
        }
        RefChecks.checkAll(biomes, context.server().registryAccess().registryOrThrow(Registries.BIOME),
                FIELD_BIOMES, context.issues(), ParamChecks.child(context.fieldPath(), FIELD_BIOMES));
    }

    // 是否填写了至少一个"有约束力"的气候参数区间（区间对象存在但两端都空视为未填写）
    private static boolean hasClimateRange(BiomeCondition params) {
        return hasBound(params.temperature())
                || hasBound(params.humidity())
                || hasBound(params.continentalness())
                || hasBound(params.erosion())
                || hasBound(params.depth())
                || hasBound(params.weirdness());
    }

    // 区间至少一端有界才算有效约束，避免 "temperature": {} 这类空区间被当作有效填写
    private static boolean hasBound(@Nullable ClimateRange range) {
        return range != null && (range.min() != null || range.max() != null);
    }

    // 单个气候参数区间的校验：两端取值均在 [-1,1]，且下界不得大于上界
    private static boolean validateRange(@Nullable ClimateRange range, String field, IssueCollector issues, String fieldPath) {
        if (range == null) {
            return true;
        }
        String path = ParamChecks.child(fieldPath, field);
        boolean valid = ClimateDomain.check(range.min(), range.max(), issues, path);
        valid &= ParamChecks.orderedRange(range.min(), range.max(), field, issues, path);
        return valid;
    }

    /**
     * 群系匹配子模式；JSON 取值由 core/type/EnumCodecs 统一为小写下划线且解析大小写不敏感。
     */
    public enum Mode {

        // 按群系注册名或标签精确匹配
        EXACT,
        // 按原版气候参数区间匹配
        CLIMATE
    }

    /**
     * 单个气候参数的取值区间。
     * 两端均可空：null 表示该端不限制；取值域 [-1,1]，由 validateParams 校验。
     */
    public record ClimateRange(Double min, Double max) {

        // 参数 JSON 字段名
        private static final String MIN_FIELD = "min";
        private static final String MAX_FIELD = "max";

        /** 参数编解码器 */
        public static final MapCodec<ClimateRange> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf(MIN_FIELD).forGetter(range -> Optional.ofNullable(range.min())),
                Codec.DOUBLE.optionalFieldOf(MAX_FIELD).forGetter(range -> Optional.ofNullable(range.max()))
        ).apply(instance, (min, max) -> new ClimateRange(min.orElse(null), max.orElse(null))));

        /** 以 Codec 形式暴露，供上层做可选字段引用 */
        public static final Codec<ClimateRange> CODEC = MAP_CODEC.codec();
    }
}
