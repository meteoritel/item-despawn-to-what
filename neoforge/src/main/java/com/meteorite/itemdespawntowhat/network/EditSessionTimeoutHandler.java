package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ForceCloseEditorPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.meteorite.itemdespawntowhat.platform.Services;

import java.util.UUID;

// 服务端 tick 处理器：定期检查编辑锁是否超时，超时则强制关闭编辑器。
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public final class EditSessionTimeoutHandler {
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 20_000L; // 每 20 秒检查一次
    private static final long TIMEOUT_MS = 300_000L; // 5 分钟无操作则超时

    private EditSessionTimeoutHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) return;
        lastCheckTime = now;

        UUID timedOut = EditSessionLockManager.checkTimeout(TIMEOUT_MS);
        if (timedOut != null) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(timedOut);
            if (player != null) {
                Services.PLATFORM.sendToPlayer(player, new ForceCloseEditorPayload());
                player.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.idle_timeout"));
            }
        }
    }
}
