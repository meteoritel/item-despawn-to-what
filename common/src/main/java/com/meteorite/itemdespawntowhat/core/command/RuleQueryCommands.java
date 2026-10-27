package com.meteorite.itemdespawntowhat.core.command;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler;
import com.meteorite.itemdespawntowhat.core.service.RuleEnableApplyService;
import com.meteorite.itemdespawntowhat.core.service.RuleSnapshotAssembler;
import com.meteorite.itemdespawntowhat.core.state.RuleEnableState;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /idtw rule 子命令组：list / show &lt;id&gt; / &lt;id&gt; enable|disable|default / orphans [clear]。
 * show 输出的是覆盖层文件原始 JSON（经 RuleSnapshotAssembler 装配），便于手改后核对；
 * 启停与恢复默认只改本服启用状态，随后按保存链路同一路径重建索引并回扫全部维度。
 */
final class RuleQueryCommands {

    private RuleQueryCommands() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 组装 rule 分支：查询 / 单条启停与本服状态 / 无主记录查看与清理
    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        ArgumentType<ResourceLocation> idArgument = ResourceLocationArgument.id();
        return Commands.literal("rule")
                .requires(RuleCommandTree::hasAccess)
                .then(Commands.literal("list").executes(ctx -> list(ctx, context)))
                .then(Commands.literal("show")
                        .then(Commands.argument("id", idArgument)
                                .executes(ctx -> show(ctx, context,
                                        ResourceLocationArgument.getId(ctx, "id")))))
                .then(Commands.literal("orphans")
                        .executes(ctx -> orphans(ctx, context))
                        .then(Commands.literal("clear").executes(ctx -> clearOrphans(ctx, context))))
                .then(Commands.argument("id", idArgument)
                        .then(Commands.literal("enable")
                                .executes(ctx -> setEnabled(ctx, context,
                                        ResourceLocationArgument.getId(ctx, "id"), true)))
                        .then(Commands.literal("disable")
                                .executes(ctx -> setEnabled(ctx, context,
                                        ResourceLocationArgument.getId(ctx, "id"), false)))
                        .then(Commands.literal("default")
                                .executes(ctx -> restoreDefault(ctx, context,
                                        ResourceLocationArgument.getId(ctx, "id")))));
    }

    // rule list：紧凑清单（id / 来源层 / 生效启用 / 来源标识）
    private static int list(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        if (RuleEditServerHandler.isSaving()) { return RuleCommandTree.saveBusy(ctx); }
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
        if (RuleEditServerHandler.isSaving()) { return RuleCommandTree.saveBusy(ctx); }
        RuleLoadResult<Rule> merged = load(ctx, context);
        if (merged == null) {
            return RuleCommandTree.notReady(ctx);
        }
        Rule found = find(merged, id);
        if (found == null) {
            return notFound(ctx, id);
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

    // rule <id> enable|disable：写本服启用状态，随后重建索引并回扫
    private static int setEnabled(CommandContext<CommandSourceStack> ctx, RuleCommandContext context,
                                  ResourceLocation id, boolean enabled) {
        if (RuleEditServerHandler.isSaving()) { return RuleCommandTree.saveBusy(ctx); }
        MinecraftServer server = ctx.getSource().getServer();
        RuleLoadResult<Rule> merged = load(ctx, context);
        if (merged == null) {
            return RuleCommandTree.notReady(ctx);
        }
        // 只接受现存规则：否则写入会立刻变成无主记录，通常意味着 id 打错
        if (find(merged, id) == null) {
            return notFound(ctx, id);
        }
        Component success = RuleCommandText.of(enabled
                ? "itemdespawntowhat.command.rule.enable.enabled"
                : "itemdespawntowhat.command.rule.enable.disabled").kv("id", id).build();
        return applyAndReply(ctx, context, server,
                List.of(new RuleEdit(id, enabled ? RuleEdit.Action.ENABLE : RuleEdit.Action.DISABLE, null)),
                success, null);
    }

    // rule <id> default：删除本服记录，使该规则重新继承默认启用
    private static int restoreDefault(CommandContext<CommandSourceStack> ctx, RuleCommandContext context,
                                      ResourceLocation id) {
        if (RuleEditServerHandler.isSaving()) { return RuleCommandTree.saveBusy(ctx); }
        MinecraftServer server = ctx.getSource().getServer();
        RuleLoadResult<Rule> merged = load(ctx, context);
        if (merged == null) {
            return RuleCommandTree.notReady(ctx);
        }
        if (find(merged, id) == null) {
            return notFound(ctx, id);
        }
        return applyAndReply(ctx, context, server,
                List.of(new RuleEdit(id, RuleEdit.Action.RESET, null)),
                RuleCommandText.of("itemdespawntowhat.command.rule.enable.default").kv("id", id).build(),
                RuleCommandText.of("itemdespawntowhat.command.rule.enable.default.none").kv("id", id).build());
    }

    // rule orphans：列出已没有对应规则的本服启用记录（加载时被忽略）
    private static int orphans(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        if (RuleEditServerHandler.isSaving()) { return RuleCommandTree.saveBusy(ctx); }
        // 运行时未就绪时规则集合不可信，若照常判定会把全部记录误报成无主
        if (!context.available()) { return RuleCommandTree.notReady(ctx); }
        RuleLoadResult<Rule> merged = load(ctx, context);
        if (merged == null) {
            return RuleCommandTree.notReady(ctx);
        }
        RuleEnableState state = RuleEnableState.get(ctx.getSource().getServer().overworld());
        List<ResourceLocation> orphans = state.orphans(existingIds(merged));
        if (orphans.isEmpty()) {
            return RuleCommandTree.reply(ctx, RuleCommandText.of(
                    "itemdespawntowhat.command.rule.orphans.none").build());
        }
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.of("itemdespawntowhat.command.rule.orphans.header")
                .kv("count", orphans.size()).build());
        for (ResourceLocation id : orphans) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.rule.orphans.entry")
                    .kv("id", id)
                    .kv("enabled", state.overrideFor(id))
                    .build());
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // rule orphans clear：只清理无主记录，不影响仍存在规则的启停状态
    private static int clearOrphans(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        if (RuleEditServerHandler.isSaving()) { return RuleCommandTree.saveBusy(ctx); }
        if (!context.available()) { return RuleCommandTree.notReady(ctx); }
        RuleLoadResult<Rule> merged = load(ctx, context);
        if (merged == null) {
            return RuleCommandTree.notReady(ctx);
        }
        RuleEnableState state = RuleEnableState.get(ctx.getSource().getServer().overworld());
        int removed = state.clearOrphans(existingIds(merged));
        return RuleCommandTree.reply(ctx, RuleCommandText.of(
                "itemdespawntowhat.command.rule.orphans.cleared").kv("count", removed).build());
    }

    // 状态写入与轻量重建共用 RuleEnableApplyService：不重读数据包，索引重建后由运行时发布并排队回扫
    private static int applyAndReply(CommandContext<CommandSourceStack> ctx, RuleCommandContext context,
                                     MinecraftServer server, List<RuleEdit> edits,
                                     Component success, @Nullable Component noChange) {
        RuleEnableApplyService.Outcome outcome = RuleEnableApplyService.apply(
                server, context.editContext(), edits);
        if (outcome.changed() == 0) {
            return RuleCommandTree.reply(ctx, noChange == null ? success : noChange);
        }
        if (outcome.publication() == null) {
            // 状态已随存档保存，但运行时上下文不可用：明确告知本次没有进入运行时
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(
                    "itemdespawntowhat.command.rule.enable.not_applied").kv("message", "").build());
        }
        return RuleCommandTree.reply(ctx, success);
    }

    // 规则不存在时的统一反馈（启停与详情共用）
    private static int notFound(CommandContext<CommandSourceStack> ctx, ResourceLocation id) {
        return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(
                "itemdespawntowhat.command.rule.show.not_found").kv("id", id).build());
    }

    // 按 id 找生效规则；找不到时返回 null
    private static @Nullable Rule find(RuleLoadResult<Rule> merged, ResourceLocation id) {
        for (var entry : merged.rules()) {
            if (entry.id().equals(id)) {
                return entry.value();
            }
        }
        return null;
    }

    // 现存规则 id 集合：无主判定以合并加载结果为准
    private static List<ResourceLocation> existingIds(RuleLoadResult<Rule> merged) {
        List<ResourceLocation> ids = new ArrayList<>(merged.rules().size());
        for (var entry : merged.rules()) {
            ids.add(entry.id());
        }
        return ids;
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
                RuleCommandText.issueTexts(merged),
                RuleEnableState.get(ctx.getSource().getServer().overworld()));
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
