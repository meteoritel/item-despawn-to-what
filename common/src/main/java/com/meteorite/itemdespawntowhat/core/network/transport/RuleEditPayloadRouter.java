package com.meteorite.itemdespawntowhat.core.network.transport;

import java.util.function.Consumer;

/**
 * S2C 载荷的客户端门面：服务端侧只装载空消费者，注册回调时静默丢弃。
 * 保留字符串消费者以兼容既有客户端实现，另提供结构化消费者供界面层使用。
 */
public final class RuleEditPayloadRouter {

    // 原始 JSON 快照消费者（字符串签名版本，供既有客户端注册代码使用）
    private static volatile Consumer<String> snapshotSink;
    // 结构化快照消费者
    private static volatile Consumer<RuleSnapshotPayload> structuredSnapshotSink;
    // 原始回执文本消费者（字符串签名版本，供既有客户端注册代码使用）
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

    private static volatile Consumer<RuleSaveProgressPayload> progressSink;

    /**
     * 安装客户端保存进度消费者；服务端不安装消费者。
     * @param sink 保存进度消费者
     */
    public static void installProgressSink(Consumer<RuleSaveProgressPayload> sink) { progressSink = sink; }

    /**
     * 分发不可变保存进度样本。
     * @param payload 服务端进度
     */
    public static void dispatchProgress(RuleSaveProgressPayload payload) {
        Consumer<RuleSaveProgressPayload> sink = progressSink;
        if (sink != null) { sink.accept(payload); }
    }

    private static volatile Consumer<RuleLifespanPayload> lifespanSink;

    /**
     * 安装客户端寿命应答消费者，服务端保持为空。
     * @param sink 应答消费者
     */
    public static void installLifespanSink(Consumer<RuleLifespanPayload> sink) { lifespanSink = sink; }

    /**
     * 分发服务端寿命应答。
     * @param payload 寿命应答
     */
    public static void dispatchLifespan(RuleLifespanPayload payload) {
        Consumer<RuleLifespanPayload> sink = lifespanSink;
        if (sink != null) sink.accept(payload);
    }

    private static volatile Consumer<RuleSourceViewPayload> sourceViewSink;

    /**
     * 安装客户端来源视图应答消费者，服务端保持为空。
     * @param sink 应答消费者
     */
    public static void installSourceViewSink(Consumer<RuleSourceViewPayload> sink) { sourceViewSink = sink; }

    /**
     * 分发来源视图应答。
     * @param payload 来源视图应答
     */
    public static void dispatchSourceView(RuleSourceViewPayload payload) {
        Consumer<RuleSourceViewPayload> sink = sourceViewSink;
        if (payload != null && sink != null) sink.accept(payload);
    }

    private static volatile Consumer<RuleSourceViewChunkPayload> sourceViewChunkSink;

    /**
     * 安装客户端来源视图分片消费者，服务端保持为空。
     * @param sink 分片消费者
     */
    public static void installSourceViewChunkSink(Consumer<RuleSourceViewChunkPayload> sink) {
        sourceViewChunkSink = sink;
    }

    /**
     * 分发来源视图分片。
     * @param payload 分片载荷
     */
    public static void dispatchSourceViewChunk(RuleSourceViewChunkPayload payload) {
        Consumer<RuleSourceViewChunkPayload> sink = sourceViewChunkSink;
        if (payload != null && sink != null) sink.accept(payload);
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
