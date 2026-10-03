package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/***
 * 模态弹窗栈。
 * <p>负责：遮罩绘制与淡入淡出、弹窗居中与逐层偏移、焦点在弹窗内部切换、
 * 以及把输入事件只投递给最上层弹窗（下层界面在栈非空时不再收到输入）。
 * <p>调用方先渲染自己的底层界面，再调用 {@link #render}，遮罩会盖在底层之上。
 */
public final class UiModalStack implements UiWidget {

    // 淡入帧数
    public static final int FADE_TICKS = 4;
    // 多层弹窗的偏移像素
    private static final int STACK_OFFSET = 6;

    // 弹窗栈（末尾为最上层）
    private final List<UiModal> modals = new ArrayList<>();
    // 弹窗内部的焦点管理
    private final UiFocusManager focusManager = new UiFocusManager();
    // 可用屏幕区域
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 剩余淡入帧数
    private int fadeTicks;

    public UiModalStack() {
        focusManager.setEnterActivates(true);
        focusManager.setSpaceActivates(true);
    }

    // 当前屏幕区域
    public UiRect bounds() {
        return bounds;
    }

    // 更新屏幕区域并重新布局
    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        relayout();
    }

    // 是否为空
    public boolean isEmpty() {
        return modals.isEmpty();
    }

    // 弹窗层数
    public int size() {
        return modals.size();
    }

    // 最上层弹窗
    public UiModal top() {
        return modals.isEmpty() ? null : modals.getLast();
    }

    // 全部弹窗（只读，底到顶）
    public List<UiModal> modals() {
        return Collections.unmodifiableList(modals);
    }

    // 弹窗内部焦点管理器
    public UiFocusManager focusManager() {
        return focusManager;
    }

    // 压入一个弹窗
    public UiModal push(UiModal modal) {
        modal.setCloseHandler(this::close);
        modals.add(modal);
        fadeTicks = FADE_TICKS;
        relayout();
        refreshFocus();
        return modal;
    }

    // 关闭最上层弹窗
    public boolean closeTop() {
        if (modals.isEmpty()) {
            return false;
        }
        modals.removeLast();
        relayout();
        refreshFocus();
        return true;
    }

    // 关闭指定弹窗
    public void close(UiModal modal) {
        if (modals.remove(modal)) {
            relayout();
            refreshFocus();
        }
    }

    // 清空弹窗
    public void clear() {
        modals.clear();
        focusManager.clear();
    }

    // 推进淡入动画，每帧调用一次
    public void tick() {
        if (fadeTicks > 0) {
            fadeTicks--;
        }
    }

    // 鼠标是否位于最上层弹窗内
    public boolean isMouseOverModal(double mouseX, double mouseY) {
        UiModal modal = top();
        return modal != null && modal.bounds().contains(mouseX, mouseY);
    }

    // 逐层居中排列，每层向右下偏移一点以便看出层叠关系
    private void relayout() {
        for (int i = 0; i < modals.size(); i++) {
            UiModal modal = modals.get(i);
            int width = modal.preferredWidth();
            int height = modal.preferredHeight();
            int x = (bounds.width() - width) / 2 + i * STACK_OFFSET;
            int y = (bounds.height() - height) / 2 + i * STACK_OFFSET;
            modal.layoutAt(x, y, width, height);
        }
    }

    // 焦点只保留在最上层弹窗内
    private void refreshFocus() {
        UiModal modal = top();
        if (modal == null) {
            focusManager.clear();
            return;
        }
        modal.addFocusTargets(focusManager);
        focusManager.focusFirst();
    }

    // ---- 渲染 ----

    @Override
    public boolean isVisible() {
        return !modals.isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (modals.isEmpty()) {
            return;
        }
        int baseAlpha = (UiPalette.MODAL_DIM >>> 24) & 0xFF;
        int alpha = baseAlpha * (FADE_TICKS - fadeTicks) / FADE_TICKS;
        if (alpha > 0) {
            int dim = (alpha << 24) | (UiPalette.MODAL_DIM & 0x00FFFFFF);
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), dim);
        }
        for (int i = 0; i < modals.size(); i++) {
            boolean isTop = i == modals.size() - 1;
            UiModal modal = modals.get(i);
            modal.render(graphics, font, isTop ? mouseX : -1, isTop ? mouseY : -1);
        }
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UiModal modal = top();
        if (modal == null) {
            return false;
        }
        if (modal.bounds().contains(mouseX, mouseY)) {
            modal.mouseClicked(mouseX, mouseY, button);
        } else if (modal.closeOnBackdrop()) {
            closeTop();
        }
        // 弹窗打开时吞掉全部鼠标点击，避免穿透到底层界面
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        UiModal modal = top();
        if (modal == null) {
            return false;
        }
        modal.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        UiModal modal = top();
        if (modal == null) {
            return false;
        }
        modal.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiModal modal = top();
        if (modal == null) {
            return false;
        }
        modal.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        UiModal modal = top();
        if (modal == null) {
            return false;
        }
        if (modal.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return focusManager.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        UiModal modal = top();
        return modal != null && modal.charTyped(codePoint, modifiers);
    }
}
