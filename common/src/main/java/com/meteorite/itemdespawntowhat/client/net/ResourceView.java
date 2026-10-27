package com.meteorite.itemdespawntowhat.client.net;

import org.jetbrains.annotations.Nullable;

/** 资源加载的只读状态；目录在等待或失败时仍可保留已接收的部分数据。 */
public record ResourceView<T>(@Nullable T value, boolean loading, boolean failed) {
    static <T> ResourceView<T> empty() { return new ResourceView<>(null, false, false); }
}
