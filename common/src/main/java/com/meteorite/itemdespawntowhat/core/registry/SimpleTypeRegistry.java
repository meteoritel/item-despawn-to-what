package com.meteorite.itemdespawntowhat.core.registry;

import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
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
 * {@link TypeRegistry} 的默认实现：注册期可变，{@link #freeze()} 之后不可变。
 *
 * <p><b>生命周期与并发约定</b>
 * <ul>
 *   <li><b>注册期</b>：只允许<b>单个线程</b>调用 {@link #register}，此阶段不保证并发读安全；</li>
 *   <li><b>冻结</b>：由执行注册的同一线程调用 {@link #freeze()}，内部结构被替换为不可变快照；</li>
 *   <li><b>冻结后</b>：{@link #find}、{@link #require}、{@link #all}、{@link #ids} 可被任意线程并发调用，
 *       注册表内容不再变化（快照通过 volatile 字段安全发布）。</li>
 * </ul>
 *
 * <p><b>语义约束</b>
 * <ul>
 *   <li>重复 id 立即抛 {@link DuplicateTypeException}，绝不静默覆盖；</li>
 *   <li>冻结后再注册抛 {@link RegistryFrozenException}；</li>
 *   <li>注册顺序被保留，{@link #all} 与 {@link #ids} 按注册先后返回
 *       （供"定义序"作为规则优先级的兜底排序依据）。</li>
 * </ul>
 *
 * <p><b>与接口的关系</b>：{@link TypeRegistry} 接口层面<b>不承诺只读</b>（{@link #register} 始终在契约内），
 * 冻结语义完全由本实现保证——冻结后再注册会在运行期抛 {@link RegistryFrozenException}。
 *
 * <p>本类属于与 core/api 同层的通用基础设施，不感知任何具体类型；内置类型注册表见阶段②。
 *
 * @param <T> 注册的类型定义（效果类型或条件类型的公共形态）
 */
public final class SimpleTypeRegistry<T extends TypeDefinition<?>> implements TypeRegistry<T> {

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

    // 冻结注册表：生成不可变快照并发布；重复调用无副作用
    // 必须由执行注册的同一线程调用；调用之后 register 抛 RegistryFrozenException
    public synchronized void freeze() {
        if (frozen != null) {
            return;
        }
        Map<ResourceLocation, T> byId = Collections.unmodifiableMap(new LinkedHashMap<>(registering));
        Set<ResourceLocation> ids = Collections.unmodifiableSet(new LinkedHashSet<>(byId.keySet()));
        frozen = new FrozenView<>(byId, List.copyOf(byId.values()), ids);
    }

    // 注册表是否已冻结
    public boolean isFrozen() {
        return frozen != null;
    }

    // 已注册的类型数量（注册期与冻结后均可用）
    public int size() {
        FrozenView<T> snapshot = frozen;
        return snapshot != null ? snapshot.byId().size() : registering.size();
    }

    // 当前可读映射：冻结后为不可变快照，注册期为单线程可变映射
    private Map<ResourceLocation, T> view() {
        FrozenView<T> snapshot = frozen;
        return snapshot != null ? snapshot.byId() : registering;
    }

    // 冻结快照：查询用哈希表 + 按注册顺序的只读视图，一旦构造不再变化
    private static final class FrozenView<T extends TypeDefinition<?>> {

        // 按 id 查询用
        private final Map<ResourceLocation, T> byId;

        // 按注册顺序的类型列表
        private final List<T> ordered;

        // 按注册顺序的 id 集合
        private final Set<ResourceLocation> ids;

        // 直接持有已构建好的三类只读视图，不做任何拷贝或校验
        private FrozenView(Map<ResourceLocation, T> byId, List<T> ordered, Set<ResourceLocation> ids) {
            this.byId = byId;
            this.ordered = ordered;
            this.ids = ids;
        }

        // 按 id 查询用映射
        private Map<ResourceLocation, T> byId() {
            return byId;
        }

        // 按注册顺序的类型列表
        private List<T> ordered() {
            return ordered;
        }

        // 按注册顺序的 id 集合
        private Set<ResourceLocation> ids() {
            return ids;
        }
    }
}
