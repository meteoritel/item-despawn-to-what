package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

/** GUI 三维图标的俯仰与深度压缩；不携带业务主题和文案。 */
public record UiSceneStyle(float pitchDegrees, float depthScale) {
    /** 图标默认使用轻俯视和压缩深度，保持 GUI 层级。 */
    public static UiSceneStyle icon() { return new UiSceneStyle(22, 0.1F); }
}
