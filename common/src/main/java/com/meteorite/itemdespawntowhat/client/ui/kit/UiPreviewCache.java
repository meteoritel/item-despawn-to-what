package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端线程拥有的有界 LRU；资源世代、失败值与加载时机由宿主决定。
 * <p>失败应作为宿主 V 保存；
 * loader 必须返回非 null 值，不捕获加载异常。图标 wrapper 与模型资源可分别拥有缓存。</p>
 * <p>世代按 equals 比较，clear 释放值但保留世代；加载、查询和失效均由同一客户端线程调用。</p>
 * <pre>{@code
 * UiPreviewCache<String, Optional<UiIcon>> cache = new UiPreviewCache<>(256);
 * cache.invalidate(resourceEpoch);
 * Optional<UiIcon> icon = cache.getOrLoad(id, key -> Optional.ofNullable(loadIcon(key)));
 * // 宿主卸载时
 * cache.clear();
 * }</pre>
 */
public final class UiPreviewCache<K, V> {
    private final int capacity;
    private final Map<K, V> values = new LinkedHashMap<>(32, 0.75F, true);
    private @Nullable Object epoch;
    /** 容量必须大于 0，命中更新最近使用顺序。 */
    public UiPreviewCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Non-positive cache capacity");
        this.capacity = capacity;
    }
    /** 世代值变化时释放本缓存全部值；同一世代不做工作。 */
    public void invalidate(@Nullable Object epoch) {
        if (Objects.equals(this.epoch, epoch)) return;
        this.epoch = epoch;
        values.clear();
    }
    /** 查询已加载值并更新 LRU 顺序，不存在返回 null。 */
    public @Nullable V get(K key) { return values.get(key); }
    /** 缺失才加载并按容量淘汰；loader 返回非 null 的宿主结果，异常由宿主处理。 */
    public V getOrLoad(K key, Function<? super K, ? extends V> loader) {
        V result = values.computeIfAbsent(key, missing -> Objects.requireNonNull(loader.apply(missing), "Preview result"));
        while (values.size() > capacity) values.remove(values.keySet().iterator().next());
        return result;
    }
    /** 释放值及其资源引用。 */
    public void clear() { values.clear(); }
    /** 当前条目数，用于宿主诊断。 */
    public int size() { return values.size(); }
}
