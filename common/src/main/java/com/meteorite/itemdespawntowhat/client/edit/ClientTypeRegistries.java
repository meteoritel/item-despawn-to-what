package com.meteorite.itemdespawntowhat.client.edit;

import com.mojang.serialization.JsonOps;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端类型注册表持有者：内置类型与本地 RuleTypeProvider 在客户端惰性装配一次。
 * 注册顺序、冲突检查与冻结边界复用核心装配点，供参数投影、默认值与条件树编解码共用。
 * 类型定义来自客户端已安装模组，与服务端数据包无关。
 */
public final class ClientTypeRegistries {

    private static volatile BuiltinTypeRegistries registries;

    // 工具类
    private ClientTypeRegistries() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 返回包含本地 SPI 扩展的已冻结条件类型表。 */
    public static TypeRegistry<ConditionType<?>> conditions() {
        return registries().conditionTypes();
    }

    /** 返回包含本地 SPI 扩展的已冻结效果类型表。 */
    public static TypeRegistry<EffectType<?>> effects() {
        return registries().effectTypes();
    }

    private static BuiltinTypeRegistries registries() {
        var local = registries;
        if (local == null) {
            synchronized (ClientTypeRegistries.class) {
                local = registries;
                if (local == null) {
                    local = BuiltinTypeRegistries.create();
                    registries = local;
                }
            }
        }
        return local;
    }

    /** 按本地类型声明的新建初值创建条件叶；必填初值缺失或解码失败时返回 null。 */
    public static @Nullable ConditionNode.Leaf leafNode(ResourceLocation type) {
        var body = BuiltinEditorDefaults.conditionLeafJson(type);
        if (body == null) {
            return null;
        }
        ConditionExpression parsed = registries().conditionExpressionCodec()
                .parse(JsonOps.INSTANCE, body)
                .result()
                .orElse(null);
        if (parsed != null && parsed.root() instanceof ConditionNode.Leaf leaf) {
            return leaf;
        }
        return null;
    }
}
