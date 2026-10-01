package com.meteorite.itemdespawntowhat.core.type.condition;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.SimpleConditionType;
import com.meteorite.itemdespawntowhat.core.type.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.condition.eval.CatalystPresentEvaluator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 条件类型 catalyst_present：判断掉落物附近是否存在指定的催化剂物品。
 * 参数 items 为候选物品引用（item id 或 #tag，至少一项），count 为需要的最少数量（1..64，默认 1）。
 * 附近扫描范围见 CatalystPresentEvaluator：物品所在方块格的 1×1×1 范围，排除源物品自身与已死亡实体。
 * JSON 示例：{ "type": "itemdespawntowhat:catalyst_present", "items": ["minecraft:blaze_powder"], "count": 3 }
 */
public record CatalystPresentCondition(boolean negated, List<TaggedId> items, int count) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "catalyst_present");

    // 参数 JSON 字段名
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_COUNT = "count";

    // 数量取值域与默认值
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 64;
    private static final int DEFAULT_COUNT = 1;

    // 参数编解码器：items 缺省为空、count 缺省为 1，缺失与越界由 validateParams 给出可读错误
    public static final MapCodec<CatalystPresentCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CommonFields.negated(CatalystPresentCondition::negated),
            TaggedId.CODEC.listOf().optionalFieldOf(FIELD_ITEMS, List.of()).forGetter(CatalystPresentCondition::items),
            Codec.INT.optionalFieldOf(FIELD_COUNT, DEFAULT_COUNT).forGetter(CatalystPresentCondition::count)
    ).apply(instance, CatalystPresentCondition::new));

    // 参数列表不可变
    public CatalystPresentCondition {
        items = List.copyOf(items);
    }

    // 条件类型 id
    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()（与 Condition#type 实例方法签名冲突）
    public static ConditionType<CatalystPresentCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, CatalystPresentCondition::validateParams, CatalystPresentEvaluator::test);
    }

    // 参数语义校验：items 必填且逐项做注册表存在性校验，count 需落在 [1,64]
    public static boolean validateParams(CatalystPresentCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.notEmpty(params.items(), FIELD_ITEMS, issues,
                ParamChecks.child(fieldPath, FIELD_ITEMS));
        valid &= ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, FIELD_COUNT, issues,
                ParamChecks.child(fieldPath, FIELD_COUNT));
        valid &= RefChecks.checkAll(params.items(), BuiltInRegistries.ITEM, FIELD_ITEMS, issues,
                ParamChecks.child(fieldPath, FIELD_ITEMS));
        return valid;
    }
}
