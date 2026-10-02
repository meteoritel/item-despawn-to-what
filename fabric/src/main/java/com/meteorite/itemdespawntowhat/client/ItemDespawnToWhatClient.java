package com.meteorite.itemdespawntowhat.client;
import com.meteorite.itemdespawntowhat.client.event.InputEvents;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.network.FabricRuleEditClientRegistrar;
import com.meteorite.itemdespawntowhat.client.register.RegisterEvent;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorOpener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** 客户端初始化：按键绑定、编辑界面入口与编辑工作区的收发通道接线。 */
public final class ItemDespawnToWhatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RegisterEvent.register();
        InputEvents.register();
        FabricRuleEditClientRegistrar.register();
        // 编辑工作区接线：安装 S2C 消费者与上行发送器（契约 §5.3）
        RuleEditClientWorkspace.installSinks();
        RuleEditClientWorkspace.installSender(payload -> ClientPlayNetworking.send(payload));
        // 编辑界面接线：把 P5 主屏注册为 EditorScreenHooks 的实现（契约 §5.3）
        RuleEditorOpener.bootstrap();
    }
}
