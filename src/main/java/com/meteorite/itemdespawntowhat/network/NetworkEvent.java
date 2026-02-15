package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.ui.Screen.ConfigSelectionScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class NetworkEvent {

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenGuiPayload.TYPE,
                OpenGuiPayload.STREAM_CODEC,
                (payload, context) -> {
                    // 安排到客户端线程
                    context.enqueueWork(() -> {
                        Minecraft.getInstance().setScreen(new ConfigSelectionScreen());
                    });

                }
        );
    }
}
