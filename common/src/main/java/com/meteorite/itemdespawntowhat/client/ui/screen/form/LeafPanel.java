package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCyclicTimeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiNarration;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 叶子面板：附加区间控件 + 目录按钮 + 表单，纵向排列。
 * <p>面板自身是模态内容控件，因此它负责把指针/键盘/字符事件转发给子控件，并在内部维护
 * Tab / Shift+Tab 的焦点顺序（附加控件 -> 目录按钮 -> 表单字段）。
 */
public final class LeafPanel implements UiWidget, UiFocusTarget {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
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
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean focused;
    private boolean enabled = true;
    private int focusIndex;

    public LeafPanel(Font font, FormView form, List<UiWidget> extras, List<UiButton> buttons, List<Runnable> reloadValues) {
        this.font = font;
        this.noteLineHeight = font.lineHeight + 1;
        this.form = form;
        this.extras = extras;
        this.buttons = buttons;
        this.reloadValues = reloadValues;
    }

    /** 历史恢复后回填已接受的字段与区间值，未完成文本由表单保留。 */
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

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        layout();
    }

    private void layout() {
        int x = bounds.x();
        int width = bounds.width();
        int cursorY = bounds.y();
        for (UiWidget extra : extras) {
            int extraHeight = extraHeight(extra);
            extra.setBounds(x, cursorY, width, extraHeight);
            cursorY += extraHeight + EXTRA_GAP;
        }
        cursorY = layoutButtons(cursorY);
        int notesHeight = notes.size() * noteLineHeight;
        form.setBounds(x, cursorY, width, Math.max(12, bounds.bottom() - cursorY - notesHeight));
        // 说明行固定排在表单下方，供应者返回 null 时仅留白不画字
        noteRects.clear();
        int noteY = cursorY + Math.max(12, bounds.bottom() - cursorY - notesHeight);
        for (int index = 0; index < notes.size(); index++) {
            noteRects.add(new UiRect(x, noteY, width, noteLineHeight));
            noteY += noteLineHeight;
        }
    }

    private int layoutButtons(int cursorY) {
        if (buttons.isEmpty()) {
            return cursorY;
        }
        int x = bounds.x();
        int limit = bounds.width();
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

    // 结束所有未完成交互（切页/关闭/失权）
    public void endInteractions() {
        for (UiWidget extra : extras) {
            if (extra instanceof UiRangeBar bar) {
                bar.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
            } else if (extra instanceof UiCyclicTimeBar bar) {
                bar.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
            }
        }
        form.onFocusScopeChanged();
    }

    public void unmount() {
        for (UiWidget extra : extras) {
            if (extra instanceof UiRangeBar bar) {
                bar.unmount();
            } else if (extra instanceof UiCyclicTimeBar bar) {
                bar.unmount();
            }
        }
        form.unmount();
    }

    public void onHostClosed() {
        for (UiWidget extra : extras) {
            if (extra instanceof UiRangeBar bar) {
                bar.onHostClosed();
            } else if (extra instanceof UiCyclicTimeBar bar) {
                bar.onHostClosed();
            }
        }
        form.onHostClosed();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        for (UiWidget extra : extras) {
            if (extra instanceof UiRangeBar bar) {
                bar.setEnabled(enabled);
            } else if (extra instanceof UiCyclicTimeBar bar) {
                bar.setEnabled(enabled);
            }
        }
        for (UiButton button : buttons) {
            button.setEnabled(enabled);
        }
        form.setEnabled(enabled);
    }

    // 面板内部焦点顺序：附加控件 -> 目录按钮 -> 表单字段
    public List<UiFocusTarget> focusTargets() {
        List<UiFocusTarget> targets = new ArrayList<>();
        for (UiWidget extra : extras) {
            if (extra instanceof UiFocusTarget target) {
                targets.add(target);
            }
        }
        targets.addAll(buttons);
        targets.addAll(form.focusTargets());
        return targets;
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        for (UiWidget extra : extras) {
            extra.render(graphics, renderFont, mouseX, mouseY);
        }
        for (UiButton button : buttons) {
            button.render(graphics, renderFont, mouseX, mouseY);
        }
        form.render(graphics, renderFont, mouseX, mouseY);
        for (int index = 0; index < notes.size() && index < noteRects.size(); index++) {
            Component note = notes.get(index).get();
            if (note == null) {
                continue;
            }
            UiRect rect = noteRects.get(index);
            String text = TextScroll.trimToWidth(font, note.getString(), Math.max(1, rect.width()));
            graphics.drawString(font, text, rect.x(), rect.y(), THEME.palette().textSecondary(), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        List<UiFocusTarget> targets = focusTargets();
        if (button == 0) {
            for (int index = 0; index < targets.size(); index++) {
                UiFocusTarget target = targets.get(index);
                boolean hit = target.canFocus() && target.bounds().contains(mouseX, mouseY);
                target.setFocused(hit);
                if (hit) {
                    focusIndex = index;
                }
            }
        }
        for (UiButton candidate : buttons) {
            if (candidate.isVisible() && candidate.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (UiWidget extra : extras) {
            if (extra.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return form.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = false;
        for (UiWidget extra : extras) {
            consumed |= extra.mouseReleased(mouseX, mouseY, button);
        }
        consumed |= form.mouseReleased(mouseX, mouseY, button);
        for (UiButton candidate : buttons) {
            consumed |= candidate.mouseReleased(mouseX, mouseY, button);
        }
        return consumed;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean consumed = false;
        for (UiWidget extra : extras) {
            consumed |= extra.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        consumed |= form.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return consumed;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return form.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            return true;
        }
        for (UiWidget extra : extras) {
            if (extra.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        for (UiButton button : buttons) {
            if (button.isFocused() && button.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return form.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean consumed = false;
        for (UiWidget extra : extras) {
            consumed |= extra.keyReleased(keyCode, scanCode, modifiers);
        }
        consumed |= form.keyReleased(keyCode, scanCode, modifiers);
        return consumed;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (UiWidget extra : extras) {
            if (extra.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return form.charTyped(codePoint, modifiers);
    }

    // 参数校验失败时，内部焦点与字段滚动保持一致。
    public void focusField(String path) {
        UiFocusTarget target = form.revealPath(path);
        List<UiFocusTarget> targets = focusTargets();
        for (int index = 0; index < targets.size(); index++) {
            boolean selected = targets.get(index) == target;
            targets.get(index).setFocused(selected);
            if (selected) {
                focusIndex = index;
            }
        }
    }

    private void moveFocus(int delta) {
        List<UiFocusTarget> targets = focusTargets();
        if (targets.isEmpty()) {
            return;
        }
        int count = targets.size();
        focusIndex = ((focusIndex + delta) % count + count) % count;
        for (int index = 0; index < count; index++) {
            targets.get(index).setFocused(index == focusIndex);
        }
        focused = true;
        // 面板内部焦点切换同样播报（屏幕的焦点管理器看不到面板内部顺序）
        UiNarration.focus(targets.get(focusIndex));
    }

    @Override
    public boolean canFocus() {
        return enabled;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        List<UiFocusTarget> targets = focusTargets();
        if (!focused) {
            for (UiFocusTarget target : targets) {
                target.setFocused(false);
            }
            return;
        }
        if (targets.isEmpty()) {
            return;
        }
        focusIndex = Math.clamp(focusIndex, 0, targets.size() - 1);
        targets.get(focusIndex).setFocused(true);
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        List<UiFocusTarget> targets = focusTargets();
        if (targets.isEmpty()) {
            return false;
        }
        focusIndex = Math.clamp(focusIndex, 0, targets.size() - 1);
        return targets.get(focusIndex).activate();
    }

    @Override
    public Component accessibleName() {
        return Component.translatable(UI + "diagram.leaf").copy().append(" (" + extras.size() + ")");
    }
}
