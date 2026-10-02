package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.core.network.transport.CloseRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.ConfirmRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.HeartbeatRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetPayload;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 新链路（core/network/transport）配置编辑 payload 的 Fabric 注册入口。
 * 当前唯一编辑协议，通道命名空间 idtw，协议版本见 RuleEditProtocol。
 * C2S 与 S2C 的**类型**都在公共初始化注册：专用服务端也必须能编码 S2C，否则连接协商会判定通道缺失；
 * S2C 的接收器在客户端初始化注册（见 client.network.FabricRuleEditClientRegistrar），服务端不注册接收器。
 */
public final class RuleEditPayloadRegistrar {

    // 重复调用安全（客户端与集成服务端共享一次公共初始化）
    private static boolean registered;

    private RuleEditPayloadRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册新链路全部 payload 类型与服务端接收器
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // 类型注册：C2S 供客户端发送、服务端接收；S2C 供服务端发送、客户端接收
        PayloadTypeRegistry.playC2S().register(ConfirmRuleEditorPayload.TYPE, ConfirmRuleEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(HeartbeatRuleEditorPayload.TYPE, HeartbeatRuleEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CloseRuleEditorPayload.TYPE, CloseRuleEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestRuleSnapshotPayload.TYPE, RequestRuleSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestRuleCatalogPayload.TYPE, RequestRuleCatalogPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SaveRuleChangeSetPayload.TYPE, SaveRuleChangeSetPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SaveRuleChangeSetChunkPayload.TYPE, SaveRuleChangeSetChunkPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenRuleEditorPayload.TYPE, OpenRuleEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RuleSnapshotPayload.TYPE, RuleSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RuleSnapshotChunkPayload.TYPE, RuleSnapshotChunkPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RuleSaveResultPayload.TYPE, RuleSaveResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RuleCatalogPayload.TYPE, RuleCatalogPayload.STREAM_CODEC);

        // 服务端接收器：统一切到服务端线程后交给共享 handler，平台侧只做薄转发
        ServerPlayNetworking.registerGlobalReceiver(ConfirmRuleEditorPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        RuleEditServerHandler.handleConfirm(context.player(), RuleRuntimeHost.editContext(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(HeartbeatRuleEditorPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        RuleEditServerHandler.handleHeartbeat(context.player(), RuleRuntimeHost.editContext(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(CloseRuleEditorPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        RuleEditServerHandler.handleClose(context.player(), RuleRuntimeHost.editContext(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(RequestRuleSnapshotPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        RuleEditServerHandler.handleSnapshotRequest(context.player(), RuleRuntimeHost.editContext(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(RequestRuleCatalogPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        RuleEditServerHandler.handleCatalogRequest(context.player(), RuleRuntimeHost.editContext(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(SaveRuleChangeSetPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        RuleEditServerHandler.handleChangeSet(context.player(), RuleRuntimeHost.editContext(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(SaveRuleChangeSetChunkPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        RuleEditServerHandler.handleChangeSetChunk(context.player(), RuleRuntimeHost.editContext(), payload)));

        // 玩家断开：清理未完成的分片传输；会话按契约在租约到期后释放
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> RuleEditServerHandler.clearPlayer(handler.getPlayer().getUUID()));
    }
}
