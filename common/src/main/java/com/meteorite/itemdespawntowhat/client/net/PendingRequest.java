package com.meteorite.itemdespawntowhat.client.net;

import java.util.UUID;

/** 请求身份与期限的公共机制；来源 LRU 与目录分页各自决定数据保留策略。 */
final class PendingRequest {
    private String id = "";
    private long deadline;
    private boolean failed;

    String start(long now, long timeoutMillis) {
        id = UUID.randomUUID().toString();
        deadline = now + timeoutMillis;
        failed = false;
        return id;
    }

    boolean waiting() { return !id.isEmpty(); }
    boolean failed() { return failed; }
    boolean matches(String requestId) { return waiting() && id.equals(requestId); }
    void complete() { id = ""; deadline = 0; failed = false; }
    void fail() { id = ""; deadline = 0; failed = true; }
    void expire(long now) { if (waiting() && now > deadline) fail(); }
}
