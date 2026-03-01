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
                                    ConfigExtractorManager.reloadAllConfigs();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Conversion configs reloaded."),
                                            true
                                    );
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
