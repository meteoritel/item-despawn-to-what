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

// 校验读取文件，重载发布运行状态；反馈明确区分这两个结果。
final class RuleMaintenanceCommands {
    private RuleMaintenanceCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> rule, RuleCommandContext context) {
        rule.then(Commands.literal("validate")
                .executes(command -> validate(command, context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(command -> validate(command, context, IntegerArgumentType.getInteger(command, "page")))));
        rule.then(Commands.literal("reload").executes(command -> reload(command, context)));
    }

    private static int validate(CommandContext<CommandSourceStack> command, RuleCommandContext context, int page) {
        if (context.editingBusy()) {
            return RuleCommandTree.saveBusy(command);
        }
        if (!context.available()) {
            return RuleCommandTree.notReady(command);
        }
        try {
            RuleLoadResult<Rule> result = context.editContext().loadMerged(command.getSource().getServer());
            return showValidation(command, result, page);
        } catch (RuntimeException failure) {
            String reason = Objects.toString(failure.getMessage(), failure.getClass().getSimpleName());
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("read.failed").arg(reason).build());
        }
    }

    private static int showValidation(CommandContext<CommandSourceStack> command, RuleLoadResult<Rule> result, int page) {
        String message = result.hasErrors() ? "validate.errors" : "validate.passed";
        RuleCommandTree.reply(command, RuleCommandText.message(message).build());
        RuleCommandTree.reply(command, RuleCommandText.summary("validate", result));
        List<Issue> issues = orderedIssues(result);
        int feedback = RuleCommandOutput.page(command, RuleCommandText.message("issues.title").build(),
                issues.size(), page, nextPage -> RuleCommandTree.commandPath("rule validate " + nextPage),
                index -> RuleCommandText.issue(issues.get(index)));
        return result.hasErrors() ? 0 : feedback;
    }

    private static int reload(CommandContext<CommandSourceStack> command, RuleCommandContext context) {
        if (context.editingBusy()) {
            return RuleCommandTree.saveBusy(command);
        }
        var outcome = context.reloadRules(command.getSource().getServer());
        if (outcome == null) {
            return RuleCommandTree.notReady(command);
        }
        if (!outcome.isApplied()) {
            Issue failure = outcome.failure();
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("reload.failed")
                    .arg(failure == null ? "" : failure.message()).build());
        }
        RuleLoadResult<Rule> result = Objects.requireNonNull(outcome.result());
        String message = result.hasErrors() ? "reload.partial" : "reload.applied";
        RuleCommandTree.reply(command, RuleCommandText.message(message).build());
        RuleCommandTree.reply(command, RuleCommandText.summary("reload", result));
        previewIssues(command, result);
        return 1;
    }

    private static void previewIssues(CommandContext<CommandSourceStack> command, RuleLoadResult<Rule> result) {
        orderedIssues(result).stream().limit(RuleCommandOutput.PAGE_SIZE)
                .forEach(issue -> RuleCommandTree.reply(command, RuleCommandText.issue(issue)));
        if (result.allIssues().size() > RuleCommandOutput.PAGE_SIZE) {
            RuleCommandTree.reply(command, RuleCommandOutput.command("issues.more", RuleCommandTree.commandPath("rule validate")));
        }
    }

    private static List<Issue> orderedIssues(RuleLoadResult<Rule> result) {
        return result.allIssues().stream().sorted(Comparator.comparing(Issue::severity)).toList();
    }
}
