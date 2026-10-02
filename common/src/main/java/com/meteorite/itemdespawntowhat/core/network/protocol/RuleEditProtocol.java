package com.meteorite.itemdespawntowhat.core.network.protocol;

/***
 * 编辑协议版本与目标标识。
 * 客户端握手时携带本版本号，不匹配即拒绝；同一时刻全局只有一个编辑目标（TARGET_ID）。
 */
public final class RuleEditProtocol {

    // 契约 §3.2：协议版本，客户端与服务端必须一致
    public static final int VERSION = 2;

    private RuleEditProtocol() {
        throw new UnsupportedOperationException("Utility class");
    }
}
