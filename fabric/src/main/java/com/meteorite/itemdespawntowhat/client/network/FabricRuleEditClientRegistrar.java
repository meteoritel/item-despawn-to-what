package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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
                (payload, context) -> context.client().execute(() -> RuleEditPayloadRouter.dispatchSnapshot(payload)));

        ClientPlayNetworking.registerGlobalReceiver(RuleSnapshotChunkPayload.TYPE,
                (payload, context) -> context.client().execute(() -> RuleEditPayloadRouter.dispatchChunk(payload)));

        ClientPlayNetworking.registerGlobalReceiver(RuleSaveResultPayload.TYPE,
                (payload, context) -> context.client().execute(() -> RuleEditPayloadRouter.dispatchResult(payload)));

        ClientPlayNetworking.registerGlobalReceiver(RuleCatalogPayload.TYPE,
                (payload, context) -> context.client().execute(() -> RuleEditPayloadRouter.dispatchCatalog(payload)));

        // 编辑入口：接收器把载荷切到客户端线程，开屏由 RuleEditClientWorkspace 的消费者统一负责
        ClientPlayNetworking.registerGlobalReceiver(OpenRuleEditorPayload.TYPE,
                (payload, context) -> context.client().execute(() -> OpenRuleEditorPayload.dispatchOpenEditor(payload)));
    }
}
