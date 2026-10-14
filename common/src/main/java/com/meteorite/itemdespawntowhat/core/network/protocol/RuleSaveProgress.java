package com.meteorite.itemdespawntowhat.core.network.protocol;

import java.util.Locale;
import java.util.Objects;

/**
 * 单次保存的不可变进度样本；条目总数未知时使用零，阶段进度不代表剩余时间。
 * @param stage 当前阶段
 * @param completed 本阶段完成条数
 * @param total 本阶段总条数，零表示未知
 * @param current 当前规则 id 或相对文件名
 * @param cancellable 是否仍可请求取消
 */
public record RuleSaveProgress(Stage stage, int completed, int total, String current, boolean cancellable) {
    /** 保存阶段；文件回滚只在原文件工作线程执行。 */
    public enum Stage {
        PREPARING, VALIDATING, PREPARING_FILES, WRITING, ROLLING_BACK,
        READING, DECODING, INDEXING, APPLYING, CANCELLING;

        /** @return 当前阶段的客户端翻译 key */
        public String messageKey() {
            return "gui.itemdespawntowhat.edit.save.stage." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 归一化计数并限制展示字符串，保持后台交接与网络内存有界。
     * @param stage 当前阶段
     * @param completed 完成条数
     * @param total 总条数，零表示未知
     * @param current 当前内容
     * @param cancellable 是否允许取消
     * @throws NullPointerException stage 为 null 时
     */
    public RuleSaveProgress {
        Objects.requireNonNull(stage, "stage");
        total = Math.max(0, total);
        completed = total == 0 ? 0 : Math.clamp(completed, 0, total);
        current = Objects.requireNonNullElse(current, "");
        if (current.length() > MAX_CURRENT_CHARS) {
            int end = MAX_CURRENT_CHARS;
            if (Character.isHighSurrogate(current.charAt(end - 1))) { end--; }
            current = current.substring(0, end);
        }
    }

    /** 单个当前内容字段的最大 UTF-16 长度。 */
    public static final int MAX_CURRENT_CHARS = 512;
}
