package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditChannels;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** NeoForge 双向注册适配；声明和客户端中立处理面均位于 common。 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public final class RuleEditPayloadRegistrar {
    private RuleEditPayloadRegistrar() { }

    /** 从唯一声明注册双向通道，专用服务端也完成下行协商注册。 */
    @SubscribeEvent public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Integer.toString(RuleEditProtocol.VERSION));
        RuleEditChannels.serverbound().forEach(channel -> server(registrar, channel));
        RuleEditChannels.clientbound().forEach(channel -> client(registrar, channel));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent logout) -> {
            if (logout.getEntity() instanceof ServerPlayer player) {
                RuleEditServerHandler.clearPlayer(player.getUUID(), RuleRuntimeHost.editContext());
            }
        });
    }

    private static <P extends CustomPacketPayload> void server(PayloadRegistrar registrar, RuleEditChannels.Serverbound<P> channel) {
        registrar.playToServer(channel.type(), channel.codec(), (payload, context) -> {
            var target = RuleRuntimeHost.editContext();
            var service = target.editService();
            context.enqueueWork(() -> {
                if (target.editService() == service && context.player() instanceof ServerPlayer player) {
                    channel.handler().accept(player, target, payload);
                }
            });
        });
    }

    private static <P extends CustomPacketPayload> void client(PayloadRegistrar registrar, RuleEditChannels.Clientbound<P> channel) {
        registrar.playToClient(channel.type(), channel.codec(), (payload, context) -> {
            var binding = RuleEditPayloadRouter.capture(context.connection());
            if (binding != null) { context.enqueueWork(() -> binding.deliver(channel, payload)); }
        });
    }
}
