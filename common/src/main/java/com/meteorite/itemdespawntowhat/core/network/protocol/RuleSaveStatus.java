package com.meteorite.itemdespawntowhat.core.network.protocol;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/***
 * 保存/请求回执状态码。客户端按码分支，禁止解析文案。
 * 状态码的线上形态就是枚举名（String），客户端保持大小写敏感比较。
 */
public enum RuleSaveStatus {
    SUCCESS,
    NO_CHANGES,
    NO_PERMISSION,
    LOCK_NOT_OWNED,
    LOCK_BUSY,
    SESSION_EXPIRED,
    VERSION_CONFLICT,
    VALIDATION_FAILED,
    WRITE_FAILED,
    SAVED_NOT_RELOADED,
    INVALID_REQUEST,
    UNAVAILABLE;

    // 线上传输用的状态码文本（枚举名）
    public String id() {
        return name();
    }

    // 客户端按状态码取翻译 key：itemdespawntowhat.edit.status.<小写状态码>
    public String messageKey() {
        return "itemdespawntowhat.edit.status." + name().toLowerCase(Locale.ROOT);
    }

    // 宽松解析：未知状态码返回 null，调用方按 UNAVAILABLE 处理
    public static @Nullable RuleSaveStatus fromId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
