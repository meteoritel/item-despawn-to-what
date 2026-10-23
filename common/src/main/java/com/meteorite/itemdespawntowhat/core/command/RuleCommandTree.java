package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.service.RuleEditPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 正式 /idtw 命令树；配置与查询位于 command，开发诊断由开发源集追加注册。 */
public final class RuleCommandTree {

    // 命令树根节点名，注册到 /idtw 之下
    public static final String ROOT = "idtw";

    private RuleCommandTree() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 把命令树注册到调度器；平台初始化时调用一次
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RuleCommandContext context) {
        // 根节点不统一鉴权：edit-lock status 任何玩家可用，
        // 权限下放到各子分支（config 的写操作与 rule 保留 hasAccess）
        var root = Commands.literal(ROOT)
                .then(RuleConfigCommands.build(context))
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

    // 统一反馈出口：下发可翻译组件，由客户端按玩家语言渲染
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

    /**
     * 提示等待整批保存结束后再读取或重载磁盘规则。
     * @param context 命令上下文
     * @return 命令失败码
     */
    public static int saveBusy(CommandContext<CommandSourceStack> context) {
        return replyFailure(context, RuleCommandText.of("itemdespawntowhat.command.save_busy").build());
    }
}
