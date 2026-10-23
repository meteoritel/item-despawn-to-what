package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;

import java.util.Arrays;

/** 有界的原始耗时样本；结束时计算精确 nearest-rank 分位数，不在 tick 中排序。 */
final class DebugMeasurements {
    static final int MAX_SAMPLES = 12_000;
    private long[] samples = new long[128];
    private int size;
    private long sum;
    private long max;

    // 添加一个非负微秒样本；容量上限与整个会话上限一致。
    void add(long microseconds) {
        if (size >= MAX_SAMPLES) { return; }
        if (size == samples.length) { samples = Arrays.copyOf(samples, Math.min(MAX_SAMPLES, size * 2)); }
        long value = Math.max(0, microseconds);
        samples[size++] = value;
        sum += value;
        max = Math.max(max, value);
    }

    // 样本数用于确定采样窗口和输出可信度。
    int size() { return size; }

    // 输出单位统一为微秒，空窗口明确返回零个样本。
    JsonObject summary() {
        JsonObject result = new JsonObject();
        result.addProperty("samples", size);
        result.addProperty("mean_us", size == 0 ? 0 : (double) sum / size);
        result.addProperty("max_us", max);
        long[] ordered = Arrays.copyOf(samples, size);
        Arrays.sort(ordered);
        result.addProperty("p50_us", percentile(ordered, 0.50));
        result.addProperty("p95_us", percentile(ordered, 0.95));
        result.addProperty("p99_us", percentile(ordered, 0.99));
        return result;
    }

    // nearest-rank 选择真实样本，不作插值，也不使用生命周期峰值替代窗口分位数。
    private static long percentile(long[] values, double fraction) {
        return values.length == 0 ? 0 : values[(int) Math.ceil(values.length * fraction) - 1];
    }
}
