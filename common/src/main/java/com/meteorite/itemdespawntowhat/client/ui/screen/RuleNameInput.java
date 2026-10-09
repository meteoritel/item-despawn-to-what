package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** 新建规则的名字输入区，固定展示命名空间并为校验错误预留位置。 */
final class RuleNameInput implements UiWidget, UiFocusTarget {
    static final int MODAL_WIDTH = 320;
    private static final int INPUT_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int ERROR_ROWS = 2;

    private final Font font;
    private final UiTextInput input;
    private final Component namespace = Component.translatable(
            TypeLabels.UI_PREFIX + "prompt.namespace", TypeLabels.OWN_NAMESPACE);
    private final Component help = Component.translatable(TypeLabels.UI_PREFIX + "prompt.name_help");
    private @Nullable Component problem;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    RuleNameInput(Font font) {
        this.font = font;
        input = new UiTextInput(font, Component.translatable(TypeLabels.UI_PREFIX + "prompt.name_hint"));
    }

    UiTextInput input() {
        return input;
    }

    void setProblem(@Nullable Component problem) {
        this.problem = problem;
    }

    int preferredHeight() {
        int width = MODAL_WIDTH - UiTheme.PADDING * 2;
        int textRows = font.split(namespace, width).size()
                + font.split(help, width).size() + ERROR_ROWS;
        return INPUT_HEIGHT + textRows * font.lineHeight + GAP * 3;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int namespaceHeight = font.split(namespace, Math.max(1, width)).size() * font.lineHeight;
        input.setBounds(x, y + namespaceHeight + GAP, width, INPUT_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        drawLines(graphics, namespace, bounds.y(), UiPalette.TEXT_SECONDARY);
        input.render(graphics, font, mouseX, mouseY);
        int errorY = drawLines(graphics, help, input.bounds().bottom() + GAP, UiPalette.TEXT_SECONDARY) + GAP;
        if (problem != null) {
            drawLines(graphics, problem, errorY, UiPalette.DANGER);
        }
    }

    private int drawLines(GuiGraphics graphics, Component text, int y, int color) {
        for (FormattedCharSequence line : font.split(text, Math.max(1, bounds.width()))) {
            graphics.drawString(font, line, bounds.x(), y, color, false);
            y += font.lineHeight;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return input.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return input.isFocused() && input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return input.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean canFocus() {
        return input.canFocus();
    }

    @Override
    public void setFocused(boolean focused) {
        input.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return input.isFocused();
    }

    @Override
    public boolean activate() {
        return input.activate();
    }

    @Override
    public Component accessibleName() {
        return input.accessibleName();
    }
}
