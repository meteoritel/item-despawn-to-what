package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import org.jetbrains.annotations.Nullable;

/** 气候区间的共享声明：端点值域供后端校验与客户端投影共用，步长只影响编辑交互。 */
public final class ClimateDomain {

    /** 两端使用同一声明值域；字段路径由各端点的名称派生。 */
    public static final DoubleSpec MIN = new DoubleSpec(
            ParamMeta.of("min", ParamPresence.NULLABLE, false), -1.0D, 1.0D, 0.0D);
    public static final DoubleSpec MAX = new DoubleSpec(
            ParamMeta.of("max", ParamPresence.NULLABLE, false), MIN.min(), MIN.max(), 0.0D);
    public static final double STEP = 0.1D;
    public static final double FINE_STEP = 0.01D;

    private ClimateDomain() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 校验可空端点的有限性与声明值域；端点顺序由区间所属类型负责。 */
    public static boolean check(@Nullable Double min, @Nullable Double max, IssueCollector issues, String path) {
        boolean valid = MIN.check(min, issues, path);
        valid &= MAX.check(max, issues, path);
        return valid;
    }
}
