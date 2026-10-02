package com.meteorite.itemdespawntowhat.core.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 全局单一编辑目标的会话状态机（契约 §3.1）与覆盖层版本戳管理。
 * 状态机：FREE --取锁--> OPENING --客户端确认--> ACTIVE --提交变更集--> APPLYING --应用结束--> ACTIVE；
 * OPENING/ACTIVE/APPLYING 在释放、断线、租约到期或强制释放时回到 FREE。
 * 同一时刻至多 1 个非 FREE 会话，因此独占性不再依赖"按玩家隔离"。
 * 时钟一律使用 MinecraftServer.getTickCount()（暂停感知），禁止挂钟时间。
 * 版本戳持久化在覆盖层根目录的 .edit_version（非 .json 后缀，不会被规则读取器扫描）。
 */
public final class EditSessionManager {

    /***
     * 会话状态（契约 §3.1）。
     */
    public enum SessionState {
        // 空闲：无任何会话
        FREE,
        // 已取锁，等待客户端确认进入编辑界面
        OPENING,
        // 客户端已确认，处于可编辑状态
        ACTIVE,
        // 正在应用变更集，期间不接受新的保存请求
        APPLYING
    }

    /***
     * 会话只读视图（供指令查询与日志使用）。
     */
    public record SessionInfo(String sessionId, UUID ownerUuid, String ownerName, SessionState state,
                              long openedTick, long lastHeartbeatTick) {
    }

    /***
     * 取锁结果：成功时携带新会话，失败时携带当前持有者信息。
     */
    public record AcquireResult(boolean acquired, SessionInfo session, String holderName, UUID holderUuid) {
    }

    private static final String VERSION_FILE_NAME = ".edit_version";

    private final Path versionFile;
    private int version;
    private byte[] diskRevision;

    // 全局唯一会话状态
    private SessionState state = SessionState.FREE;
    private UUID ownerUuid;
    private String ownerName = "";
    private String sessionId;
    private long openedTick;
    private long lastHeartbeatTick;

    public EditSessionManager(Path overlayRoot) {
        this.versionFile = overlayRoot.resolve(VERSION_FILE_NAME);
        this.version = readVersion();
        refreshDiskRevision();
    }

    // 当前覆盖层版本戳
    public synchronized int version() {
        return version;
    }

    // 覆盖层内容变化后推进版本戳并持久化
    public synchronized int bumpVersion() {
        version = version + 1;
        persistVersion();
        refreshDiskRevision();
        return version;
    }

    // 在快照和保存前同步磁盘修订，手工编辑尚未 reload 也不能被旧客户端视图覆盖。
    public synchronized void synchronizeDiskRevision() {
        byte[] current;
        try {
            current = fingerprint();
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException("无法核对规则目录修订", failure);
        }
        if (!java.util.Arrays.equals(current, diskRevision)) {
            version++;
            persistVersion();
            diskRevision = current;
        }
    }

    // 当前活跃会话数：全局至多 1
    public synchronized int activeSessionCount() {
        return state == SessionState.FREE ? 0 : 1;
    }

    // 当前会话只读视图；空闲时返回 null
    public synchronized SessionInfo session() {
        return state == SessionState.FREE ? null : snapshotInfo();
    }

    // 取锁：成功则创建 OPENING 会话（sessionId 不可预测），失败返回持有者信息
    public synchronized AcquireResult acquire(UUID owner, String name, long tick) {
        if (state != SessionState.FREE) {
            return new AcquireResult(false, null, ownerName, ownerUuid);
        }
        this.ownerUuid = owner;
        this.ownerName = name == null ? "" : name;
        this.sessionId = UUID.randomUUID().toString();
        this.state = SessionState.OPENING;
        this.openedTick = tick;
        this.lastHeartbeatTick = tick;
        return new AcquireResult(true, snapshotInfo(), null, null);
    }

    // 客户端确认：仅 OPENING 且 sessionId 匹配时进入 ACTIVE
    public synchronized boolean confirm(String sessionId, long tick) {
        if (state != SessionState.OPENING || !matches(sessionId)) {
            return false;
        }
        this.state = SessionState.ACTIVE;
        this.lastHeartbeatTick = tick;
        return true;
    }

