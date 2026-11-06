package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleOrigin;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.model.Rule;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** 规则查询与本服启停；普通查询只消费已发布视图，文件读取集中于显式来源与维护命令。 */
final class RuleQueryCommands {
    private static final String PREFIX = "itemdespawntowhat.command.";
    private static final int RAW_LINE_CODE_POINTS = 1200;

    private RuleQueryCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        var rule = Commands.literal("rule").requires(RuleCommandTree::hasAccess)
                .executes(RuleCommandTree::help)
                .then(Commands.literal("list").executes(ctx -> list(ctx, context, 1, ""))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx, context, page(ctx), ""))
                                .then(Commands.argument("filter", StringArgumentType.greedyString())
                                        .executes(ctx -> list(ctx, context, page(ctx), StringArgumentType.getString(ctx, "filter"))))))
                .then(Commands.literal("show")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> suggest(builder, context, null))
                                .executes(ctx -> show(ctx, context))
                                .then(Commands.literal("raw").executes(ctx -> raw(ctx, context, 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> raw(ctx, context, page(ctx)))))))
                .then(Commands.literal("orphans").executes(ctx -> orphans(ctx, context, false, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> orphans(ctx, context, false, page(ctx))))
                        .then(Commands.literal("clear").executes(ctx -> orphans(ctx, context, true, 1))));
        action(rule, context, "enable", RuleEdit.Action.ENABLE);
        action(rule, context, "disable", RuleEdit.Action.DISABLE);
        action(rule, context, "reset", RuleEdit.Action.RESET);
        RuleMaintenanceCommands.attach(rule, context);
        return rule;
    }

    private static void action(LiteralArgumentBuilder<CommandSourceStack> rule, RuleCommandContext context,
                               String name, RuleEdit.Action action) {
        rule.then(Commands.literal(name).then(Commands.argument("id", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> suggest(builder, context, action))
                .executes(ctx -> apply(ctx, context, ResourceLocationArgument.getId(ctx, "id"), action))));
    }

    private static int page(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "page");
    }

    private static int list(CommandContext<CommandSourceStack> ctx, RuleCommandContext context, int page, String filter) {
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) { return RuleCommandTree.notReady(ctx); }
        String query = filter.toLowerCase(Locale.ROOT);
        List<LoadedRule<Rule>> entries = result.rules().stream()
                .filter(entry -> entry.id().toString().contains(query)
                        || entry.value().displayName() != null && entry.value().displayName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(entry -> entry.id().toString())).toList();
        Map<ResourceLocation, Boolean> overrides = context.publishedOverrides();
        long enabled = entries.stream().filter(entry -> entry.value().enabled()).count();
        Component header = RuleCommandText.of(PREFIX + "rule.list.header").arg(entries.size())
                .arg(enabled).arg(entries.size() - enabled).arg(filter).build();
        return RuleCommandOutput.page(ctx, header, entries.size(), page,
                value -> "/idtw rule list " + value + (filter.isEmpty() ? "" : " " + filter), index -> {
                    LoadedRule<Rule> entry = entries.get(index);
                    var line = RuleCommandText.of(PREFIX + "rule.list.entry")
                            .arg(RuleCommandText.state(entry.value().enabled()))
                            .arg(overrides.get(entry.id()) == null ? Component.empty()
                                    : RuleCommandText.of(PREFIX + "rule.override_badge").build())
                            .arg(name(entry.value())).arg(entry.id()).build();
                    return RuleCommandOutput.inspect(line, detail(entry, overrides.get(entry.id())),
                                    "/idtw rule show " + entry.id()).copy()
                            .withStyle(entry.value().enabled() ? ChatFormatting.WHITE : ChatFormatting.GRAY);
                });
    }

    private static int show(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) { return RuleCommandTree.notReady(ctx); }
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        RuleSourceIndex.Entry source = result.sourceIndex().find(id);
        if (source == null) { return notFound(ctx, id); }
        LoadedRule<Rule> entry = find(result, id);
        RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.show.title")
                .arg(entry == null ? Component.empty() : name(entry.value())).arg(id).build());
        RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.show.source")
                .arg(origin(winner(source).origin())).build());
        if (entry == null) {
            RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.show.invalid").build());
        } else {
            Rule rule = entry.value();
            Boolean override = context.publishedOverrides().get(id);
            var declaredEnabled = winner(source).body().get("enabled");
            boolean defaultEnabled = declaredEnabled == null || declaredEnabled.isJsonNull() || declaredEnabled.getAsBoolean();
            RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.show.state")
                    .arg(RuleCommandText.state(rule.enabled()))
                    .arg(RuleCommandText.state(defaultEnabled)).arg(RuleCommandText.state(override)).build());
            var triggers = Component.empty();
            rule.effectiveTriggers().forEach(trigger -> {
                if (!triggers.getSiblings().isEmpty()) { triggers.append(" / "); }
                triggers.append(RuleCommandText.of(PREFIX + "trigger." + trigger.key()).build());
            });
            RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.show.structure")
                    .arg(rule.priority()).arg(triggers).arg(rule.triggerAfterSeconds())
                    .arg(rule.effectiveOutcomes().size()).arg(rule.allEffects().size()).build());
        }
        return RuleCommandTree.reply(ctx, RuleCommandOutput.command("rule.show.raw_link", "/idtw rule show " + id + " raw"));
    }

    private static int raw(CommandContext<CommandSourceStack> ctx, RuleCommandContext context, int page) {
        if (context.editingBusy()) { return RuleCommandTree.saveBusy(ctx); }
        var service = context.editContext().editService();
        if (!context.available() || service == null) { return RuleCommandTree.notReady(ctx); }
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        service.readRawRule(context.editContext(), ctx.getSource().getServer(), id, result -> {
            switch (result.status()) {
                case SUCCESS -> {
                    String text = result.json();
                    int points = text.codePointCount(0, text.length());
                    int lines = (points + RAW_LINE_CODE_POINTS - 1) / RAW_LINE_CODE_POINTS;
                    RuleCommandOutput.page(ctx, RuleCommandText.of(PREFIX + "rule.show.raw_source")
                                    .arg(id).arg(result.origin()).build(), lines, page,
                            value -> "/idtw rule show " + id + " raw " + value, index -> {
                                int start = text.offsetByCodePoints(0, index * RAW_LINE_CODE_POINTS);
                                int end = text.offsetByCodePoints(start, Math.min(RAW_LINE_CODE_POINTS, points - index * RAW_LINE_CODE_POINTS));
                                String fragment = text.substring(start, end);
                                return Component.literal(fragment).withStyle(style -> style
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fragment))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                RuleCommandText.of(PREFIX + "rule.show.copy").build())));
                            });
                }
                case INVALID_REQUEST -> notFound(ctx, id);
                default -> RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "rule.show.failed").arg(id).build());
            }
        });
        return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.show.accepted").arg(id).build());
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, RuleCommandContext context,
                              ResourceLocation id, RuleEdit.Action action) {
        if (context.editingBusy()) { return RuleCommandTree.saveBusy(ctx); }
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) { return RuleCommandTree.notReady(ctx); }
        if (find(result, id) == null) { return notFound(ctx, id); }
        var outcome = RuleEnableApplyService.apply(ctx.getSource().getServer(), context.editContext(),
                List.of(new RuleEdit(id, action, null)), result.sourceIndex(), result.allIssues());
        if (outcome.changed() != 0 && outcome.publication() == null) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "rule.enable.not_applied").build());
        }
        String status = outcome.changed() == 0 ? "unchanged" : action.name().toLowerCase(Locale.ROOT);
        RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.enable." + status).arg(id).build());
        return showState(ctx, context, id);
    }

    private static int showState(CommandContext<CommandSourceStack> ctx, RuleCommandContext context, ResourceLocation id) {
        var result = context.publishedRules();
        var entry = result == null ? null : find(result, id);
        if (entry == null) { return 1; }
        return RuleCommandTree.reply(ctx, detail(entry, context.publishedOverrides().get(id)));
    }

    private static int orphans(CommandContext<CommandSourceStack> ctx, RuleCommandContext context, boolean clear, int page) {
        if (context.editingBusy()) { return RuleCommandTree.saveBusy(ctx); }
        var service = context.editContext().editService();
        if (!context.available() || service == null) { return RuleCommandTree.notReady(ctx); }
        service.readRuleSources(context.editContext(), ctx.getSource().getServer(), read -> {
            if (read == null || read.issues().hasErrors()) {
                RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "rule.orphans.read_failed").build());
                return;
            }
            Set<ResourceLocation> existing = read.index().entries().stream().map(RuleSourceIndex.Entry::id).collect(Collectors.toSet());
            RuleEnableState state = RuleEnableState.get(ctx.getSource().getServer().overworld());
            if (clear) {
                RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.orphans.cleared")
                        .arg(state.clearOrphans(existing)).build());
            } else {
                List<ResourceLocation> ids = state.orphans(existing);
                RuleCommandOutput.page(ctx, RuleCommandText.of(PREFIX + "rule.orphans.header").arg(ids.size()).build(),
                        ids.size(), page, value -> "/idtw rule orphans " + value,
                        index -> RuleCommandText.of(PREFIX + "rule.orphans.entry").arg(ids.get(index))
                                .arg(RuleCommandText.state(state.overrideFor(ids.get(index)))).build());
            }
        });
        return RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "rule.orphans.accepted").build());
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder,
                                                           RuleCommandContext context, @Nullable RuleEdit.Action action) {
        RuleLoadResult<Rule> result = context.publishedRules();
        if (result == null) { return builder.buildFuture(); }
        Map<ResourceLocation, LoadedRule<Rule>> valid = result.rules().stream()
                .collect(Collectors.toMap(LoadedRule::id, entry -> entry));
        Map<ResourceLocation, Boolean> overrides = context.publishedOverrides();
        var entries = result.sourceIndex().entries().stream()
                .filter(entry -> action == null || valid.containsKey(entry.id()))
                .filter(entry -> action != RuleEdit.Action.RESET || overrides.get(entry.id()) != null).toList();
        return SharedSuggestionProvider.suggestResource(entries, builder, RuleSourceIndex.Entry::id, entry -> {
            LoadedRule<Rule> loaded = valid.get(entry.id());
            return loaded == null ? RuleCommandText.of(PREFIX + "rule.show.invalid").build()
                    : detail(loaded, overrides.get(entry.id()));
        });
    }

    private static Component name(Rule rule) {
        return rule.displayName() == null ? Component.empty() : Component.literal(rule.displayName());
    }

    private static Component detail(LoadedRule<Rule> entry, @Nullable Boolean override) {
        return RuleCommandText.of(PREFIX + "rule.detail").arg(name(entry.value())).arg(entry.id())
                .arg(RuleCommandText.state(entry.value().enabled())).arg(RuleCommandText.state(override))
                .arg(origin(entry.origin())).build();
    }

    private static Component origin(RuleOrigin origin) {
        return RuleCommandText.layer(origin.layer()).copy().append(" ")
                .append(origin.packId() == null ? "" : "[" + origin.packId() + "] ").append(origin.path());
    }

    private static RawRuleEntry winner(RuleSourceIndex.Entry entry) {
        return entry.overlay() != null ? entry.overlay() : entry.base();
    }

    private static @Nullable LoadedRule<Rule> find(RuleLoadResult<Rule> result, ResourceLocation id) {
        return result.rules().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }

    private static int notFound(CommandContext<CommandSourceStack> ctx, ResourceLocation id) {
        return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "rule.show.not_found").arg(id).build());
    }
}
