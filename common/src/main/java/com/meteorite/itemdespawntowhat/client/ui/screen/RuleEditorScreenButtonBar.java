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

    // 上次布局的几何与按钮宽度签名：逐帧重排时相同输入直接跳过测量与换行分配
    private int generation;
    private int layoutSignature = Integer.MIN_VALUE;
    private int layoutX = Integer.MIN_VALUE;
    private int layoutY = Integer.MIN_VALUE;
    private int layoutWidth = Integer.MIN_VALUE;

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

    // 替换按钮集合（按钮实例由调用方创建与持有）；换代保证新实例一定会重新布局
    void set(List<UiButton> next) {
        buttons.clear();
        buttons.addAll(next);
        generation++;
    }

    // 指定宽度下按钮排所需高度；无可见按钮时为 0
    int preferredHeight(int width) {
        List<UiButton> visible = visibleButtons();
        if (visible.isEmpty()) {
            return 0;
        }
        // 宽度非正时沿用「视为单行」的旧口径（与 layout 的单行判定一致）
        if (width <= 0) {
            return buttonHeight;
        }
        return flowFor(visible, width).height();
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

    // 布局输入签名：按钮实例、可见性与首选宽度任一变化都要重排，宽度变化由调用参数一并计入
    private int signatureOf(int width) {
        int signature = 31 * (31 + generation) + buttons.size();
        for (UiButton button : buttons) {
            boolean visible = button.isVisible();
            signature = 31 * signature + (visible ? 1 : 0);
            signature = 31 * signature + (visible ? button.preferredWidth(BUTTON_PAD) : 0);
        }
        return 31 * signature + width;
    }

    void layout(int y, int width) {
        layout(RuleEditorPageSupport.PAD, y, width);
    }

    // 从 (x, y) 起依次摆放按钮，超出宽度换行；fillRow 在单行时均分剩余宽度
    void layout(int x, int y, int width) {
        int signature = signatureOf(width);
        if (signature == layoutSignature && x == layoutX && y == layoutY && width == layoutWidth) {
            return;
        }
        layoutSignature = signature;
        layoutX = x;
        layoutY = y;
        layoutWidth = width;
        int limit = Math.max(MIN_ROW_WIDTH, width);
        List<UiButton> visible = visibleButtons();
        int[] widths = widthsOf(visible, limit);
        int used = 0;
        for (int itemWidth : widths) {
            used += itemWidth;
        }
        // 单行口径与换行共用同一累计：总宽加间距仍放得下即为一行
        if (fillRow && widths.length > 0 && used + (widths.length - 1) * gap <= limit) {
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
