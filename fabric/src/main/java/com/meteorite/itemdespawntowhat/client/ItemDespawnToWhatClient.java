package com.meteorite.itemdespawntowhat.client;

import com.meteorite.itemdespawntowhat.client.event.InputEvents;
import com.meteorite.itemdespawntowhat.client.network.FabricRuleEditClientRegistrar;
import com.meteorite.itemdespawntowhat.client.register.RegisterEvent;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.network.registrar.ConfigEditClientPayloadRegistrar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;

public class ItemDespawnToWhatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RegisterEvent.register();
        InputEvents.register();
        ConfigEditClientPayloadRegistrar.register();
        // 新链路（core/network/transport）S2C 接收器
        FabricRuleEditClientRegistrar.register();

        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client) {
                SuggestionProvider.clearCache();
            }
        });
    }
}
