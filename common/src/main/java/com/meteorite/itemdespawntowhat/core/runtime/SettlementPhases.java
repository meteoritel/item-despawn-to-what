package com.meteorite.itemdespawntowhat.core.runtime;

/**
 * 结算阶段状态机：只维护「计划 → 准备 → 派发 → 交付 → 完成」的当前阶段与合法跃迁。
 * 阶段推进的决定权在聚合根：本类只接收已判定的事件（计划定稿、准备完毕、派发完毕、交付完毕），
 * 不读取账目也不写世界，因此取消与异常路径仍由聚合根按固定顺序收尾。
 */
final class SettlementPhases {

    // 结算阶段：一次性效果的领先准备已覆盖全部组时会跳过准备阶段，直接进入派发
    enum Phase { PLANNING, PREPARING, DISPATCHING, DELIVERING, DONE }

    private Phase phase = Phase.PLANNING;

    Phase current() {
        return phase;
    }

    // 计划定稿：还有未固定的组就进入准备阶段，否则直接派发
    void beginPreparingOrDispatching(boolean allPrepared) {
        phase = allPrepared ? Phase.DISPATCHING : Phase.PREPARING;
    }

    // 承诺的组全部固定输入完成
    void preparingCompleted() {
        phase = Phase.DISPATCHING;
    }

    // 派发结束（含截断）：剩余库存进入交付
    void dispatchingCompleted() {
        phase = Phase.DELIVERING;
    }

    // 无需派发（计划为空或整批返还）：直接进入交付
    void skipToDelivering() {
        phase = Phase.DELIVERING;
    }

    // 返还全部交付完毕
    void deliveringCompleted() {
        phase = Phase.DONE;
    }
}
