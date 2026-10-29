package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

/** 场景准备与绘制的语义状态；提示文案由宿主本地化。 */
public enum PreviewState {
    PREPARING, READY, EMPTY, MISSING_SUBJECT, NO_LEVEL, TOO_LARGE, FAILED;

    /** 准备等待不是失败，宿主只为终态不可用补充提示。 */
    public boolean unavailable() {
        return this != READY && this != PREPARING;
    }
}
