package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** 阶段目录驱动开发命令，单命令启动整阶段，数量与冷却参数分开约束。 */
final class DebugPipelineCommands {
    private static final String PREFIX = "itemdespawntowhat.command.debug.pipeline.";

    // 工具类不创建实例。
    private DebugPipelineCommands() {}

    // 权限与开发环境由父级debug命令统一限制。
    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        var root = Commands.literal("pipeline").executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> DebugPipelineManager.status(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> DebugPipelineManager.stop(ctx.getSource(), context)));
        for (var stage : DebugPipelineCatalog.stages()) {
            var command = Commands.literal(stage.name()).executes(ctx -> DebugPipelineManager.start(
                    ctx.getSource(), context, stage.name(), stage.defaultEntities(), DebugPipelineCatalog.DEFAULT_GAP_SECONDS));
            if (stage.quantity() == DebugPipelineCatalog.Quantity.NONE) {
                command.then(Commands.argument("gap_seconds", IntegerArgumentType.integer(
                        DebugPipelineCatalog.MIN_GAP_SECONDS, DebugPipelineCatalog.MAX_GAP_SECONDS))
                        .executes(ctx -> DebugPipelineManager.start(ctx.getSource(), context, stage.name(), 0,
                                IntegerArgumentType.getInteger(ctx, "gap_seconds"))));
            } else {
                String argument = stage.quantity() == DebugPipelineCatalog.Quantity.CEILING ? "max_entities" : "entities";
                int minimum = stage.quantity() == DebugPipelineCatalog.Quantity.CEILING ? 100 : 1;
                command.then(Commands.argument(argument, IntegerArgumentType.integer(minimum, DebugScenarioSpec.MAX_BENCHMARK_SOURCES))
                        .executes(ctx -> DebugPipelineManager.start(ctx.getSource(), context, stage.name(),
                                IntegerArgumentType.getInteger(ctx, argument), DebugPipelineCatalog.DEFAULT_GAP_SECONDS))
                        .then(Commands.argument("gap_seconds", IntegerArgumentType.integer(
                                DebugPipelineCatalog.MIN_GAP_SECONDS, DebugPipelineCatalog.MAX_GAP_SECONDS))
                                .executes(ctx -> DebugPipelineManager.start(ctx.getSource(), context, stage.name(),
                                        IntegerArgumentType.getInteger(ctx, argument), IntegerArgumentType.getInteger(ctx, "gap_seconds")))));
            }
            root.then(command);
        }
        return root;
    }

    // 帮助及描述使用本地化，新增资源会自动获得命令列表条目。
    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(PREFIX + "help"), false);
        for (var stage : DebugPipelineCatalog.stages()) {
            source.sendSuccess(() -> Component.translatable(PREFIX + "entry", "/idtw debug pipeline " + stage.name(),
                    Component.translatable(PREFIX + "description." + stage.name())), false);
        }
        return 1;
    }
}
