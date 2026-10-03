package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;

import java.util.Arrays;

/**
 * 有界的整数样本分布：与 DebugMeasurements 相同的 nearest-rank 口径与采样上限，
 * 但单位由调用方决定（比例、tick 数等），因此键名不带 _us 后缀。
 * 只做样本收集与结束时一次排序，不在 tick 内计算分位数。
 */
final class DebugDistribution {
    static final int MAX_SAMPLES = DebugMeasurements.MAX_SAMPLES;
    private long[] samples = new long[128];
    private int size;
    private long sum;
    private long max;

    // 添加一个样本（负值按 0 处理）；达到上限后停止采集，不对更长窗口伪造统计。
    void add(long value) {
        if (size >= MAX_SAMPLES) { return; }
        if (size == samples.length) { samples = Arrays.copyOf(samples, Math.min(MAX_SAMPLES, size * 2)); }
        long normalized = Math.max(0L, value);
        samples[size++] = normalized;
        sum += normalized;
        max = Math.max(max, normalized);
    }

    int size() { return size; }

    // 输出 samples/mean/max/p50/p95/p99；单位与调用方传入的一致，由字段语义说明。
    JsonObject summary() {
        JsonObject result = new JsonObject();
        result.addProperty("samples", size);
        result.addProperty("mean", size == 0 ? 0 : (double) sum / size);
        result.addProperty("max", max);
        long[] ordered = Arrays.copyOf(samples, size);
        Arrays.sort(ordered);
        result.addProperty("p50", percentile(ordered, 0.50));
        result.addProperty("p95", percentile(ordered, 0.95));
        result.addProperty("p99", percentile(ordered, 0.99));
        return result;
    }

    // nearest-rank 选择真实样本，不作插值，也不使用生命周期峰值替代窗口分位数。
    private static long percentile(long[] values, double fraction) {
        return values.length == 0 ? 0 : values[(int) Math.ceil(values.length * fraction) - 1];
    }
}
