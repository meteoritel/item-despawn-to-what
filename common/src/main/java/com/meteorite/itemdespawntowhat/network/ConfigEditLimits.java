package com.meteorite.itemdespawntowhat.network;

import java.nio.charset.StandardCharsets;

/**
 * 配置编辑网络传输的统一资源上限。
 */
public final class ConfigEditLimits {

    public static final int MAX_DIRECT_PACKET_BYTES = 32_766;
    public static final int MAX_CHUNK_BYTES = 30_000;
    public static final int MAX_CONFIG_BYTES = 4 * 1024 * 1024;
    public static final int MAX_CHUNK_COUNT =
            (MAX_CONFIG_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    public static final int MAX_TRANSFER_ID_LENGTH = 64;
    public static final int MAX_SESSIONS_PER_PLAYER = 2;
    public static final long SESSION_TIMEOUT_MS = 60_000L;

    private ConfigEditLimits() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 网络限制按 UTF-8 字节数计算，避免多字节字符绕过长度检查
    public static int encodedLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    public static boolean isConfigSizeValid(String value) {
        return value != null && encodedLength(value) <= MAX_CONFIG_BYTES;
    }
}
