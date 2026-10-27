package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/** 平台注册器捕捉当前连接的接收绑定；延后执行时只向原绑定交付。 */
public final class RuleEditPayloadRouter {
    private static volatile Binding current;
    private RuleEditPayloadRouter() { }

    /** 在连接建立时绑定一个接收对象；调用方先清理前次工作区。 */
    public static void bind(Connection connection, RuleEditClientReceiver receiver) {
        current = new Binding(connection, receiver);
    }

    /** 仅解除指定连接，旧连接的断开事件不会清除新绑定。 */
    public static void unbind(@Nullable Connection connection) {
        Binding binding = current;
        if (binding == null || binding.connection != connection) { return; }
        current = null;
    }

    /** 网络回调在调度前捕捉所属连接的绑定。 */
    public static @Nullable Binding capture(Connection connection) {
        Binding binding = current;
        return binding != null && binding.connection == connection ? binding : null;
    }

    /** 绑定身份随连接创建；旧回调持有的旧实例不能向新工作区投递。 */
    public static final class Binding {
        private final Connection connection;
        private final RuleEditClientReceiver receiver;
        private Binding(Connection connection, RuleEditClientReceiver receiver) { this.connection = connection; this.receiver = receiver; }

        /** 在客户端线程执行时复核绑定与连接状态。 */
        public <P extends CustomPacketPayload> void deliver(RuleEditChannels.Clientbound<P> channel, P payload) {
            if (current == this && connection.isConnected()) { channel.handler().accept(receiver, payload); }
        }
    }
}
