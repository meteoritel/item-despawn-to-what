package com.meteorite.itemdespawntowhat.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * 新链路命令树根（规划书 3.10）：/idtw config|rule|debug 三组子命令。
 * 与旧链路命令并存（旧 /idtw edit|reload|inspect 保持原样）：
 * Brigadier 对同名子节点执行合并，因此同一 idtw 字面量下新旧子命令同时可用，
 * 且新根节点**不声明 executes**，不会覆盖旧根节点的既有行为。
 */
public final class RuleCommandTree {

    // 新链路命令分组名，注册到 /idtw 之下
    public static final String ROOT = "idtw";

    private RuleCommandTree() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 把新链路命令树注册到调度器；平台初始化时调用一次
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RuleCommandContext context) {
        dispatcher.register(Commands.literal(ROOT)
                .requires(RuleCommandTree::hasAccess)
                .then(RuleConfigCommands.build(context))
                .then(RuleQueryCommands.build(context))
                .then(RuleDebugCommands.build(context)));
    }

    // 权限口径与 core/network 的编辑会话一致：单人世界放行，否则要求权限等级 ≥2（规划书 3.10）
    public static boolean hasAccess(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        return server != null && (server.isSingleplayer() || source.hasPermission(2));
    }

    // 统一反馈出口：下发可翻译组件，由客户端按玩家语言渲染（阶段⑤ i18n 约定）
    static int reply(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendSuccess(() -> text, false);
        return 1;
    }

    // 清单类输出：逐行下发组件，空清单返回 0
    static int replyAll(CommandContext<CommandSourceStack> context, List<Component> lines) {
        for (Component line : lines) {
            context.getSource().sendSuccess(() -> line, false);
        }
        return lines.isEmpty() ? 0 : 1;
    }

    // 失败反馈出口：错误类信息走 sendFailure（客户端渲染为红色），命令返回 0
    static int replyFailure(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendFailure(text);
        return 0;
    }

    // 运行时尚未就绪时的统一提示
    static int notReady(CommandContext<CommandSourceStack> context) {
        return replyFailure(context, RuleCommandText.of("itemdespawntowhat.command.not_ready").build());
    }
}
