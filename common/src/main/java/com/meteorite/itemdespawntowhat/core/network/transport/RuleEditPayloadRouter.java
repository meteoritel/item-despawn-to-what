package com.meteorite.itemdespawntowhat.core.network.transport;

import java.util.function.Consumer;

/**
 * 新链路 S2C 载荷的客户端门面：服务端侧只装载空消费者，注册回调时静默丢弃。
 * 保留字符串消费者以兼容既有客户端实现，另提供结构化消费者供 P5 界面层使用。
 */
public final class RuleEditPayloadRouter {

    // 原始 JSON 快照消费者（旧签名，兼容既有客户端注册代码）
    private static volatile Consumer<String> snapshotSink;
    // 结构化快照消费者
    private static volatile Consumer<RuleSnapshotPayload> structuredSnapshotSink;
    // 原始回执文本消费者（旧签名，兼容既有客户端注册代码）
    private static volatile Consumer<String> resultSink;
    // 结构化回执消费者
    private static volatile Consumer<RuleSaveResultPayload> structuredResultSink;
    // 目录分页消费者
    private static volatile Consumer<RuleCatalogPayload> catalogSink;
    // 快照分片消费者
    private static volatile Consumer<RuleSnapshotChunkPayload> chunkSink;

    private RuleEditPayloadRouter() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 安装/清除原始快照 JSON 消费者
    public static void installSnapshotSink(Consumer<String> sink) {
        snapshotSink = sink;
    }

    // 安装/清除结构化快照消费者
    public static void installSnapshotPayloadSink(Consumer<RuleSnapshotPayload> sink) {
        structuredSnapshotSink = sink;
    }

    // 安装/清除原始回执文本消费者
    public static void installResultSink(Consumer<String> sink) {
        resultSink = sink;
    }

    // 安装/清除结构化回执消费者
    public static void installResultPayloadSink(Consumer<RuleSaveResultPayload> sink) {
        structuredResultSink = sink;
    }

    // 安装/清除目录分页消费者
    public static void installCatalogSink(Consumer<RuleCatalogPayload> sink) {
        catalogSink = sink;
    }

    // 安装/清除快照分片消费者
    public static void installChunkSink(Consumer<RuleSnapshotChunkPayload> sink) {
        chunkSink = sink;
    }

    // 分发快照分片
    public static void dispatchChunk(RuleSnapshotChunkPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<RuleSnapshotChunkPayload> sink = chunkSink;
        if (sink != null) {
            sink.accept(payload);
        }
    }

    // 分发原始快照 JSON
    public static void dispatchSnapshot(String json) {
        Consumer<String> sink = snapshotSink;
        if (sink != null) {
            sink.accept(json);
        }
    }

    // 分发结构化快照：先给结构化消费者，再给原始 JSON 消费者
    public static void dispatchSnapshot(RuleSnapshotPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<RuleSnapshotPayload> structured = structuredSnapshotSink;
        if (structured != null) {
            structured.accept(payload);
        }
        dispatchSnapshot(payload.snapshotJson());
    }

    // 分发原始回执文本
    public static void dispatchResult(String text) {
        Consumer<String> sink = resultSink;
        if (sink != null) {
            sink.accept(text);
        }
    }

    // 分发结构化回执：先给结构化消费者，再把状态码作为文本给兼容消费者
    public static void dispatchResult(RuleSaveResultPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<RuleSaveResultPayload> structured = structuredResultSink;
        if (structured != null) {
            structured.accept(payload);
        }
        dispatchResult(payload.statusCode());
    }

    // 分发目录分页
    public static void dispatchCatalog(RuleCatalogPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<RuleCatalogPayload> sink = catalogSink;
        if (sink != null) {
            sink.accept(payload);
        }
    }
}
