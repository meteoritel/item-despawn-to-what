package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.service.RuleEditPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 组装正式命令树并提供反馈出口；执行逻辑在服务端线程读取当前宿主状态。
 * 根帮助与会话状态公开，管理动作分别校验权限；开发命令由开发源集追加。
 */
public final class RuleCommandTree {

    /**
     * 正式命令与开发命令共用的根节点名称。
     */
    public static final String ROOT = Constants.ID_NAMESPACE;

    private RuleCommandTree() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 注册规则与编辑器命令；上下文在执行时提供当前服务端状态。
     *
     * @param dispatcher 本次服务端的命令调度器
     * @param context    与服务端宿主绑定的命令能力
     */
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

    /**
     * 向执行者发送反馈，保留组件的本地化与交互。
     *
     * @param context 当前命令执行上下文
     * @param text    完整反馈组件
     * @return 命令成功值 1
     */
    public static int reply(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendSuccess(() -> text, false);
        return 1;
    }

    /**
     * 向执行者发送失败反馈。
     *
     * @param context 当前命令执行上下文
     * @param text    失败原因
     * @return 命令失败值 0
     */
    public static int replyFailure(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendFailure(text);
        return 0;
    }

    /**
     * 提示等待运行时启动。
     *
     * @param context 当前命令执行上下文
     * @return 命令失败值 0
     */
    public static int notReady(CommandContext<CommandSourceStack> context) {
        return replyFailure(context, RuleCommandText.message("not_ready").build());
    }

    /**
     * 提示等待整批保存结束后再读取文件或提交修改。
     *
     * @param context 命令上下文
     * @return 命令失败码
     */
    public static int saveBusy(CommandContext<CommandSourceStack> context) {
        return replyFailure(context, RuleCommandText.message("save_busy").build());
    }

    static String commandPath(String arguments) {
        return "/" + ROOT + " " + arguments;
    }

    static int help(CommandContext<CommandSourceStack> command) {
        reply(command, RuleCommandText.message("help.title").build());
        if (hasAccess(command.getSource())) {
            reply(command, RuleCommandOutput.command("help.rule", commandPath("rule list")));
            reply(command, RuleCommandText.message("help.actions").build());
            if (command.getSource().getEntity() instanceof ServerPlayer) {
                reply(command, RuleCommandOutput.command("help.editor", commandPath("editor open")));
            }
        }
        reply(command, RuleCommandOutput.command("help.status", commandPath("editor status")));
        if (RuleEditPermissions.isAdmin(command.getSource())) {
            reply(command, RuleCommandOutput.command("help.release", commandPath("editor release")));
        }
        return 1;
    }
}
