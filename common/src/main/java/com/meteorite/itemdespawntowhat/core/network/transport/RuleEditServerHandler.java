package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.service.RuleEditService;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 独占编辑会话的网络入口（薄转发）：payload -> RuleEditService -> 结果载荷下发。
 * 会话状态机与业务编排在 EditSessionManager / RuleEditService；本类只负责
 * 就绪与权限检查、活动 tick 取值、把结果写回对应玩家。
 * 时钟一律使用 MinecraftServer.getTickCount()（暂停时不再流逝）。
 */
public final class RuleEditServerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    // 当前覆盖层根目录对应的会话状态机与服务层；根目录变化时整体重建
    private static EditSessionManager sessionManager;
    private static RuleEditService editService;
    private static Path sessionOverlayRoot;

    private RuleEditServerHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 会话状态机；覆盖层根目录不可用时返回 null（运行时与命令层兼容旧调用点）
    public static synchronized @Nullable EditSessionManager sessionManager(RuleEditServerContext context) {
        if (context == null || context.overlayRoot() == null) {
            return null;
        }
        return sessions(context);
    }

    // 服务层；覆盖层根目录不可用时返回 null
    public static synchronized @Nullable RuleEditService service(RuleEditServerContext context) {
        if (context == null || context.overlayRoot() == null) {
            return null;
        }
        sessions(context);
        return editService;
    }

    // 运行时是否就绪：覆盖层根目录与类型注册表都可用
    public static boolean isReady(@Nullable RuleEditServerContext context) {
        return context != null && context.overlayRoot() != null && context.typeRegistries() != null;
    }

    // 编辑权限：单人存档直接放行，否则要求权限等级 2
    public static boolean canEdit(@Nullable ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        return server.isSingleplayer()
                || player.createCommandSourceStack().hasPermission(RuleEditSessionLimits.REQUIRED_PERMISSION_LEVEL);
    }

    // 取当前覆盖层根目录对应的状态机：根目录变化则重建状态机与服务层
    private static synchronized EditSessionManager sessions(RuleEditServerContext context) {
        Path root = Objects.requireNonNull(context.overlayRoot(), "overlayRoot");
        if (sessionManager == null || !root.equals(sessionOverlayRoot)) {
            sessionManager = new EditSessionManager(root);
            editService = new RuleEditService(sessionManager);
            sessionOverlayRoot = root;
        }
        return sessionManager;
    }

    // 「打开编辑器」唯一入口：先原子取锁，成功或失败都把结论下发客户端；未就绪或无权限返回 null
    public static @Nullable RuleEditService.OpenOutcome openEditor(ServerPlayer player,
                                                                   RuleEditServerContext context) {
        RuleEditService service = readyService(player, context, "");
        if (service == null) {
            return null;
        }
        RuleEditService.OpenOutcome outcome = service.openEditor(player, tick(player));
        context.sendTo(player, outcome.payload());
        return outcome;
    }

    // C2S 确认：进入 ACTIVE 并回发快照
    public static void handleConfirm(ServerPlayer player, RuleEditServerContext context,
                                     ConfirmRuleEditorPayload payload) {
        RuleEditService service = readyService(player, context, sessionId(payload == null ? null : payload.sessionId()));
        if (service == null || payload == null) {
            return;
        }
        dispatch(context, player, service.confirm(player, context, serverOf(player), payload, tick(player)));
    }

    // C2S 心跳：正常无回执，失效回 SESSION_EXPIRED
    public static void handleHeartbeat(ServerPlayer player, RuleEditServerContext context,
                                       HeartbeatRuleEditorPayload payload) {
        RuleEditService service = readyService(player, context, sessionId(payload == null ? null : payload.sessionId()));
        if (service == null || payload == null) {
            return;
        }
        dispatch(context, player, service.heartbeat(player, payload, tick(player)));
    }

    // C2S 关闭：释放会话
    public static void handleClose(ServerPlayer player, RuleEditServerContext context,
                                   CloseRuleEditorPayload payload) {
        RuleEditService service = readyService(player, context, sessionId(payload == null ? null : payload.sessionId()));
        if (service == null || payload == null) {
            return;
        }
        dispatch(context, player, service.close(player, payload));
    }

    // C2S 快照请求：仅当前会话持有者有效
    public static void handleSnapshotRequest(ServerPlayer player, RuleEditServerContext context,
                                             RequestRuleSnapshotPayload payload) {
        RuleEditService service = readyService(player, context, sessionId(payload == null ? null : payload.sessionId()));
        if (service == null || payload == null) {
            return;
        }
        dispatch(context, player, service.requestSnapshot(player, context, serverOf(player), payload, tick(player)));
    }

    // C2S 目录请求：P3 只回骨架目录
    public static void handleCatalogRequest(ServerPlayer player, RuleEditServerContext context,
                                            RequestRuleCatalogPayload payload) {
        RuleEditService service = readyService(player, context, sessionId(payload == null ? null : payload.sessionId()));
        if (service == null || payload == null) {
            return;
        }
        dispatch(context, player, service.requestCatalog(player, payload, tick(player)));
    }

    // C2S 变更集提交
    public static void handleChangeSet(ServerPlayer player, RuleEditServerContext context,
                                       SaveRuleChangeSetPayload payload) {
        RuleEditService service = readyService(player, context, sessionId(payload == null ? null : payload.sessionId()));
        if (service == null || payload == null) {
            return;
        }
        dispatch(context, player, service.submitChangeSet(player, context, serverOf(player), payload, tick(player)));
    }

    // C2S 变更集分片
    public static void handleChangeSetChunk(ServerPlayer player, RuleEditServerContext context,
                                            SaveRuleChangeSetChunkPayload payload) {
        RuleEditService service = readyService(player, context, sessionId(payload == null ? null : payload.sessionId()));
        if (service == null || payload == null) {
            return;
        }
        dispatch(context, player, service.submitChunk(player, context, serverOf(player), payload, tick(player)));
    }

    // 每 20 tick 由服务端 tick 事件驱动：检查确认窗口与租约
    public static synchronized void expireIdle(@Nullable MinecraftServer server,
                                               @Nullable RuleEditServerContext context) {
        if (server == null || context == null || editService == null) {
            return;
        }
        RuleEditService.Expired expired = editService.expireSessions(server, server.getTickCount());
        if (expired.target() != null) {
            dispatch(context, expired.target(), expired.outbound());
        }
    }

    // 玩家断开：只清分片缓存；会话按契约在租约到期后释放
    public static synchronized void clearPlayer(UUID playerId) {
        if (editService != null) {
            editService.clearPlayer(playerId);
        }
    }

    // 停服：清空分片与幂等记录，并丢弃当前状态机
    public static synchronized void reset() {
        if (editService != null) {
            editService.reset();
        }
        sessionManager = null;
        editService = null;
        sessionOverlayRoot = null;
    }

    // 就绪 + 权限 + 服务层三重检查；失败时把结构化回执直接发给玩家
    private static @Nullable RuleEditService readyService(ServerPlayer player, @Nullable RuleEditServerContext context,
                                                          String sessionId) {
        if (context == null) {
            LOGGER.warn("编辑请求被忽略：运行时上下文不可用");
            return null;
        }
        if (!isReady(context)) {
            sendResult(context, player, sessionId, RuleSaveStatus.UNAVAILABLE);
            return null;
        }
        if (!canEdit(player)) {
            sendResult(context, player, sessionId, RuleSaveStatus.NO_PERMISSION);
            return null;
        }
        RuleEditService service = service(context);
        if (service == null) {
            sendResult(context, player, sessionId, RuleSaveStatus.UNAVAILABLE);
            return null;
        }
        return service;
    }

    // 发送单条失败回执（无会话、无操作号）
    private static void sendResult(RuleEditServerContext context, ServerPlayer player, String sessionId,
                                   RuleSaveStatus status) {
        context.sendTo(player, RuleSaveResultPayload.of(sessionId(sessionId), "", status,
                status.messageKey(), List.of(), 0, false, false, List.of()));
    }

    // 按顺序下发一次请求产生的全部载荷
    private static void dispatch(RuleEditServerContext context, ServerPlayer player,
                                 RuleEditService.Outbound outbound) {
        if (outbound == null) {
            return;
        }
        for (CustomPacketPayload packet : outbound.packets()) {
            context.sendTo(player, packet);
        }
    }

    // 活动 tick；服务端缺失时退化为 0（会话会因租约立即到期而释放）
    private static long tick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? 0L : server.getTickCount();
    }

    // 取服务端实例；缺失由服务层按 UNAVAILABLE 处理
    private static @Nullable MinecraftServer serverOf(ServerPlayer player) {
        return player.getServer();
    }

    // sessionId 归一化：空值统一为空串，避免 NPE 并让回执可被客户端识别
    private static String sessionId(@Nullable String value) {
        return value == null ? "" : value;
    }
}
