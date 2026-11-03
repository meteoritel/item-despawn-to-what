package com.meteorite.itemdespawntowhat.client.ui.kit.api.preview;

/** GUI 三维图标的俯仰与深度压缩；不携带业务主题和文案。 */
public record UiSceneStyle(float pitchDegrees, float depthScale) {
    /** 图标使用轻俯视，深度压缩只作用于独立工作区内的模型。 */
    public static UiSceneStyle icon() { return new UiSceneStyle(22, 0.1F); }
}
