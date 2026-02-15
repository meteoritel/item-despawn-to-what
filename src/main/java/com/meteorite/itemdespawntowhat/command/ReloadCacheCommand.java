package com.meteorite.itemdespawntowhat.command;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class ReloadCacheCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reloadConversionConfigs")
                .requires(source -> {
                    MinecraftServer server = source.getServer();
                    // 单人模式
                    return server.isSingleplayer() || source.hasPermission(2);})
                .executes(context -> {

                    ConfigExtractorManager.reloadAllConfigs();

                    context.getSource().sendSuccess(
                            () -> Component.literal("Conversion configs reloaded."),
                            true
                    );
                    return 1;
                })
        );
    }
}
