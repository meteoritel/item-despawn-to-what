package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorOpener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 注册 NeoForge 客户端按键绑定，并接线客户端编辑工作区的收发通道。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class RegisterEvent {

    static {
        // 客户端编辑工作区接线：安装 S2C 消费者与上行发送器（契约 §5.3，本类仅客户端加载）
        RuleEditClientWorkspace.installSinks();
        RuleEditClientWorkspace.installSender(PacketDistributor::sendToServer);
        // 编辑界面接线：把 P5 主屏注册为 EditorScreenHooks 的实现（契约 §5.3）
        RuleEditorOpener.bootstrap();
    }

    // 注册按键
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.openGuiKey);
    }
}
