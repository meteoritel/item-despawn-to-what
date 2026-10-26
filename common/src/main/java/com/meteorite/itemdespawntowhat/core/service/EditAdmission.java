package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Exclusive Edit Session 的统一准入：把（请求者, 会话号）按 owns → isOwner → confirmed → permission
 * 的固定顺序判定，产出已授权会话句柄或带状态码的类型化拒绝。
 *
 * <p>准入必须只有一处：分散判定时每个入口都会对「先判会话号还是先判持有者」「拒绝用哪个
 * 状态码」给出略有差别的答案，客户端只能按最宽松的分支兜底。状态码选择因此也收在这里，
 * 由 {@link Reason} 一张表映射，调用方只负责组装载荷。</p>
 *
 * <p>权限判定不在本类实现：编辑权限属于命令侧策略（{@link RuleEditPermissions}），
 * 由构造点注入谓词后服务层不再依赖网络入口。</p>
 */
public final class EditAdmission {

    /** 拒绝原因；决定回执状态码，也决定调用方是否中止在途工作。 */
    public enum Reason {
        /** 会话号不属于当前会话：空闲、已释放或已换会话。 */
        SESSION_MISMATCH,
        /** 会话存在，但请求者不是持有者。 */
        NOT_OWNER,
        /** 会话尚未被客户端确认，持有者身份仍可能变化。 */
        NOT_CONFIRMED,
        /** 持有者已下线，既无法确认身份也无法判定权限。 */
        PLAYER_ABSENT,
        /** 持有者仍在，但已失去编辑权限。 */
        NO_PERMISSION
    }

    /** 已授权会话句柄：只承载通过准入时的会话身份。 */
    public record Authorized(String sessionId, UUID ownerUuid) implements Decision {
    }

    /** 类型化拒绝：状态码与文案 key 在此确定，调用方不得自行改写。 */
    public record Refusal(Reason reason, RuleSaveStatus status, String messageCode) implements Decision {
    }

    /** 准入结论。 */
    public sealed interface Decision permits Authorized, Refusal {
    }

    private final EditSessionManager sessions;
    private final Predicate<ServerPlayer> permitted;

    public EditAdmission(EditSessionManager sessions, Predicate<ServerPlayer> permitted) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.permitted = Objects.requireNonNull(permitted, "permitted");
    }

    /**
     * 只校验会话号归属，用于没有玩家上下文的续接（快照交付）：内容已在请求时刻完成授权。
     */
    public Decision admitSession(String sessionId) {
        return decide(null, sessionId, false, false);
    }

    /**
     * 校验会话号与持有者，不判权限：取消保存只是撤回本人仍在途的请求，不新增写权限。
     */
    public Decision admitOwner(@Nullable ServerPlayer player, String sessionId) {
        return decide(player, sessionId, false, false);
    }

    /**
     * 校验会话号、持有者与权限，不要求确认：心跳与关闭在确认前就可能到达，
     * 分片累积与保存续接同样只需持有者身份。
     */
    public Decision admitMember(@Nullable ServerPlayer player, String sessionId) {
        return decide(player, sessionId, false, true);
    }

    /**
     * 在会员要求之上要求客户端已确认：读取类请求与提交都依赖确认后的目标锁。
     */
    public Decision admitConfirmed(@Nullable ServerPlayer player, String sessionId) {
        return decide(player, sessionId, true, true);
    }

    /** 取拒绝结论；已授权时返回 {@code null}。 */
    public static @Nullable Refusal refusal(Decision decision) {
        return decision instanceof Refusal rejection ? rejection : null;
    }

    private Decision decide(@Nullable ServerPlayer player, String sessionId, boolean requireConfirmed, boolean requirePermission) {
        EditSessionManager.SessionInfo session = sessions.session();
        if (session == null || !sessions.owns(sessionId)) {
            return reject(player, Reason.SESSION_MISMATCH);
        }
        if (player == null) {
            return reject(null, Reason.PLAYER_ABSENT);
        }
        if (!sessions.isOwner(player.getUUID())) {
            return reject(player, Reason.NOT_OWNER);
        }
        if (requireConfirmed && !sessions.confirmed(sessionId)) {
            return reject(player, Reason.NOT_CONFIRMED);
        }
        if (requirePermission && !permitted.test(player)) {
            return reject(player, Reason.NO_PERMISSION);
        }
        return new Authorized(sessionId, session.ownerUuid());
    }

    private Refusal reject(@Nullable ServerPlayer player, Reason reason) {
        RuleSaveStatus status = switch (reason) {
            case NOT_OWNER -> RuleSaveStatus.LOCK_NOT_OWNED;
            case NOT_CONFIRMED, PLAYER_ABSENT -> RuleSaveStatus.SESSION_EXPIRED;
            case NO_PERMISSION -> RuleSaveStatus.NO_PERMISSION;
            case SESSION_MISMATCH -> player == null || sessions.isOwner(player.getUUID())
                    ? RuleSaveStatus.SESSION_EXPIRED
                    : RuleSaveStatus.LOCK_NOT_OWNED;
        };
        return new Refusal(reason, status, status.messageKey());
    }
}
