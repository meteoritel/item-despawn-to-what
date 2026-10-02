package com.meteorite.itemdespawntowhat.client.ui.view;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * 服务端编辑回执的本地化助手。
 * 回执格式为 {@code key|参数1|参数2}：仅当 key 以 {@code itemdespawntowhat.} 开头时按语言文件翻译，
 * 其余文本（旧文本、调试输出）原样返回，避免把非 key 文本当成翻译键。
 */
public final class EditMessage {

    // 服务端回执 key 前缀
    private static final String SERVER_KEY_PREFIX = "itemdespawntowhat.";
    // 回执分隔符
    private static final String SEPARATOR = "|";
    // save_success 的第 5 个参数（冲突明细）可缺省，占位符数量固定为 5
    private static final String SAVE_SUCCESS_KEY = "itemdespawntowhat.edit.save_success";
    private static final int SAVE_SUCCESS_ARG_COUNT = 5;

    private EditMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 把服务端回执文本翻译为可显示组件；空文本返回空组件
    public static Component translate(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        String[] parts = raw.split(Pattern.quote(SEPARATOR), -1);
        String key = parts[0].trim();
        if (!key.startsWith(SERVER_KEY_PREFIX)) {
            return Component.literal(raw);
        }
        String[] args = new String[parts.length - 1];
        for (int index = 1; index < parts.length; index++) {
            args[index - 1] = parts[index];
        }
        if (SAVE_SUCCESS_KEY.equals(key) && args.length == SAVE_SUCCESS_ARG_COUNT - 1) {
            // 缺省冲突明细时补空串，避免语言文件中残留未替换的占位符
            String[] padded = new String[SAVE_SUCCESS_ARG_COUNT];
            System.arraycopy(args, 0, padded, 0, args.length);
            padded[SAVE_SUCCESS_ARG_COUNT - 1] = "";
            args = padded;
        }
        return Component.translatable(key, (Object[]) args);
    }

    // 翻译后取纯文本，供需要拼接字符串的场景复用
    public static String translateToText(@Nullable String raw) {
        return translate(raw).getString();
    }
}
