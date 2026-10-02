package com.meteorite.itemdespawntowhat.core.network.transport;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：服务端要求客户端打开规则编辑入口（模板选择屏）。无载荷字段。
 * 阶段⑤ 的 /idtw config edit 使用；入站分发点由平台客户端接收器调用 {@link #dispatchOpenEditor()}，
 * 本类同时承载"打开编辑界面"的客户端消费者（服务端永不安装，因此在服务端是空操作），
 * 这样 core 包不会反向依赖 client 包。
 */
public record OpenRuleEditorPayload() implements CustomPacketPayload {

    public static final Type<OpenRuleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "open_rule_editor")
    );

    public static final StreamCodec<ByteBuf, OpenRuleEditorPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenRuleEditorPayload());

    // 客户端打开编辑界面的消费者；只在客户端初始化时安装
    private static volatile Runnable openEditorSink;

    // 安装/清除客户端打开回调；传 null 表示清除
    public static void installOpenEditorSink(Runnable sink) {
        openEditorSink = sink;
    }

    // 分发"打开编辑界面"信号；尚无消费者时（专用服务端）静默丢弃
    public static void dispatchOpenEditor() {
        Runnable sink = openEditorSink;
        if (sink != null) {
            sink.run();
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
