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

/** 编辑器入口与会话管理；打开界面前确认客户端能力并取得独占会话。 */
final class RuleEditorCommands {
    private static final String PREFIX = "itemdespawntowhat.command.editor.";

    private RuleEditorCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        return Commands.literal("editor")
                .executes(ctx -> open(ctx, context))
                .then(Commands.literal("status").executes(ctx -> status(ctx, context)))
                .then(Commands.literal("release").requires(RuleEditPermissions::isAdmin)
                        .executes(ctx -> release(ctx, context)));
    }

    private static int open(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        if (!RuleCommandTree.hasAccess(ctx.getSource())) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "no_permission").build());
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "player_only").build());
        }
        RuleEditServerContext editContext = context.editContext();
        RuleEditService service = editContext.editService();
        var transport = editContext.transport();
        if (!context.available() || service == null || transport == null) { return RuleCommandTree.notReady(ctx); }
        if (!editContext.canReachClient(player)) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "client_unavailable").build());
        }
        var outcome = service.openEditor(player, ctx.getSource().getServer().getTickCount(), editContext.runtimeLimits());
        transport.send(editContext, player, outcome.payload());
        if (!outcome.acquired()) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "lock_busy")
                    .arg(outcome.holderName()).build());
        }
        return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "sent").build());
    }

    private static int status(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        var service = context.editContext().editService();
        if (service == null) { return RuleCommandTree.notReady(ctx); }
        var sessions = service.sessions();
        var session = sessions.session();
        if (session == null) {
            return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "status.free").build());
        }
        var state = RuleCommandText.of(PREFIX + "state." + session.state().name().toLowerCase(Locale.ROOT)).build();
        if (ctx.getSource().getEntity() instanceof ServerPlayer player && sessions.isOwner(player.getUUID())) {
            return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "status.self").arg(state).build());
        }
        if (RuleCommandTree.hasAccess(ctx.getSource())) {
            return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "status.held_by")
                    .arg(session.ownerName()).arg(state).build());
        }
        return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "status.locked").build());
    }

    private static int release(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        var editContext = context.editContext();
        var service = editContext.editService();
        if (service == null) { return RuleCommandTree.notReady(ctx); }
        var released = service.forceRelease();
        if (released == null) {
            return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "release.idle").build());
        }
        if (released.state() == EditSessionManager.SessionState.APPLYING) {
            return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "release.pending").build());
        }
        if (released.ownerUuid() != null) {
            ServerPlayer owner = ctx.getSource().getServer().getPlayerList().getPlayer(released.ownerUuid());
            if (owner != null) {
                Objects.requireNonNull(editContext.transport()).send(editContext, owner,
                        new RuleEditReply.SessionEvent(released.sessionId(), RuleSaveStatus.SESSION_EXPIRED));
            }
        }
        return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "release.released")
                .arg(released.ownerName()).build());
    }
}
