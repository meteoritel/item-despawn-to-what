package com.meteorite.itemdespawntowhat.client.ui.widget;

/**
 * 由弹窗按钮行托管底部内容的控件。
 * <p>{@link UiModal#inlineFooter()} 打开后，弹窗把按钮行也算进内容区高度，并在布局完成后把按钮实际占用的
 * 右侧宽度与行高交给内容控件；控件据此把自己的底部行（状态、翻页）排到按钮左边，避免再单独占一行。</p>
 * <p>未实现本接口的内容控件不受影响：弹窗只是多给一行高度，按钮仍在自己那一行上。</p>
 */
public interface UiFooterHost {

    // right：按钮占用的右侧宽度；height：按钮行高度（0 表示没有可见按钮）
    void setFooterReserved(int right, int height);
}
