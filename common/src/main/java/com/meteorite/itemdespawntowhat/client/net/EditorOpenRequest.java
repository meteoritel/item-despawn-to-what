package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import java.util.List;

/**
 * 打开编辑器界面的请求：client/net 收到服务端 {@code OpenRuleEditorPayload} 授权后构造，界面层只读。
 * 字段与服务端的 {@code OpenRuleEditorPayload} 一一对应，界面层不得自行改写或推断会话参数。
 */
public record EditorOpenRequest(String sessionId, String targetId, int contextRevision,
                                int protocolVersion, RuleRuntimeLimits limits, String statusCode,
                                List<String> messageArgs, String fallbackMessage) {

    public EditorOpenRequest {
        sessionId = sessionId == null ? "" : sessionId;
        targetId = targetId == null ? "" : targetId;
        statusCode = statusCode == null ? "" : statusCode;
        messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
        fallbackMessage = fallbackMessage == null ? "" : fallbackMessage;
    }
}
