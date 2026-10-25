package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 一次排版的只读快照节点：矩形、文案与文本带宽都取自排版缓存，供外部诊断与自动化测试读取。
 *
 * <p>快照不参与绘制与命中，只描述「这一帧排成什么样」。{@code textWidth} 是文案的实际测量宽度，
 * {@code textBudget} 是留给文案的容量：文档行给的是行内带宽，超宽时排版只占带宽部分、其余靠悬停滚动补齐；
 * 控件节点给的是内容区宽度。两者都按同一口径参与 {@link #clipped()}，为真表示文案装不下。</p>
 *
 * <p>{@code enabled} 与 {@code visible} 只对控件类节点有意义；行、分隔线与 Frame 这类排版产物恒为真。</p>
 *
 * @param kind 节点种类
 * @param rect 节点矩形（GUI 坐标，绝对位置）
 * @param text 节点文案；无文案时为空文本，永不为 {@code null}
 * @param textWidth 文案测量宽度
 * @param textBudget 文案可用带宽；0 表示该节点不做带宽裁剪
 * @param enabled 节点当前是否可交互
 * @param visible 节点当前是否可见
 * @param children 子节点；叶节点为空列表，永不为 {@code null}
 */
public record UiLayoutNode(Kind kind, UiRect rect, Component text, int textWidth, int textBudget,
                           boolean enabled, boolean visible, List<UiLayoutNode> children) {
    /** 节点种类：控件、文本输入、树、文档行、分隔线、文档 Frame、模态层。 */
    public enum Kind { CONTROL, TEXT_INPUT, TREE, ROW, DIVIDER, FRAME, MODAL }

    public UiLayoutNode {
        text = text == null ? Component.empty() : text;
        children = List.copyOf(children);
    }

    /** 文案按带宽裁过：可用带宽装不下完整文案。 */
    public boolean clipped() {
        return textBudget > 0 && textWidth > textBudget;
    }
}
