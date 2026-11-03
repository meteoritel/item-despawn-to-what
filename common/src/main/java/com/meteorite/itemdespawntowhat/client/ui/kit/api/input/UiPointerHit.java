package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTarget;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** 一次无副作用命中的归属链；局部坐标与实际叶子一起保留，供绘制和焦点政策共用。 */
public record UiPointerHit(List<UiInputTarget> owners, double x, double y,
                           @Nullable UiFocusTarget focus, @Nullable UiTarget tooltip) {
    /** 已解析但没有实际命中的快照，避免下层重复解析或穿透。 */
    public static final UiPointerHit EMPTY = new UiPointerHit(List.of(), 0, 0, null, null);

    /** 保存当前解析的不可变归属链。 */
    public UiPointerHit {
        owners = List.copyOf(owners);
    }

    /** 普通叶子命中；焦点能力与事件是否消费相互独立。 */
    public static UiPointerHit leaf(UiInputTarget owner, double x, double y) {
        return new UiPointerHit(List.of(owner), x, y,
                owner instanceof UiFocusTarget target ? target : null, null);
    }

    /** 将容器加入归属链，保持实际叶子与其局部坐标。 */
    public UiPointerHit within(UiInputTarget parent) {
        if (owns(parent)) return this;
        List<UiInputTarget> chain = new ArrayList<>(owners.size() + 1);
        chain.add(parent);
        chain.addAll(owners);
        return new UiPointerHit(chain, x, y, focus, tooltip);
    }

    /** 以对象身份判断包装、核心或容器是否属于本次命中。 */
    public boolean owns(UiInputTarget target) {
        for (UiInputTarget owner : owners) if (owner == target) return true;
        return false;
    }

    /** 保留命中归属，附加已经选定的提示来源。 */
    public UiPointerHit withTooltip(@Nullable UiTarget target) {
        return new UiPointerHit(owners, x, y, focus, target);
    }

    /** 内部模式或操作区自行处理焦点，外部派发仅记录有效命中。 */
    public UiPointerHit preservingFocus() {
        return new UiPointerHit(owners, x, y, null, tooltip);
    }

    /** 按输入优先级查询子目标，不消费事件；容器需先检查自己的裁剪。 */
    public static @Nullable UiPointerHit first(double x, double y, UiInputTarget... targets) {
        for (UiInputTarget target : targets) {
            if (target == null) continue;
            UiPointerHit hit = target.hitTest(x, y);
            if (hit != null) return hit;
        }
        return null;
    }

    /** 已有列表按逆绘制顺序解析，避免额外维护输入树。 */
    public static @Nullable UiPointerHit topmost(double x, double y, List<? extends UiInputTarget> targets) {
        for (int index = targets.size() - 1; index >= 0; index--) {
            UiPointerHit hit = targets.get(index).hitTest(x, y);
            if (hit != null) return hit;
        }
        return null;
    }
}
