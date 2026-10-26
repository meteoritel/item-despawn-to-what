package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleLifespanPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveProgressPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSourceViewPayload;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 新链路配置编辑 S2C 载荷的 Fabric 客户端接收器。
 * 只在客户端初始化注册；接收器不直接依赖门面类，统一经 RuleEditPayloadRouter 分发，
 * 从而让 core 包（服务端同样加载）不出现对 client 包的引用。
 */
public final class FabricRuleEditClientRegistrar {

    private FabricRuleEditClientRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 注册 S2C 接收器；同一通道重复注册只会覆盖处理器，不会抛异常。
     * 网络回调借用游戏客户端；这里只调度任务，不负责关闭 Minecraft 实例。
     */

    public static void register() {
        receive(RuleSnapshotPayload.TYPE, RuleEditPayloadRouter::dispatchSnapshot);
        receive(RuleChunkPayload.SNAPSHOT, RuleEditPayloadRouter::dispatchChunk);
        receive(RuleSaveProgressPayload.TYPE, RuleEditPayloadRouter::dispatchProgress);
        receive(RuleSaveResultPayload.TYPE, RuleEditPayloadRouter::dispatchResult);
        receive(RuleCatalogPayload.TYPE, RuleEditPayloadRouter::dispatchCatalog);
        receive(RuleLifespanPayload.TYPE, RuleEditPayloadRouter::dispatchLifespan);
        receive(RuleSourceViewPayload.TYPE, RuleEditPayloadRouter::dispatchSourceView);
        receive(RuleChunkPayload.SOURCE_VIEW, RuleEditPayloadRouter::dispatchChunk);
        // 编辑入口：接收器把载荷切到客户端线程，开屏由 RuleEditClientWorkspace 的消费者统一负责
        receive(OpenRuleEditorPayload.TYPE, OpenRuleEditorPayload::dispatchOpenEditor);
    }

    // 统一接收入口：先把载荷切到客户端线程，再交给分发者，新增载荷时不再复制这段样板
    private static <P extends CustomPacketPayload> void receive(
            CustomPacketPayload.Type<P> type, Consumer<P> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> context.client().execute(() -> handler.accept(payload)));
    }
}
