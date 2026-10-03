package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;

/**
 * 效果类型定义：一种已注册的效果类别。
 * 参数对象 P 同时实现 {@link Effect}，因此分发解码后可直接作为效果使用；
 * 执行器由类型自带（Q25 决定），运行时按 type 字段一次查表即可拿到编解码器与执行器。
 */
public interface EffectType<P extends Effect> extends TypeDefinition<P> {

    // 该效果类型的服务端执行器
    EffectExecutor<P> executor();

    // 是否属于「一次性效果」（天气 / 闪电 / 爆炸等）：不参与数量上限，仅含一次性效果的候选每源最多一组
    default boolean oneShot() {
        return false;
    }
}
