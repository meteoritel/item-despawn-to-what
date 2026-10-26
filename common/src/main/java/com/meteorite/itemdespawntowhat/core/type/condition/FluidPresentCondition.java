package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.RefListSpec;
import com.meteorite.itemdespawntowhat.core.api.param.RefSpec;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.FluidPresentEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.List;

/**
 * 条件类型 fluid_present：判断掉落物所在位置的流体类型。
 * fluids 为可选引用列表，命中其中任一种即可；列表为空时采用 fluid，二者均空表示任意流体。
 * 源流体与流动流体均按同一流体族匹配。
 * JSON 示例：{ "type": "idtw:fluid_present", "fluids": ["minecraft:water", "minecraft:lava"] }
 */
public record FluidPresentCondition(TaggedId fluid, List<TaggedId> fluids) implements Condition {

    /** 类型 id，同时是 JSON 中 type 字段的取值 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "fluid_present");

    // 参数 JSON 字段名
    private static final String FIELD_FLUID = "fluid";
    // minecraft:empty 在流体注册表中存在，但永远不可能匹配到任何实际流体
    private static final ResourceLocation EMPTY_FLUID = ResourceLocation.withDefaultNamespace("empty");

    // 参数声明：fluid 为历史单值字段（可空），fluids 为优先使用的列表；两者共用同一组界面文案
    public static final RefSpec FLUID = RefSpec.nullable(FIELD_FLUID, "minecraft:fluid")
            .label("fluid_present.fluid")
            .hidden();
    public static final RefListSpec FLUIDS = RefListSpec
            .defaultedEmpty(RuleFields.FLUIDS, "minecraft:fluid")
            .label("fluid_present.fluid")
            .hint("fluid_present.fluid.hint");
    public static final ParamList PARAMS = ParamList.of(FLUID, FLUIDS);

    /** 参数编解码器：fluid 缺省为 null（任意流体）。 */
    public static MapCodec<FluidPresentCondition> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                FLUID.fieldForNullable(value -> Optional.ofNullable(value.fluid())),
                FLUIDS.fieldFor(FluidPresentCondition::fluids)
        ).apply(instance, (fluid, fluids) -> new FluidPresentCondition(fluid.orElse(null), fluids)));
    }

    public FluidPresentCondition {
        fluids = List.copyOf(fluids);
    }

    /** 实际参与匹配的引用：fluids 非空时优先，否则退回单个 fluid；两者皆空得到空列表（表示任意流体） */
    public List<TaggedId> references() {
        return fluids.isEmpty() && fluid != null ? List.of(fluid) : fluids;
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    /** 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突） */
    public static ConditionType<FluidPresentCondition> conditionType() {
        return new SimpleConditionType<>(ID, codec(), FluidPresentCondition::validateParams, FluidPresentEvaluator::test,
                PARAMS);
    }

    /** 参数语义校验：fluid 为空表示任意流体（合法）；非空时做注册表存在性校验并提示 empty 的无效用法 */
    public static boolean validateParams(FluidPresentCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = true;
        // 报错字段随引用来源：列表非空时问题出在 fluids，回退到单数 fluid 时出在 fluid
        String path = ParamChecks.child(fieldPath, params.fluids().isEmpty() ? FIELD_FLUID : RuleFields.FLUIDS);
        for (TaggedId fluid : params.references()) {
            valid &= RefChecks.check(fluid, BuiltInRegistries.FLUID, FIELD_FLUID, issues, path);
            if (!fluid.tag() && EMPTY_FLUID.equals(fluid.id())) {
                issues.warn("空流体不会匹配实际流体；留空列表表示任意流体", null, path);
            }
        }
        return valid;
    }
}
