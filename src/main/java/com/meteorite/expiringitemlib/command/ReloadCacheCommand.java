package com.meteorite.expiringitemlib.command;

import com.meteorite.expiringitemlib.ConfigExtractorManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ReloadCacheCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reloadConversionConfigs")
                .requires(cs -> cs.hasPermission(2))
                .executes(ctx -> {

                    ConfigExtractorManager.reloadAllConfigs();

                    ctx.getSource().sendSuccess(
                            () -> Component.literal("Conversion configs reloaded."),
                            true
                    );
                    return 1;
                })
        );
    }
}
