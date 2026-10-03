package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.debug.RuleDebugCommands;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/** /idtw 的唯一命令入口；配置与查询位于 command，诊断入口位于 debug。 */
public final class RuleCommandTree {

    // 新链路命令分组名，注册到 /idtw 之下
    public static final String ROOT = "idtw";

    private RuleCommandTree() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 把新链路命令树注册到调度器；平台初始化时调用一次
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RuleCommandContext context) {
        // 根节点不再统一鉴权：契约 §3.7 要求 edit-lock status 任何玩家可用，
        // 权限下放到各子分支（config 的写操作与 rule/debug 全部保留 hasAccess）
        var root = Commands.literal(ROOT)
                .then(RuleConfigCommands.build(context))
                .then(RuleQueryCommands.build(context));
        if (DebugMode.ENABLED) { root.then(RuleDebugCommands.build(context).requires(RuleCommandTree::hasAccess)); }
        dispatcher.register(root);
    }

    // 权限口径与 core/network 的编辑会话一致：单人世界放行，否则要求权限等级 ≥2（规划书 3.10）
    public static boolean hasAccess(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        return server.isSingleplayer() || source.hasPermission(2);
    }

    // 统一反馈出口：下发可翻译组件，由客户端按玩家语言渲染（阶段⑤ i18n 约定）
    public static int reply(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendSuccess(() -> text, false);
        return 1;
    }

    // 清单类输出：逐行下发组件，空清单返回 0
    public static int replyAll(CommandContext<CommandSourceStack> context, List<Component> lines) {
        for (Component line : lines) {
            context.getSource().sendSuccess(() -> line, false);
        }
        return lines.isEmpty() ? 0 : 1;
    }

    // 失败反馈出口：错误类信息走 sendFailure（客户端渲染为红色），命令返回 0
    public static int replyFailure(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendFailure(text);
        return 0;
    }

    // 运行时尚未就绪时的统一提示
    public static int notReady(CommandContext<CommandSourceStack> context) {
        return replyFailure(context, RuleCommandText.of("itemdespawntowhat.command.not_ready").build());
    }
}
