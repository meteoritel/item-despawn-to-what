package com.meteorite.itemdespawntowhat.core.network.transport;

import java.util.function.Consumer;

/**
 * S2C 载荷的客户端门面：服务端侧只装载空消费者，注册回调时静默丢弃。
 * 大文本一律经 {@link #installChunkSink} / {@link #dispatchChunk} 这一条分片入口进出，
 * 直发整包另有独立消费者，两者由发送端决定走哪边。
 */
public final class RuleEditPayloadRouter {

    // 结构化快照消费者
    private static volatile Consumer<RuleSnapshotPayload> structuredSnapshotSink;
    // 结构化回执消费者
    private static volatile Consumer<RuleSaveResultPayload> structuredResultSink;
    // 目录分页消费者
    private static volatile Consumer<RuleCatalogPayload> catalogSink;
    // 分片消费者，快照、来源视图与变更集三个通道共用
    private static volatile Consumer<RuleChunkPayload> chunkSink;

    private RuleEditPayloadRouter() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 安装/清除结构化快照消费者
    public static void installSnapshotPayloadSink(Consumer<RuleSnapshotPayload> sink) {
        structuredSnapshotSink = sink;
    }

    // 安装/清除结构化回执消费者
    public static void installResultPayloadSink(Consumer<RuleSaveResultPayload> sink) {
        structuredResultSink = sink;
    }

    // 安装/清除目录分页消费者
    public static void installCatalogSink(Consumer<RuleCatalogPayload> sink) {
        catalogSink = sink;
    }

    /**
     * 安装分片消费者：三个分片通道共用这一条入口，通道差异由调用方按 {@link RuleChunkPayload#type()} 自行区分。
     * @param sink 分片消费者，null 表示清除
     */
    public static void installChunkSink(Consumer<RuleChunkPayload> sink) {
        chunkSink = sink;
    }

    /**
     * 分发一片文本分片；未安装消费者时静默丢弃，服务端正是以此形成空实现。
     * @param payload 分片载荷，null 时忽略
     */
    public static void dispatchChunk(RuleChunkPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<RuleChunkPayload> sink = chunkSink;
        if (sink != null) {
            sink.accept(payload);
        }
    }

    /**
     * 分发整包快照：只有不超过直发上限的文本走这里，更大的由 {@link RuleTextChunker} 切片后走 {@link #dispatchChunk}。
     * @param payload 快照载荷，null 时忽略
     */
    public static void dispatchSnapshot(RuleSnapshotPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<RuleSnapshotPayload> structured = structuredSnapshotSink;
        if (structured != null) {
            structured.accept(payload);
        }
    }

    /**
     * 分发整包保存回执：状态码由回执载荷自身承载。
     * @param payload 回执载荷，null 时忽略
     */
    public static void dispatchResult(RuleSaveResultPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<RuleSaveResultPayload> structured = structuredResultSink;
        if (structured != null) {
            structured.accept(payload);
        }
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
