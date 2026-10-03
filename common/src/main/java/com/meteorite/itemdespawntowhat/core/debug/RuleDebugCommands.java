package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandTree;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 开发场景入口：目录声明驱动注册、默认参数及帮助，新增场景无需修改命令树。 */
public final class RuleDebugCommands {
    private static final String PREFIX = "itemdespawntowhat.command.debug.scene.";

    // 工具类不创建实例。
    private RuleDebugCommands() {}

    // 所有名称由同一 manifest 提供，保持已有字面量补全和权限判断。
    public static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        var functional = Commands.literal("run");
        var benchmarks = Commands.literal("bench");
        DebugScenarioCatalog.entries(false).forEach(spec -> register(functional, context, spec));
        DebugScenarioCatalog.entries(true).forEach(spec -> register(benchmarks, context, spec));
        return Commands.literal("debug")
                .requires(source -> DebugMode.ENABLED && RuleCommandTree.hasAccess(source))
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("examples").executes(ctx -> examples(ctx.getSource())))
                .then(functional).then(benchmarks)
                .then(DebugPipelineCommands.build(context))
                .then(Commands.literal("stop").executes(ctx -> DebugScenarioManager.stop(ctx.getSource(), context)))
                .then(Commands.literal("status").executes(ctx -> DebugScenarioManager.status(ctx.getSource())))
                .then(Commands.literal("mark").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> DebugScenarioManager.mark(ctx.getSource(), StringArgumentType.getString(ctx, "text")))));
    }

    // 功能场景与空负载允许改时长，非空性能负载额外允许改实体数；均使用声明默认值。
    private static void register(LiteralArgumentBuilder<CommandSourceStack> parent, RuleCommandContext context, DebugScenarioSpec spec) {
        var command = Commands.literal(spec.name()).executes(ctx ->
                DebugScenarioManager.start(ctx.getSource(), context, spec.name(), spec.benchmark(), spec.sources(), spec.seconds()));
        if (spec.benchmark() && spec.sources() > 0) {
            command.then(Commands.argument("entities", IntegerArgumentType.integer(1, DebugScenarioSpec.MAX_BENCHMARK_SOURCES))
                    .executes(ctx -> DebugScenarioManager.start(ctx.getSource(), context, spec.name(), true,
                            IntegerArgumentType.getInteger(ctx, "entities"), spec.seconds()))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 300)).executes(ctx ->
                            DebugScenarioManager.start(ctx.getSource(), context, spec.name(), true,
                                    IntegerArgumentType.getInteger(ctx, "entities"), IntegerArgumentType.getInteger(ctx, "seconds")))));
        } else {
            command.then(Commands.argument("seconds", IntegerArgumentType.integer(10, 300)).executes(ctx ->
                    DebugScenarioManager.start(ctx.getSource(), context, spec.name(), spec.benchmark(), spec.sources(),
                            IntegerArgumentType.getInteger(ctx, "seconds"))));
        }
        parent.then(command);
    }

    // 帮助展示控制与日志入口，不另维护一份场景名称列表。
    private static int help(CommandSourceStack source) {
        for (String key : List.of("help.run", "help.examples", "help.bench", "help.control", "help.pipeline", "help.logs")) {
            source.sendSuccess(() -> Component.translatable(PREFIX + key), false);
        }
        return 1;
    }

    // 所有已注册场景自动获得帮助条目，描述使用对应资源标识的本地化键。
    private static int examples(CommandSourceStack source) {
        for (boolean benchmark : List.of(false, true)) {
            for (DebugScenarioSpec spec : DebugScenarioCatalog.entries(benchmark)) {
                String command = "/idtw debug " + spec.key().replace('/', ' ');
                source.sendSuccess(() -> Component.translatable(PREFIX + "example.entry", command,
                        Component.translatable(spec.descriptionKey())), false);
            }
        }
        return 1;
    }
}
