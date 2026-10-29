package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.param.BoolSpec;
import com.meteorite.itemdespawntowhat.core.api.param.EnumSpec;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ParamShape;
import com.meteorite.itemdespawntowhat.core.api.param.RefSpec;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectOffset;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.PlaceBlockExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * place_block：在触发位置按指定形状放置方块。
 * block（TaggedId，可空）与 use_source_block 至少需要其一：前者放置指定方块，后者放置源物品对应的方块。
 * radius 是本次放置的最大向外扩散边界：方形、圆形与十字的候选固定在触发位置所在的那一层，
 * 立方体与球体的候选在以触发位置为中心的三维范围内展开。limit（可空，省略表示不限）
 * 是该形状覆盖范围内同类方块的数量上限：它按同一个形状判定统计，只收窄实际放置量，不扩大边界，
 * 因此该形状在当前半径下最多能填多少个位置，就是 limit 的上限。
 * 本类只承载参数模型与参数校验。
 */
public record PlaceBlockEffect(
        @Nullable TaggedId block,
        boolean useSourceBlock,
        Shape shape,
        int count,
        int radius,
        @Nullable Integer limit,
        int delayTicks,
        double chance,
        EffectOffset offset
) implements Effect {

    /** 效果类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "place_block");

    // 类型专属参数字段名
    public static final String BLOCK_FIELD = "block";
    public static final String USE_SOURCE_BLOCK_FIELD = "use_source_block";
    public static final String SHAPE_FIELD = "shape";
    public static final String COUNT_FIELD = "count";
    public static final String RADIUS_FIELD = "radius";
    public static final String LIMIT_FIELD = "limit";

    // 参数默认值与取值区间
    public static final boolean DEFAULT_USE_SOURCE_BLOCK = false;
    public static final Shape DEFAULT_SHAPE = Shape.SQUARE;
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int DEFAULT_RADIUS = 6;
    public static final int MIN_RADIUS = 1;
    public static final int MIN_LIMIT = 1;

    /**
     * 形状与半径对应的候选位置总数，用作该形状下 limit 的取值上限。
     *
     * <p>统计口径是形状覆盖的整个区域，与执行器的候选判定共用 {@link Shape#accepts}，
     * 因此编辑器的滑块窗口、运行期可填充的位置数与上限三者一致。limit 超过该容量时仍可保存，
     * 但区域里本来就装不下那么多同类方块，等价于不限。radius 自身不设静态上界，
     * 由服务端配置 max_place_block_radius 在加载与保存校验时限定。</p>
     *
     * @param shape  放置形状；null 按默认形状
     * @param radius 放置半径
     * @return 该形状在半径内的位置总数，超出 int 表示范围时取 int 上界
     */
    public static int limitUpperBound(@Nullable Shape shape, int radius) {
        return (shape == null ? DEFAULT_SHAPE : shape).capacity(radius);
    }

    // 放置形状；新增取值会自动进入 codec 与编辑器轮询按钮，容量统计与候选判定由同一个 accepts 决定
    public enum Shape {
        SQUARE(false),
        CIRCLE(false),
        CROSS(false),
        CUBE(true),
        SPHERE(true);

        /** JSON 取值：小写下划线，解析大小写不敏感 */
        public static final Codec<Shape> CODEC = EnumCodecs.lowerCase(Shape.class);

        // 单层形状的位置数不超过边长平方，边长超过它时结果必然超出 int 表示范围
        private static final long MAX_INT_SQUARE_SIDE = 46340L;
        // 内接立方体的边长超过它时，体积必然超出 int 表示范围
        private static final long MAX_INT_CUBE_SIDE = 1291L;
        private static final double SQRT_3 = Math.sqrt(3.0D);

        private final boolean threeDimensional;

        Shape(boolean threeDimensional) {
            this.threeDimensional = threeDimensional;
        }

        /**
         * 候选位置是否在触发位置的上下方向展开。
         *
         * <p>单层形状的候选固定在触发位置所在的那一层；三维形状以触发位置为中心向外扩展，
         * 由执行器据此决定是否遍历垂直偏移。</p>
         *
         * @return 三维形状返回 true
         */
        public boolean threeDimensional() {
            return threeDimensional;
        }

        /**
         * 偏移是否落在本形状内：单层形状忽略垂直偏移，圆形与球体按欧氏距离收边。
         *
         * <p>执行器的候选生成与 {@link #capacity} 的数量统计共用本判定，界面上的上限才不会与实际可填充位置脱节；
         * 圆形与球体都用整数平方距离比较，边界点不会因开方的浮点误差被多算或少算。</p>
         *
         * @param dx     相对触发位置的水平 x 偏移
         * @param dy     相对触发位置的垂直偏移；单层形状传 0
         * @param dz     相对触发位置的水平 z 偏移
         * @param radius 放置半径
         * @return 该偏移是否属于本形状
         */
        public boolean accepts(int dx, int dy, int dz, int radius) {
            return switch (this) {
                case SQUARE, CUBE -> true;
                case CROSS -> dx == 0 || dz == 0;
                case CIRCLE -> squaredDistance(dx, 0, dz) < squared(radius + 1L);
                case SPHERE -> squaredDistance(dx, dy, dz) < squared(radius + 1L);
            };
        }

        /**
         * 该形状在指定半径内的位置总数，也就是 limit 的取值上限。
         *
         * <p>方形取边长的平方、十字取两条轴、立方体取边长的立方，圆形与球体按 {@link #accepts} 收边后逐层统计；
         * 半径大到结果必然超出 int 时直接返回上界，不做逐点枚举。</p>
         *
         * @param radius 放置半径
         * @return 位置总数，超出 int 表示范围时取 int 上界
         */
        public int capacity(int radius) {
            int r = Math.max(radius, MIN_RADIUS);
            long side = r * 2L + 1L;
            return switch (this) {
                case CROSS -> (int) Math.min(4L * r + 1L, Integer.MAX_VALUE);
                case SQUARE -> side > MAX_INT_SQUARE_SIDE ? Integer.MAX_VALUE : (int) (side * side);
                case CUBE -> cubeCapacity(side);
                case CIRCLE -> circleCapacity(r, side);
                case SPHERE -> sphereCapacity(r, side);
            };
        }

        /** JSON 取值：枚举名的小写形式。 */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * 解析 JSON 取值；空值与未知取值回落到默认形状。
         *
         * @param value wire 取值，大小写不敏感
         * @return 对应形状
         */
        public static Shape fromWire(@Nullable String value) {
            if (value == null) {
                return DEFAULT_SHAPE;
            }
            for (Shape shape : values()) {
                if (shape.wireName().equalsIgnoreCase(value)) {
                    return shape;
                }
            }
            return DEFAULT_SHAPE;
        }

        // 立方体容量：边长立方，先按边长排除越过 int 上界的乘法
        private static int cubeCapacity(long side) {
            if (side > MAX_INT_SQUARE_SIDE) {
                return Integer.MAX_VALUE;
            }
            long square = side * side;
            return square > Integer.MAX_VALUE / side ? Integer.MAX_VALUE : (int) (square * side);
        }

        // 圆形容量：按行累加满足 dx² + dz² < (radius + 1)² 的位置数
        private static int circleCapacity(int radius, long side) {
            if (side > MAX_INT_SQUARE_SIDE) {
                return Integer.MAX_VALUE;
            }
            long limitSquared = squared(radius + 1L);
            long total = 0L;
            for (int dx = -radius; dx <= radius; dx++) {
                total += spanWidth(limitSquared - (long) dx * dx);
                if (total > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
            return (int) total;
        }

        // 球体容量：逐层累加满足 dx² + dy² + dz² < (radius + 1)² 的位置数
        private static int sphereCapacity(int radius, long side) {
            if (side > MAX_INT_SQUARE_SIDE) {
                return Integer.MAX_VALUE;
            }
            long limit = radius + 1L;
            // 内接立方体已超过 int 上界：不必逐点枚举即可断定容量取上界
            if ((long) (limit / SQRT_3) * 2L + 1L > MAX_INT_CUBE_SIDE) {
                return Integer.MAX_VALUE;
            }
            long limitSquared = limit * limit;
            long total = 0L;
            for (int dy = -radius; dy <= radius; dy++) {
                long plane = limitSquared - (long) dy * dy;
                if (plane <= 0L) {
                    continue;
                }
                long layer = 0L;
                for (int dx = -radius; dx <= radius; dx++) {
                    layer += spanWidth(plane - (long) dx * dx);
                }
                total += layer;
                if (total > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
            return (int) total;
        }

        // 剩余平方距离内可容纳的整数坐标个数：|t|² < remaining 的解数
        private static long spanWidth(long remaining) {
            return remaining <= 0L ? 0L : integerSqrt(remaining - 1L) * 2L + 1L;
        }

        // 向下取整的整数平方根：浮点开方只作起点，再按整数比较夹逼，避免边界误差
        private static long integerSqrt(long value) {
            long root = (long) Math.sqrt((double) value);
            while (root > 0L && root * root > value) {
                root--;
            }
            while ((root + 1L) * (root + 1L) <= value) {
                root++;
            }
            return root;
        }

        // 圆形与球体共用的整数平方距离
        private static long squaredDistance(int dx, int dy, int dz) {
            return (long) dx * dx + (long) dy * dy + (long) dz * dz;
        }

        private static long squared(long value) {
            return value * value;
        }
    }

    /** 参数声明：类型专属字段在前，通用字段复用 CommonFields 的声明；limit 的滑块窗口按默认形状与半径的容量取上限 */
    public static final RefSpec BLOCK = RefSpec.nullable(BLOCK_FIELD, "minecraft:block");
    public static final BoolSpec USE_SOURCE_BLOCK = BoolSpec.defaulted(USE_SOURCE_BLOCK_FIELD, DEFAULT_USE_SOURCE_BLOCK)
            .initial(DEFAULT_USE_SOURCE_BLOCK);
    public static final EnumSpec<Shape> SHAPE = EnumSpec
            .defaulted(SHAPE_FIELD, Shape.CODEC, DEFAULT_SHAPE, "place_block_shape", Shape.values())
            .editorRequired(false)
            .initial(DEFAULT_SHAPE)
            .shape(ParamShape.ENUM_CYCLE)
            .hint("place_block.shape.hint");
    public static final IntSpec COUNT = IntSpec.defaulted(COUNT_FIELD, DEFAULT_COUNT, MIN_COUNT, MAX_COUNT)
            .initial(DEFAULT_COUNT);
    public static final IntSpec RADIUS = IntSpec
            .defaulted(RADIUS_FIELD, DEFAULT_RADIUS, MIN_RADIUS, Integer.MAX_VALUE)
            .initial(DEFAULT_RADIUS)
            .window(MIN_RADIUS, Integer.MAX_VALUE);
    public static final IntSpec LIMIT = IntSpec.nullable(LIMIT_FIELD, MIN_LIMIT, Integer.MAX_VALUE)
            .bounded()
            .window(MIN_LIMIT, limitUpperBound(DEFAULT_SHAPE, DEFAULT_RADIUS));
    public static final ParamList<PlaceBlockEffect> PARAMS = ParamList.of(BLOCK.bind(PlaceBlockEffect::block), USE_SOURCE_BLOCK.bind(PlaceBlockEffect::useSourceBlock), SHAPE.bind(PlaceBlockEffect::shape), COUNT.bind(PlaceBlockEffect::count), RADIUS.bind(PlaceBlockEffect::radius), LIMIT.bind(PlaceBlockEffect::limit)).plus(CommonFields.EFFECT_PARAMS);

    /** 类型专属参数编解码器；通用字段复用 CommonFields 片段，可空字段经 Optional 进入 codec */
    public static MapCodec<PlaceBlockEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARAMS.nullableField(BLOCK),
                PARAMS.field(USE_SOURCE_BLOCK),
                PARAMS.field(SHAPE),
                PARAMS.field(COUNT),
                PARAMS.field(RADIUS),
                PARAMS.nullableField(LIMIT),
                PARAMS.field(CommonFields.DELAY_TICKS),
                PARAMS.field(CommonFields.CHANCE),
                PARAMS.field(CommonFields.OFFSET)
        ).apply(instance, (block, useSourceBlock, shape, count, radius, limit, delayTicks, chance, offset) ->
                new PlaceBlockEffect(block.orElse(null), useSourceBlock, shape, count, radius,
                        limit.orElse(null), delayTicks, chance, offset)));
    }

    /** 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。；显式实现执行状态入口：放置需要按预算分批推进，lambda 只能表达一次同步执行。 */
    public static EffectType<PlaceBlockEffect> effectType() {
        EffectExecutor<PlaceBlockEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(PlaceBlockEffect effect, EffectContext context) {
                return PlaceBlockExecutor.execute(effect, context);
            }

            @Override
            public EffectPreparation prepare(PlaceBlockEffect effect, ServerLevel level, Vec3 position,
                                             ItemEntity sourceEntity, ItemStack sourceStack,
                                             boolean fillOrigin) {
                // 目标方块在成功转化当刻按组固定：tag 成员与源物品映射不随执行时机变化，候选偏移走跨调用缓存
                return PlaceBlockExecutor.plan(effect, level, sourceStack, fillOrigin);
            }

            @Override
            public EffectExecution start(PlaceBlockEffect effect, EffectContext context) {
                // 无固定输入入口：目标方块只在成功转化当刻按组固定，不重新解析
                return PlaceBlockExecutor.start(effect, context);
            }

            @Override
            public EffectExecution start(PlaceBlockEffect effect, EffectContext context,
                                         @Nullable EffectPreparation preparation) {
                return PlaceBlockExecutor.start(effect, context, preparation);
            }
        };
        return new SimpleEffectType<>(ID, codec(), PlaceBlockEffect::validateParams,
                PlaceBlockEffect::validateRuntimeLimits, executor, ProductKind.BLOCK, PARAMS);
    }

    /** 参数语义校验；问题写入 issues，不抛异常 */
    public static boolean validateParams(PlaceBlockEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = true;
        if (params.block() == null && !params.useSourceBlock()) {
            issues.error("block 与 use_source_block 至少需要其一，否则没有可放置的方块", null,
                    ParamChecks.child(fieldPath, BLOCK_FIELD));
            valid = false;
        }
        valid &= RefChecks.check(params.block(), BuiltInRegistries.BLOCK, BLOCK_FIELD, issues,
                ParamChecks.child(fieldPath, BLOCK_FIELD));

        return valid;
    }

    /**
     * 运行期上限校验：radius 不得超过服务端配置的单次放置半径上限。
     *
     * <p>放置开销随半径立方增长，超出上限的规则在加载时即被拒载，避免一条规则拖慢服务端。</p>
     *
     * @param params 已解码的放置参数
     * @param limits 当前服务端生效的运行期上限
     * @param issues 问题收集器
     * @param fieldPath 参数在规则中的字段路径
     * @return 是否在上限内
     */
    public static boolean validateRuntimeLimits(PlaceBlockEffect params, RuleRuntimeLimits limits,
                                                IssueCollector issues, String fieldPath) {
        int maxRadius = limits.maxPlaceRadius();
        if (params.radius() <= maxRadius) {
            return true;
        }
        issues.error("radius 超过服务端允许的单次放置半径上限 " + maxRadius
                        + "（配置项 max_place_block_radius）", null, ParamChecks.child(fieldPath, RADIUS_FIELD));
        return false;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

}
