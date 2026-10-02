package com.meteorite.itemdespawntowhat.client;
import com.meteorite.itemdespawntowhat.client.event.InputEvents;
import com.meteorite.itemdespawntowhat.client.network.FabricRuleEditClientRegistrar;
import com.meteorite.itemdespawntowhat.client.register.RegisterEvent;
import net.fabricmc.api.ClientModInitializer;

/** 客户端仅提供占位入口，完整规则编辑器留待前端重构。 */
public final class ItemDespawnToWhatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RegisterEvent.register();
        InputEvents.register();
        FabricRuleEditClientRegistrar.register();
    }
}
