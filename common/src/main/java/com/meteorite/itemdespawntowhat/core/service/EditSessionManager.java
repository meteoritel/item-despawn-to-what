package com.meteorite.itemdespawntowhat.core.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 配置编辑会话与版本戳管理。
 * 与旧链路的"全局单 UUID 锁"不同：会话按玩家维度隔离，一个玩家编辑不影响其他玩家；
 * 并发保护改由版本戳承担——保存时携带客户端视图版本，服务端版本不一致即拒绝整批。
 * 版本戳持久化在覆盖层根目录的 .edit_version（非 .json 后缀，不会被规则读取器扫描）。
 */
public final class EditSessionManager {

    // 会话空闲超时：5 分钟（与旧链路一致的量级）
    public static final long DEFAULT_TIMEOUT_MILLIS = 300_000L;

    private static final String VERSION_FILE_NAME = ".edit_version";

    private final Path versionFile;
    private final long timeoutMillis;
    private final Map<UUID, Long> activeSessions = new HashMap<>();
    private int version;

    public EditSessionManager(Path overlayRoot) {
        this(overlayRoot, DEFAULT_TIMEOUT_MILLIS);
    }

    public EditSessionManager(Path overlayRoot, long timeoutMillis) {
        this.versionFile = overlayRoot.resolve(VERSION_FILE_NAME);
        this.timeoutMillis = Math.max(1_000L, timeoutMillis);
        this.version = readVersion();
    }

    // 当前覆盖层版本戳
    public synchronized int version() {
        return version;
    }

    // 打开/续期某个玩家的编辑会话
    public synchronized void open(UUID player, long now) {
        activeSessions.put(player, now);
    }

    // 会话是否有效（顺带清理空闲超时的会话）
    public synchronized boolean isActive(UUID player, long now) {
        expireIdle(now);
        Long last = activeSessions.get(player);
        if (last == null) {
            return false;
        }
        return now - last <= timeoutMillis;
    }

    // 关闭会话
    public synchronized void close(UUID player) {
        activeSessions.remove(player);
    }

    // 清理空闲会话
    public synchronized void expireIdle(long now) {
        activeSessions.entrySet().removeIf(entry -> now - entry.getValue() > timeoutMillis);
    }

    // 版本戳校验：客户端视图过期时返回 false
    public synchronized boolean versionMatches(int expected) {
        return expected == version;
    }

    // 覆盖层内容变化后推进版本戳并持久化
    public synchronized int bumpVersion() {
        version = version + 1;
        persistVersion();
        return version;
    }

    // 当前活跃会话数（供调试与命令输出）
    public synchronized int activeSessionCount() {
        return activeSessions.size();
    }

    // 读取持久化版本；缺失或非法按 0 处理
    private int readVersion() {
        try {
            if (Files.isRegularFile(versionFile)) {
                return Integer.parseInt(Files.readString(versionFile, StandardCharsets.UTF_8).trim());
            }
        } catch (IOException | NumberFormatException ignored) {
            // 版本文件损坏不影响启动，按 0 重新计数
        }
        return 0;
    }

    // 版本写盘失败不阻断保存流程，仅保持内存计数
    private void persistVersion() {
        try {
            Files.createDirectories(versionFile.getParent());
            Files.writeString(versionFile, Integer.toString(version), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 见方法注释：版本戳不是权威数据，落盘失败可容忍
        }
    }
}
