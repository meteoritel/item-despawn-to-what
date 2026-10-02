package com.meteorite.itemdespawntowhat.client.edit;

import java.util.Collection;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 效果类型编辑器注册表：与条件注册表同构，回退标签 key 不同。
 * <p>效果参数里的条件字段仍然交给同一个条件树控件编辑。
 */
public final class EffectEditorRegistry {

    // 未注册效果类型的回退标签 key
    private static final TypeEditorRegistry REGISTRY =
            new TypeEditorRegistry("gui.itemdespawntowhat.edit.unknown_effect");

    private EffectEditorRegistry() {
    }

    // 注册效果编辑器描述
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
