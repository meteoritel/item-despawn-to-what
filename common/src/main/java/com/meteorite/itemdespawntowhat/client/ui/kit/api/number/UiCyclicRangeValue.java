package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

/** 周期区间的不可变有向快照；from 大于 to 表示跨周期，两端身份保持不变。 */
public record UiCyclicRangeValue(double from, double to) {
    /** 两端必须是核心已经折返后的有限值，周期归一化由核心负责。 */
    public UiCyclicRangeValue {
        if (!Double.isFinite(from) || !Double.isFinite(to)) {
            throw new IllegalArgumentException("Cyclic endpoints must be finite");
        }
    }
}
