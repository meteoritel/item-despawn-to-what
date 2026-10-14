package com.meteorite.itemdespawntowhat.core.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 全局单一编辑目标的会话状态机与覆盖层版本戳管理。
 * 状态机：FREE --取锁--> OPENING --客户端确认--> ACTIVE --提交变更集--> APPLYING --应用结束--> ACTIVE；
 * OPENING/ACTIVE 可主动释放或超时释放；APPLYING 的释放延后至保存收尾，租约不打断在途保存。
 * 同一时刻至多 1 个非 FREE 会话，因此独占性不再依赖"按玩家隔离"。
 * 时钟一律使用 MinecraftServer.getTickCount()（暂停感知），禁止挂钟时间。
 * 版本戳持久化在覆盖层根目录的 .edit_version（非 .json 后缀，不会被规则读取器扫描）。
 */
public final class EditSessionManager {

    /**
     * 会话状态。
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

    /**
     * 会话只读视图（供指令查询与日志使用）。
     */
    public record SessionInfo(String sessionId, UUID ownerUuid, String ownerName, SessionState state,
                              long openedTick, long lastHeartbeatTick) {
    }

    /**
     * 取锁结果：成功时携带新会话，失败时携带当前持有者信息。
     */
    public record AcquireResult(boolean acquired, SessionInfo session, String holderName, UUID holderUuid) {
    }

    private final RuleOverlayStore store;

    // 全局唯一会话状态
    private SessionState state = SessionState.FREE;
    private UUID ownerUuid;
    private String ownerName = "";
    private String sessionId;
    private long openedTick;
    private long lastHeartbeatTick;
    private boolean releasePending;

    /**
     * 创建覆盖层对应的独占会话与文件工作所有者。
     * @param overlayRoot 覆盖层根目录
     * @throws IllegalStateException 上一生命周期仍有文件工作未结束
     */
    public EditSessionManager(Path overlayRoot) {
        RuleOverlayStore.requireIdle();
        store = new RuleOverlayStore(overlayRoot);
    }

    /** @return 当前覆盖层修订号 */
    public synchronized int version() { return store.version(); }

    /**
     * 成功重载后推进修订，持久化由文件队列执行。
     * @return 推进后的修订号
     */
    public synchronized int bumpVersion() { return store.bumpVersion(); }

    /**
     * 同步覆盖层内容基线；调用方必须位于文件后台线程。
     * @throws java.io.UncheckedIOException 文件读取失败
     */
    public void synchronizeDiskRevision() {
        try { acceptDiskRevision(readDiskRevision(), false); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /**
     * 在后台读取目录内容指纹，不访问世界。
     * @return 全目录内容指纹
     * @throws IOException 文件读取失败
     */
    public byte[] readDiskRevision() throws IOException { return store.fingerprint(() -> true); }

    byte[] readDiskRevision(java.util.function.BooleanSupplier active) throws IOException {
        return store.fingerprint(active);
    }

    /**
     * 接纳完整读取的覆盖层基线并登记待持久化修订。
     * @param revision 全目录内容指纹，可为 null 以重新建立基线
     * @param saved 是否接纳一次新提交
     */
    public synchronized void acceptDiskRevision(byte[] revision, boolean saved) {
        store.acceptRevision(revision, saved);
    }

    /**
     * 登记修订持久化请求；旧调用的修订参数不能覆盖最新已分配修订。
     * @param revision 调用方已观察的修订号
     */
    public void persistRevision(int revision) {
        if (revision > store.version()) { throw new IllegalArgumentException("不能持久化尚未分配的修订号"); }
        store.flushRevision();
    }

    RuleOverlayStore store() { return store; }

    /**
     * 在下一服务端加载规则前确认上一生命周期的真实文件调用已经退出。
     * @throws IllegalStateException 尚有旧文件工作
     */
    public static void requireFileWorkIdle() { RuleOverlayStore.requireIdle(); }

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

    // 会话是否已确认（ACTIVE 或 APPLYING）：快照/目录请求的状态守卫
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

    // 应用结束执行延后释放，否则回到 ACTIVE 并刷新租约
    public synchronized void finishApply(String sessionId, long tick) {
        if (state == SessionState.APPLYING && matches(sessionId)) {
            if (releasePending) { clearSession(); }
            else { this.state = SessionState.ACTIVE; this.lastHeartbeatTick = tick; }
        }
    }

    // 请求释放；APPLYING 延后至 finishApply，返回目标会话，空闲时返回 null
    public synchronized SessionInfo release() {
        if (state == SessionState.FREE) {
            return null;
        }
        SessionInfo released = snapshotInfo();
        if (state == SessionState.APPLYING) { releasePending = true; }
        else { clearSession(); }
        return released;
    }

    // 超时检查只回收 OPENING/ACTIVE；APPLYING 等待保存收尾
    public synchronized SessionInfo expire(long tick) {
        // 保存工作与回滚未结束前，不开放同一编辑目标；结束时会刷新租约。
        if (state == SessionState.APPLYING) { return null; }
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

    private boolean matches(String sessionId) {
        return this.sessionId != null && this.sessionId.equals(sessionId);
    }

    private SessionInfo snapshotInfo() {
        return new SessionInfo(sessionId, ownerUuid, ownerName, state, openedTick, lastHeartbeatTick);
    }

    private void clearSession() {
        this.state = SessionState.FREE;
        this.releasePending = false;
        this.ownerUuid = null;
        this.ownerName = "";
        this.sessionId = null;
        this.openedTick = 0L;
        this.lastHeartbeatTick = 0L;
    }

}
