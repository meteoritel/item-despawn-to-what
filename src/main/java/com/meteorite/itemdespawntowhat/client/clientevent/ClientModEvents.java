package com.meteorite.itemdespawntowhat.client.clientevent;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.openGuiKey);
    }



}
