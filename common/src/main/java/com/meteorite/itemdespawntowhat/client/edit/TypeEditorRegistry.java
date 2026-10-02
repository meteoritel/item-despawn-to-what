package com.meteorite.itemdespawntowhat.client.edit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 类型编辑器注册表：把类型 id 映射到编辑器描述。
 * <p>未注册的类型不会报错，而是返回一份只读回退描述
 * （展示原始 JSON 摘要，原样保留未识别字段），
 * 这样第三方扩展类型也能在界面里被看到而不会被破坏。
 * <p>回退标签的本地化 key 由构造参数给出，条件与效果各用一个。
 */
public final class TypeEditorRegistry {

    // 回退标签的本地化 key
    private final String fallbackLabelKey;
    // 已注册描述，保持注册顺序
    private final Map<ResourceLocation, TypeEditorDescriptor> descriptors = new LinkedHashMap<>();

    // 建立注册表
    public TypeEditorRegistry(String fallbackLabelKey) {
        this.fallbackLabelKey = Objects.requireNonNull(fallbackLabelKey, "fallbackLabelKey");
    }

    // 注册描述；重复注册同一 id 抛异常，避免静默覆盖
    public void register(TypeEditorDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        ResourceLocation id = descriptor.id();
        if (descriptors.containsKey(id)) {
            throw new IllegalStateException("类型编辑器重复注册：" + id);
        }
        descriptors.put(id, descriptor);
    }

    // 是否已注册
    public boolean isRegistered(ResourceLocation id) {
        return descriptors.containsKey(id);
    }

    // 查找已注册描述，未注册返回 null
    public @Nullable TypeEditorDescriptor find(ResourceLocation id) {
        return descriptors.get(id);
    }

    // 取描述：未注册时返回只读回退描述
    public TypeEditorDescriptor descriptorFor(ResourceLocation id) {
        TypeEditorDescriptor registered = descriptors.get(id);
        if (registered != null) {
            return registered;
        }
        return TypeEditorDescriptor.readOnly(id, Component.translatableWithFallback(fallbackLabelKey, id.toString()));
    }

    // 全部已注册描述
    public Collection<TypeEditorDescriptor> all() {
        return java.util.List.copyOf(descriptors.values());
    }

    // 全部已注册 id
    public Set<ResourceLocation> ids() {
        return java.util.Set.copyOf(descriptors.keySet());
    }

    // 已注册数量
    public int size() {
        return descriptors.size();
    }
}
