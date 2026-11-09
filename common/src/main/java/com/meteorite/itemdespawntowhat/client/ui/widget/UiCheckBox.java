package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiShellWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidgetShell;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 复选框：左侧像素勾选框，右侧标签文本（颜色状态一律辅以文字，不只靠颜色区分）。
 * <p>鼠标交互点限制在左侧方框内（{@link UiRect#centeredSquare}）：点击标签文字或行内空白不会切换，
 * 避免整行命中带来的误触；键盘焦点与回车、空格切换不受此限制。
 * <p>方框内用高对比度的 X 标记选中，键盘导航时在方框上绘制焦点轮廓。
 */
public final class UiCheckBox implements UiShellWidget {

    // 勾选框边长
    public static final int BOX_SIZE = 9;
    // 勾选框与标签的水平间距
    public static final int LABEL_GAP = 3;

    private final UiWidgetShell shell = new UiWidgetShell(this);
    private final Font font;
    private Component label;
    private boolean checked;
    private @Nullable Consumer<Boolean> onChanged;

    // 注入的宿主主题
    private final UiHostTheme theme;

    /** 创建方框按下切换、键盘通过通用焦点路由激活的复选框。 */
    public UiCheckBox(UiHostTheme theme, Font font, Component label, boolean checked) {
        this.theme = UiHostThemes.require(theme);
        this.font = Objects.requireNonNull(font, "font");
        this.label = label == null ? Component.empty() : label;
        this.checked = checked;
    }

    /** {@inheritDoc} */
    @Override
    public UiWidgetShell shell() {
        return shell;
    }

    // 附带内边距的首选宽度
    public int preferredWidth(int padding) {
        return BOX_SIZE + LABEL_GAP + font.width(label) + padding * 2;
    }

    public Component label() {
        return label;
    }

    public UiCheckBox setLabel(Component next) {
        this.label = next == null ? Component.empty() : next;
        return this;
    }

    public boolean isChecked() {
        return checked;
    }

    /** 静默回填选中状态，供服务器快照同步使用。 */
    public UiCheckBox setChecked(boolean next) {
        this.checked = next;
        return this;
    }

    /** 用户触发的切换：改变状态并回调 */
    public boolean toggle() {
        if (!shell.isInteractive()) {
            return false;
        }
        checked = !checked;
        if (onChanged != null) {
            onChanged.accept(checked);
        }
        return true;
    }

    public UiCheckBox setOnChanged(@Nullable Consumer<Boolean> listener) {
        this.onChanged = listener;
        return this;
    }

    /** 禁用时结束当前交互，复合控件同步子目标资格。 */
    public UiCheckBox setEnabled(boolean next) {
        shell.setEnabled(next);
        return this;
    }

    /** 隐藏时结束当前交互并退出焦点序列，恢复显示保留内容。 */
    public UiCheckBox setVisible(boolean next) {
        shell.setVisible(next);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public void render(GuiGraphics graphics, Font renderFont, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        if (!isVisible()) {
            return;
        }
        // 绘制与命中共用同一份方框几何：只有方框本身响应悬停
        UiRect box = bounds().centeredSquare(BOX_SIZE);
        boolean hovered = isEnabled() && pointer.contains(box);
        theme.drawInset(graphics, box);
        if (checked) {
            graphics.fill(box.x() + 2, box.y() + 2, box.right() - 2, box.bottom() - 2,
                    isEnabled() ? theme.palette().controls().accent() : theme.palette().controls().controlDisabled());

            graphics.drawString(renderFont, "X", box.x() + 2, box.y() + 1, theme.palette().controls().textOnDark(), false);
        }
        int textX = box.right() + LABEL_GAP;
        int maxWidth = Math.max(0, bounds().right() - textX);
        String text = TextScroll.trimToWidth(renderFont, label.getString(), maxWidth);
        int color = !isEnabled() ? theme.palette().surfaces().disabledText() : (hovered ? theme.palette().surfaces().text() : theme.textColor(true));
        int textY = bounds().y() + Math.max(0, (bounds().height() - renderFont.lineHeight) / 2);
        graphics.drawString(renderFont, text, textX, textY, color, false);
        shell.renderFocusOutline(graphics, box, theme);
    }

    /** {@inheritDoc} */
    @Override
    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (!isVisible() || !isEnabled() || button != 0 || !bounds().centeredSquare(BOX_SIZE).contains(mouseX, mouseY)) {
            return false;
        }

        return toggle();
    }

    @Override
    public boolean activate() {
        return toggle();
    }

    @Override
    public @Nullable Component accessibleName() {
        return label;
    }
}
