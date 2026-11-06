package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.service.RuleEditPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 正式 /idtw 命令树；配置与查询位于 command，开发诊断由开发源集追加注册。 */
public final class RuleCommandTree {

    // 命令树根节点名，注册到 /idtw 之下
    public static final String ROOT = Constants.ID_NAMESPACE;

    private RuleCommandTree() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 注册正式命令；根帮助与会话状态公开，管理能力在各操作内判定权限。 */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RuleCommandContext context) {
        var root = Commands.literal(ROOT)
                .executes(RuleCommandTree::help)
                .then(RuleEditorCommands.build(context))
                .then(RuleQueryCommands.build(context));
        dispatcher.register(root);
    }

    /**
     * 按编辑协议相同口径判定管理权限。
     *
     * @param source 当前命令执行者
     * @return 单人存档房主或权限等级达标时为 true
     */
    public static boolean hasAccess(CommandSourceStack source) {
        return RuleEditPermissions.hasAccess(source);
    }

    /** 下发可翻译反馈，成功返回 1。 */
    public static int reply(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendSuccess(() -> text, false);
        return 1;
    }

    /** 下发失败反馈并返回 0。 */
    public static int replyFailure(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendFailure(text);
        return 0;
    }

    /** 提示服务端运行时尚未就绪。 */
    public static int notReady(CommandContext<CommandSourceStack> context) {
        return replyFailure(context, RuleCommandText.of("itemdespawntowhat.command.not_ready").build());
    }

    /**
     * 提示等待整批保存结束后再读取或重载磁盘规则。
     * @param context 命令上下文
     * @return 命令失败码
     */
    public static int saveBusy(CommandContext<CommandSourceStack> context) {
        return replyFailure(context, RuleCommandText.of("itemdespawntowhat.command.save_busy").build());
    }

    static int help(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, RuleCommandText.of("itemdespawntowhat.command.help.title").build());
        if (hasAccess(ctx.getSource())) {
            reply(ctx, RuleCommandOutput.command("help.rule", "/idtw rule list"));
            reply(ctx, RuleCommandText.of("itemdespawntowhat.command.help.actions").build());
            if (ctx.getSource().getEntity() instanceof ServerPlayer) {
                reply(ctx, RuleCommandOutput.command("help.editor", "/idtw editor"));
            }
        }
        reply(ctx, RuleCommandOutput.command("help.status", "/idtw editor status"));
        if (RuleEditPermissions.isAdmin(ctx.getSource())) {
            reply(ctx, RuleCommandOutput.command("help.release", "/idtw editor release"));
        }
        return 1;
    }
}
