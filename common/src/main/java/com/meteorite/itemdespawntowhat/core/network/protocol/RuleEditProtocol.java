package com.meteorite.itemdespawntowhat.core.network.protocol;

/**
 * 编辑协议版本与目标标识。
 * 客户端握手时携带本版本号，不匹配即拒绝；同一时刻全局只有一个编辑目标
 * （{@link com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits#TARGET_ID}）。
 */
public final class RuleEditProtocol {

    // 协议版本，客户端与服务端必须一致
    public static final int VERSION = 4;

    private RuleEditProtocol() {
        throw new UnsupportedOperationException("Utility class");
    }
}
