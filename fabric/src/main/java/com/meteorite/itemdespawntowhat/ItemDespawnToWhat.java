package com.meteorite.itemdespawntowhat;
import com.meteorite.itemdespawntowhat.network.registrar.RuleEditPayloadRegistrar;
import com.meteorite.itemdespawntowhat.runtime.FabricRuleCommandRegistrar;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeEvents;
import net.fabricmc.api.ModInitializer;

/** Fabric 入口：仅注册统一规则后端、命令和编辑协议。 */
public final class ItemDespawnToWhat implements ModInitializer {
    public static final String MOD_ID = Constants.MOD_ID;
    @Override
    public void onInitialize() {
        RuleRuntimeEvents.register();
        RuleEditPayloadRegistrar.register();
        FabricRuleCommandRegistrar.register();
    }
}
