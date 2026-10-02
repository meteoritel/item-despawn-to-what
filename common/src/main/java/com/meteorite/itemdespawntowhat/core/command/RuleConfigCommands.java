package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * /idtw config 子命令组：reload / edit / validate / list / convert（规划书 3.10）。
 * 全部只读或经由既有 core/service 能力落盘，命令层不直接写文件（convert 例外，走 RuleConvertService）。
 */
final class RuleConfigCommands {

    private RuleConfigCommands() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 组装 config 分支
    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        return Commands.literal("config")
                .requires(RuleCommandTree::hasAccess)
                .then(Commands.literal("reload").executes(ctx -> reload(ctx, context)))
                .then(Commands.literal("edit").executes(ctx -> edit(ctx, context)))
                .then(Commands.literal("validate").executes(ctx -> validate(ctx, context)))
                .then(Commands.literal("list").executes(ctx -> list(ctx, context)))
                .then(Commands.literal("convert").executes(ctx -> convert(ctx, context)));
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

    // config edit：向发起者发送打开编辑入口的空负载
    private static int edit(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return RuleCommandTree.replyFailure(ctx,
                    RuleCommandText.of("itemdespawntowhat.command.config.edit.player_only").build());
        }
        if (context.editContext().typeRegistries() == null) {
            return RuleCommandTree.notReady(ctx);
        }
        context.editContext().sendTo(player, new OpenRuleEditorPayload());
        return RuleCommandTree.reply(ctx,
                RuleCommandText.of("itemdespawntowhat.command.config.edit.sent").kv("player", player.getName().getString()).build());
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

    // config convert：旧链路配置一次性转成新覆盖层规则（转换前备份到 _old_chain_backup/）
    private static int convert(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        Path overlayRoot = context.editContext().overlayRoot();
        if (overlayRoot == null) {
            return RuleCommandTree.notReady(ctx);
        }
        IssueCollector issues = new IssueCollector();
        RuleConvertService.Report report = RuleConvertService.convert(
                overlayRoot, context.overlayNamespace(), context.overlayVersion(), issues,
                ctx.getSource().getServer(), context.editContext().typeRegistries());

        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.of("itemdespawntowhat.command.config.convert.summary")
                .kv("converted", report.converted())
                .kv("unmapped", report.unmapped().size())
                .kv("backed_up", report.backedUp())
                .kv("written", report.writtenFiles())
                .build());
        for (String note : report.notes()) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.config.convert.note").kv("detail", note).build());
        }
        for (String unmapped : report.unmapped()) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.config.convert.unmapped")
                    .kv("detail", unmapped).build());
        }
        lines.addAll(RuleCommandText.issues(new RuleLoadResult<>(List.of(), issues)));

        // 转换后立即重建索引并回扫，让新规则马上生效（与 GUI 保存后的路径一致）
        if (report.converted() > 0) {
            RuleLoadResult<Rule> reloaded = context.reloadRules(ctx.getSource().getServer());
            if (reloaded != null) {
                lines.add(RuleCommandText.summary("convert", reloaded));
            }
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // 复用编辑层的合并加载（只读，不改变运行时索引）
    private static RuleLoadResult<Rule> load(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        return context.editContext().loadMerged(ctx.getSource().getServer());
    }
}
