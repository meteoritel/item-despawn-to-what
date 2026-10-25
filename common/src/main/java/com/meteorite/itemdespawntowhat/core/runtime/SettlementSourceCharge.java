package com.meteorite.itemdespawntowhat.core.runtime;

import java.util.function.IntConsumer;

/**
 * 单组源成本的实际占用：各效果共享同一组成本，按最高占用计费。
 * 方块按成功数量占用成本，整数源物品向上取整；其他行为与概率尝试占满本组成本。
 * 未占用的源物品一直保留在结算库存中，取消和恢复沿用同一条返还路径。
 * 组内唯一效果因安全选址失败而没有产出时，本组已占用的成本经释放通道原样退回结算库存；
 * 退回后仍可继续占用，账目按最新占用重算。
 */
final class SettlementSourceCharge {
    private final int cost;
    private final IntConsumer consume;
    private final IntConsumer release;
    private int charged;

    SettlementSourceCharge(int cost, IntConsumer consume, IntConsumer release) {
        this.cost = cost;
        this.consume = consume;
        this.release = release;
    }

    void chargeFully() {
        chargeTo(cost);
    }

    IntConsumer placementProgress(int resultBudget, IntConsumer progress) {
        return new IntConsumer() {
            private long placed;

            @Override
            public void accept(int units) {
                placed += units;
                chargeTo((int) Math.min(cost, (placed * cost + resultBudget - 1) / resultBudget));
                progress.accept(units);
            }
        };
    }

    // 释放本组已占用的成本：占用归零并等额退回，返回退回数量（未占用时为 0）。
    // 只用于「本组没有任何真实产出」的失败；释放后若同组还有效果继续占用，会按新的占用重新扣回。
    int releaseCharged() {
        int released = charged;
        if (released <= 0) {
            return 0;
        }
        charged = 0;
        release.accept(released);
        return released;
    }

    private void chargeTo(int required) {
        if (required > charged) {
            consume.accept(required - charged);
            charged = required;
        }
    }
}
