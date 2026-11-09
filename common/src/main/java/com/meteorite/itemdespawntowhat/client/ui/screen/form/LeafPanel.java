package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiShellWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidgetShell;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCyclicTimeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 叶子面板：附加区间控件 + 目录按钮 + 表单，纵向排列。
 * <p>面板自身是模态内容控件，因此它负责把指针/键盘/字符事件转发给子控件，并在内部维护
 * Tab / Shift+Tab 的焦点顺序（附加控件 -> 目录按钮 -> 表单字段）。
 */
public final class LeafPanel implements UiShellWidget {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private final UiHostTheme THEME;
    private static final String UI = TypeLabels.UI_PREFIX;
    // 区间条 / 昼夜条高度（标签行 + 轨道；昼夜条另含预设行）
    private static final int RANGE_HEIGHT = 24;
    private static final int CYCLIC_HEIGHT = 40;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 2;
    private static final int EXTRA_GAP = 2;

    // 按钮宽度：先满足最小可点宽度，再压进可用宽度（可用宽度可能小于最小值）
    // 这里刻意不用 Math.clamp：可用宽度可能小于最小宽度，clamp 的 min>max 会抛异常
    @SuppressWarnings("ManualMinMaxCalculation")
    private static int fitWidth(int preferred, int available) {
        int atLeastMin = Math.max(preferred, 60);
        return available < atLeastMin ? available : atLeastMin;
    }

    private final Font font;
    private final FormView form;
    private final List<UiWidget> extras;
    private final List<UiButton> buttons;
    private final List<Runnable> reloadValues;
    // 动态说明行（门槛留空时的「默认：N」等）：供应者返回 null 表示当前不画出该行
    private final List<Supplier<Component>> notes = new ArrayList<>();
    private final List<UiRect> noteRects = new ArrayList<>();
    // 说明行行高：比正文字高多 1 像素，避免与表单末行贴死
    private final int noteLineHeight;
    private final UiChildren<UiWidget> members = new UiChildren<>();
    private final UiWidgetShell shell = new UiWidgetShell(this);

    /** {@inheritDoc} */
    @Override
    public UiWidgetShell shell() {
        return shell;
    }

    public LeafPanel(Font font, FormView form, List<UiWidget> extras, List<UiButton> buttons, List<Runnable> reloadValues) {
        this.font = font;
        this.noteLineHeight = font.lineHeight + 1;
        this.form = form;
        this.THEME = form.theme();
        this.extras = extras;
        this.buttons = buttons;
        this.reloadValues = reloadValues;
        members.addAll(extras);
        members.addAll(buttons);
        members.add(form);
    }

    /**
     * 历史恢复后回填已接受的字段与区间值，未完成文本由表单保留。
     */
    public void reload() {
        form.reload();
        reloadValues.forEach(Runnable::run);
    }

    // 追加一条动态说明行：高度在布局时预留，内容每帧重新求值
    public LeafPanel addNote(Supplier<Component> note) {
        notes.add(note);
        return this;
    }

    // 面板内容总高（模态据此申请高度）
    public int contentHeight() {
        int height = 0;
        for (UiWidget extra : extras) {
            height += extraHeight(extra) + EXTRA_GAP;
        }
        if (!buttons.isEmpty()) {
            height += BUTTON_HEIGHT + BUTTON_GAP;
        }
        height += form.contentHeight();
        height += notes.size() * noteLineHeight;
        return Math.max(24, height);
    }

    private static int extraHeight(UiWidget widget) {
        return widget instanceof UiCyclicTimeBar ? CYCLIC_HEIGHT : RANGE_HEIGHT;
    }

