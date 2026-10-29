package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

/** 转台参数，时间由宿主传入；周期为零表示静态展示。 */
public record PreviewMotion(float startYawDegrees, long periodMillis) {
    /** 计算当前 yaw，角度单位为弧度。 */
    public float yaw(long timeMillis) {
        double degrees = startYawDegrees;
        if (periodMillis > 0) degrees += Math.floorMod(timeMillis, periodMillis) * 360.0 / periodMillis;
        return (float) Math.toRadians(degrees);
    }
}
