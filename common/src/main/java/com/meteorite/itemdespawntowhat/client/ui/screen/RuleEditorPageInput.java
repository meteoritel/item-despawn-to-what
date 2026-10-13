package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑页的输入事件分发：把按下、释放、拖动、滚动与键盘输入派发给当前页控件，
 * 并维护结果页动作列表的拖动排序状态。
 */
final class RuleEditorPageInput {

    // 判定拖动已开始的垂直位移阈值（像素）
    private static final double DRAG_START_THRESHOLD = 3;

    // 拖动目标
    private enum DragKind {
        NONE,
        EFFECT
    }

    private final RuleEditorEditPages pages;

    // 本次按下命中的页面控件：释放只派发给它，避免无关控件吞掉释放
    private @Nullable UiWidget pressedWidget;

    // 拖动状态：局部预览、不每帧写数据，释放时一次提交
    private DragKind dragKind = DragKind.NONE;
    private int dragFrom = -1;
    private int dragTo = -1;
    private boolean dragActive;
    private double dragStartY;

    RuleEditorPageInput(RuleEditorEditPages pages) {
        this.pages = pages;
    }

    // 清空拖动状态（页面重建时调用）
    void resetDrag() {
        dragKind = DragKind.NONE;
        dragFrom = -1;
        dragTo = -1;
        dragActive = false;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pages.pageScroll.contains(mouseX, mouseY)) {
            return false;
        }
        if (pages.pageScroll.mousePressed(mouseX, mouseY, button)) {
            return true;
        }
        // 行内删除先于列表选中与拖动排序，避免叉号触发双击激活或拖动。
        if (pages.ruleEditorResultsPage.clickRemove(mouseX, mouseY, button)) {
            pages.flushRebuild();
            return true;
        }
        boolean handled = false;
        for (UiWidget widget : pages.liveWidgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) {
                // 记录按下归属：随后的释放与拖动只发给这个控件
                pressedWidget = widget;
                handled = true;
                break;
            }
        }
        if (pages.lastArea != null) {
            pages.layout(pages.lastArea);
        }
        if (button == 0 && !pages.rebuildPending()) {
            captureDrag(mouseX, mouseY);
        }
        pages.flushRebuild();
        return handled;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        pages.pageScroll.mouseReleased();
        if (dragKind != DragKind.NONE) {
            int from = dragFrom;
            int to = dragTo;
            boolean active = dragActive;
            dragKind = DragKind.NONE;
            dragActive = false;
            dragFrom = -1;
            dragTo = -1;
            if (active && from >= 0 && to >= 0 && from != to) {
                pages.ruleEditorResultsPage.moveEffect(from, to);
                pages.flushRebuild();
                return true;
            }
        }
        // 释放只派发给承接按下的控件；没有归属时不派发，避免残留按下态吞掉释放
        UiWidget owner = pressedWidget;
        pressedWidget = null;
        boolean handled = owner != null && owner.isVisible() && owner.mouseReleased(mouseX, mouseY, button);
        pages.flushRebuild();
        return handled;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pages.pageScroll.isDragging()) {
            pages.pageScroll.mouseDragged(mouseY);
            if (pages.lastArea != null) {
                pages.layout(pages.lastArea);
            }
            return true;
        }
        if (dragKind != DragKind.NONE) {
            if (Math.abs(mouseY - dragStartY) >= DRAG_START_THRESHOLD) {
                dragActive = true;
                dragTo = rowIndexAt(mouseY);
            }
            if (dragActive) {
                return true;
            }
        }
        for (UiWidget widget : pages.liveWidgets) {
            if (widget.isVisible() && widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!pages.pageScroll.contains(mouseX, mouseY)) {
            return false;
        }
        for (UiWidget widget : pages.liveWidgets) {
            if (widget.isVisible() && widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        boolean changed = pages.pageScroll.scrollBy(scrollY);
        if (changed && pages.lastArea != null) {
            pages.layout(pages.lastArea);
        }
        return changed;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (UiWidget widget : pages.liveWidgets) {
            if (widget instanceof UiFocusTarget target && !target.isFocused()) {
                continue;
            }
            if (widget.isVisible() && widget.keyPressed(keyCode, scanCode, modifiers)) {
                pages.flushRebuild();
                return true;
            }
        }
        pages.flushRebuild();
        return false;
    }

    boolean charTyped(char codePoint, int modifiers) {
        for (UiWidget widget : pages.liveWidgets) {
            if (widget.isVisible() && widget.charTyped(codePoint, modifiers)) {
                pages.flushRebuild();
                return true;
            }
        }
        pages.flushRebuild();
        return false;
    }

    // 拖动预览：高亮目标行
    void renderDragPreview(GuiGraphics graphics) {
        if (!dragActive) {
            return;
        }
        UiListView<?> list = pages.ruleEditorResultsPage.effectList;
        UiRect rect = list == null ? null : list.visibleRowBounds(dragTo);
        if (rect != null) {
            RuleEditorEditPages.THEME.drawSelection(graphics, rect);
        }
    }

    // 记录潜在拖动源（点击列表行后开始跟踪）
    private void captureDrag(double mouseX, double mouseY) {
        dragKind = DragKind.NONE;
        dragFrom = -1;
        dragTo = -1;
        dragActive = false;
        UiListView<?> list = null;
        DragKind kind = DragKind.NONE;
        if (pages.ruleEditorResultsPage.effectList != null && pages.ruleEditorResultsPage.effectList.isVisible()
                && pages.ruleEditorResultsPage.effectList.bounds().contains(mouseX, mouseY)) {
            list = pages.ruleEditorResultsPage.effectList;
            kind = DragKind.EFFECT;
        }
        if (list == null || list.itemIndexAt(mouseX, mouseY) < 0) {
            return;
        }
        dragKind = kind;
        dragFrom = list.selectedIndex();
        dragStartY = mouseY;
    }

    // 指针位置对应的行下标
    private int rowIndexAt(double mouseY) {
        UiListView<?> list = pages.ruleEditorResultsPage.effectList;
        if (list == null || list.size() == 0) {
            return -1;
        }
        return list.dragIndexAt(mouseY);
    }
}
