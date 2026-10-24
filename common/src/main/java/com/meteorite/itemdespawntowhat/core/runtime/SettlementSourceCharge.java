package com.meteorite.itemdespawntowhat.core.runtime;

import java.util.function.IntConsumer;

/**
 * 单组源成本的实际占用：各效果共享同一组成本，按最高占用计费。
 * 方块按成功数量占用成本，整数源物品向上取整；其他行为与概率尝试占满本组成本。
 * 未占用的源物品一直保留在结算库存中，取消和恢复沿用同一条返还路径。
 */
final class SettlementSourceCharge {
    private final int cost;
    private final IntConsumer consume;
    private int charged;

    SettlementSourceCharge(int cost, IntConsumer consume) {
        this.cost = cost;
        this.consume = consume;
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

    private void chargeTo(int required) {
        if (required > charged) {
            consume.accept(required - charged);
            charged = required;
        }
    }
}
