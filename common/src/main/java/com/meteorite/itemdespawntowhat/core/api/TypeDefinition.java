package com.meteorite.itemdespawntowhat.core.api;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 可注册类型定义的公共契约（效果类型与条件类型共用）。
 * 参数对象 P 是该类型专属的不可变记录，其编解码由本定义自行提供。
 */
public interface TypeDefinition<P> {

    // 该类型的稳定标识，同时是 JSON 中 type 字段的取值
    ResourceLocation id();

    // 该类型专属参数的编解码器（扁平字段，与规则/效果通用字段处在同一对象中）
    MapCodec<P> codec();

    // 校验类型专属参数的语义合法性；默认通过，具体类型按需重写
    default boolean validateParams(P params, IssueCollector issues, String fieldPath) {
        return true;
    }

    /**
     * 校验运行期上限（由服务端配置决定的参数边界）；默认通过，具体类型按需重写。
     *
     * <p>与 {@link #validateParams} 分开：参数自身的合法区间写在规则契约里、与配置无关，
     * 运行期上限随服务端配置变化，只决定这一条规则能否被加载或保存。</p>
     *
     * @param params 该类型已解码的参数对象
     * @param limits 当前服务端生效的运行期上限
     * @param issues 问题收集器
     * @param fieldPath 参数在规则中的字段路径
     * @return 是否在上限内
     */
    default boolean validateRuntimeLimits(P params, RuleRuntimeLimits limits, IssueCollector issues, String fieldPath) {
        return true;
    }
}
