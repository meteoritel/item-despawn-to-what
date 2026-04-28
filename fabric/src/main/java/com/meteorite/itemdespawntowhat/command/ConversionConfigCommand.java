package com.meteorite.itemdespawntowhat.command;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.network.payload.s2c.OpenGuiPayload;
import com.meteorite.itemdespawntowhat.platform.Services;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ConversionConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("conversion_config")
                        .requires(source -> {
                            MinecraftServer server = source.getServer();
                            return server.isSingleplayer() || source.hasPermission(2);
                        })
                        .then(buildReloadCommand())
                        .then(buildEditCommand())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildReloadCommand() {
        return Commands.literal("reload")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!ConfigExtractorManager.reloadAllConfigs(Services.PLATFORM.getConfigDir())) {
                        return 0;
                    }

                    Component message = Component.translatable("gui.itemdespawntowhat.command.reload.success");
                    source.getServer().getPlayerList().broadcastSystemMessage(message, true);
                    if (!(source.getEntity() instanceof ServerPlayer)) {
                        source.sendSuccess(() -> message, true);
                    }
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildEditCommand() {
        return Commands.literal("edit")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer serverPlayer)) {
                        return 0;
                    }
                    ServerPlayNetworking.send(serverPlayer, new OpenGuiPayload());
                    return 1;
                });
    }
}
