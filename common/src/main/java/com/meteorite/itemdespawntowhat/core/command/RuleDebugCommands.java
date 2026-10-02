package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.core.api.ClimateSample;
import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import com.meteorite.itemdespawntowhat.core.runtime.ExpressionEvaluator;
import com.meteorite.itemdespawntowhat.core.runtime.RuntimeClimateSampler;
import com.meteorite.itemdespawntowhat.core.runtime.RuntimeConditionContext;
import com.meteorite.itemdespawntowhat.core.runtime.RuntimeTagLookup;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /idtw debug 子命令组：stats / inspect / why / biome（规划书 3.10）。
 * 全部为只读诊断：追踪状态与候选规则取自 ConversionRuntime，条件求值复用 core/runtime 的求值器，
 * 保证"命令看到的结论"与"运行时实际判定"同源。
 */
final class RuleDebugCommands {

    // 视线拾取的最大距离（方块）
    private static final double INSPECT_RANGE = 8.0D;

    private RuleDebugCommands() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 组装 debug 分支
    static LiteralArgumentBuilder<CommandSourceStack> build(RuleCommandContext context) {
        return Commands.literal("debug")
                .requires(RuleCommandTree::hasAccess)
                .then(Commands.literal("stats").executes(ctx -> stats(ctx, context)))
                .then(Commands.literal("inspect").executes(ctx -> inspect(ctx, context)))
                .then(Commands.literal("why").executes(ctx -> why(ctx, context)))
                .then(Commands.literal("biome").executes(ctx -> biome(ctx, context)));
    }

