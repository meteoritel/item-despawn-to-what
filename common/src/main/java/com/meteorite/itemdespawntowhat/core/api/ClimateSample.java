package com.meteorite.itemdespawntowhat.core.api;

/**
 * 原版 MultiNoiseBiomeSource 的 6 个气候参数采样值（量纲与参数化区间一致）。
 */
public record ClimateSample(
        double temperature,
        double humidity,
        double continentalness,
        double erosion,
        double depth,
        double weirdness
) {
}
