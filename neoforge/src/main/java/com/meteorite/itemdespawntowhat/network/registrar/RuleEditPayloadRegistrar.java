package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetPayload;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 新链路（core/network/transport）配置编辑 payload 的 NeoForge 注册入口。
 * 当前唯一编辑协议，通道命名空间 idtw。
 * C2S 与 S2C 都在公共 mod 事件注册：NeoForge 的连接协商是双向匹配的，专用服务端若缺少 S2C 注册会协商失败；
 * S2C 的处理器只经 RuleEditPayloadRouter 分发，不引用任何客户端类，因此在专用服务端上注册同样安全（该分支永不执行）。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public final class RuleEditPayloadRegistrar {

    // 与 Fabric 端保持同一协议版本号
    private static final String PROTOCOL_VERSION = "1";

    private RuleEditPayloadRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册新链路全部 payload 与处理器
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // C2S：客户端请求快照 / 提交变更集 / 提交大变更集分片
        registrar.playToServer(RequestRuleSnapshotPayload.TYPE, RequestRuleSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditServerHandler.handleSnapshotRequest(context.player(), RuleRuntimeHost.editContext())));

        registrar.playToServer(SaveRuleChangeSetPayload.TYPE, SaveRuleChangeSetPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditServerHandler.handleChangeSet(context.player(), RuleRuntimeHost.editContext(),
                                payload.changeSetJson())));

        registrar.playToServer(SaveRuleChangeSetChunkPayload.TYPE, SaveRuleChangeSetChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditServerHandler.handleChangeSetChunk(context.player(), RuleRuntimeHost.editContext(),
                                payload)));

        // S2C：类型与处理器都在公共事件注册，处理器只做入站分发（不引用客户端类）
        registrar.playToClient(RuleSnapshotPayload.TYPE, RuleSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditPayloadRouter.dispatchSnapshot(payload.snapshotJson())));

        registrar.playToClient(RuleSaveResultPayload.TYPE, RuleSaveResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RuleEditPayloadRouter.dispatchResult(payload.text())));

        // 阶段⑤ 编辑入口：同样只做入站分发，客户端打开界面由 OpenRuleEditorPayload 的消费者执行
        registrar.playToClient(OpenRuleEditorPayload.TYPE, OpenRuleEditorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(OpenRuleEditorPayload::dispatchOpenEditor));

        // 玩家登出：释放编辑会话与未完成的分片传输
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent logout) -> {
            if (logout.getEntity() instanceof ServerPlayer player) {
                RuleEditServerHandler.clearPlayer(player.getUUID());
            }
        });
    }
}
