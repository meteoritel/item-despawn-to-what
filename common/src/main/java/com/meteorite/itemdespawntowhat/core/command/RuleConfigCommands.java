package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.service.RuleEditService;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * /idtw config 子命令组：reload / edit / validate / list / edit-lock（规划书 3.10、契约 §3.7）。
 * 全部只读或经由既有 core/service 能力落盘，命令层不直接写文件。
 * 旧链路转换器 /idtw config convert 已按 P8 结论退役（见 docs/plan/v1.2.1-migration-evaluation.md）。
 * 「编辑入口」必须先原子取锁成功才下发打开载荷；取锁失败回结构化 LOCK_BUSY 状态与持有者。
 */
final class RuleConfigCommands {

    private RuleConfigCommands() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 组装 config 分支；只有 edit-lock status 不要求编辑权限（契约 §3.7：任何玩家都能查看自身状态）
    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        return Commands.literal("config")
                .then(Commands.literal("reload")
                        .requires(RuleCommandTree::hasAccess)
                        .executes(ctx -> reload(ctx, context)))
                .then(Commands.literal("edit")
                        .requires(RuleCommandTree::hasAccess)
                        .executes(ctx -> edit(ctx, context)))
                .then(Commands.literal("validate")
                        .requires(RuleCommandTree::hasAccess)
                        .executes(ctx -> validate(ctx, context)))
                .then(Commands.literal("list")
                        .requires(RuleCommandTree::hasAccess)
                        .executes(ctx -> list(ctx, context)))
                .then(Commands.literal("edit-lock")
                        .then(Commands.literal("status").executes(ctx -> editLockStatus(ctx, context)))
                        .then(Commands.literal("release")
                                .requires(source -> source.hasPermission(RuleEditSessionLimits.REQUIRED_PERMISSION_LEVEL))
                                .executes(ctx -> editLockRelease(ctx, context))));
    }

    // config reload：重建规则索引并回扫全部维度，回显条数与问题数
    private static int reload(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        MinecraftServer server = ctx.getSource().getServer();
        RuleLoadResult<Rule> result = context.reloadRules(server);
        if (result == null) {
            return RuleCommandTree.notReady(ctx);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.summary("reload", result));
        lines.addAll(RuleCommandText.issues(result));
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // config edit：先原子取锁，成功后下发 OpenRuleEditorPayload；失败下发 LOCK_BUSY 并提示持有者
    private static int edit(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return RuleCommandTree.replyFailure(ctx,
                    RuleCommandText.of("itemdespawntowhat.command.config.edit.player_only").build());
        }
        RuleEditServerContext editContext = context.editContext();
        if (!RuleEditServerHandler.isReady(editContext)) {
            return RuleCommandTree.notReady(ctx);
        }
        if (!RuleEditServerHandler.canEdit(player)) {
            return RuleCommandTree.replyFailure(ctx,
                    RuleCommandText.of("itemdespawntowhat.edit.no_permission").build());
        }

        // 唯一入口：先取锁，成功才发打开载荷；失败同样要下发 LOCK_BUSY 载荷让客户端提示持有者
        RuleEditService.OpenOutcome outcome = RuleEditServerHandler.openEditor(player, editContext);
        if (outcome == null) {
            return RuleCommandTree.notReady(ctx);
        }
        if (!outcome.acquired()) {
            return RuleCommandTree.replyFailure(ctx,
                    RuleCommandText.of("itemdespawntowhat.command.config.edit.lock_busy")
                            .kv("player", outcome.holderName())
                            .build());
        }
        return RuleCommandTree.reply(ctx, RuleCommandText.of("itemdespawntowhat.command.config.edit.sent")
                .kv("player", player.getName().getString()).build());
    }

    // config edit-lock status：查看编辑锁状态的统一出口
    private static int editLockStatus(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        EditSessionManager sessions = RuleEditServerHandler.sessionManager(context.editContext());
        if (sessions == null) {
            return RuleCommandTree.notReady(ctx);
        }
        EditSessionManager.SessionInfo session = sessions.session();
        if (session == null) {
            return RuleCommandTree.reply(ctx,
                    RuleCommandText.of("itemdespawntowhat.command.config.edit_lock.status.free").build());
        }

        // 自身持有的会话：无需任何权限即可查看（含会话号与状态）
        if (ctx.getSource().getEntity() instanceof ServerPlayer player && sessions.isOwner(player.getUUID())) {
            return RuleCommandTree.reply(ctx,
                    RuleCommandText.of("itemdespawntowhat.command.config.edit_lock.status.self")
                            .kv("state", session.state().name().toLowerCase(java.util.Locale.ROOT))
                            .kv("session", session.sessionId())
                            .build());
        }

        // 他人持有：有权限时显示持有者，无权限只提示已被占用（不泄露会话号）
        if (RuleCommandTree.hasAccess(ctx.getSource())) {
            return RuleCommandTree.reply(ctx,
                    RuleCommandText.of("itemdespawntowhat.command.config.edit_lock.status.held_by")
                            .kv("player", session.ownerName())
                            .kv("state", session.state().name().toLowerCase(java.util.Locale.ROOT))
                            .build());
        }
        return RuleCommandTree.reply(ctx,
                RuleCommandText.of("itemdespawntowhat.command.config.edit_lock.status.locked").build());
    }

    // config edit-lock release：权限 ≥2 强制释放，并通知原持有者其会话已失效
    private static int editLockRelease(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        RuleEditServerContext editContext = context.editContext();
        EditSessionManager sessions = RuleEditServerHandler.sessionManager(editContext);
        if (sessions == null) {
            return RuleCommandTree.notReady(ctx);
        }
        EditSessionManager.SessionInfo released = sessions.release();
        if (released == null) {
            return RuleCommandTree.reply(ctx,
                    RuleCommandText.of("itemdespawntowhat.command.config.edit_lock.release.idle").build());
        }

        // 原持有者若在线，立刻收到 SESSION_EXPIRED，客户端据此关闭编辑界面并回到未持有状态
        MinecraftServer server = ctx.getSource().getServer();
        if (released.ownerUuid() != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(released.ownerUuid());
            if (owner != null) {
                editContext.sendTo(owner, RuleSaveResultPayload.of(released.sessionId(), "",
                        RuleSaveStatus.SESSION_EXPIRED, RuleSaveStatus.SESSION_EXPIRED.messageKey(),
                        List.of(), sessions.version(), false, false, List.of()));
            }
        }
        return RuleCommandTree.reply(ctx,
                RuleCommandText.of("itemdespawntowhat.command.config.edit_lock.release.released")
                        .kv("player", released.ownerName()).build());
    }

    // config validate：离线体检全部来源，输出问题清单（含来源与字段路径）与统计
    private static int validate(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        RuleLoadResult<Rule> result = load(ctx, context);
        if (result == null) {
            return RuleCommandTree.notReady(ctx);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.summary("validate", result));
        lines.addAll(RuleCommandText.issues(result));
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // config list：逐条列出生效规则（id / 来源层 / 优先级 / 触发秒数 / 效果数 / 是否启用）
    private static int list(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        RuleLoadResult<Rule> result = load(ctx, context);
        if (result == null) {
            return RuleCommandTree.notReady(ctx);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.summary("list", result));
        for (var entry : result.rules()) {
            Rule rule = entry.value();
            lines.add(RuleCommandText.of("itemdespawntowhat.command.config.list.entry")
                    .kv("id", rule.id())
                    .kv("layer", entry.origin().layer().name().toLowerCase(java.util.Locale.ROOT))
                    .kv("priority", rule.priority())
                    .kv("trigger_seconds", rule.triggerAfterSeconds())
                    .kv("effects", rule.effects().size())
                    .kv("enabled", rule.enabled())
                    .kv("origin", entry.origin().display())
                    .build());
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // 复用编辑层的合并加载（只读，不改变运行时索引）
    private static RuleLoadResult<Rule> load(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        return context.editContext().loadMerged(ctx.getSource().getServer());
    }
}
