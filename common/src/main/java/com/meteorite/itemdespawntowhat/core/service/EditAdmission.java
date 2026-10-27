package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Exclusive Edit Session 的统一准入：按会话号 → 持有者 → 确认 → 权限的固定顺序判定，
 * 通过时返回 {@code null}，否则返回拒绝原因与状态码。{@code player} 为请求玩家，
 * 离线时为 {@code null}；{@code sessionId} 为请求绑定的编辑会话号。
 *
 * <p>判定顺序与状态码映射集中在此，各入口只负责组装载荷，内部续接只校验会话号；
 * 编辑权限由构造点注入的谓词判定，服务层不依赖网络入口。</p>
 */
public final class EditAdmission {

    /** 拒绝原因；决定回执状态码与调用方是否中止在途工作。 */
    public enum Reason {
        // 会话号不属于当前会话：空闲、已释放或已换会话
        SESSION_MISMATCH,
        NOT_OWNER,
        // 客户端尚未确认，持有者身份仍可能变化
        NOT_CONFIRMED,
        // 持有者已下线，无法确认身份或判定权限
        PLAYER_ABSENT,
        NO_PERMISSION
    }

    /** 准入拒绝：原因供服务端收尾判定，状态码决定协议回执与文案 key。 */
    public record Refusal(Reason reason, RuleSaveStatus status) {
    }

    private final EditSessionManager sessions;
    private final Predicate<ServerPlayer> permitted;

    public EditAdmission(EditSessionManager sessions, Predicate<ServerPlayer> permitted) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.permitted = Objects.requireNonNull(permitted, "permitted");
    }

    /**
     * 会话仍存在且会话号匹配时返回 true；用于没有玩家上下文的续接（快照交付），
     * 内容已在请求时刻完成授权。
     */
    public boolean sessionMatches(String sessionId) {
        return sessions.owns(sessionId);
    }

    /** 校验会话号与持有者，不判权限：取消保存只撤回本人在途的请求，不新增写权限。 */
    public @Nullable Refusal admitOwner(@Nullable ServerPlayer player, String sessionId) {
        return decide(player, sessionId, false, false);
    }

    /** 校验会话号、持有者与权限，不要求确认：心跳、关闭与分片续接在确认前就可能到达。 */
    public @Nullable Refusal admitMember(@Nullable ServerPlayer player, String sessionId) {
        return decide(player, sessionId, false, true);
    }

    /** 要求客户端已确认：读取类请求与提交都依赖确认后的目标锁。 */
    public @Nullable Refusal admitConfirmed(@Nullable ServerPlayer player, String sessionId) {
        return decide(player, sessionId, true, true);
    }

    private @Nullable Refusal decide(@Nullable ServerPlayer player, String sessionId, boolean requireConfirmed, boolean requirePermission) {
        EditSessionManager.SessionInfo session = sessions.session();
        boolean isOwner = player != null && session != null && player.getUUID().equals(session.ownerUuid());
        if (session == null || !session.sessionId().equals(sessionId)) {
            RuleSaveStatus status = player == null || isOwner
                    ? RuleSaveStatus.SESSION_EXPIRED : RuleSaveStatus.LOCK_NOT_OWNED;
            return new Refusal(Reason.SESSION_MISMATCH, status);
        }
        if (player == null) {
            return new Refusal(Reason.PLAYER_ABSENT, RuleSaveStatus.SESSION_EXPIRED);
        }
        if (!isOwner) {
            return new Refusal(Reason.NOT_OWNER, RuleSaveStatus.LOCK_NOT_OWNED);
        }
        if (requireConfirmed && session.state() != EditSessionManager.SessionState.ACTIVE
                && session.state() != EditSessionManager.SessionState.APPLYING) {
            return new Refusal(Reason.NOT_CONFIRMED, RuleSaveStatus.SESSION_EXPIRED);
        }
        if (requirePermission && !permitted.test(player)) {
            return new Refusal(Reason.NO_PERMISSION, RuleSaveStatus.NO_PERMISSION);
        }
        return null;
    }
}
