package com.meteorite.itemdespawntowhat.core.network.protocol;

/**
 * 编辑协议版本与目标标识。
 * 客户端握手时携带本版本号，不匹配即拒绝；同一时刻全局只有一个编辑目标
 * （{@link com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits#TARGET_ID}）。
 */
public final class RuleEditProtocol {

    // 协议版本，客户端与服务端必须一致；快照条目瘦身并新增来源视图按需请求后递增
    public static final int VERSION = 6;

    private RuleEditProtocol() {
        throw new UnsupportedOperationException("Utility class");
    }
}
