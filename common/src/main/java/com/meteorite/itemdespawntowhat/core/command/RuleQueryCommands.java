package com.meteorite.itemdespawntowhat.core.command;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import com.meteorite.itemdespawntowhat.core.service.RuleSnapshotAssembler;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * /idtw rule 子命令组：list 与 show &lt;id&gt;（规划书 3.10）。
 * show 输出的是覆盖层文件原始 JSON（经 RuleSnapshotAssembler 装配），便于手改后核对。
 */
final class RuleQueryCommands {

    private RuleQueryCommands() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 组装 rule 分支
    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        ArgumentType<ResourceLocation> idArgument = ResourceLocationArgument.id();
        return Commands.literal("rule")
                .requires(RuleCommandTree::hasAccess)
                .then(Commands.literal("list").executes(ctx -> list(ctx, context)))
                .then(Commands.literal("show")
                        .then(Commands.argument("id", idArgument)
                                .executes(ctx -> show(ctx, context,
                                        ResourceLocationArgument.getId(ctx, "id")))));
    }

    // rule list：紧凑清单（id / 来源层 / 是否启用 / 来源标识）
    private static int list(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        RuleLoadResult<Rule> result = load(ctx, context);
        if (result == null) {
            return RuleCommandTree.notReady(ctx);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.summary("rule_list", result));
        for (var entry : result.rules()) {
            Rule rule = entry.value();
            lines.add(RuleCommandText.of("itemdespawntowhat.command.rule.list.entry")
                    .kv("id", rule.id())
                    .kv("layer", entry.origin().layer().name().toLowerCase(java.util.Locale.ROOT))
                    .kv("enabled", rule.enabled())
                    .kv("origin", entry.origin().display())
                    .build());
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // rule show <id>：输出该规则的原始 JSON（覆盖层文件原文优先）
    private static int show(CommandContext<CommandSourceStack> ctx, RuleCommandContext context, ResourceLocation id) {
        RuleLoadResult<Rule> merged = load(ctx, context);
        if (merged == null) {
            return RuleCommandTree.notReady(ctx);
        }
        Rule found = null;
        for (var entry : merged.rules()) {
            if (entry.id().equals(id)) {
                found = entry.value();
                break;
            }
        }
        if (found == null) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(
                    "itemdespawntowhat.command.rule.show.not_found").kv("id", id).build());
        }
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.of("itemdespawntowhat.command.rule.show.header")
                .kv("id", found.id())
                .kv("priority", found.priority())
                .kv("trigger_seconds", found.triggerAfterSeconds())
                .kv("effects", found.effects().size())
                .kv("enabled", found.enabled())
                .build());
        JsonObject raw = rawJson(ctx, context, merged, id);
        if (raw != null) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.rule.show.json")
                    .kv("json", raw.toString()).build());
        } else {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.rule.show.no_raw_json")
                    .kv("id", id).build());
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // 经 RuleSnapshotAssembler 取该规则的原始 JSON（覆盖层原文 / 数据包规则的编码结果）
    private static JsonObject rawJson(CommandContext<CommandSourceStack> ctx, RuleCommandContext context,
                                      RuleLoadResult<Rule> merged, ResourceLocation id) {
        var registries = context.editContext().typeRegistries();
        if (registries == null) {
            return null;
        }
        RuleSnapshot snapshot = RuleSnapshotAssembler.assemble(
                merged.rules(),
                context.editContext().overlayRoot(),
                context.overlayNamespace(),
                context.overlayVersion(),
                registries.effectTypes(),
                registries.conditionTypes(),
                RuleCommandText.issueTexts(merged));
        for (RuleSnapshotEntry entry : snapshot.entries()) {
            // effective 为"当前生效视图"：覆盖层条目即文件原文，数据包条目为模型编码结果
            JsonObject rule = entry.effective();
            if (rule == null) {
                continue;
            }
            if (rule.has("id") && id.toString().equals(rule.get("id").getAsString())) {
                return rule;
            }
        }
        return null;
    }

    // 复用编辑层的合并加载（只读）
    private static RuleLoadResult<Rule> load(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        return context.editContext().loadMerged(ctx.getSource().getServer());
    }
}
