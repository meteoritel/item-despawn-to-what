package com.meteorite.itemdespawntowhat.core.registry;

import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistrar;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 单线程类型装配 builder：注册时保序，冻结后发布独立的只读视图。
 * <p>内置登记与 SPI 只持有 {@link TypeRegistrar}；装配所有者调用 {@link #freeze()}，
 * 消费方仅持有返回的 {@link TypeRegistry}。冻结后注册抛出 {@link RegistryFrozenException}，
 * 重复 id 抛出 {@link DuplicateTypeException}，两种失败均不改变已发布内容。</p>
 * <p>注册与冻结由同一装配线程执行；冻结视图以 volatile 安全发布，可跨线程查询。</p>
 *
 * @param <T> 注册的类型定义
 */
public final class SimpleTypeRegistry<T extends TypeDefinition<?>> implements TypeRegistrar<T> {

    // 注册期唯一可变结构：键为类型 id，LinkedHashMap 保留注册顺序
    private final Map<ResourceLocation, T> registering = new LinkedHashMap<>();

    // 冻结后的不可变快照；为 null 表示仍在注册期（volatile 负责快照的安全发布）
    private volatile FrozenView<T> frozen;

    // 注册一个类型定义；重复 id 抛 DuplicateTypeException，注册表已冻结时抛 RegistryFrozenException
    @Override
    public void register(T definition) {
        Objects.requireNonNull(definition, "definition 不能为 null");
        ResourceLocation id = Objects.requireNonNull(
                definition.id(),
                "类型定义 " + definition.getClass().getName() + " 的 id() 不能返回 null");
        if (frozen != null) {
            throw new RegistryFrozenException(id, definition);
        }
        // putIfAbsent 保证冲突时不写入，注册表保持原状
        T existing = registering.putIfAbsent(id, definition);
        if (existing != null) {
            throw new DuplicateTypeException(id, existing, definition);
        }
    }

    // 查询类型定义；id 为 null 或未注册时返回空 Optional
    @Override
    public Optional<T> find(ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(view().get(id));
    }

    // 查询类型定义；不存在时抛出带 id 与已注册清单的 IllegalStateException
    @Override
    public T require(ResourceLocation id) {
        T definition = id == null ? null : view().get(id);
        if (definition == null) {
            throw new IllegalStateException("未注册的类型 id: " + id + "，已注册的类型: " + ids());
        }
        return definition;
    }

    // 全部已注册类型；返回不可变集合，按注册顺序排列
    @Override
    public Collection<T> all() {
        FrozenView<T> snapshot = frozen;
        if (snapshot != null) {
            return snapshot.ordered();
        }
        // 注册期也返回快照：调用方拿到的集合不会随之后的注册而变化
        return List.copyOf(registering.values());
    }

    // 全部已注册 id；返回不可变集合，按注册顺序排列
    @Override
    public Set<ResourceLocation> ids() {
        FrozenView<T> snapshot = frozen;
        if (snapshot != null) {
            return snapshot.ids();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(registering.keySet()));
    }

    /**
     * 关闭注册窗口并返回保序、不可变的查询视图；重复调用返回同一视图。
     * 必须由执行注册的装配线程调用。
     *
     * @return 仅暴露查询能力的冻结视图
     */
    public synchronized TypeRegistry<T> freeze() {
        if (frozen == null) {
            Map<ResourceLocation, T> byId = Collections.unmodifiableMap(new LinkedHashMap<>(registering));
            Set<ResourceLocation> ids = Collections.unmodifiableSet(new LinkedHashSet<>(byId.keySet()));
            frozen = new FrozenView<>(byId, List.copyOf(byId.values()), ids);
            registering.clear();
        }
        return frozen;
    }

    // 当前可读映射：冻结后为不可变快照，注册期为单线程可变映射
    private Map<ResourceLocation, T> view() {
        FrozenView<T> snapshot = frozen;
        return snapshot != null ? snapshot.byId() : registering;
    }

    // 保留查询映射与定义顺序，不暴露任何注册能力。
    private record FrozenView<T extends TypeDefinition<?>>(
            Map<ResourceLocation, T> byId, List<T> ordered, Set<ResourceLocation> ids) implements TypeRegistry<T> {
        @Override
        public Optional<T> find(ResourceLocation id) {
            return Optional.ofNullable(id == null ? null : byId.get(id));
        }

        @Override
        public T require(ResourceLocation id) {
            return find(id).orElseThrow(() -> new IllegalStateException(
                    "未注册的类型 id: " + id + "，已注册的类型: " + ids));
        }

        @Override
        public Collection<T> all() {
            return ordered;
        }
    }
}
