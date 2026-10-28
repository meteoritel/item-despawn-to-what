package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
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
final class RuleEditorPageInput implements UiInteractionSession.Target {

    // 判定拖动已开始的垂直位移阈值（像素）
    private static final double DRAG_START_THRESHOLD = 3;

    // 拖动目标
    private enum DragKind {
        NONE,
        EFFECT
    }

    private final RuleEditorEditSections pages;

    // 列表排序使用所属作用域的唯一会话；条目位置与阈值仅为手势数据。
    private @Nullable UiInteractionSession interaction;

    // 拖动状态：局部预览、不每帧写数据，释放时一次提交
    private DragKind dragKind = DragKind.NONE;
    private int dragFrom = -1;
    private int dragTo = -1;
    private boolean dragActive;
    private double dragStartY;

    RuleEditorPageInput(RuleEditorEditSections pages) {
        this.pages = pages;
    }

    // 清空拖动状态（页面重建时调用）
    void resetDrag() {
        if (interaction != null) interaction.end(this, UiInteractionSession.EndReason.CANCEL);
        dragKind = DragKind.NONE;
        dragFrom = -1;
        dragTo = -1;
        dragActive = false;
    }

    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (!pages.pageScroll.contains(mouseX, mouseY)) {
            return false;
        }
        if (pages.pageScroll.mousePressed(context)) {
            return true;
        }
        // 行内删除先于列表选中与拖动排序，避免叉号触发双击激活或拖动。
        if (pages.resultsSection.clickRemove(mouseX, mouseY, button)) {
            pages.actions.flushRebuild();
            return true;
        }
        boolean handled = false;
        for (UiWidget widget : pages.content.widgets) {
            if (widget.isVisible() && widget.mousePressed(context)) {
                handled = true;
                break;
            }
        }
        // 命中可能改变行可见性或控件高度；几何未变时这里只命中布局缓存
        if (pages.lastArea != null) {
            pages.ensureLayout(pages.lastArea);
        }
        if (button == 0 && !pages.actions.rebuildPending()) {
            captureDrag(mouseX, mouseY);
            if (dragKind != DragKind.NONE) {
                interaction = context.scope().interaction();
                interaction.begin(this, UiInteractionSession.Source.POINTER, button);
            }
        }
        pages.actions.flushRebuild();
        return handled;
    }

    public boolean mouseReleased(UiInputContext context) {
        int from = dragFrom;
        int to = dragTo;
        boolean changed = dragActive && from >= 0 && to >= 0 && from != to;
        resetDrag();
        if (changed) pages.resultsSection.moveEffect(from, to);
        pages.actions.flushRebuild();
        return true;
    }

    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (dragKind == DragKind.NONE) return false;
        if (Math.abs(context.pointerY() - dragStartY) >= DRAG_START_THRESHOLD) {
            dragActive = true;
            dragTo = rowIndexAt(context.pointerY());
        }
        return true;
    }

    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
        dragKind = DragKind.NONE;
        dragFrom = -1;
        dragTo = -1;
        dragActive = false;
    }

    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!pages.pageScroll.contains(mouseX, mouseY)) {
            return false;
        }
        for (UiWidget widget : pages.content.widgets) {
            if (widget.isVisible() && widget.mouseScrolled(context, scrollX, scrollY)) {
                return true;
            }
        }
        boolean changed = pages.pageScroll.scrollBy(scrollY);
        if (changed && pages.lastArea != null) {
            // 页面滚动只平移已排好的控件，不重新测量
            pages.ensureLayout(pages.lastArea);
        }
        return changed;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (UiWidget widget : pages.content.widgets) {
            if (widget instanceof UiFocusTarget target && !target.isFocused()) {
                continue;
            }
            if (widget.isVisible() && widget.keyPressed(keyCode, scanCode, modifiers)) {
                pages.actions.flushRebuild();
                return true;
            }
        }
        pages.actions.flushRebuild();
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (UiWidget widget : pages.content.widgets) {
            if (widget.isVisible() && widget.charTyped(codePoint, modifiers)) {
                pages.actions.flushRebuild();
                return true;
            }
        }
        pages.actions.flushRebuild();
        return false;
    }

    // 拖动预览：高亮目标行
    void renderDragPreview(GuiGraphics graphics) {
        if (!dragActive) {
            return;
        }
        UiListView<?> list = pages.resultsSection.effectList;
        UiRect rect = list == null ? null : list.visibleRowBounds(dragTo);
        if (rect != null) {
            pages.THEME.drawSelection(graphics, rect);
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
        if (pages.resultsSection.effectList != null && pages.resultsSection.effectList.isVisible()
                && pages.resultsSection.effectList.bounds().contains(mouseX, mouseY)) {
            list = pages.resultsSection.effectList;
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
        UiListView<?> list = pages.resultsSection.effectList;
        if (list == null || list.size() == 0) {
            return -1;
        }
        return list.dragIndexAt(mouseY);
    }
}
