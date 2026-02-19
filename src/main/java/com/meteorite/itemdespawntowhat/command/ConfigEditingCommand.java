package com.meteorite.itemdespawntowhat.command;

import com.meteorite.itemdespawntowhat.network.OpenGuiPayload;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ConfigEditingCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("editConversionConfig")
                        .requires(source -> {
                            MinecraftServer server = source.getServer();
                            // 单人模式 或 op 权限
                            return server.isSingleplayer() || source.hasPermission(2);
                        })
                        .executes(context -> {
                            // 发包到执行指令的玩家客户端
                            CommandSourceStack source = context.getSource();
                            // 必须由玩家执行，不能由命令方块执行
                            if (!(source.getEntity() instanceof ServerPlayer serverPlayer)) {
                                return 0;
                            }

                            OpenGuiPayload payload = new OpenGuiPayload();
                            PacketDistributor.sendToPlayer(serverPlayer, payload);

                            return 1;
                        })
        );
    }

}