    // debug stats：规则数、版本戳、会话数、server.json 关键值与各维度追踪/到期任务数
    private static int stats(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        ConversionRuntime runtime = context.runtime();
        if (runtime == null) {
            return RuleCommandTree.notReady(ctx);
        }
        MinecraftServer server = ctx.getSource().getServer();
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.stats.runtime")
                .kv("rules", runtime.index().ordered().size())
                .kv("version", context.overlayVersion())
                .kv("sessions", context.activeSessionCount())
                .build());
        ServerConfig config = context.serverConfig();
        if (config != null) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.stats.config")
                    .kv("check_interval_ticks", config.checkIntervalTicks())
                    .kv("backoff_max_ticks", config.backoffMaxTicks())
                    .kv("max_checks_per_tick", config.maxChecksPerTick())
                    .kv("overlay_directory", config.overlayDirectory())
                    .kv("fabric_lifespan_fallback_ticks", config.fabricLifespanFallbackTicks())
                    .kv("debug_logging", config.debugLogging())
                    .build());
        }
        for (ServerLevel level : server.getAllLevels()) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.stats.level")
                    .kv("dimension", level.dimension().location())
                    .kv("tracked", runtime.trackedCount(level))
                    .kv("pending", runtime.pendingTasks(level))
                    .build());
            for (boolean effects : new boolean[]{false, true}) {
                var stats = runtime.queueStats(level, effects);
                lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.stats.queue")
                        .kv("queue", effects ? "effects" : "checks").kv("pending", stats.pending())
                        .kv("peak", stats.peakPending()).kv("visited", stats.totalVisited())
                        .kv("last_us", stats.lastMicros()).kv("max_us", stats.maxMicros())
                        .kv("oldest_ticks", stats.oldestDelay()).build());
            }
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // debug inspect：视线内掉落物的追踪状态
    private static int inspect(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        ConversionRuntime runtime = context.runtime();
        if (runtime == null) {
            return RuleCommandTree.notReady(ctx);
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(
                    "itemdespawntowhat.command.debug.player_only").build());
        }
        ItemEntity item = findLookedAtItem(player);
        if (item == null) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(
                    "itemdespawntowhat.command.debug.no_target").kv("range", INSPECT_RANGE).build());
        }
        ServerLevel level = player.serverLevel();
        ConversionRuntime.TrackedItemInfo info = runtime.debugInfo(level, item);
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.inspect.result")
                .kv("item", itemId(item))
                .kv("count", item.getItem().getCount())
                .kv("pos", posText(item.blockPosition()))
                .kv("age", item.getAge())
                .kv("tracked", info.tracked())
                .kv("failures", info.failureCount())
                .kv("locked", info.locked())
                .kv("next_check_tick", info.nextCheckTick())
                .kv("candidates", runtime.candidatesFor(item).size())
                .build());
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // debug why：候选规则（按优先级）+ 每条规则条件是否成立 + 最终命中项
    private static int why(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        ConversionRuntime runtime = context.runtime();
        if (runtime == null) {
            return RuleCommandTree.notReady(ctx);
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(
                    "itemdespawntowhat.command.debug.player_only").build());
        }
        ItemEntity item = findLookedAtItem(player);
        if (item == null) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(
                    "itemdespawntowhat.command.debug.no_target").kv("range", INSPECT_RANGE).build());
        }
        BuiltinTypeRegistries registries = context.editContext().typeRegistries();
        if (registries == null) {
            return RuleCommandTree.notReady(ctx);
        }

        ServerLevel level = player.serverLevel();
        ConversionRuntime.TrackedItemInfo info = runtime.debugInfo(level, item);
        List<Rule> candidates = runtime.candidatesFor(item);
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.why.header")
                .kv("item", itemId(item))
                .kv("pos", posText(item.blockPosition()))
                .kv("tracked", info.tracked())
                .kv("failures", info.failureCount())
                .kv("candidates", candidates.size())
                .build());

        // 条件求值上下文：标签与气候采样对象本次调用内复用（各自带缓存）
        ConditionContext conditionContext = new RuntimeConditionContext(
                level, item, item.blockPosition(), level.random,
                new RuntimeTagLookup(level), new RuntimeClimateSampler(level));
        boolean chosen = false;
        for (Rule rule : candidates) {
            boolean matched = runtime.isEligible(level, item, rule) && ExpressionEvaluator.matches(
                    rule.conditions(), conditionContext, registries.conditionTypes());
            lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.why.candidate")
                    .kv("id", rule.id())
                    .kv("priority", rule.priority())
                    .kv("leaves", rule.complexity())
                    .kv("effects", rule.effects().size())
                    .kv("matched", matched)
                    .build());
            if (matched && !chosen) {
                // 同一掉落物只执行优先级最高的命中规则（规划书 Q8/Q39）
                chosen = true;
                lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.why.chosen")
                        .kv("id", rule.id()).build());
            }
        }
        if (candidates.isEmpty()) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.why.no_candidate").build());
        } else if (!chosen) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.why.none_matched").build());
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // debug biome：当前位置的群系与 6 个气候参数
    private static int biome(CommandContext<CommandSourceStack> ctx, RuleCommandContext context) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        ResourceLocation biomeId = level.getBiome(pos).unwrapKey()
                .map(key -> key.location()).orElse(null);
        List<Component> lines = new ArrayList<>();
        lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.biome.header")
                .kv("dimension", level.dimension().location())
                .kv("pos", posText(pos))
                .kv("biome", biomeId == null ? "" : biomeId)
                .build());
        ClimateSample sample = new RuntimeClimateSampler(level).sample(pos);
        if (sample == null) {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.biome.no_climate").build());
        } else {
            lines.add(RuleCommandText.of("itemdespawntowhat.command.debug.biome.climate")
                    .kv("temperature", round(sample.temperature()))
                    .kv("humidity", round(sample.humidity()))
                    .kv("continentalness", round(sample.continentalness()))
                    .kv("erosion", round(sample.erosion()))
                    .kv("depth", round(sample.depth()))
                    .kv("weirdness", round(sample.weirdness()))
                    .build());
        }
        return RuleCommandTree.replyAll(ctx, lines);
    }

    // 视线拾取最近的掉落物：按视线射线与实体包围盒求交，取命中点最近的一个
    private static @Nullable ItemEntity findLookedAtItem(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(INSPECT_RANGE));
        AABB search = player.getBoundingBox().expandTowards(view.scale(INSPECT_RANGE)).inflate(1.0D);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, search, entity -> entity.isAlive())) {
            Optional<Vec3> hit = item.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    // 掉落物的物品注册名
    private static ResourceLocation itemId(ItemEntity item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
    }

    // 方块坐标的可读文本
    private static String posText(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // 气候参数保留 3 位小数，便于直接抄进 climate 区间
    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
