package com.meteorite.itemdespawntowhat.command;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.network.payload.s2c.OpenGuiPayload;
import com.meteorite.itemdespawntowhat.platform.Services;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 跨平台配置重载与编辑命令注册入口。
 */
public final class ConversionConfigCommand {

    private ConversionConfigCommand() {
        throw new UnsupportedOperationException("Utility class");
    }

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
                    if (!(context.getSource().getEntity() instanceof ServerPlayer serverPlayer)) {
                        return 0;
                    }
                    Services.PLATFORM.sendToPlayer(serverPlayer, new OpenGuiPayload());
                    return 1;
                });
    }
}
