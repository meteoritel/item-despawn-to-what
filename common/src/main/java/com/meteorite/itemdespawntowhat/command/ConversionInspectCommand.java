package com.meteorite.itemdespawntowhat.command;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionDebugResult;
import com.meteorite.itemdespawntowhat.config.runtime.CompiledConversionRule;
import com.meteorite.itemdespawntowhat.server.conversion.ConversionTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 输出玩家视线中掉落物的转换追踪与条件诊断信息。
 */
public final class ConversionInspectCommand {
    private static final double INSPECT_DISTANCE = 8.0;
    private static final List<String> CONDITION_NAMES = List.of(
            "dimension", "outdoor", "surrounding_blocks", "catalyst", "inner_fluid");

    private ConversionInspectCommand() {
    }

    public static int execute(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.itemdespawntowhat.inspect.player_only"));
            return 0;
        }

        ItemEntity itemEntity = findTargetItem(player);
        if (itemEntity == null) {
            source.sendFailure(Component.translatable("command.itemdespawntowhat.inspect.no_target"));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        send(player, Component.translatable("command.itemdespawntowhat.inspect.target",
                itemId, itemEntity.getUUID(), itemEntity.getItem().getCount()));

        ConversionTracker.ConversionState state = ConversionTracker.getState(itemEntity);
        if (state == null) {
            send(player, Component.translatable("command.itemdespawntowhat.inspect.tracked.no")
                    .withStyle(ChatFormatting.RED));
            send(player, Component.translatable(untrackedReason(itemEntity, itemId)));
            sendNoRuleDetails(player);
            return 1;
        }

        send(player, Component.translatable("command.itemdespawntowhat.inspect.tracked.yes")
                .withStyle(ChatFormatting.GREEN));
        CompiledConversionRule rule = state.selectedConfigId().isEmpty()
                ? null
                : ConfigExtractorManager.getRuleByInternalId(state.selectedConfigId());
        if (rule == null) {
            sendNoRuleDetails(player);
            return 1;
        }

        send(player, Component.translatable("command.itemdespawntowhat.inspect.rule",
                rule.internalId(), rule.definition().getConversionType().id()));
        send(player, Component.translatable("command.itemdespawntowhat.inspect.progress",
                state.checkTimer(), rule.conversionTime()));

        Map<String, Boolean> results = new LinkedHashMap<>();
        for (ConditionDebugResult result : rule.debugConditions(itemEntity, player.serverLevel())) {
            results.put(result.name(), result.passed());
        }
        sendConditionResults(player, results);
        return 1;
    }

    private static ItemEntity findTargetItem(ServerPlayer player) {
        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                player,
                entity -> entity instanceof ItemEntity && entity.isAlive(),
                INSPECT_DISTANCE);
        if (hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof ItemEntity itemEntity) {
            return itemEntity;
        }
        return null;
    }

    private static String untrackedReason(ItemEntity itemEntity, ResourceLocation itemId) {
        if (itemEntity.getTags().contains(Constants.CHECK_LOCK_TAG)) {
            return "command.itemdespawntowhat.inspect.reason.locked";
        }
        if (!ConfigExtractorManager.hasAnyConfigs(itemId)) {
            return "command.itemdespawntowhat.inspect.reason.no_config";
        }
        return "command.itemdespawntowhat.inspect.reason.not_registered";
    }

    private static void sendNoRuleDetails(ServerPlayer player) {
        send(player, Component.translatable("command.itemdespawntowhat.inspect.rule.none"));
        send(player, Component.translatable("command.itemdespawntowhat.inspect.progress.na"));
        sendConditionResults(player, Map.of());
    }

    private static void sendConditionResults(ServerPlayer player, Map<String, Boolean> results) {
        for (String name : CONDITION_NAMES) {
            Boolean passed = results.get(name);
            Component status;
            if (passed == null) {
                status = Component.translatable("command.itemdespawntowhat.inspect.status.na")
                        .withStyle(ChatFormatting.GRAY);
            } else if (passed) {
                status = Component.translatable("command.itemdespawntowhat.inspect.status.pass")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                status = Component.translatable("command.itemdespawntowhat.inspect.status.fail")
                        .withStyle(ChatFormatting.RED);
            }
            send(player, Component.translatable("command.itemdespawntowhat.inspect.condition",
                    Component.translatable("command.itemdespawntowhat.inspect.condition." + name), status));
        }
    }

    private static void send(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
}
