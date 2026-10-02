package com.meteorite.itemdespawntowhat.client.edit;

import java.util.Collection;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 条件类型编辑器注册表：条件编辑器与效果编辑器共用同一条件树控件，
 * 但类型列表各自独立，因此这里单独持有一份注册表。
 * <p>类型标签统一走本地化 key，未注册类型返回只读回退描述。
 */
public final class ConditionEditorRegistry {

    // 未注册条件类型的回退标签 key
    private static final TypeEditorRegistry REGISTRY =
            new TypeEditorRegistry("gui.itemdespawntowhat.edit.unknown_condition");

    private ConditionEditorRegistry() {
    }

    // 注册条件编辑器描述
    public static void register(TypeEditorDescriptor descriptor) {
        REGISTRY.register(descriptor);
    }

    // 是否已注册
    public static boolean isRegistered(ResourceLocation id) {
        return REGISTRY.isRegistered(id);
    }

    // 查找已注册描述，未注册返回 null
    public static @Nullable TypeEditorDescriptor find(ResourceLocation id) {
        return REGISTRY.find(id);
    }

    // 取描述：未注册时返回只读回退描述
    public static TypeEditorDescriptor descriptorFor(ResourceLocation id) {
        return REGISTRY.descriptorFor(id);
    }

    // 全部已注册描述
    public static Collection<TypeEditorDescriptor> all() {
        return REGISTRY.all();
    }

    // 全部已注册 id
    public static Set<ResourceLocation> ids() {
        return REGISTRY.ids();
    }
}
