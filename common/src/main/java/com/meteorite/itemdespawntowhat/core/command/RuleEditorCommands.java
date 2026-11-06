package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.service.RuleEditPermissions;
import com.meteorite.itemdespawntowhat.core.service.RuleEditService;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEditReply;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Objects;

// 公开状态查询与管理动作共用 editor 分支，打开界面前确认客户端能力并取得独占会话。
final class RuleEditorCommands {
    private RuleEditorCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        return Commands.literal("editor")
                .executes(command -> open(command, context))
                .then(Commands.literal("open").requires(RuleCommandTree::hasAccess)
                        .executes(command -> open(command, context)))
                .then(Commands.literal("status").executes(command -> status(command, context)))
                .then(Commands.literal("release").requires(RuleEditPermissions::isAdmin)
                        .executes(command -> release(command, context)));
    }

    private static int open(CommandContext<CommandSourceStack> command, RuleCommandContext context) {
        if (!RuleCommandTree.hasAccess(command.getSource())) {
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("editor.no_permission").build());
        }
        if (!(command.getSource().getEntity() instanceof ServerPlayer player)) {
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("editor.player_only").build());
        }
        RuleEditServerContext editContext = context.editContext();
        RuleEditService service = editContext.editService();
        var transport = editContext.transport();
        if (!context.available() || service == null || transport == null) {
            return RuleCommandTree.notReady(command);
        }
        if (!editContext.canReachClient(player)) {
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("editor.client_unavailable").build());
        }
        var outcome = service.openEditor(player, command.getSource().getServer().getTickCount(), editContext.runtimeLimits());
        transport.send(editContext, player, outcome.payload());
        if (!outcome.acquired()) {
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("editor.lock_busy")
                    .arg(outcome.holderName()).build());
        }
        return RuleCommandTree.reply(command, RuleCommandText.message("editor.sent").build());
    }

    private static int status(CommandContext<CommandSourceStack> command, RuleCommandContext context) {
        var service = context.editContext().editService();
        if (service == null) {
            return RuleCommandTree.notReady(command);
        }
        var sessions = service.sessions();
        var session = sessions.session();
        if (session == null) {
            return RuleCommandTree.reply(command, RuleCommandText.message("editor.status.free").build());
        }
        var state = RuleCommandText.message("editor.state." + session.state().name().toLowerCase(Locale.ROOT)).build();
        if (command.getSource().getEntity() instanceof ServerPlayer player && sessions.isOwner(player.getUUID())) {
            return RuleCommandTree.reply(command, RuleCommandText.message("editor.status.self").arg(state).build());
        }
        if (RuleCommandTree.hasAccess(command.getSource())) {
            return RuleCommandTree.reply(command, RuleCommandText.message("editor.status.held_by")
                    .arg(session.ownerName()).arg(state).build());
        }
        return RuleCommandTree.reply(command, RuleCommandText.message("editor.status.locked").build());
    }

    private static int release(CommandContext<CommandSourceStack> command, RuleCommandContext context) {
        var editContext = context.editContext();
        var service = editContext.editService();
        if (service == null) {
            return RuleCommandTree.notReady(command);
        }
        var released = service.forceRelease();
        if (released == null) {
            return RuleCommandTree.reply(command, RuleCommandText.message("editor.release.idle").build());
        }
        if (released.state() == EditSessionManager.SessionState.APPLYING) {
            return RuleCommandTree.reply(command, RuleCommandText.message("editor.release.pending").build());
        }
        notifyReleasedOwner(command, editContext, released);
        return RuleCommandTree.reply(command, RuleCommandText.message("editor.release.released")
                .arg(released.ownerName()).build());
    }

    private static void notifyReleasedOwner(CommandContext<CommandSourceStack> command,
                                            RuleEditServerContext context, EditSessionManager.SessionInfo released) {
        if (released.ownerUuid() == null) {
            return;
        }
        ServerPlayer owner = command.getSource().getServer().getPlayerList().getPlayer(released.ownerUuid());
        if (owner != null) {
            Objects.requireNonNull(context.transport()).send(context, owner,
                    new RuleEditReply.SessionEvent(released.sessionId(), RuleSaveStatus.SESSION_EXPIRED));
        }
    }
}
