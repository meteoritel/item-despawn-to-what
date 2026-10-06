package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/*** 客户端展示名称：直接读取已加载的注册表与语言，不触发目录请求。 */
public final class RuleDisplayLabels {
    private RuleDisplayLabels() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 名称查询均为注册表索引查找；未知资源交给调用方保留原始 ID。
    public static @Nullable Component label(ResourceLocation id) {
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.get(id).getDescription();
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).getName();
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        }
        TypeEditorDescriptor descriptor = EffectEditorRegistry.find(id);
        if (descriptor == null) {
            descriptor = ConditionEditorRegistry.find(id);
        }
        return descriptor == null ? null : descriptor.label();
    }
}
