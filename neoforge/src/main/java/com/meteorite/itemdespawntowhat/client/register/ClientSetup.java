package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorOpener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge 客户端初始化：接线编辑工作区的收发通道与编辑界面实现。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    /** 客户端启动时安装 S2C 消费者、上行发送器与编辑器界面入口。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // FMLClientSetupEvent 是并行分发事件，接线统一排到客户端主线程执行
        event.enqueueWork(() -> {
            // 客户端编辑工作区接线：安装 S2C 消费者与上行发送器
            RuleEditClientWorkspace.installSender(PacketDistributor::sendToServer);
            // 编辑界面接线：把编辑器主屏注册为 EditorScreenHooks 的实现
            RuleEditorOpener.bootstrap();
        });
    }
}
