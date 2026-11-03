package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** 一次无副作用命中的归属链；局部坐标与实际叶子一起保留，供绘制和焦点政策共用。 */
public record UiPointerHit(List<UiInputTarget> owners, double x, double y,
                           @Nullable UiFocusTarget focus, @Nullable UiTarget tooltip, List<Row> rows, @Nullable Tooltip presentation) {
    /** 行身份只附着实际命中，不额外创建 Widget 或 Tab 位。 */
    public record Row(UiInputTarget owner, int index, Object key, UiRect bounds) { }

    /** 富提示的呈现 seam；业务解释与内容仍由宿主提供。 */
    @FunctionalInterface public interface Tooltip {
        /** 在当前提示域绘制宿主内容，位置使用 GUI 坐标。 */
        void render(GuiGraphics graphics, Font font, int x, int y);
    }

    /** 创建普通叶子或归属链，尚未附加行与富提示。 */
    public UiPointerHit(List<UiInputTarget> owners, double x, double y,
                        @Nullable UiFocusTarget focus, @Nullable UiTarget tooltip) {
        this(owners, x, y, focus, tooltip, List.of(), null);
    }
    /** 已解析但没有实际命中的快照，避免下层重复解析或穿透。 */
    public static final UiPointerHit EMPTY = new UiPointerHit(List.of(), 0, 0, null, null);

    /** 保存当前解析的不可变归属链。 */
    public UiPointerHit {
        owners = List.copyOf(owners);
        rows = List.copyOf(rows);
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
        return new UiPointerHit(chain, x, y, focus, tooltip, rows, presentation);
    }

    /** 以对象身份判断包装、核心或容器是否属于本次命中。 */
    public boolean owns(UiInputTarget target) {
        for (UiInputTarget owner : owners) if (owner == target) return true;
        return false;
    }

    /** 保留命中归属，附加已经选定的提示来源。 */
    public UiPointerHit withTooltip(@Nullable UiTarget target) {
        return new UiPointerHit(owners, x, y, focus, target, rows, presentation);
    }

    /** 内部模式或操作区自行处理焦点，外部派发仅记录有效命中。 */
    public UiPointerHit preservingFocus() {
        return new UiPointerHit(owners, x, y, null, tooltip, rows, presentation);
    }

    /** 追加本层实际行，嵌套表单与列表分别保留自己的身份。 */
    public UiPointerHit withRow(UiInputTarget owner, int index, Object key, UiRect bounds) {
        List<Row> chain = new ArrayList<>(rows);
        chain.add(new Row(owner, index, key, bounds));
        return new UiPointerHit(owners, x, y, focus, tooltip, chain, presentation);
    }
    /** 查询特定容器的行；留白、滚动条与视口外返回 -1。 */
    public int rowIndex(UiInputTarget owner) {
        for (Row row : rows) if (row.owner() == owner) return row.index();
        return -1;
    }
    /** 普通提示也从同一次命中产生；空内容显式截断下层查询。 */
    public UiPointerHit withTextTooltip(List<Component> text, UiRect bounds) {
        return withTooltip(new UiTarget(UiTarget.Kind.ROW, -1, null, bounds, text, null));
    }
    /** 富提示与普通提示共用唯一目标和门禁。 */
    public UiPointerHit withPresentation(@Nullable Tooltip painter) {
        return new UiPointerHit(owners, x, y, focus, tooltip, rows, painter);
    }
    /** 普通文字和富内容的共同呈现出口；调用方先检查作用域门禁。 */
    public void renderTooltip(GuiGraphics graphics, Font font, UiSurfaceStyle style, int pointerX, int pointerY) {
        if (presentation != null) presentation.render(graphics, font, pointerX, pointerY);
        else if (tooltip != null && !tooltip.tooltip().isEmpty()) UiFlatText.componentTooltip(graphics, font,
                new UiRect(0, 0, graphics.guiWidth(), graphics.guiHeight()), tooltip.tooltip(),
                graphics.guiWidth(), pointerX, pointerY, style);
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
