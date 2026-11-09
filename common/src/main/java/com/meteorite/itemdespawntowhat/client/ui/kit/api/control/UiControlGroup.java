package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import java.util.function.Consumer;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 控件组：按**显式层序**绘制控件、返回最上层命中目标与唯一 tooltip，并把可聚焦控件交给
 * {@link UiFocusManager}（见 {@link #addFocusTargets(Consumer)}），按压交给命中控件的作用域会话。
 *
 * <p>宿主稳定持有控件实例：内容或尺寸变化时调用 {@link #beginUpdate()} / {@link #obtain(Object)} /
 * {@link #endUpdate()}，同一个稳定 key 永远拿到同一个 {@link UiControl}；滚动、悬停等逐帧操作只改状态，
 * 不重建控件、也不重新测量文字。内容未变时宿主不必调用更新三件套。</p>
 *
 * <p>组内按逆绘制顺序命中，宿主把本组装入所属作用域的内容树；持续拖动与释放由路由器送达
 * 会话目标。坐标一律与控件矩形同坐标系，
 * 放在 {@code UiScrollView} 的平移 pose 内调用时传入内容坐标即可。</p>
 */
public final class UiControlGroup implements UiInputContainer {
    /**
     * 稳定 key → 控件；迭代顺序即创建顺序，也是绘制与命中的层序（后创建者在上层）。
     */
    private final Map<Object, UiControl> byKey = new LinkedHashMap<>();
    private final UiChildren<UiControl> ordered = new UiChildren<>();
    private final Set<Object> seen = new HashSet<>();
    private @Nullable UiControlStyle style;
    private UiStyleSource theme = UiMinecraftTheme.INSTANCE;
    /**
     * 是否处于 beginUpdate/endUpdate 之间；不在区间内时 endUpdate 不做裁剪，避免误清空。
     */
    private boolean updating;

    // ---------- 稳定 key 复用 ----------

    /**
     * 开始一次内容更新：随后未再 {@link #obtain(Object)} 的 key 会在 {@link #endUpdate()} 时移除。
     */
    public void beginUpdate() {
        seen.clear();
        updating = true;
    }

    /**
     * 按稳定 key 取控件；不存在时创建。必须在 {@link #beginUpdate()} 与 {@link #endUpdate()} 之间调用。
     */
    public UiControl obtain(Object key) {
        Objects.requireNonNull(key, "Control key");
        seen.add(key);
        UiControl control = byKey.get(key);
        if (control == null) {
            control = new UiControl();
            control.setFocusable(true);
            control.setTheme(theme, UiControlRole.SECONDARY);
            if (style != null) control.setStyle(style);
            byKey.put(key, control);
            ordered.add(control);
        }
        return control;
    }

    /**
     * 结束内容更新：丢弃本次未复用的控件，并清理落在它们上面的按压与焦点引用。
     */
    public void endUpdate() {
        if (updating) {
            for (var entry : byKey.entrySet()) {
                if (!seen.contains(entry.getKey()))
                    entry.getValue().endInteraction(UiInteractionSession.EndReason.UNMOUNTED);
            }
            byKey.keySet().retainAll(seen);
            // 控件数量在几十量级，线性判定比再维护一份下标映射更省代码。
            ordered.removeIf(control -> !byKey.containsValue(control));
        }
        updating = false;
        seen.clear();

    }

    /**
     * 清空全部控件与交互状态；重新填充前层序为空。
     */
    public void clear() {
        for (UiControl control : ordered) control.endInteraction(UiInteractionSession.EndReason.UNMOUNTED);
        byKey.clear();
        ordered.clear();
        seen.clear();

    }

    public int size() {
        return ordered.size();
    }

    public boolean isEmpty() {
        return ordered.isEmpty();
    }

    /**
     * 只读视图，按绘制层序（底层在前）；宿主据此在浮层里叠加原生控件。
     */
    public List<UiControl> controls() {
        return List.copyOf(ordered);
    }

    /** 实际成员同时供绘制、命中和导航使用。 */
    @Override public UiChildren<UiControl> inputChildren() { return ordered; }

    // ---------- 样式 ----------

    /**
     * 设置默认样式；已存在与新创建的控件一并套用。
     */
    public void setStyle(UiControlStyle style) {
        this.style = Objects.requireNonNull(style);
        for (UiControl control : ordered) control.setStyle(style);
    }

    /** 已有及后续控件共享根主题；显式 setStyle 覆盖结构画法。 */
    public void setTheme(UiStyleSource theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        style = null;
        for (UiControl control : ordered) control.setTheme(theme, UiControlRole.SECONDARY);
    }

    public UiControlStyle style() {
        return style == null ? theme.controlStyle(UiControlRole.SECONDARY) : style;
    }

    // ---------- 绘制 / 命中 ----------

    // 不可见控件直接跳过（UiControl.render 同样会提前返回），省掉一次逐帧虚调用。
    // 本类不自行设置视口裁剪：宿主把它放在 UiScrollView 的平移 pose 内时，裁剪已由滚动视口给出。
    public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);

        for (UiControl control : ordered) {
            if (control.isVisible()) control.render(graphics, font, pointer);
        }
    }

    /**
     * 最上层命中控件；被上层控件遮挡时不会命中下层，与绘制层序一致。
     */
    @Nullable
    public UiControl controlAt(double x, double y) {
        UiPointerHit hit = hitTest(x, y);
        if (hit == null) return null;
        for (var owner : hit.owners()) if (owner instanceof UiControl control) return control;
        return null;
    }

    /**
     * 唯一提示来源；激活由命中控件的输入契约处理。
     */
    @Nullable
    public UiPointerHit.TextTooltip tooltipAt(double x, double y) {
        UiPointerHit hit = hitTest(x, y);
        return hit == null ? null : hit.tooltip();
    }

    /**
     * 同一位置只能有一个 tooltip 来源：只画最上层命中且提示非空的目标。
     */
    public void renderTooltip(GuiGraphics graphics, Font font, UiPointer pointer) {
        if (!pointer.allowsTooltip() || pointer.hit() == null) return;
        pointer.hit().renderTooltip(graphics, font, theme.tooltipSkin(), (int) pointer.x(), (int) pointer.y());
    }

}
