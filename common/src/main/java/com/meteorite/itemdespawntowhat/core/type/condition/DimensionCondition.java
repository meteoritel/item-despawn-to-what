package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceCheck;
import com.meteorite.itemdespawntowhat.core.extension.DynamicReferenceContext;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.DimensionEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 条件类型 dimension：按掉落物所在维度匹配。
 * 参数 dimensions 为维度 id 列表，至少一项；命中任意一项即成立。
 * 维度属于动态注册表（不在 BuiltInRegistries 中），本层只做语法与数量校验，
 * 存在性复核留给的运行时/命令层（那里能拿到 RegistryAccess）。
 * JSON 示例：{ "type": "idtw:dimension", "dimensions": ["minecraft:overworld"] }
 */
public record DimensionCondition(List<ResourceLocation> dimensions) implements Condition, DynamicReferenceCheck {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.ID_NAMESPACE, "dimension");

    // 参数 JSON 字段名
    private static final String FIELD_DIMENSIONS = "dimensions";

    // 参数编解码器：叶级取反不受支持（改用条件树的 inverted 节点）；列表缺省为空，缺失由 validateParams 给出可读错误
    public static final MapCodec<DimensionCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf(FIELD_DIMENSIONS, List.of())
                    .forGetter(DimensionCondition::dimensions)
    ).apply(instance, DimensionCondition::new));

    // 参数列表不可变
    public DimensionCondition {
        dimensions = List.copyOf(dimensions);
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<DimensionCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, DimensionCondition::validateParams, DimensionEvaluator::test);
    }

    // 参数语义校验：维度列表必填
    public static boolean validateParams(DimensionCondition params, IssueCollector issues, String fieldPath) {
        return ParamChecks.notEmpty(params.dimensions(), FIELD_DIMENSIONS, issues,
                ParamChecks.child(fieldPath, FIELD_DIMENSIONS));
    }

    /**
     * 动态引用校验：每一项维度 id 都必须存在于当前服务端的维度键集合中。
     *
     * @param context 当前服务端、统一议题收集通道与当前条件叶的字段路径
     */
    @Override
    public void checkDynamicReferences(DynamicReferenceContext context) {
        for (ResourceLocation id : dimensions) {
            if (!context.server().levelKeys().contains(ResourceKey.create(Registries.DIMENSION, id))) {
                context.issues().error("未知维度: " + id, null,
                        ParamChecks.child(context.fieldPath(), FIELD_DIMENSIONS));
            }
        }
    }
}
