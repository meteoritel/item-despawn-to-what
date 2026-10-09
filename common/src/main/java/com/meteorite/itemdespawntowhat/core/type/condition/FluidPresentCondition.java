package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.FluidPresentEvaluator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.List;

/**
 * 条件类型 fluid_present：判断掉落物所在位置的流体类型。
 * fluids 为可选引用列表，命中其中任一种即可；列表为空时采用 fluid，二者均空表示任意流体。
 * 参数 require_source 默认 true，表示只匹配流体源方块；判定见 FluidPresentEvaluator（非源模式下额外接受同一流体族的流动变体）。
 * JSON 示例：{ "type": "itemdespawntowhat:fluid_present", "fluids": ["minecraft:water", "minecraft:lava"] }
 */
public record FluidPresentCondition(TaggedId fluid, boolean requireSource, List<TaggedId> fluids) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "fluid_present");

    // 参数 JSON 字段名
    private static final String FIELD_FLUID = "fluid";
    private static final String FIELD_REQUIRE_SOURCE = "require_source";

    // require_source 默认值：只看源方块，避免流动流体随手匹配
    private static final boolean DEFAULT_REQUIRE_SOURCE = true;

    // minecraft:empty 在流体注册表中存在，但永远不可能匹配到任何实际流体
    private static final ResourceLocation EMPTY_FLUID = ResourceLocation.withDefaultNamespace("empty");

    // 参数编解码器：fluid 缺省为 null（任意流体），require_source 缺省为 true
    public static final MapCodec<FluidPresentCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TaggedId.CODEC.optionalFieldOf(FIELD_FLUID).forGetter(value -> Optional.ofNullable(value.fluid())),
            Codec.BOOL.optionalFieldOf(FIELD_REQUIRE_SOURCE, DEFAULT_REQUIRE_SOURCE)
                    .forGetter(FluidPresentCondition::requireSource),
            TaggedId.CODEC.listOf().optionalFieldOf(RuleFields.FLUIDS, List.of()).forGetter(FluidPresentCondition::fluids)
    ).apply(instance, (fluid, requireSource, fluids) -> new FluidPresentCondition(fluid.orElse(null), requireSource, fluids)));

    public FluidPresentCondition {
        fluids = List.copyOf(fluids);
    }

    public List<TaggedId> references() {
        return fluids.isEmpty() && fluid != null ? List.of(fluid) : fluids;
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<FluidPresentCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, FluidPresentCondition::validateParams, FluidPresentEvaluator::test);
    }

    // 参数语义校验：fluid 为空表示任意流体（合法）；非空时做注册表存在性校验并提示 empty 的无效用法
    public static boolean validateParams(FluidPresentCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = true;
        for (TaggedId fluid : params.references()) {
            String path = ParamChecks.child(fieldPath, RuleFields.FLUIDS);
            valid &= RefChecks.check(fluid, BuiltInRegistries.FLUID, FIELD_FLUID, issues, path);
            if (!fluid.tag() && EMPTY_FLUID.equals(fluid.id())) {
                issues.warn("空流体不会匹配实际流体；留空列表表示任意流体", null, path);
            }
        }
        return valid;
    }
}
