package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;

/**
 * 滚动控件的视口换算：行内容宽度、屏幕纵向坐标到内容坐标、固定行高下的行下标。
 * <p>滚动条由 {@link UiScrollView} 画在视口右侧并占用 {@link UiScrollView#SCROLLBAR_WIDTH} 宽，
 * 行宽与行命中都得分出这段宽度，所以预留值只有一处：控件不再各自声明常量，滚动条出现时
 * 各控件的行宽变化量因此一致。
 * <p>本类无状态，只做换算。命中门禁复用 {@link UiScrollView#canHoverContent}；行高策略仍由各控件决定：
 * 固定行高走 {@link #fixedRowIndex}，非等高行由控件自己做二分查找。
 */
final class RowGeometry {

    // 滚动条可见时行内容让出的宽度：与 kit 绘制滚动条占用的宽度同源
    static final int SCROLLBAR_RESERVE = UiScrollView.SCROLLBAR_WIDTH;

    private RowGeometry() {
    }

    // 内容区可用宽度：视口宽扣掉滚动条占用，负值按 0 钳制
    static int contentWidth(UiScrollView view) {
        int width = view.viewport().width();
        if (view.isScrollbarVisible()) {
            width -= SCROLLBAR_RESERVE;
        }
        return Math.max(0, width);
    }

    // 屏幕纵向坐标换算为内容坐标；调用方先用 UiScrollView.canHoverContent 排除视口外与滚动条，结果非负
    static int contentY(UiScrollView view, double mouseY) {
        return (int) (mouseY - view.viewport().y()) + view.offset();
    }

    // 固定行高控件的行下标：落在行间距留白（行内容比行距矮时）或越界返回 -1
    static int fixedRowIndex(int contentY, int rowPitch, int rowHeight, int rowCount) {
        int pitch = Math.max(1, rowPitch);
        if (contentY % pitch >= rowHeight) {
            return -1;
        }
        int index = contentY / pitch;
        return index >= 0 && index < rowCount ? index : -1;
    }
}
