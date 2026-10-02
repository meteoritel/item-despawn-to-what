package com.meteorite.itemdespawntowhat.core.network.transport;

import java.util.function.Consumer;

/**
 * 入站载荷分发点。
 * 平台侧注册的接收器只调用本类，从而避免 core 包（服务端同样会加载）反向依赖 client 包：
 * 真正的消费者由客户端门面 RuleEditorClient 在类初始化时注册；服务端永不注册，因此这里是空操作。
 */
public final class RuleEditPayloadRouter {

    // 快照文本消费者（客户端门面）
    private static volatile Consumer<String> snapshotSink;
    // 保存结果文本消费者（客户端门面）
    private static volatile Consumer<String> resultSink;

    private RuleEditPayloadRouter() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 安装快照文本消费者；传 null 表示清除
    public static void installSnapshotSink(Consumer<String> sink) {
        snapshotSink = sink;
    }

    // 安装保存结果文本消费者；传 null 表示清除
    public static void installResultSink(Consumer<String> sink) {
        resultSink = sink;
    }

    // 分发快照文本；尚无消费者时静默丢弃（客户端门面未加载前不会主动请求快照）
    public static void dispatchSnapshot(String text) {
        Consumer<String> sink = snapshotSink;
        if (sink != null) {
            sink.accept(text);
        }
    }

    // 分发保存结果文本；尚无消费者时静默丢弃
    public static void dispatchResult(String text) {
        Consumer<String> sink = resultSink;
        if (sink != null) {
            sink.accept(text);
        }
    }
}
