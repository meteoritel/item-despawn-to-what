package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleOrigin;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.service.RuleEditService.RawRuleResult;
import com.meteorite.itemdespawntowhat.core.service.RuleEnableApplyService;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import com.meteorite.itemdespawntowhat.core.state.RuleEnableState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// 普通查询使用发布视图，raw 与孤儿检查显式读取来源，避免把未应用的文件改动展示为运行状态。
final class RuleQueryCommands {
    private static final int RAW_FRAGMENT_CODE_POINTS = 1200;

    private RuleQueryCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        var rule = Commands.literal("rule")
                .requires(RuleCommandTree::hasAccess)
                .executes(RuleCommandTree::help)
                .then(buildList(context))
                .then(buildShow(context))
                .then(buildOrphans(context))
                .then(buildEnableAction(context, RuleEdit.Action.ENABLE))
                .then(buildEnableAction(context, RuleEdit.Action.DISABLE))
                .then(buildEnableAction(context, RuleEdit.Action.RESET));
        RuleMaintenanceCommands.attach(rule, context);
        return rule;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildList(RuleCommandContext context) {
        return Commands.literal("list")
                .executes(command -> list(command, context, 1, ""))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(command -> list(command, context, pageNumber(command), ""))
                        .then(Commands.argument("filter", StringArgumentType.greedyString())
                                .executes(command -> list(command, context, pageNumber(command),
                                        StringArgumentType.getString(command, "filter")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildShow(RuleCommandContext context) {
        var raw = Commands.literal("raw")
                .executes(command -> readRaw(command, context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(command -> readRaw(command, context, pageNumber(command))));
        return Commands.literal("show")
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((command, builder) -> suggestIds(builder, context, null))
                        .executes(command -> show(command, context))
                        .then(raw));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildOrphans(RuleCommandContext context) {
        return Commands.literal("orphans")
                .executes(command -> listOrphans(command, context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(command -> listOrphans(command, context, pageNumber(command))))
                .then(Commands.literal("clear").executes(command -> clearOrphans(command, context)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildEnableAction(
            RuleCommandContext context, RuleEdit.Action action) {
        return Commands.literal(action.name().toLowerCase(Locale.ROOT))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((command, builder) -> suggestIds(builder, context, action))
                        .executes(command -> applyEnableAction(command, context, action)));
    }

    private static int pageNumber(CommandContext<CommandSourceStack> command) {
        return IntegerArgumentType.getInteger(command, "page");
    }

    private static int list(CommandContext<CommandSourceStack> command, RuleCommandContext context,
                            int page, String filter) {
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) {
            return RuleCommandTree.notReady(command);
        }
        String query = filter.toLowerCase(Locale.ROOT);
        List<LoadedRule<Rule>> entries = result.rules().stream()
                .filter(entry -> matchesFilter(entry, query))
                .sorted(Comparator.comparing(entry -> entry.id().toString()))
                .toList();
        Map<ResourceLocation, Boolean> overrides = context.publishedOverrides();
        long enabledCount = entries.stream().filter(entry -> entry.value().enabled()).count();
        Component header = RuleCommandText.message("rule.list.header")
                .arg(entries.size()).arg(enabledCount).arg(entries.size() - enabledCount).arg(filter).build();
        return RuleCommandOutput.page(command, header, entries.size(), page,
                nextPage -> RuleCommandTree.commandPath("rule list " + nextPage + (filter.isEmpty() ? "" : " " + filter)),
                index -> listEntry(entries.get(index), overrides.get(entries.get(index).id())));
    }

    private static boolean matchesFilter(LoadedRule<Rule> entry, String query) {
        String displayName = entry.value().displayName();
        return entry.id().toString().contains(query)
                || displayName != null && displayName.toLowerCase(Locale.ROOT).contains(query);
    }

    private static Component listEntry(LoadedRule<Rule> entry, @Nullable Boolean override) {
        Component badge = override == null ? Component.empty() : RuleCommandText.message("rule.override_badge").build();
        Component line = RuleCommandText.message("rule.list.entry")
                .arg(RuleCommandText.state(entry.value().enabled())).arg(badge)
                .arg(displayName(entry.value())).arg(entry.id()).build();
        return RuleCommandOutput.inspect(line, ruleDetail(entry, override), showCommand(entry.id()))
                .withStyle(entry.value().enabled() ? ChatFormatting.WHITE : ChatFormatting.GRAY);
    }

    private static int show(CommandContext<CommandSourceStack> command, RuleCommandContext context) {
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) {
            return RuleCommandTree.notReady(command);
        }
        ResourceLocation id = ResourceLocationArgument.getId(command, "id");
        RuleSourceIndex.Entry source = result.sourceIndex().find(id);
        if (source == null) {
            return notFound(command, id);
        }
        LoadedRule<Rule> entry = findRule(result, id);
        RuleCommandTree.reply(command, RuleCommandText.message("rule.show.title")
                .arg(entry == null ? Component.empty() : displayName(entry.value())).arg(id).build());
        RawRuleEntry winner = winningSource(source);
        RuleCommandTree.reply(command, RuleCommandText.message("rule.show.source").arg(sourceLabel(winner.origin())).build());
        if (entry == null) {
            RuleCommandTree.reply(command, RuleCommandText.message("rule.show.invalid").build());
        } else {
            RuleCommandTree.reply(command, enableState(entry.value(), winner, context.publishedOverrides().get(id)));
            RuleCommandTree.reply(command, ruleStructure(entry.value()));
        }
        return RuleCommandTree.reply(command, RuleCommandOutput.command("rule.show.raw_link", showCommand(id) + " raw"));
    }

    private static Component enableState(Rule rule, RawRuleEntry source, @Nullable Boolean override) {
        var declaredEnabled = source.body().get("enabled");
        boolean defaultEnabled = declaredEnabled == null || declaredEnabled.isJsonNull() || declaredEnabled.getAsBoolean();
        return RuleCommandText.message("rule.show.state")
                .arg(RuleCommandText.state(rule.enabled()))
                .arg(RuleCommandText.state(defaultEnabled)).arg(RuleCommandText.state(override)).build();
    }

    private static Component ruleStructure(Rule rule) {
        var triggers = Component.empty();
        for (var trigger : rule.effectiveTriggers()) {
            if (!triggers.getSiblings().isEmpty()) {
                triggers.append(" / ");
            }
            triggers.append(RuleCommandText.message("trigger." + trigger.key()).build());
        }
        return RuleCommandText.message("rule.show.structure")
                .arg(rule.priority()).arg(triggers).arg(rule.triggerAfterSeconds())
                .arg(rule.effectiveOutcomes().size()).arg(rule.allEffects().size()).build();
    }

    private static int readRaw(CommandContext<CommandSourceStack> command, RuleCommandContext context, int page) {
        if (context.editingBusy()) {
            return RuleCommandTree.saveBusy(command);
        }
        var service = context.editContext().editService();
        if (!context.available() || service == null) {
            return RuleCommandTree.notReady(command);
        }
        ResourceLocation id = ResourceLocationArgument.getId(command, "id");
        service.readRawRule(context.editContext(), command.getSource().getServer(), id,
                result -> replyRawResult(command, id, page, result));
        return RuleCommandTree.reply(command, RuleCommandText.message("rule.show.accepted").arg(id).build());
    }

    private static void replyRawResult(CommandContext<CommandSourceStack> command, ResourceLocation id,
                                       int page, RawRuleResult result) {
        switch (result.status()) {
            case SUCCESS -> showRawPage(command, id, page, result);
            case INVALID_REQUEST -> notFound(command, id);
            default ->
                    RuleCommandTree.replyFailure(command, RuleCommandText.message("rule.show.failed").arg(id).build());
        }
    }

    private static void showRawPage(CommandContext<CommandSourceStack> command, ResourceLocation id,
                                    int page, RawRuleResult result) {
        String json = result.json();
        int codePointCount = json.codePointCount(0, json.length());
        int fragmentCount = (codePointCount + RAW_FRAGMENT_CODE_POINTS - 1) / RAW_FRAGMENT_CODE_POINTS;
        Component header = RuleCommandText.message("rule.show.raw_source").arg(id).arg(result.origin()).build();
        RuleCommandOutput.page(command, header, fragmentCount, page,
                nextPage -> showCommand(id) + " raw " + nextPage,
                index -> rawFragment(json, codePointCount, index));
    }

    private static Component rawFragment(String json, int codePointCount, int index) {
        // 按 Unicode code point 切分，保证复制后的片段能按顺序还原完整 JSON。
        int firstCodePoint = index * RAW_FRAGMENT_CODE_POINTS;
        int start = json.offsetByCodePoints(0, firstCodePoint);
        int end = json.offsetByCodePoints(start, Math.min(RAW_FRAGMENT_CODE_POINTS, codePointCount - firstCodePoint));
        return RuleCommandOutput.copyable(json.substring(start, end), RuleCommandText.message("rule.show.copy").build());
    }

    private static int applyEnableAction(CommandContext<CommandSourceStack> command, RuleCommandContext context,
                                         RuleEdit.Action action) {
        if (context.editingBusy()) {
            return RuleCommandTree.saveBusy(command);
        }
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) {
            return RuleCommandTree.notReady(command);
        }
        ResourceLocation id = ResourceLocationArgument.getId(command, "id");
        if (findRule(result, id) == null) {
            return notFound(command, id);
        }
        var outcome = RuleEnableApplyService.apply(command.getSource().getServer(), context.editContext(),
                List.of(new RuleEdit(id, action, null)), result.sourceIndex(), result.allIssues());
        if (outcome.changed() != 0 && outcome.publication() == null) {
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("rule.enable.not_applied").build());
        }
        String status = outcome.changed() == 0 ? "unchanged" : action.name().toLowerCase(Locale.ROOT);
        RuleCommandTree.reply(command, RuleCommandText.message("rule.enable." + status).arg(id).build());
        return showEnableState(command, context, id);
    }

    private static int showEnableState(CommandContext<CommandSourceStack> command, RuleCommandContext context,
                                       ResourceLocation id) {
        RuleLoadResult<Rule> result = context.publishedRules();
        LoadedRule<Rule> entry = result == null ? null : findRule(result, id);
        if (entry == null) {
            return 1;
        }
        return RuleCommandTree.reply(command, ruleDetail(entry, context.publishedOverrides().get(id)));
    }

    private static int listOrphans(CommandContext<CommandSourceStack> command, RuleCommandContext context, int page) {
        return readRuleIds(command, context, existingIds -> {
            RuleEnableState state = RuleEnableState.get(command.getSource().getServer().overworld());
            List<ResourceLocation> ids = state.orphans(existingIds);
            Component header = RuleCommandText.message("rule.orphans.header").arg(ids.size()).build();
            RuleCommandOutput.page(command, header, ids.size(), page,
                    nextPage -> RuleCommandTree.commandPath("rule orphans " + nextPage),
                    index -> RuleCommandText.message("rule.orphans.entry").arg(ids.get(index))
                            .arg(RuleCommandText.state(state.overrideFor(ids.get(index)))).build());
        });
    }

    private static int clearOrphans(CommandContext<CommandSourceStack> command, RuleCommandContext context) {
        return readRuleIds(command, context, existingIds -> {
            RuleEnableState state = RuleEnableState.get(command.getSource().getServer().overworld());
            RuleCommandTree.reply(command, RuleCommandText.message("rule.orphans.cleared")
                    .arg(state.clearOrphans(existingIds)).build());
        });
    }

    private static int readRuleIds(CommandContext<CommandSourceStack> command, RuleCommandContext context,
                                   Consumer<Set<ResourceLocation>> completion) {
        if (context.editingBusy()) {
            return RuleCommandTree.saveBusy(command);
        }
        var service = context.editContext().editService();
        if (!context.available() || service == null) {
            return RuleCommandTree.notReady(command);
        }
        service.readRuleSources(context.editContext(), command.getSource().getServer(), read -> {
            if (read == null || read.issues().hasErrors()) {
                RuleCommandTree.replyFailure(command, RuleCommandText.message("rule.orphans.read_failed").build());
                return;
            }
            // 语义非法的规则仍拥有 ID，避免将它的启停记录误清理为孤儿。
            Set<ResourceLocation> existingIds = read.index().entries().stream()
                    .map(RuleSourceIndex.Entry::id).collect(Collectors.toSet());
            completion.accept(existingIds);
        });
        return RuleCommandTree.reply(command, RuleCommandText.message("rule.orphans.accepted").build());
    }

    private static CompletableFuture<Suggestions> suggestIds(SuggestionsBuilder builder,
                                                             RuleCommandContext context, @Nullable RuleEdit.Action action) {
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) {
            return builder.buildFuture();
        }
        Map<ResourceLocation, LoadedRule<Rule>> validRules = result.rules().stream()
                .collect(Collectors.toMap(LoadedRule::id, entry -> entry));
        Map<ResourceLocation, Boolean> overrides = context.publishedOverrides();
        var entries = result.sourceIndex().entries().stream()
                .filter(entry -> action == null || validRules.containsKey(entry.id()))
                .filter(entry -> action != RuleEdit.Action.RESET || overrides.containsKey(entry.id()))
                .toList();
        return SharedSuggestionProvider.suggestResource(entries, builder, RuleSourceIndex.Entry::id, entry -> {
            LoadedRule<Rule> loaded = validRules.get(entry.id());
            return loaded == null ? RuleCommandText.message("rule.show.invalid").build()
                    : ruleDetail(loaded, overrides.get(entry.id()));
        });
    }

    private static Component displayName(Rule rule) {
        return rule.displayName() == null ? Component.empty() : Component.literal(rule.displayName());
    }

    private static Component ruleDetail(LoadedRule<Rule> entry, @Nullable Boolean override) {
        return RuleCommandText.message("rule.detail").arg(displayName(entry.value())).arg(entry.id())
                .arg(RuleCommandText.state(entry.value().enabled())).arg(RuleCommandText.state(override))
                .arg(sourceLabel(entry.origin())).build();
    }

    private static Component sourceLabel(RuleOrigin origin) {
        var label = RuleCommandText.layer(origin.layer()).copy().append(" ");
        if (origin.packId() != null) {
            label.append("[" + origin.packId() + "] ");
        }
        return label.append(origin.path());
    }

    private static RawRuleEntry winningSource(RuleSourceIndex.Entry entry) {
        return entry.overlay() != null ? entry.overlay() : entry.base();
    }

    private static @Nullable LoadedRule<Rule> findRule(RuleLoadResult<Rule> result, ResourceLocation id) {
        return result.rules().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }

    private static String showCommand(ResourceLocation id) {
        return RuleCommandTree.commandPath("rule show " + id);
    }

    private static int notFound(CommandContext<CommandSourceStack> command, ResourceLocation id) {
        return RuleCommandTree.replyFailure(command, RuleCommandText.message("rule.show.not_found").arg(id).build());
    }
}
