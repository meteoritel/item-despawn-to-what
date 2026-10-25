package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.List;

/**
 * 能把当前排版结果导成只读快照的宿主或控件。
 *
 * <p>实现方负责把自身持有的几何换算成 GUI 坐标后返回：控件的 {@code bounds}、文档的行几何、
 * 滚动容器的滚动偏移都在实现内部结清，调用方拿到的是「屏幕上看到的那个位置」。
 * 没有内容时返回空列表——这条约定让诊断方无需区分空界面与未实现。</p>
 *
 * <p>快照是只读的：调用方不得据此改几何，kit 也不从快照读回状态。</p>
 */
public interface UiLayoutSource {
    /** 当前排版快照；坐标为 GUI 坐标，无内容时返回空列表。 */
    List<UiLayoutNode> layoutNodes();
}
