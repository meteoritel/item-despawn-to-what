package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.PlaceBlockExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * place_block：在触发位置按指定形状放置方块。
 * block（TaggedId，可空）与 use_source_block 至少需要其一：前者放置指定方块，后者放置源物品对应的方块。
 * radius 是本次放置的最大向外扩散边界（放置候选为该范围内的单层位置）；limit（可空，省略表示不限）
 * 是以触发位置为中心、边长为 2 * radius + 1 的立体方框内同类方块的数量上限，只收窄实际放置量，不扩大边界。
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
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "place_block");

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
     * 半径对应方框内的位置总数，用作该半径下 limit 滑块的窗口上限。
     *
     * <p>方框内最多只能容纳这么多个方块，所以 limit 写超过它的值不会拒载，只是从此限不到数量、等价于不限；
     * 滑块窗口仍按这个容量收窄，窗口外的合法值可以精确输入并保存。radius 自身不设静态上界，
     * 由服务端配置 max_place_block_radius 在加载与保存校验时限定。</p>
     *
     * @param radius 放置半径
     * @return (2 * radius + 1)³，超出 int 表示范围时取 int 上界
     */
    public static int limitUpperBound(int radius) {
        long span = Math.max(radius, MIN_RADIUS) * 2L + 1L;
        long total = span * span * span;
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    // 放置形状
    public enum Shape {
        SQUARE,
        CIRCLE,
        CROSS;

        // JSON 取值：小写下划线，解析大小写不敏感
        public static final Codec<Shape> CODEC = EnumCodecs.lowerCase(Shape.class);
    }

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<PlaceBlockEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.optionalFieldOf(BLOCK_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.block())),
                Codec.BOOL.optionalFieldOf(USE_SOURCE_BLOCK_FIELD, DEFAULT_USE_SOURCE_BLOCK)
                        .forGetter(PlaceBlockEffect::useSourceBlock),
                Shape.CODEC.optionalFieldOf(SHAPE_FIELD, DEFAULT_SHAPE).forGetter(PlaceBlockEffect::shape),
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(PlaceBlockEffect::count),
                Codec.intRange(MIN_RADIUS, Integer.MAX_VALUE).optionalFieldOf(RADIUS_FIELD, DEFAULT_RADIUS)
                        .forGetter(PlaceBlockEffect::radius),
                Codec.intRange(MIN_LIMIT, Integer.MAX_VALUE).optionalFieldOf(LIMIT_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.limit())),
                CommonFields.delayTicks(PlaceBlockEffect::delayTicks),
                CommonFields.chance(PlaceBlockEffect::chance),
                CommonFields.optionalConditions(PlaceBlockEffect::conditions, expressionCodec)
        ).apply(instance, (block, useSourceBlock, shape, count, radius, limit, delayTicks, chance, conditions) ->
                new PlaceBlockEffect(block.orElse(null), useSourceBlock, shape, count, radius,
                        limit.orElse(null), delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器。
    // 显式实现执行状态入口：放置需要按预算分批推进，lambda 只能表达一次同步执行。
    public static EffectType<PlaceBlockEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        EffectExecutor<PlaceBlockEffect> executor = new EffectExecutor<>() {
            @Override
            public EffectResult execute(PlaceBlockEffect effect, EffectContext context) {
                return PlaceBlockExecutor.execute(effect, context);
            }

            @Override
            public @Nullable EffectPreparation prepare(PlaceBlockEffect effect, ServerLevel level, Vec3 position,
                                                       ItemEntity sourceEntity, ItemStack sourceStack,
                                                       boolean fillOrigin) {
                // 目标方块在此刻按组固定：tag 成员与源物品映射都不再随执行时机变化，候选偏移走跨调用缓存
                return PlaceBlockExecutor.plan(effect, level, sourceStack, fillOrigin);
            }

            @Override
            public EffectExecution start(PlaceBlockEffect effect, EffectContext context) {
                // 无固定输入入口同样不做到期重新解析：目标方块只在成功转化当刻按组固定
                return PlaceBlockExecutor.start(effect, context);
            }

            @Override
            public EffectExecution start(PlaceBlockEffect effect, EffectContext context,
                                         @Nullable EffectPreparation preparation) {
                return PlaceBlockExecutor.start(effect, context, preparation);
            }
        };
        return new SimpleEffectType<>(ID, codec(expressionCodec), PlaceBlockEffect::validateParams,
                PlaceBlockEffect::validateRuntimeLimits, executor, ProductKind.BLOCK);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(PlaceBlockEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.required(params.shape(), SHAPE_FIELD, issues,
                ParamChecks.child(fieldPath, SHAPE_FIELD));
        if (params.block() == null && !params.useSourceBlock()) {
            issues.error("block 与 use_source_block 至少需要其一，否则没有可放置的方块", null,
                    ParamChecks.child(fieldPath, BLOCK_FIELD));
            valid = false;
        }
        valid &= RefChecks.check(params.block(), BuiltInRegistries.BLOCK, BLOCK_FIELD, issues,
                ParamChecks.child(fieldPath, BLOCK_FIELD));
        valid &= ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
        valid &= ParamChecks.inRange(params.radius(), MIN_RADIUS, Integer.MAX_VALUE, RADIUS_FIELD, issues,
                ParamChecks.child(fieldPath, RADIUS_FIELD));
        if (params.limit() != null && params.limit() < MIN_LIMIT) {
            issues.error(LIMIT_FIELD + " 不能小于 " + MIN_LIMIT + "，当前为 " + params.limit(), null,
                    ParamChecks.child(fieldPath, LIMIT_FIELD));
            valid = false;
        }
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

    // 效果级条件替换：其余参数原样保留，供运行期门槛投影重建真实效果类型
    @Override
    public PlaceBlockEffect withConditions(@Nullable ConditionExpression conditions) {
        return new PlaceBlockEffect(block, useSourceBlock, shape, count, radius, limit, delayTicks, chance, conditions);
    }
}
