package com.meteorite.itemdespawntowhat.core.api;

/**
 * 效果执行回执：结算层记账的唯一依据。
 * appliedUnits 为「实际完成量」（物品件数 / 实体个数 / 方块数 / 经验点数 / 世界效果次数），
 * pendingUnits 为「已受理但尚未完成量」（分批生成、延迟生成），
 * 二者由异步批次通过 {@link EffectContext#reportProgress(int)} 逐步收敛。
 * oneShot 标记一次性世界效果：不参与容量计算，对同一源最多尝试一组。
 */
public record EffectResult(Outcome outcome, int appliedUnits, int pendingUnits, boolean oneShot, String detail) {

    // 效果结论：已应用 / 已受理待完成 / 跳过 / 失败
    public enum Outcome { APPLIED, DEFERRED, SKIPPED, FAILED }

    public EffectResult {
        outcome = outcome == null ? Outcome.SKIPPED : outcome;
        appliedUnits = Math.max(0, appliedUnits);
        pendingUnits = Math.max(0, pendingUnits);
        detail = detail == null ? "" : detail;
    }

    // 同步完成：units 为真实完成量
    public static EffectResult applied(int units) {
        return new EffectResult(Outcome.APPLIED, units, 0, false, "");
    }

    // 同步完成并附带说明
    public static EffectResult applied(int units, String detail) {
        return new EffectResult(Outcome.APPLIED, units, 0, false, detail);
    }

    // 已受理（分批/延迟生成），等待 reportProgress 收敛为实际完成量
    public static EffectResult deferred(int units) {
        return new EffectResult(Outcome.DEFERRED, 0, units, false, "");
    }

    // 已受理并附带说明
    public static EffectResult deferred(int units, String detail) {
        return new EffectResult(Outcome.DEFERRED, 0, units, false, detail);
    }

    // 未执行：条件/概率/容量上限/资源不足等，属于已支付组内的正常结果
    public static EffectResult skipped(String reason) {
        return new EffectResult(Outcome.SKIPPED, 0, 0, false, reason);
    }

    // 执行失败：异常或配置错误，由结算层单独计数
    public static EffectResult failed(String reason) {
        return new EffectResult(Outcome.FAILED, 0, 0, false, reason);
    }

    // 标记为一次性世界效果（运行时按效果类型的注册信息补写）
    public EffectResult asOneShot() {
        return oneShot ? this : new EffectResult(outcome, appliedUnits, pendingUnits, true, detail);
    }

    // 是否计入本组账目（跳过与失败不计入产出）
    public boolean counted() {
        return outcome == Outcome.APPLIED || outcome == Outcome.DEFERRED;
    }
}
