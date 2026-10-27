package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditChannels;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric 公共类型注册和 C2S 接收器；S2C 接收器由客户端初始化阶段注册。 */
public final class RuleEditPayloadRegistrar {
    private RuleEditPayloadRegistrar() { }

    /** 从同一声明注册类型和服务端处理器。 */
    public static void register() {
        RuleEditChannels.serverbound().forEach(RuleEditPayloadRegistrar::server);
        RuleEditChannels.clientbound().forEach(RuleEditPayloadRegistrar::clientType);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                RuleEditServerHandler.clearPlayer(handler.getPlayer().getUUID(), RuleRuntimeHost.editContext()));
    }

    private static <P extends CustomPacketPayload> void server(RuleEditChannels.Serverbound<P> channel) {
        PayloadTypeRegistry.playC2S().register(channel.type(), channel.codec());
        ServerPlayNetworking.registerGlobalReceiver(channel.type(), (payload, context) -> {
            var target = RuleRuntimeHost.editContext();
            var service = target.editService();
            context.server().execute(() -> {
                if (target.editService() == service) { channel.handler().accept(context.player(), target, payload); }
            });
        });
    }

    private static <P extends CustomPacketPayload> void clientType(RuleEditChannels.Clientbound<P> channel) {
        PayloadTypeRegistry.playS2C().register(channel.type(), channel.codec());
    }
}
