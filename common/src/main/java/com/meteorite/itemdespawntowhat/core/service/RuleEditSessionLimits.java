package com.meteorite.itemdespawntowhat.core.service;

/**
 * 独占编辑会话的时间与权限常量（契约 §3.1、§3.2）。
 * 所有时间常量以"服务端活动 tick"为单位（20 tick = 1 秒），暂停时租约不流逝。
 */
public final class RuleEditSessionLimits {

    // OPENING 等待客户端确认的窗口（秒）
    public static final int OPEN_CONFIRM_WINDOW_SECONDS = 15;

    // 客户端心跳间隔（秒），仅作服务端校验与客户端实现参考
    public static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    // 会话租约（秒）：超过该时长无心跳即释放
    public static final int LEASE_SECONDS = 60;

    // 独占编辑目标标识（全局唯一，不再按玩家维度隔离）
    public static final String TARGET_ID = "itemdespawntowhat:rules";

    // 打开编辑器所需的权限等级
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    // 每个会话保留的幂等操作记录条数（契约 §3.3）
    public static final int MAX_OPERATION_HISTORY = 16;

    // 每秒对应的 tick 数
    public static final long TICKS_PER_SECOND = 20L;

    // OPENING 确认窗口（tick）
    public static final long OPEN_CONFIRM_WINDOW_TICKS = OPEN_CONFIRM_WINDOW_SECONDS * TICKS_PER_SECOND;

    // 心跳间隔（tick）
    public static final long HEARTBEAT_INTERVAL_TICKS = HEARTBEAT_INTERVAL_SECONDS * TICKS_PER_SECOND;

    // 会话租约（tick）
    public static final long LEASE_TICKS = LEASE_SECONDS * TICKS_PER_SECOND;

    private RuleEditSessionLimits() {
        throw new UnsupportedOperationException("Utility class");
    }
}
