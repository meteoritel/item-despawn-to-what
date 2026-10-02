package com.meteorite.itemdespawntowhat.core.network.transport;

import java.util.ArrayList;
import java.util.List;

/***
 * 文本切分工具：按 UTF-8 字节上限切分字符串。
 * 上行变更集与下行快照的分片共用同一实现，保证两侧对"一片多大"的口径完全一致，
 * 且不会把代理对（surrogate pair）拆到两个分片里。
 */
final class RuleEditTextChunks {

    private RuleEditTextChunks() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 按 UTF-8 字节上限切分文本：逐码点累计，超限前断开
    static List<String> splitByUtf8Bytes(String text, int maxBytes) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int start = 0;
        int bytes = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int codePointBytes = utf8Length(codePoint);
            if (bytes > 0 && bytes + codePointBytes > maxBytes) {
                chunks.add(text.substring(start, index));
                start = index;
                bytes = 0;
            }
            bytes += codePointBytes;
            index += Character.charCount(codePoint);
        }
        if (start < text.length()) {
            chunks.add(text.substring(start));
        }
        return chunks;
    }

    // 单个码点的 UTF-8 字节数
    static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }
}
