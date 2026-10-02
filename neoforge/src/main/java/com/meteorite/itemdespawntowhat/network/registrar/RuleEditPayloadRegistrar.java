package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.core.network.transport.CloseRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.ConfirmRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.HeartbeatRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetPayload;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 新链路（core/network/transport）配置编辑 payload 的 NeoForge 注册入口。
 * 当前唯一编辑协议，通道命名空间 idtw；协议版本与 RuleEditProtocol.VERSION 保持一致。
 * C2S 与 S2C 都在公共 mod 事件注册：NeoForge 的连接协商是双向匹配的，专用服务端若缺少 S2C 注册会协商失败；
 * S2C 的处理器只经 RuleEditPayloadRouter 分发，不引用任何客户端类，因此在专用服务端上注册同样安全（该分支永不执行）。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public final class RuleEditPayloadRegistrar {

    // 与 Fabric 端保持同一协议版本号（契约 §3：版本 2）
    private static final String PROTOCOL_VERSION = "2";

    private RuleEditPayloadRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 服务端处理器：把 NeoForge 的载荷上下文收敛为 ServerPlayer + 载荷
    @FunctionalInterface
    private interface ServerHandler<T extends CustomPacketPayload> {
        void handle(ServerPlayer player, T payload);
    }

    // 注册新链路全部 payload 与处理器
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // C2S：确认 / 心跳 / 关闭 / 请求快照 / 请求目录 / 提交变更集 / 提交分片
        registrar.playToServer(ConfirmRuleEditorPayload.TYPE, ConfirmRuleEditorPayload.STREAM_CODEC,
                (payload, context) -> handleOnServer(context, payload, (player, body) ->
                        RuleEditServerHandler.handleConfirm(player, RuleRuntimeHost.editContext(), body)));

        registrar.playToServer(HeartbeatRuleEditorPayload.TYPE, HeartbeatRuleEditorPayload.STREAM_CODEC,
                (payload, context) -> handleOnServer(context, payload, (player, body) ->
                        RuleEditServerHandler.handleHeartbeat(player, RuleRuntimeHost.editContext(), body)));

        registrar.playToServer(CloseRuleEditorPayload.TYPE, CloseRuleEditorPayload.STREAM_CODEC,
                (payload, context) -> handleOnServer(context, payload, (player, body) ->
                        RuleEditServerHandler.handleClose(player, RuleRuntimeHost.editContext(), body)));

        registrar.playToServer(RequestRuleSnapshotPayload.TYPE, RequestRuleSnapshotPayload.STREAM_CODEC,
                (payload, context) -> handleOnServer(context, payload, (player, body) ->
                        RuleEditServerHandler.handleSnapshotRequest(player, RuleRuntimeHost.editContext(), body)));

        registrar.playToServer(RequestRuleCatalogPayload.TYPE, RequestRuleCatalogPayload.STREAM_CODEC,
                (payload, context) -> handleOnServer(context, payload, (player, body) ->
                        RuleEditServerHandler.handleCatalogRequest(player, RuleRuntimeHost.editContext(), body)));

        registrar.playToServer(SaveRuleChangeSetPayload.TYPE, SaveRuleChangeSetPayload.STREAM_CODEC,
                (payload, context) -> handleOnServer(context, payload, (player, body) ->
                        RuleEditServerHandler.handleChangeSet(player, RuleRuntimeHost.editContext(), body)));

        registrar.playToServer(SaveRuleChangeSetChunkPayload.TYPE, SaveRuleChangeSetChunkPayload.STREAM_CODEC,
                (payload, context) -> handleOnServer(context, payload, (player, body) ->
                        RuleEditServerHandler.handleChangeSetChunk(player, RuleRuntimeHost.editContext(), body)));

        // S2C：类型与处理器都在公共事件注册，处理器只做入站分发（不引用客户端类）
        registrar.playToClient(RuleSnapshotPayload.TYPE, RuleSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditPayloadRouter.dispatchSnapshot(payload)));

        registrar.playToClient(RuleSnapshotChunkPayload.TYPE, RuleSnapshotChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditPayloadRouter.dispatchChunk(payload)));

        registrar.playToClient(RuleSaveResultPayload.TYPE, RuleSaveResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditPayloadRouter.dispatchResult(payload)));

        registrar.playToClient(RuleCatalogPayload.TYPE, RuleCatalogPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditPayloadRouter.dispatchCatalog(payload)));

        // 编辑入口：同样只做入站分发，客户端打开界面由 OpenRuleEditorPayload 的消费者执行
        registrar.playToClient(OpenRuleEditorPayload.TYPE, OpenRuleEditorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> OpenRuleEditorPayload.dispatchOpenEditor(payload)));

        // 玩家登出：清理未完成的分片传输；会话按契约在租约到期后释放
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent logout) -> {
            if (logout.getEntity() instanceof ServerPlayer player) {
                RuleEditServerHandler.clearPlayer(player.getUUID());
            }
        });
    }

    // NeoForge 的 IPayloadContext.player() 返回 Player；仅在确为服务端玩家时切入服务端线程执行
    private static <T extends CustomPacketPayload> void handleOnServer(IPayloadContext context, T payload,
                                                                       ServerHandler<T> action) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                action.handle(player, payload);
            }
        });
    }
}
