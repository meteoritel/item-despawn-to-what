package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 文件校验与规则重载；问题分页与规则查询共用反馈出口。 */
final class RuleMaintenanceCommands {
    private static final String PREFIX = "itemdespawntowhat.command.";

    private RuleMaintenanceCommands() { }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> rule, RuleCommandContext context) {
        rule.then(Commands.literal("validate").executes(ctx -> validate(ctx, context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> validate(ctx, context, IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx, context)));
    }

    private static int validate(CommandContext<CommandSourceStack> ctx, RuleCommandContext context, int page) {
        if (context.editingBusy()) { return RuleCommandTree.saveBusy(ctx); }
        if (!context.available()) { return RuleCommandTree.notReady(ctx); }
        try {
            RuleLoadResult<Rule> result = context.editContext().loadMerged(ctx.getSource().getServer());
            RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + (result.hasErrors()
                    ? "validate.errors" : "validate.passed")).build());
            RuleCommandTree.reply(ctx, RuleCommandText.summary("validate", result));
            List<Issue> issues = orderedIssues(result);
            int feedback = RuleCommandOutput.page(ctx, RuleCommandText.of(PREFIX + "issues.title").build(),
                    issues.size(), page, value -> "/idtw rule validate " + value,
                    index -> RuleCommandText.issue(issues.get(index)));
            return result.hasErrors() ? 0 : feedback;
        } catch (RuntimeException failure) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "read.failed")
                    .arg(Objects.toString(failure.getMessage(), failure.getClass().getSimpleName())).build());
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        if (context.editingBusy()) { return RuleCommandTree.saveBusy(ctx); }
        var outcome = context.reloadRules(ctx.getSource().getServer());
        if (outcome == null) { return RuleCommandTree.notReady(ctx); }
        if (!outcome.isApplied()) {
            Issue failure = outcome.failure();
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "reload.failed")
                    .arg(failure == null ? "" : failure.message()).build());
        }
        RuleLoadResult<Rule> result = Objects.requireNonNull(outcome.result());
        RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + (result.hasErrors()
                ? "reload.partial" : "reload.applied")).build());
        RuleCommandTree.reply(ctx, RuleCommandText.summary("reload", result));
        orderedIssues(result).stream().limit(RuleCommandOutput.PAGE_SIZE)
                .forEach(issue -> RuleCommandTree.reply(ctx, RuleCommandText.issue(issue)));
        if (result.allIssues().size() > RuleCommandOutput.PAGE_SIZE) {
            RuleCommandTree.reply(ctx, RuleCommandOutput.command("issues.more", "/idtw rule validate"));
        }
        return 1;
    }

    private static List<Issue> orderedIssues(RuleLoadResult<Rule> result) {
        return result.allIssues().stream().sorted(Comparator.comparing(Issue::severity)).toList();
    }
}
