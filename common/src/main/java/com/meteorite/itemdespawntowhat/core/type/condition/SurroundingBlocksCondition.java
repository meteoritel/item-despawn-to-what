package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.Evaluability;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
import com.meteorite.itemdespawntowhat.core.type.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.SurroundingBlocksEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 条件类型 surrounding_blocks：按掉落物上/下/北/南/东/西六个相邻方块的引用匹配。
 * 每个方向可空（null = 该方向不限制），引用支持方块 id 或 #tag，允许同一方向多方向叠加；
 * 六个方向全空视为无意义的条件，直接报错。
 * JSON 示例：{ "type": "itemdespawntowhat:surrounding_blocks", "down": "#minecraft:logs" }
 */
public record SurroundingBlocksCondition(
        TaggedId up,
        TaggedId down,
        TaggedId north,
        TaggedId south,
        TaggedId east,
        TaggedId west
) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "surrounding_blocks");

    // 参数 JSON 字段名
    private static final String FIELD_UP = "up";
    private static final String FIELD_DOWN = "down";
    private static final String FIELD_NORTH = "north";
    private static final String FIELD_SOUTH = "south";
    private static final String FIELD_EAST = "east";
    private static final String FIELD_WEST = "west";

    // 参数编解码器：六个方向均可选，缺省为 null
    public static final MapCodec<SurroundingBlocksCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TaggedId.CODEC.optionalFieldOf(FIELD_UP).forGetter(value -> Optional.ofNullable(value.up())),
            TaggedId.CODEC.optionalFieldOf(FIELD_DOWN).forGetter(value -> Optional.ofNullable(value.down())),
            TaggedId.CODEC.optionalFieldOf(FIELD_NORTH).forGetter(value -> Optional.ofNullable(value.north())),
            TaggedId.CODEC.optionalFieldOf(FIELD_SOUTH).forGetter(value -> Optional.ofNullable(value.south())),
            TaggedId.CODEC.optionalFieldOf(FIELD_EAST).forGetter(value -> Optional.ofNullable(value.east())),
            TaggedId.CODEC.optionalFieldOf(FIELD_WEST).forGetter(value -> Optional.ofNullable(value.west()))
    ).apply(instance, (up, down, north, south, east, west) -> new SurroundingBlocksCondition(
            up.orElse(null), down.orElse(null), north.orElse(null),
            south.orElse(null), east.orElse(null), west.orElse(null))));

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    // 周围区块未全部加载时无法判定，通过可求值性门禁返回 UNAVAILABLE，而不是把「判不了」当成「不成立」
    public static ConditionType<SurroundingBlocksCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, SurroundingBlocksCondition::validateParams, SurroundingBlocksEvaluator::test,
                (params, context) -> LoadedChunks.containsArea(context.level(), context.pos(), 1)
                        ? Evaluability.AVAILABLE : Evaluability.UNAVAILABLE);
    }

    // 参数语义校验：六个方向不能全空；非空方向做注册表存在性校验
    public static boolean validateParams(SurroundingBlocksCondition params, IssueCollector issues, String fieldPath) {
        boolean anyPresent = params.up() != null
                || params.down() != null
                || params.north() != null
                || params.south() != null
                || params.east() != null
                || params.west() != null;
        if (!anyPresent) {
            issues.error("六个方向不能全部为空（否则该条件无意义）", null, fieldPath);
            return false;
        }
        boolean valid = checkDirection(params.up(), FIELD_UP, issues, fieldPath);
        valid &= checkDirection(params.down(), FIELD_DOWN, issues, fieldPath);
        valid &= checkDirection(params.north(), FIELD_NORTH, issues, fieldPath);
        valid &= checkDirection(params.south(), FIELD_SOUTH, issues, fieldPath);
        valid &= checkDirection(params.east(), FIELD_EAST, issues, fieldPath);
        valid &= checkDirection(params.west(), FIELD_WEST, issues, fieldPath);
        return valid;
    }

    // 单个方向引用校验：null 表示该方向不限制
    private static boolean checkDirection(@Nullable TaggedId reference, String field,
                                          IssueCollector issues, String fieldPath) {
        if (reference == null) {
            return true;
        }
        return RefChecks.check(reference, BuiltInRegistries.BLOCK, field, issues,
                ParamChecks.child(fieldPath, field));
    }
}
