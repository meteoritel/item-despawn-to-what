package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;

/**
 * 效果类型定义：一种已注册的效果类别。
 * 参数对象 P 同时实现 {@link Effect}，因此分发解码后可直接作为效果使用。
 */
public interface EffectType<P extends Effect> extends TypeDefinition<P> {
}
