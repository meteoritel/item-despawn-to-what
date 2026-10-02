package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/***
 * 编辑链路载荷的共用编解码片段：字符串列表、可空字符串与结构化问题列表。
 * 全部字段按契约的"先校验长度、不截断"原则编解码，超限即抛解码异常交由连接层断开。
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
            buffer.writeUtf(value, RuleEditLimits.MAX_CODE_CHARS);
        }
    }

    // 读取可空字符串
    static String readNullableString(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS) : null;
    }

    // 写入结构化问题列表
    static void writeIssues(FriendlyByteBuf buffer, List<RuleIssue> issues) {
        List<RuleIssue> safe = issues == null ? List.of() : issues;
        buffer.writeVarInt(safe.size());
        for (RuleIssue issue : safe) {
            buffer.writeUtf(issue.severity(), RuleEditLimits.MAX_CODE_CHARS);
            writeNullableString(buffer, issue.ruleId());
            buffer.writeUtf(issue.origin(), RuleEditLimits.MAX_CODE_CHARS);
            buffer.writeUtf(issue.fieldPath(), RuleEditLimits.MAX_FALLBACK_CHARS);
            buffer.writeUtf(issue.messageCode(), RuleEditLimits.MAX_CODE_CHARS);
            writeStrings(buffer, issue.messageArgs());
            buffer.writeUtf(issue.fallbackMessage(), RuleEditLimits.MAX_FALLBACK_CHARS);
        }
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
