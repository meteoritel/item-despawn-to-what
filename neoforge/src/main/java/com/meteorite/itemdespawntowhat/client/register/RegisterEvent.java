package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorPlaceholderScreen;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 注册 NeoForge 客户端按键绑定。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class RegisterEvent {

    static {
        // 阶段⑤：/idtw config edit 的 S2C 编辑入口 → 打开模板选择屏（本类仅客户端加载）
        OpenRuleEditorPayload.installOpenEditorSink(
                () -> Minecraft.getInstance().setScreen(new RuleEditorPlaceholderScreen()));
    }

    // 注册按键
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.openGuiKey);
    }
}
