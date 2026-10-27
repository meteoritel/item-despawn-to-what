package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 文本正文载荷的共享视图：直发整包与分片携带的都是同一段 JSON 文本。
 * 只用于按 UTF-8 计量收发字节，不进任何线上签名，也不改变各载荷自身的字段。
 */
public interface RuleTextPayload extends CustomPacketPayload {

    /**
     * 载荷携带的文本正文（JSON）
     */
    String text();
}