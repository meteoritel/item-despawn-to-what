package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorPlaceholderScreen;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * 新链路配置编辑 S2C 载荷的 Fabric 客户端接收器。
 * 只在客户端初始化注册；接收器不直接依赖门面类，统一经 RuleEditPayloadRouter 分发，
 * 从而让 core 包（服务端同样加载）不出现对 client 包的引用。
 */
public final class FabricRuleEditClientRegistrar {

    private FabricRuleEditClientRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册 S2C 接收器；重复调用安全
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RuleSnapshotPayload.TYPE,
                (payload, context) -> RuleEditPayloadRouter.dispatchSnapshot(payload.snapshotJson()));

        ClientPlayNetworking.registerGlobalReceiver(RuleSaveResultPayload.TYPE,
                (payload, context) -> RuleEditPayloadRouter.dispatchResult(payload.text()));

        // 阶段⑤ 编辑入口：接收器只把信号切到客户端线程，真正的开屏动作由下面安装的回调执行
        OpenRuleEditorPayload.installOpenEditorSink(
                () -> Minecraft.getInstance().setScreen(new RuleEditorPlaceholderScreen()));
        ClientPlayNetworking.registerGlobalReceiver(OpenRuleEditorPayload.TYPE,
                (payload, context) -> context.client().execute(OpenRuleEditorPayload::dispatchOpenEditor));
    }
}
