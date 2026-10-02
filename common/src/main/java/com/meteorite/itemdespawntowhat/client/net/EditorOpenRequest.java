package com.meteorite.itemdespawntowhat.client.net;

import java.util.List;

/***
 * 打开编辑器界面的请求（契约 §5.3）：client/net 收到服务端 {@code OpenRuleEditorPayload} 授权后构造，界面层只读。
 * 字段与协议 §3.3 的 OpenRuleEditorPayload 一一对应，界面层不得自行改写或推断会话参数。
 */
public record EditorOpenRequest(String sessionId, String targetId, int contextRevision,
                                int protocolVersion, String statusCode,
                                List<String> messageArgs, String fallbackMessage) {

    public EditorOpenRequest {
        sessionId = sessionId == null ? "" : sessionId;
        targetId = targetId == null ? "" : targetId;
        statusCode = statusCode == null ? "" : statusCode;
        messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
        fallbackMessage = fallbackMessage == null ? "" : fallbackMessage;
    }
}
