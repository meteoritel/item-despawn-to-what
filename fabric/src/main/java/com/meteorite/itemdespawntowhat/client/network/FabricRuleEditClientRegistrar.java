package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditChannels;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric 客户端接线：注册器常驻进程，接收对象绑定单次连接。 */
public final class FabricRuleEditClientRegistrar {
    private FabricRuleEditClientRegistrar() { }

    /** 注册唯一声明中的下行通道及连接生命周期事件。 */
    public static void register() {
        RuleEditChannels.clientbound().forEach(FabricRuleEditClientRegistrar::receive);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                RuleEditClientWorkspace.instance().bindConnection(handler.getConnection()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                RuleEditClientWorkspace.instance().unbindConnection(handler.getConnection()));
    }

    private static <P extends CustomPacketPayload> void receive(RuleEditChannels.Clientbound<P> channel) {
        ClientPlayNetworking.registerGlobalReceiver(channel.type(), (payload, context) -> {
            var binding = RuleEditPayloadRouter.capture(context.player().connection.getConnection());
            if (binding != null) { context.client().execute(() -> binding.deliver(channel, payload)); }
        });
    }
}
