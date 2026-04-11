package com.meteorite.itemdespawntowhat.command;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.network.OpenGuiPayload;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ConversionConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("conversion_config")
                        .requires(source -> {
                            MinecraftServer server = source.getServer();
                            // 单人模式 或 op 权限
                            return server.isSingleplayer() || source.hasPermission(2);
                        })
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!ConfigExtractorManager.reloadAllConfigs()) {
                                        return 0;
                                    }

                                    Component message = Component.translatable("gui.itemdespawntowhat.command.reload.success");
                                    source.getServer().getPlayerList().broadcastSystemMessage(message, true);
                                    if (!(source.getEntity() instanceof ServerPlayer)) {
                                        source.sendSuccess(() -> message, true);
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("edit")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    // 必须由玩家执行，不能由命令方块执行
                                    if (!(source.getEntity() instanceof ServerPlayer serverPlayer)) {
                                        return 0;
                                    }
                                    PacketDistributor.sendToPlayer(serverPlayer, new OpenGuiPayload());
                                    return 1;
                                })
                        )
        );
    }
}
