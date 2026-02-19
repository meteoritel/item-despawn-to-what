package com.meteorite.itemdespawntowhat.debug;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;

/*
 * 被游戏各个时候参数的神奇变化气晕 =_=
 * 一气之下让小克同学帮忙写了一份详细的调试日志……
 * screen类的minecraft实例不是主类单例啊可恶！
*/
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class PlayerStateDebugger {
    private static final Logger LOGGER = LogManager.getLogger();

    // 调试信息输出开关
    private static boolean ENABLE_DEBUG = false;
    // 输出日志的时间间隔
    private static int LOG_INTERVAL_TICKS = 100; // 5秒
    // 只在状态变化时输出日志
    private static boolean LOG_ONLY_ON_CHANGE = true;
    // 是否显示玩家列表
    private static boolean SHOW_PLAYER_LIST = true;

    private static int tickCounter = 0;
    private static GameState lastState = null;
    private static int lastPlayerCount = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLE_DEBUG) {
            return;
        }

        tickCounter++;

        if (tickCounter >= LOG_INTERVAL_TICKS) {
            tickCounter = 0;
            logCurrentState();
        }
    }

    private static void logCurrentState() {
        Minecraft minecraft = Minecraft.getInstance();
        GameState currentState = getCurrentState(minecraft);
        int currentPlayerCount = getPlayerCount(minecraft);

        // 状态变化或玩家数量变化时输出详细信息
        boolean stateChanged = currentState != lastState;
        boolean playerCountChanged = currentPlayerCount != lastPlayerCount;

        if (stateChanged || playerCountChanged || !LOG_ONLY_ON_CHANGE) {
            LOGGER.info("========== 游戏状态信息 ==========");

            if (stateChanged) {
                LOGGER.info("状态变化: {} -> {}", lastState, currentState);
            } else {
                LOGGER.info("当前状态: {}", currentState);
            }

            if (playerCountChanged) {
                LOGGER.info("玩家数变化: {} -> {}", lastPlayerCount, currentPlayerCount);
            }

            logDetailedState(minecraft, currentState);
            LOGGER.info("===================================");

            lastState = currentState;
            lastPlayerCount = currentPlayerCount;
        } else {
            LOGGER.debug("状态: {} | 玩家数: {}", currentState, currentPlayerCount);
        }
    }

    private static void logDetailedState(Minecraft minecraft, GameState state) {
        LOGGER.info("┌─ 客户端信息");
        LOGGER.info("│  - 客户端世界已加载: {}", isClientWorldLoaded(minecraft));
        LOGGER.info("│  - 玩家实例存在: {}", minecraft.player != null);
        LOGGER.info("│  - 游戏模式: {}", isSinglePlayerMode(minecraft) ? "单人" : "多人");

        if (minecraft.player != null) {
            LOGGER.info("│  - 玩家名称: {}", minecraft.player.getName().getString());
            LOGGER.info("│  - 玩家UUID: {}", minecraft.player.getUUID());
            LOGGER.info("│  - 玩家位置: ({}, {}, {})",
                    String.format("%.2f", minecraft.player.getX()),
                    String.format("%.2f", minecraft.player.getY()),
                    String.format("%.2f", minecraft.player.getZ()));
            LOGGER.info("│  - 当前维度: {}", minecraft.player.level().dimension().location());
        }

        if (minecraft.level != null) {
            LOGGER.info("│  - 世界时间: {}", minecraft.level.getDayTime());
            LOGGER.info("│  - 难度: {}", minecraft.level.getDifficulty());
        }

        // 服务端信息
        LOGGER.info("├─ 服务端信息");

        if (isSinglePlayerMode(minecraft)) {
            logSinglePlayerServerInfo(minecraft);
        } else {
            logMultiPlayerServerInfo(minecraft);
        }

        // 网络连接信息
        LOGGER.info("├─ 网络连接信息");
        logNetworkInfo(minecraft);

        // 玩家列表信息
        if (SHOW_PLAYER_LIST) {
            LOGGER.info("└─ 玩家列表信息");
            logPlayerListInfo(minecraft);
        } else {
            LOGGER.info("└─ (玩家列表输出已禁用)");
        }
    }

    private static void logSinglePlayerServerInfo(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();

        LOGGER.info("│  - 服务器类型: 单人游戏内置服务器");
        LOGGER.info("│  - 服务器实例: {}", server != null ? "存在" : "不存在");

        if (server != null) {
            LOGGER.info("│  - 服务器运行中: {}", server.isRunning());
            LOGGER.info("│  - 服务器Tick: {}", server.getTickCount());
            LOGGER.info("│  - 最大玩家数: {}", server.getMaxPlayers());
            LOGGER.info("│  - 当前玩家数: {}", server.getPlayerCount());
            LOGGER.info("│  - 局域网已开放: {}", server.isPublished());

            if (server.isPublished()) {
                LOGGER.info("│  - 局域网端口: {}", server.getPort());
                LOGGER.info("│  - 局域网游戏模式: {}", server.getDefaultGameType());
            }

            LOGGER.info("│  - MOTD: {}", server.getMotd());
            LOGGER.info("│  - 服务器版本: {}", server.getServerVersion());


            // 服务端世界信息
            LOGGER.info("│  - 服务端世界时间: {}", server.overworld().getDayTime());
            LOGGER.info("│  - 服务端已加载区块数: {}", server.overworld().getChunkSource().getLoadedChunksCount());

            // 在线玩家详情
            if (server.getPlayerCount() > 0) {
                LOGGER.info("│  - 在线玩家详情:");
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                for (ServerPlayer player : players) {
                    LOGGER.info("│    • {} (Ping: {}ms, 游戏模式: {})",
                            player.getName().getString(),
                            player.connection.latency(),
                            player.gameMode.getGameModeForPlayer());
                }
            }
        }
    }

    private static void logMultiPlayerServerInfo(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();

        LOGGER.info("│  - 服务器类型: 多人游戏服务器");
        LOGGER.info("│  - 连接实例: {}", connection != null ? "存在" : "不存在");

        if (connection != null) {
            // 服务器基本信息
            if (minecraft.getCurrentServer() != null) {
                LOGGER.info("│  - 服务器名称: {}", minecraft.getCurrentServer().name);
                LOGGER.info("│  - 服务器地址: {}", minecraft.getCurrentServer().ip);
                LOGGER.info("│  - 服务器资源包: {}",
                        minecraft.getCurrentServer().getResourcePackStatus());
            }

            // 连接状态
            boolean isConnected = connection.getConnection().isConnected();
            LOGGER.info("│  - 连接状态: {}", isConnected ? "已连接" : "未连接");
            LOGGER.info("│  - 连接地址: {}", connection.getConnection().getRemoteAddress());
            LOGGER.info("│  - 加密连接: {}", connection.getConnection().isEncrypted());

            // 服务器品牌信息
            String serverBrand = connection.serverBrand();
            LOGGER.info("│  - 服务器品牌: {}", serverBrand != null ? serverBrand : "未知");

            // 玩家数量
            Collection<PlayerInfo> players = connection.getOnlinePlayers();
            LOGGER.info("│  - 在线玩家数: {}", players.size());
        }
    }

    private static void logNetworkInfo(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();

        if (connection != null) {
            LOGGER.info("│  - 网络已连接: {}", connection.getConnection().isConnected());
            LOGGER.info("│  - 网络通道活跃: {}", connection.getConnection().channel().isActive());
            LOGGER.info("│  - 平均延迟: {} ms", connection.getConnection().getAverageReceivedPackets());

            if (minecraft.player != null && connection.getPlayerInfo(minecraft.player.getUUID()) != null) {
                PlayerInfo playerInfo = connection.getPlayerInfo(minecraft.player.getUUID());
                if (playerInfo != null) {
                    LOGGER.info("│  - 当前Ping: {} ms", playerInfo.getLatency());
                }
            }
        } else if (isSinglePlayerMode(minecraft)) {
            LOGGER.info("│  - 网络类型: 单人本地连接（无网络延迟）");
        } else {
            LOGGER.info("│  - 网络状态: 未连接");
        }
    }

    private static void logPlayerListInfo(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();

        if (connection != null) {
            Collection<PlayerInfo> players = connection.getOnlinePlayers();

            if (players.isEmpty()) {
                LOGGER.info("   - 当前无在线玩家");
            } else {
                LOGGER.info("   - 在线玩家列表 (共{}人):", players.size());
                int index = 1;
                for (PlayerInfo player : players) {
                    String gameMode = player.getGameMode().getName();
                    LOGGER.info("   {}. {} | Ping: {}ms | 模式: {} | UUID: {}",
                            index++,
                            player.getProfile().getName(),
                            player.getLatency(),
                            gameMode,
                            player.getProfile().getId());
                }
            }
        } else {
            LOGGER.info("   - 无法获取玩家列表（连接不存在）");
        }
    }

    private static int getPlayerCount(Minecraft minecraft) {
        if (isSinglePlayerMode(minecraft)) {
            MinecraftServer server = minecraft.getSingleplayerServer();
            return server != null ? server.getPlayerCount() : 0;
        } else {
            ClientPacketListener connection = minecraft.getConnection();
            return connection != null ? connection.getOnlinePlayers().size() : 0;
        }
    }

    // ========== 控制方法 ==========

    public static void setDebugEnabled(boolean enabled) {
        ENABLE_DEBUG = enabled;
        LOGGER.info("状态调试输出已{}", enabled ? "启用" : "禁用");
    }

    public static void setLogIntervalSeconds(int seconds) {
        LOG_INTERVAL_TICKS = seconds * 20;
        LOGGER.info("日志输出间隔已设置为 {} 秒 ({} ticks)", seconds, LOG_INTERVAL_TICKS);
    }

    public static void setLogOnlyOnChange(boolean onlyOnChange) {
        LOG_ONLY_ON_CHANGE = onlyOnChange;
        LOGGER.info("仅在状态变化时输出: {}", onlyOnChange);
    }

    public static void setShowPlayerList(boolean show) {
        SHOW_PLAYER_LIST = show;
        LOGGER.info("显示玩家列表: {}", show);
    }

    public static void logStateNow() {
        LOGGER.info("========== 手动触发状态输出 ==========");
        Minecraft minecraft = Minecraft.getInstance();
        logDetailedState(minecraft, getCurrentState(minecraft));
        LOGGER.info("======================================");
    }

    // ========== 状态判断方法 ==========

    private static boolean isClientWorldLoaded(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null;
    }

    private static boolean isSinglePlayerMode(Minecraft minecraft) {
        return minecraft.hasSingleplayerServer();
    }

    private static boolean isMultiPlayerMode(Minecraft minecraft) {
        return !minecraft.hasSingleplayerServer()
                && minecraft.getConnection() != null;
    }

    private static boolean isSinglePlayerServerReady(Minecraft minecraft) {
        if (!isSinglePlayerMode(minecraft)) {
            return false;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        return server != null && server.isRunning();
    }

    private static boolean isMultiPlayerServerConnected(Minecraft minecraft) {
        if (!isMultiPlayerMode(minecraft)) {
            return false;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return false;
        return connection.getConnection().isConnected();
    }

    private static GameState getCurrentState(Minecraft minecraft) {
        if (!isClientWorldLoaded(minecraft)) {
            return GameState.MENU_OR_LOADING;
        }

        if (isSinglePlayerMode(minecraft)) {
            MinecraftServer server = minecraft.getSingleplayerServer();
            if (server != null && server.isPublished()) {
                return GameState.SINGLEPLAYER_LAN_OPEN;
            } else if (isSinglePlayerServerReady(minecraft)) {
                return GameState.SINGLEPLAYER_IN_GAME;
            } else {
                return GameState.SINGLEPLAYER_LOADING;
            }
        } else {
            if (isMultiPlayerServerConnected(minecraft)) {
                return GameState.MULTIPLAYER_IN_GAME;
            } else {
                return GameState.MULTIPLAYER_CONNECTING;
            }
        }
    }

    public enum GameState {
        MENU_OR_LOADING("主菜单/加载中"),
        SINGLEPLAYER_LOADING("单人游戏-加载中"),
        SINGLEPLAYER_IN_GAME("单人游戏-游戏中"),
        SINGLEPLAYER_LAN_OPEN("单人游戏-局域网已开放"),
        MULTIPLAYER_CONNECTING("多人游戏-连接中"),
        MULTIPLAYER_IN_GAME("多人游戏-游戏中");

        private final String description;

        GameState(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

}
