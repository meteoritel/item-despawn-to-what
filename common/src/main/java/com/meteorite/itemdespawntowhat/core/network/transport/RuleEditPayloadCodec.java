package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑链路载荷的共用编解码片段：字符串列表、可空字符串与结构化问题列表。
 * 入站字段严格校验长度；服务端出站的诊断问题按协议限额裁剪，保留完整 Unicode 代理对。
 */
final class RuleEditPayloadCodec {

    private RuleEditPayloadCodec() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 写入字符串列表：先写条数，再逐条写入
    static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        List<String> safe = values == null ? List.of() : values;
        buffer.writeVarInt(safe.size());
        for (String value : safe) {
            buffer.writeUtf(value == null ? "" : value, RuleEditLimits.MAX_MESSAGE_ARG_CHARS);
        }
    }

    // 读取字符串列表；条数超限视为非法载荷
    static List<String> readStrings(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > RuleEditLimits.MAX_MESSAGE_ARGS) {
            throw new DecoderException("消息参数条数非法: " + count);
        }
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(buffer.readUtf(RuleEditLimits.MAX_MESSAGE_ARG_CHARS));
        }
        return List.copyOf(values);
    }

    // 写入可空字符串：仅用于结构化问题里的 ruleId
    static void writeNullableString(FriendlyByteBuf buffer, String value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeUtf(diagnosticText(value, RuleEditLimits.MAX_CODE_CHARS), RuleEditLimits.MAX_CODE_CHARS);
        }
    }

    // 读取可空字符串
    static String readNullableString(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS) : null;
    }

    // 写入结构化问题列表
    static void writeIssues(FriendlyByteBuf buffer, List<RuleIssue> issues) {
        List<RuleIssue> safe = issues == null ? List.of()
                : issues.subList(0, Math.min(issues.size(), RuleEditLimits.MAX_ISSUES_PER_RESULT));
        buffer.writeVarInt(safe.size());
        for (RuleIssue issue : safe) {
            buffer.writeUtf(diagnosticText(issue.severity(), RuleEditLimits.MAX_CODE_CHARS), RuleEditLimits.MAX_CODE_CHARS);
            writeNullableString(buffer, issue.ruleId());
            buffer.writeUtf(diagnosticText(issue.origin(), RuleEditLimits.MAX_CODE_CHARS), RuleEditLimits.MAX_CODE_CHARS);
            buffer.writeUtf(diagnosticText(issue.fieldPath(), RuleEditLimits.MAX_FALLBACK_CHARS), RuleEditLimits.MAX_FALLBACK_CHARS);
            buffer.writeUtf(diagnosticText(issue.messageCode(), RuleEditLimits.MAX_CODE_CHARS), RuleEditLimits.MAX_CODE_CHARS);
            writeStrings(buffer, issue.messageArgs().stream().limit(RuleEditLimits.MAX_MESSAGE_ARGS)
                    .map(value -> diagnosticText(value, RuleEditLimits.MAX_MESSAGE_ARG_CHARS)).toList());
            buffer.writeUtf(diagnosticText(issue.fallbackMessage(), RuleEditLimits.MAX_FALLBACK_CHARS), RuleEditLimits.MAX_FALLBACK_CHARS);
        }
    }

    // FriendlyByteBuf 按 UTF-16 长度和最多三倍字节数限额；完整代理对的 UTF-8 编码同样满足该字节界限。
    private static String diagnosticText(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        int end = maxChars;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    // 读取结构化问题列表；条数超限视为非法载荷
    static List<RuleIssue> readIssues(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > RuleEditLimits.MAX_ISSUES_PER_RESULT) {
            throw new DecoderException("结构化问题条数非法: " + count);
        }
        List<RuleIssue> issues = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            issues.add(new RuleIssue(
                    buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                    readNullableString(buffer),
                    buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                    buffer.readUtf(RuleEditLimits.MAX_FALLBACK_CHARS),
                    buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                    readStrings(buffer),
                    buffer.readUtf(RuleEditLimits.MAX_FALLBACK_CHARS)));
        }
        return List.copyOf(issues);
    }
}
