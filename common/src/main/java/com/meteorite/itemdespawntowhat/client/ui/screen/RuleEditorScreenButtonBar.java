package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

/**
 * 规则编辑器按钮排：把一组 {@link UiButton} 按可用宽度自动换行、左对齐排布，并转发渲染与鼠标输入。
 *
 * <p>只持有按钮引用，不读取屏幕状态；几何在 {@link #layout(int, int, int)} 时写入按钮。
 * {@link RuleEditorScreenListPage} 用它承载管理页工具排与列表底排，
 * {@link RuleEditorScreenEditPage} 用它承载编辑页底排。</p>
 */
final class RuleEditorScreenButtonBar {

    // 默认按钮高度与按钮间距
    private static final int DEFAULT_BUTTON_H = 16;
    private static final int DEFAULT_GAP = 2;
    // 按钮首选宽度的文字预留与最小可点击宽度
    private static final int BUTTON_PAD = 6;
    private static final int MIN_BUTTON_W = 24;
    // 宽度非正时的最小可用宽度
    private static final int MIN_ROW_WIDTH = 1;

    private final int buttonHeight;
    private final int gap;
    private final boolean fillRow;
    private final List<UiButton> buttons = new ArrayList<>();

    RuleEditorScreenButtonBar() {
        this(DEFAULT_BUTTON_H, DEFAULT_GAP, false);
    }

    RuleEditorScreenButtonBar(int buttonHeight, int gap, boolean fillRow) {
        this.buttonHeight = buttonHeight;
        this.gap = gap;
        this.fillRow = fillRow;
    }

    // 替换按钮集合（按钮实例由调用方创建与持有）
    void set(List<UiButton> next) {
        buttons.clear();
        buttons.addAll(next);
    }

    // 指定宽度下按钮排所需高度；无可见按钮时为 0
    int preferredHeight(int width) {
        List<UiButton> visible = visibleButtons();
        if (visible.isEmpty()) {
            return 0;
        }
        // 宽度非正时沿用「视为单行」的旧口径（与 rows(int) 一致）
        if (width <= 0) {
            return buttonHeight;
        }
        return flowFor(visible, width).height();
    }

    // 按按钮首选宽度累计换行，估算行数
    private int rows(int width) {
        List<UiButton> visible = visibleButtons();
        if (visible.isEmpty()) {
            return 0;
        }
        if (width <= 0) {
            return 1;
        }
        return flowFor(visible, width).rowCount();
    }

    // 可见按钮（kit 不接受可见性概念，下标由宿主映射）
    private List<UiButton> visibleButtons() {
        List<UiButton> visible = new ArrayList<>();
        for (UiButton button : buttons) {
            if (button.isVisible()) visible.add(button);
        }
        return visible;
    }

    // 按按钮首选宽度量宽（下限 MIN_BUTTON_W、上限可用宽度）
    private int[] widthsOf(List<UiButton> visible, int limit) {
        int[] widths = new int[visible.size()];
        for (int index = 0; index < visible.size(); index++) {
            widths[index] = Math.clamp(visible.get(index).preferredWidth(BUTTON_PAD), Math.min(MIN_BUTTON_W, limit), limit);
        }
        return widths;
    }

    // 换行几何：测量与实际摆放共用同一次结果
    private UiFlowLayout.Wrap flowFor(List<UiButton> visible, int limit) {
        return UiFlowLayout.wrap(widthsOf(visible, limit), limit, gap, buttonHeight);
    }

    void layout(int y, int width) {
        layout(RuleEditorPageSupport.PAD, y, width);
    }

    // 从 (x, y) 起依次摆放按钮，超出宽度换行；fillRow 在单行时均分剩余宽度
    void layout(int x, int y, int width) {
        int limit = Math.max(MIN_ROW_WIDTH, width);
        List<UiButton> visible = visibleButtons();
        int[] widths = widthsOf(visible, limit);
        if (fillRow && rows(width) == 1 && widths.length > 0) {
            int used = 0;
            for (int itemWidth : widths) {
                used += itemWidth;
            }
            int extra = Math.max(0, (limit - used - (widths.length - 1) * gap) / widths.length);
            for (int index = 0; index < widths.length; index++) {
                widths[index] += extra;
            }
        }
        UiFlowLayout.Wrap flow = UiFlowLayout.wrap(widths, limit, gap, buttonHeight);
        for (int index = 0; index < widths.length; index++) {
            UiRect offset = flow.bounds(index);
            visible.get(index).setBounds(x + offset.x(), y + offset.y(), offset.width(), offset.height());
        }
    }

    void render(@NotNull GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        for (UiButton button : buttons) {
            if (button.isVisible()) {
                button.render(graphics, renderFont, mouseX, mouseY);
            }
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (UiButton candidate : buttons) {
            if (candidate.isVisible() && candidate.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = false;
        for (UiButton candidate : buttons) {
            if (candidate.isVisible()) {
                consumed |= candidate.mouseReleased(mouseX, mouseY, button);
            }
        }
        return consumed;
    }
}
