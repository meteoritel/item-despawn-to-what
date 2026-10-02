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

/** 开发专用场景入口；指令准备实际负载，过程与结果自动输出到 IDEA 控制台。 */
public final class RuleDebugCommands {
    private static final String PREFIX = "itemdespawntowhat.command.debug.scene.";

    // 工具类不创建实例。
    private RuleDebugCommands() {}

    // 场景名称使用固定字面量，游戏内可直接补全。
    public static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        var scenarios = Commands.literal("run");
        for (String name : DebugScenarioDefinition.FUNCTIONAL) {
            scenarios.then(Commands.literal(name).executes(ctx ->
                    DebugScenarioManager.start(ctx.getSource(), context, name, false, 1, 12)));
        }
        var baseline = Commands.literal("baseline")
                .executes(ctx -> DebugScenarioManager.start(ctx.getSource(), context, "baseline", true, 0, 60))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 300)).executes(ctx ->
                        DebugScenarioManager.start(ctx.getSource(), context, "baseline", true, 0,
                                IntegerArgumentType.getInteger(ctx, "seconds"))));
        var benchmarks = Commands.literal("bench").then(baseline);
        for (String name : List.of("convert", "retry")) { bench(benchmarks, context, name, 1000, 60); }
        // normal 为不转化的对照负载，converted 为转化负载，两者默认 10000 实体 / 10 秒。
        for (String name : List.of("normal", "converted")) { bench(benchmarks, context, name, 10000, 10); }
        return Commands.literal("debug")
                .requires(source -> DebugMode.ENABLED && RuleCommandTree.hasAccess(source))
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(scenarios).then(benchmarks)
                .then(Commands.literal("stop").executes(ctx -> DebugScenarioManager.stop(ctx.getSource(), context)))
                .then(Commands.literal("status").executes(ctx -> DebugScenarioManager.status(ctx.getSource())))
                .then(Commands.literal("mark").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> DebugScenarioManager.mark(ctx.getSource(), StringArgumentType.getString(ctx, "text")))));
    }

    // 统一注册性能场景；默认实体数与时长随场景不同，均可由参数覆盖。
    private static void bench(LiteralArgumentBuilder<CommandSourceStack> parent, RuleCommandContext context,
                              String name, int defaultEntities, int defaultSeconds) {
        parent.then(Commands.literal(name)
                .executes(ctx -> DebugScenarioManager.start(ctx.getSource(), context, name, true, defaultEntities, defaultSeconds))
                .then(Commands.argument("entities", IntegerArgumentType.integer(1, 10000))
                        .executes(ctx -> DebugScenarioManager.start(ctx.getSource(), context, name, true,
                                IntegerArgumentType.getInteger(ctx, "entities"), defaultSeconds))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 300)).executes(ctx ->
                                DebugScenarioManager.start(ctx.getSource(), context, name, true,
                                        IntegerArgumentType.getInteger(ctx, "entities"),
                                        IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    // 帮助展示操作场景和控制台过滤方式，所有聊天文案均本地化。
    private static int help(CommandSourceStack source) {
        for (String key : List.of("help.run", "help.bench", "help.control", "help.logs")) {
            source.sendSuccess(() -> Component.translatable(PREFIX + key), false);
        }
        return 1;
    }
}