    // 心跳续期：OPENING/ACTIVE/APPLYING 均接受，刷新租约
    public synchronized boolean heartbeat(String sessionId, long tick) {
        if (state == SessionState.FREE || !matches(sessionId)) {
            return false;
        }
        this.lastHeartbeatTick = tick;
        return true;
    }

    // 会话归属校验：sessionId 匹配且会话非空闲
    public synchronized boolean owns(String sessionId) {
        return state != SessionState.FREE && matches(sessionId);
    }

    // 该玩家是否为当前持有者
    public synchronized boolean isOwner(UUID player) {
        return state != SessionState.FREE && ownerUuid != null && ownerUuid.equals(player);
    }

    // 会话是否已确认（ACTIVE 或 APPLYING）：快照/目录请求的状态守卫（契约 §3.3 要求校验状态）
    public synchronized boolean confirmed(String sessionId) {
        return (state == SessionState.ACTIVE || state == SessionState.APPLYING) && matches(sessionId);
    }

    // 进入应用阶段：仅 ACTIVE 可切换，防止并发保存
    public synchronized boolean beginApply(String sessionId) {
        if (state != SessionState.ACTIVE || !matches(sessionId)) {
            return false;
        }
        this.state = SessionState.APPLYING;
        return true;
    }

    // 应用结束回到 ACTIVE，并按当前 tick 刷新租约
    public synchronized void finishApply(String sessionId, long tick) {
        if (state == SessionState.APPLYING && matches(sessionId)) {
            this.state = SessionState.ACTIVE;
            this.lastHeartbeatTick = tick;
        }
    }

    // 主动释放（客户端关闭、强制释放）；返回被释放的会话，空闲时返回 null
    public synchronized SessionInfo release() {
        if (state == SessionState.FREE) {
            return null;
        }
        SessionInfo released = snapshotInfo();
        clearSession();
        return released;
    }

    // 超时检查：OPENING 超确认窗口或非空闲会话超租约即释放，返回被释放的会话
    public synchronized SessionInfo expire(long tick) {
        if (state == SessionState.OPENING) {
            if (tick - openedTick > RuleEditSessionLimits.OPEN_CONFIRM_WINDOW_TICKS) {
                SessionInfo expired = snapshotInfo();
                clearSession();
                return expired;
            }
            return null;
        }
        if (state != SessionState.FREE && tick - lastHeartbeatTick > RuleEditSessionLimits.LEASE_TICKS) {
            SessionInfo expired = snapshotInfo();
            clearSession();
            return expired;
        }
        return null;
    }

    // 断线处理：契约要求会话在租约到期后释放，因此此处只保留会话，由 expire 负责回收

    private boolean matches(String sessionId) {
        return sessionId != null && this.sessionId != null && this.sessionId.equals(sessionId);
    }

    private SessionInfo snapshotInfo() {
        return new SessionInfo(sessionId, ownerUuid, ownerName, state, openedTick, lastHeartbeatTick);
    }

    private void clearSession() {
        this.state = SessionState.FREE;
        this.ownerUuid = null;
        this.ownerName = "";
        this.sessionId = null;
        this.openedTick = 0L;
        this.lastHeartbeatTick = 0L;
    }

    private void refreshDiskRevision() {
        try {
            diskRevision = fingerprint();
        } catch (IOException failure) {
            diskRevision = null;
            org.apache.logging.log4j.LogManager.getLogger().warn("无法读取规则目录修订，后续保存将重新核对", failure);
        }
    }

    // 只读所有规则文件；坏 JSON 的原始字节同样参与修订，不解析、不写回。
    private byte[] fingerprint() throws IOException {
        java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        Path root = versionFile.getParent().resolve(com.meteorite.itemdespawntowhat.core.load.RulePaths.OVERLAY_RULES_DIRECTORY);
        if (!Files.isDirectory(root)) {
            return digest.digest();
        }
        try (var walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                byte[] name = root.relativize(file).toString().getBytes(StandardCharsets.UTF_8);
                byte[] content = Files.readAllBytes(file);
                digest.update(java.nio.ByteBuffer.allocate(8).putInt(name.length).putInt(content.length).array());
                digest.update(name);
                digest.update(content);
            }
        }
        return digest.digest();
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
