package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * 类型注册表的查询契约：装配器向消费者发布冻结后的不可变视图。
 * 注册窗口的查询能力由 TypeRegistrar 扩展，运行时消费者不持有注册或冻结能力。
 */
public interface TypeRegistry<T extends TypeDefinition<?>> {

    // 查询类型定义，不存在时返回空
    Optional<T> find(ResourceLocation id);

    // 查询类型定义，不存在时立即抛出异常（仅供内部已确认存在的路径使用）
    T require(ResourceLocation id);

    // 已注册的全部类型
    Collection<T> all();

    // 已注册的全部类型 id
    Set<ResourceLocation> ids();

    // 便捷判空查询，允许传入 null
    default @Nullable T getOrNull(@Nullable ResourceLocation id) {
        return id == null ? null : find(id).orElse(null);
    }
}
