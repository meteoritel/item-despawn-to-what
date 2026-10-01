package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * 类型注册表契约：效果类型与条件类型共用同一套注册与查询语义。
 * 实现必须保证注册完成后对外呈现为不可变视图（见 core/registry 的实现）。
 */
public interface TypeRegistry<T extends TypeDefinition<?>> {

    // 注册一个类型定义；重复 id 必须抛出异常而不是静默覆盖
    void register(T definition);

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
