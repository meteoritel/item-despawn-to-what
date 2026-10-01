package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * 类型参数语义校验的公共助手。
 * 各内置效果/条件类型在 validateParams 中复用本类，保证错误信息与字段路径格式一致。
 */
public final class ParamChecks {

    private ParamChecks() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 必填项：null 视为缺失
    public static boolean required(@Nullable Object value, String field, IssueCollector issues, String path) {
        if (value == null) {
            issues.error(field + " 不能为空", null, path);
            return false;
        }
        return true;
    }

    // 整型区间
    public static boolean inRange(int value, int min, int max, String field, IssueCollector issues, String path) {
        if (value < min || value > max) {
            issues.error(field + " 必须在 [" + min + ", " + max + "] 内，当前为 " + value, null, path);
            return false;
        }
        return true;
    }

    // 浮点区间
    public static boolean inRange(double value, double min, double max, String field, IssueCollector issues, String path) {
        if (value < min || value > max) {
            issues.error(field + " 必须在 [" + min + ", " + max + "] 内，当前为 " + value, null, path);
            return false;
        }
        return true;
    }

    // 必须为正数
    public static boolean positive(int value, String field, IssueCollector issues, String path) {
        if (value <= 0) {
            issues.error(field + " 必须为正数，当前为 " + value, null, path);
            return false;
        }
        return true;
    }

    // 集合非空
    public static boolean notEmpty(@Nullable Collection<?> values, String field, IssueCollector issues, String path) {
        if (values == null || values.isEmpty()) {
            issues.error(field + " 不能为空", null, path);
            return false;
        }
        return true;
    }

    // 两值构成的区间：min 不得大于 max（任一为空表示该端不限制）
    public static boolean orderedRange(@Nullable Integer min, @Nullable Integer max, String field,
                                       IssueCollector issues, String path) {
        if (min != null && max != null && min > max) {
            issues.error(field + " 的下界 " + min + " 不能大于上界 " + max, null, path);
            return false;
        }
        return true;
    }

    // 浮点两值区间：min 不得大于 max
    public static boolean orderedRange(@Nullable Double min, @Nullable Double max, String field,
                                       IssueCollector issues, String path) {
        if (min != null && max != null && min > max) {
            issues.error(field + " 的下界 " + min + " 不能大于上界 " + max, null, path);
            return false;
        }
        return true;
    }

    // 拼接子字段路径
    public static String child(String path, String field) {
        return path == null || path.isEmpty() ? field : path + "." + field;
    }

    // 拼接带下标的子字段路径
    public static String index(String path, int index) {
        return path + "[" + index + "]";
    }
}