    /** {@inheritDoc} */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        shell.setBounds(x, y, width, height);
        layout();
    }

    private void layout() {
        int x = bounds().x();
        int width = bounds().width();
        int cursorY = bounds().y();
        for (UiWidget extra : extras) {
            int extraHeight = extraHeight(extra);
            extra.setBounds(x, cursorY, width, extraHeight);
            cursorY += extraHeight + EXTRA_GAP;
        }
        cursorY = layoutButtons(cursorY);
        int notesHeight = notes.size() * noteLineHeight;
        form.setBounds(x, cursorY, width, Math.max(12, bounds().bottom() - cursorY - notesHeight));
        // 说明行固定排在表单下方，供应者返回 null 时仅留白不画字
        noteRects.clear();
        int noteY = cursorY + Math.max(12, bounds().bottom() - cursorY - notesHeight);
        for (int index = 0; index < notes.size(); index++) {
            noteRects.add(new UiRect(x, noteY, width, noteLineHeight));
            noteY += noteLineHeight;
        }
    }

    private int layoutButtons(int cursorY) {
        if (buttons.isEmpty()) {
            return cursorY;
        }
        int x = bounds().x();
        int limit = bounds().width();
        int[] widths = new int[buttons.size()];
        for (int index = 0; index < buttons.size(); index++) {
            widths[index] = fitWidth(buttons.get(index).preferredWidth(4), limit);
        }
        UiFlowLayout.Wrap flow = UiFlowLayout.wrap(widths, limit, BUTTON_GAP, BUTTON_HEIGHT);
        for (int index = 0; index < buttons.size(); index++) {
            UiRect offset = flow.bounds(index);
            buttons.get(index).setBounds(x + offset.x(), cursorY + offset.y(), offset.width(), offset.height());
        }
        // 返回值含一行尾随间距，kit 的 height() 不含，这里补回
        return cursorY + flow.height() + BUTTON_GAP;
    }

    public void unmount() {
        for (UiWidget extra : extras) {
            if (extra instanceof UiRangeBar bar) {
                bar.endInteraction(UiInteractionSession.EndReason.UNMOUNTED);
            } else if (extra instanceof UiCyclicTimeBar bar) {
                bar.endInteraction(UiInteractionSession.EndReason.UNMOUNTED);
            }
        }
        form.unmount();
    }

    /** 禁用时结束当前交互，复合控件同步子目标资格。 */
    public void setEnabled(boolean nextEnabled) {
        shell.setEnabled(nextEnabled);
        for (UiWidget extra : extras) {
            if (extra instanceof UiRangeBar bar) {
                bar.setEnabled(nextEnabled);
            } else if (extra instanceof UiCyclicTimeBar bar) {
                bar.setEnabled(nextEnabled);
            }
        }
        for (UiButton button : buttons) {
            button.setEnabled(nextEnabled);
        }
        form.setEnabled(nextEnabled);
    }

    // 面板内部焦点顺序：附加控件 -> 目录按钮 -> 表单字段
    public List<UiFocusTarget> focusTargets() {
        List<UiFocusTarget> targets = new ArrayList<>();
        members.addFocusTargets(targets::add);
        return targets;
    }

    /** 焦点组保持自身身份，普通输入沿实际成员派发。 */
    @Override public long focusRevision() { return members.focusRevision() + UiShellWidget.super.focusRevision(); }
    /** 会话结束后传播本地退出通知。 */
    @Override public void scopeExited(UiInteractionSession.EndReason reason) { members.scopeExited(reason); }
    /** 指针只进入已命中的成员。 */
    @Override public boolean mousePressed(UiInputContext context) { return isEnabled() && members.mousePressed(context); }
    /** 配对释放沿原成员派发。 */
    @Override public boolean mouseReleased(UiInputContext context) { return members.mouseReleased(context); }
    /** 嵌套表单保留自己的视口回退。 */
    @Override public boolean mouseScrolled(UiInputContext context, double x, double y) { return members.mouseScrolled(context, x, y); }
    /** 普通按键沿持焦成员派发。 */
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return members.keyPressed(key, scan, modifiers); }
    /** 普通释放沿持焦成员派发。 */
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return members.keyReleased(key, scan, modifiers); }
    /** 字符沿持焦成员派发。 */
    @Override public boolean charTyped(char value, int modifiers) { return members.charTyped(value, modifiers); }

    /** {@inheritDoc} */
    @Override
    public void render(GuiGraphics graphics, Font renderFont, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        for (UiWidget extra : extras) {
            extra.render(graphics, renderFont, pointer);
        }
        for (UiButton button : buttons) {
            button.render(graphics, renderFont, pointer);
        }
        form.render(graphics, renderFont, pointer);
        for (int index = 0; index < notes.size() && index < noteRects.size(); index++) {
            Component note = notes.get(index).get();
            if (note == null) {
                continue;
            }
            UiRect rect = noteRects.get(index);
            String text = TextScroll.trimToWidth(font, note.getString(), Math.max(1, rect.width()));
            graphics.drawString(font, text, rect.x(), rect.y(), THEME.palette().surfaces().secondaryText(), false);
        }
    }

    // 参数框内正在输入文本：画布快捷键让位，避免输入字母时触发复制/缩放
    boolean capturesKeyboard() {
        return form.isEditing();
    }

    // 参数校验失败时，内部焦点与字段滚动保持一致。
    public void focusField(FieldPath path) {
        UiFocusTarget target = form.revealPath(path);
        if (target != null) target.focusState().request();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFocused() {
        return focusState().containsFocus();
    }

    @Override
    public boolean activate() {
        return false;
    }

    @Override
    public Component accessibleName() {
        return Component.translatable(UI + "diagram.leaf").copy().append(" (" + extras.size() + ")");
    }
    /** 使用当前布局解析实际命中，保留容器归属与叶子身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (!bounds().contains(x, y)) return null;
        UiPointerHit hit = members.hitTest(x, y);
        return hit == null ? null : hit.within(this);
    }

}
