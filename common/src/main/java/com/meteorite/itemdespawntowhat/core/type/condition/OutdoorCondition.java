package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.OutdoorEvaluator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件类型 outdoor：判断掉落物是否暴露在天空下（露天）。
 * 无类型专属参数，仅继承叶级取反字段 negated。
 * JSON 示例：{ "type": "itemdespawntowhat:outdoor", "negated": true }
 */
public record OutdoorCondition(boolean negated) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "outdoor");

    // 参数编解码器：只有取反字段
    public static final MapCodec<OutdoorCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CommonFields.negated(OutdoorCondition::negated)
    ).apply(instance, OutdoorCondition::new));

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<OutdoorCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, OutdoorCondition::validateParams, OutdoorEvaluator::test);
    }

    // 无类型专属参数，参数恒合法
    public static boolean validateParams(OutdoorCondition params, IssueCollector issues, String fieldPath) {
        return true;
    }
}
