package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.type.BuiltinConditionTypes;
import com.meteorite.itemdespawntowhat.core.type.BuiltinEffectTypes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/***
 * 客户端类型注册表持有者：内置条件/效果类型在客户端只建一次，供热加载与条件树控件共用。
 * 本类不引入任何服务端数据，注册表内容与数据包无关（内置类型是编译期固定的）。
 */
public final class ClientTypeRegistries {

    // 内置条件类型注册表（惰性）
    private static volatile com.meteorite.itemdespawntowhat.core.api.TypeRegistry<ConditionType<?>> conditions;
    // 内置效果类型注册表（惰性，依赖条件表达式编解码器）
    private static volatile com.meteorite.itemdespawntowhat.core.api.TypeRegistry<EffectType<?>> effects;

    // 工具类
    private ClientTypeRegistries() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 内置条件类型注册表
    public static com.meteorite.itemdespawntowhat.core.api.TypeRegistry<ConditionType<?>> conditions() {
        var local = conditions;
        if (local == null) {
            synchronized (ClientTypeRegistries.class) {
                local = conditions;
                if (local == null) {
                    local = BuiltinConditionTypes.create();
                    conditions = local;
                }
            }
        }
        return local;
    }

    // 内置效果类型注册表
    public static com.meteorite.itemdespawntowhat.core.api.TypeRegistry<EffectType<?>> effects() {
        var local = effects;
        if (local == null) {
            synchronized (ClientTypeRegistries.class) {
                local = effects;
                if (local == null) {
                    local = BuiltinEffectTypes.create(RuleCodecs.conditionExpressionCodec(conditions()));
                    effects = local;
                }
            }
        }
        return local;
    }

    // 按类型 id 生成一个可用的条件叶节点（默认参数取自 BuiltinEditorDefaults）
    public static @Nullable ConditionNode.Leaf leafNode(ResourceLocation type) {
        var body = BuiltinEditorDefaults.conditionLeafJson(type);
        if (body == null) {
            return null;
        }
        ConditionExpression parsed = RuleCodecs.conditionExpressionCodec(conditions())
                .parse(com.mojang.serialization.JsonOps.INSTANCE, body)
                .result()
                .orElse(null);
        if (parsed != null && parsed.root() instanceof ConditionNode.Leaf leaf) {
            return leaf;
        }
        return null;
    }
}
