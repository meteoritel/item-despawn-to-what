package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.service.RuleOverlayWriter;
import com.meteorite.itemdespawntowhat.core.service.RuleSnapshotAssembler;
import com.mojang.serialization.DataResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 新链路配置编辑的服务端权威处理流程（两端共用，平台 registrar 只做薄转发）。
 * 流程固定为：权限校验 → 会话校验 → 版本戳校验 → 写覆盖层 → 推进版本 → 重建索引并全维度 rescan → 回执 + 新快照。
 * 版本冲突时**不落盘**，只回冲突文本与最新快照；所有回执都经 S2C 结果文本下发，不依赖聊天栏。
 */
public final class RuleEditServerHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    // 与旧链路 ConfigEditAccessControl 和 /idtw 命令一致的权限口径
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    // 服务端会话与版本戳：按覆盖层根目录缓存，单人世界切换存档时自动重建
    private static EditSessionManager sessionManager;
    private static Path sessionOverlayRoot;

    private RuleEditServerHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    // C2S：请求快照；顺带为该玩家打开（或续期）per-player 编辑会话
    public static void handleSnapshotRequest(Player player, RuleEditServerContext context) {
        ServerPlayer serverPlayer = asServerPlayer(player);
        if (serverPlayer == null) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        if (!canEdit(serverPlayer)) {
            sendResult(context, serverPlayer, "itemdespawntowhat.edit.no_permission");
            return;
        }
        if (!isReady(context)) {
            sendResult(context, serverPlayer, "itemdespawntowhat.edit.runtime_not_ready");
            return;
        }
        EditSessionManager sessions = sessions(context);
        sessions.open(serverPlayer.getUUID(), System.currentTimeMillis());
        sendSnapshot(server, serverPlayer, context, sessions);
    }

    // C2S：提交变更集（直发或分片重组后的完整 JSON 文本）
    public static void handleChangeSet(Player player, RuleEditServerContext context, String json) {
        ServerPlayer serverPlayer = asServerPlayer(player);
        if (serverPlayer == null) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        if (!canEdit(serverPlayer)) {
            sendResult(context, serverPlayer, "没有配置编辑权限：需要 OP（权限等级 ≥ 2）或单人模式");
            return;
        }
        if (!isReady(context)) {
            sendResult(context, serverPlayer, "itemdespawntowhat.edit.runtime_not_ready");
            return;
        }

        DataResult<RuleEditChangeSet> parsed = RuleEditChangeSet.parse(json);
        RuleEditChangeSet changeSet = parsed.result().orElse(null);
        if (changeSet == null) {
            sendResult(context, serverPlayer, "itemdespawntowhat.edit.parse_failed|" + parsed.error().map(error -> error.message()).orElse("unknown"));
            return;
        }

        EditSessionManager sessions = sessions(context);
        long now = System.currentTimeMillis();
        if (!sessions.isActive(serverPlayer.getUUID(), now)) {
            // 会话缺失或空闲超时：不落盘，提示重新打开界面（会话由请求快照时创建）
            sendResult(context, serverPlayer, "itemdespawntowhat.edit.session_expired");
            sendSnapshot(server, serverPlayer, context, sessions);
            return;
        }
        if (changeSet.isEmpty()) {
            sendResult(context, serverPlayer, "itemdespawntowhat.edit.nothing_to_save");
            return;
        }
        if (!sessions.versionMatches(changeSet.expectedVersion())) {
            // 服务端权威：版本不一致时整批拒绝，回传冲突说明与最新快照
            sendResult(context, serverPlayer, "itemdespawntowhat.edit.version_conflict|" + sessions.version()
                    + "|" + changeSet.expectedVersion());
            sendSnapshot(server, serverPlayer, context, sessions);
            return;
        }

        // 落盘依据是磁盘上的文件内容（A1 闭环），不基于运行时快照整文件覆盖
        IssueCollector issues = new IssueCollector();
        RuleOverlayWriter.ApplyResult applied = new RuleOverlayWriter(
                context.overlayRoot(), context.overlayNamespace()).apply(changeSet, issues);
        sessions.bumpVersion();
        sessions.open(serverPlayer.getUUID(), System.currentTimeMillis());

        // 索引重建 + 全维度 rescan：已存在的掉落物立即按新规则重选（A3 闭环）
        try {
            context.rebuildAndRescan(server);
        } catch (RuntimeException e) {
            LOGGER.error("保存后重建规则索引失败（覆盖层已写入，保留上一版索引）", e);
        }

        sendResult(context, serverPlayer, buildSuccessText(changeSet, applied, issues));
        sendSnapshot(server, serverPlayer, context, sessions);
    }

    // C2S：提交大变更集分片；收齐后复用直发保存流程
    public static void handleChangeSetChunk(Player player,
                                            RuleEditServerContext context, SaveRuleChangeSetChunkPayload payload) {
        ServerPlayer serverPlayer = asServerPlayer(player);
        if (serverPlayer == null) {
            return;
        }
        if (!canEdit(serverPlayer)) {
            sendResult(context, serverPlayer, "没有配置编辑权限：需要 OP（权限等级 ≥ 2）或单人模式");
            return;
        }
        String joined = RuleEditChunkAccumulator.accept(serverPlayer, payload);
        if (joined == null) {
            // 未收齐或分片非法：不回执，等待剩余分片或由客户端超时处理
            return;
        }
        handleChangeSet(serverPlayer, context, joined);
    }

    // 玩家断开：释放会话与未完成的分片传输
    public static synchronized void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        RuleEditChunkAccumulator.clear(playerId);
        if (sessionManager != null) {
            sessionManager.close(playerId);
        }
    }

    // 服务端停止：清空分片缓存；会话对象随下次启动按覆盖层目录重建
    public static synchronized void reset() {
        RuleEditChunkAccumulator.clearAll();
        sessionManager = null;
        sessionOverlayRoot = null;
    }

    // 组装并下发快照：合并结果 + 覆盖层原始 JSON 由 core/service 的装配器统一负责
    private static void sendSnapshot(MinecraftServer server, ServerPlayer player,
                                     RuleEditServerContext context, EditSessionManager sessions) {
        RuleLoadResult<Rule> merged = context.loadMerged(server);
        RuleSnapshot snapshot = RuleSnapshotAssembler.assemble(
                merged.rules(),
                context.overlayRoot(),
                context.overlayNamespace(),
                sessions.version(),
                context.typeRegistries().effectTypes(),
                context.typeRegistries().conditionTypes(),
                issueTexts(merged));
        String text = snapshot.serialize();
        if (RuleEditLimits.encodedLength(text) > RuleEditLimits.MAX_SNAPSHOT_BYTES) {
            sendResult(context, player, "itemdespawntowhat.edit.snapshot_too_large");
            return;
        }
        context.sendTo(player, new RuleSnapshotPayload(text));
    }

    // 保存回执文本：条数、写入文件数、跳过项与校验问题一并回报
    private static String buildSuccessText(RuleEditChangeSet changeSet,
                                           RuleOverlayWriter.ApplyResult applied,
                                           IssueCollector issues) {
        StringBuilder text = new StringBuilder();
        text.append("itemdespawntowhat.edit.save_success|")
                .append(changeSet.edits().size()).append('|')
                .append(applied.writtenFiles()).append('|')
                .append(applied.conflicts().size()).append('|')
                .append(issues.issues().size());
        if (!applied.conflicts().isEmpty()) {
            text.append('|').append(String.join("; ", applied.conflicts()));
        }
        return text.toString();
    }

    // 加载问题转文本，随快照下发供界面提示
    private static List<String> issueTexts(RuleLoadResult<Rule> merged) {
        List<String> texts = new ArrayList<>();
        for (Issue issue : merged.allIssues()) {
            texts.add(issue.format());
        }
        return texts;
    }

    // 下发一段结果文本；payload 类型需已在发送侧注册（两端 registrar 都在公共初始化注册）
    private static void sendResult(RuleEditServerContext context, ServerPlayer player, String text) {
        context.sendTo(player, new RuleSaveResultPayload(text));
    }

    // 运行时是否就绪：覆盖层路径与类型注册表都由平台引导阶段注入
    private static boolean isReady(RuleEditServerContext context) {
        return context != null
                && context.overlayRoot() != null
                && context.typeRegistries() != null;
    }

    // 编辑会话管理器（供 /idtw debug stats 只读查询版本戳与活跃会话数）；
    // 覆盖层目录尚未就绪时返回 null，命令层按"不可用"提示
    public static synchronized @Nullable EditSessionManager sessionManager(RuleEditServerContext context) {
        if (context == null || context.overlayRoot() == null) {
            return null;
        }
        return sessions(context);
    }

    // 会话管理器按覆盖层根目录缓存；单人世界切换存档后目录不变则沿用
    private static synchronized EditSessionManager sessions(RuleEditServerContext context) {
        Path root = context.overlayRoot();
        if (sessionManager == null || !root.equals(sessionOverlayRoot)) {
            sessionManager = new EditSessionManager(root);
            sessionOverlayRoot = root;
        }
        return sessionManager;
    }

    // 服务端 payload 的玩家必然是 ServerPlayer；不满足时视为非法请求直接忽略
    private static @Nullable ServerPlayer asServerPlayer(@Nullable Player player) {
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    // 权限口径与旧链路、/idtw 命令保持一致
    private static boolean canEdit(@Nullable ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        return server != null
                && (server.isSingleplayer()
                || player.createCommandSourceStack().hasPermission(REQUIRED_PERMISSION_LEVEL));
    }
}
