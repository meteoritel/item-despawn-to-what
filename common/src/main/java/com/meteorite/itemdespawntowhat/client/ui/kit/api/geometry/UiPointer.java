package com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import org.jetbrains.annotations.Nullable;

/** 绘制指针快照；保存原始坐标、作用域门禁、捕获所有者和一次解析结果。 */
public record UiPointer(double x, double y, boolean allowed,
                        @Nullable UiInteractionSession.Target capture, @Nullable UiPointerHit hit) {
    /** 没有作用域的只读绘制使用普通指针。 */
    public static UiPointer at(double x, double y) {
        return new UiPointer(x, y, true, null, null);
    }

    /** 容器在当前布局上只解析一次；兄弟分支读取同一命中结果。 */
    public UiPointer resolve(UiInputTarget target) {
        if (!allowed || hit != null) return this;
        UiPointerHit resolved = target.hitTest(x, y);
        return new UiPointer(x, y, true, capture, resolved == null ? UiPointerHit.EMPTY : resolved);
    }

    /** 为子分支选择指针；捕获时只有拥有该会话目标的分支可悬停。 */
    public UiPointer forTarget(UiInputTarget target) {
        boolean inBranch = hit == null || hit.owns(target);
        boolean ownsCapture = capture == null || capture == target || hit != null && hit.owns(capture);
        return new UiPointer(x, y, allowed && inBranch && ownsCapture, capture, hit);
    }

    /** 坐标变换保留归属与作用域门禁。 */
    public UiPointer withPosition(double nextX, double nextY) {
        return new UiPointer(nextX, nextY, allowed, capture, hit);
    }

    /** 模态遮挡或内容裁剪只收紧允许状态。 */
    public UiPointer restrict(boolean visible) {
        return new UiPointer(x, y, allowed && visible, capture, hit);
    }

    /** 当前分支可悬停且坐标落在实际几何内。 */
    public boolean contains(UiRect rect) {
        return allowed && rect.contains(x, y);
    }

    /** 持续指针交互期间关闭提示，键盘和文本会话不受影响。 */
    public boolean allowsTooltip() {
        return allowed && capture == null;
    }
}
