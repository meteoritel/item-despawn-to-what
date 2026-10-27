package com.meteorite.itemdespawntowhat.core.service;

/** 编辑业务预算：条数与公共调度步骤独立于传输帧大小。 */
public final class RuleAuthoringLimits {
    public static final int MAX_EDITS_PER_CHANGE_SET = 2048;
    public static final int SAVE_ENTRIES_PER_STEP = 8;
    public static final long SAVE_STEP_BUDGET_NANOS = 1_000_000L;
    public static final int SNAPSHOT_ENTRIES_PER_STEP = 32;

    private RuleAuthoringLimits() { }
}
