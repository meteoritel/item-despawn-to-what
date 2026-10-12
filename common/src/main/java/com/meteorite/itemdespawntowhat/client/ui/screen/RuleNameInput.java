package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** 规则名字输入区，仅展示名称限制并为校验错误预留位置。 */
final class RuleNameInput implements UiWidget, UiFocusTarget {

    // 宿主默认主题：屏幕按主题取色与绘制原语
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    static final int MODAL_WIDTH = 320;
    private static final int INPUT_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int ERROR_ROWS = 2;

    private final Font font;
    private final UiTextInput input;
    private final Component help = Component.translatable(TypeLabels.UI_PREFIX + "prompt.name_help");
    private @Nullable Component problem;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    RuleNameInput(Font font) {
        this.font = font;
        input = new UiTextInput(THEME, font, Component.translatable(TypeLabels.UI_PREFIX + "prompt.name_hint"));
        input.setFilter(RuleNameInput::allowsCharacters);
    }

    static boolean allowsCharacters(String name) {
        return name.chars().allMatch(character -> character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9' || character == '_');
    }

    UiTextInput input() {
        return input;
    }

    void setProblem(@Nullable Component problem) {
        this.problem = problem;
    }

    int preferredHeight() {
        int width = MODAL_WIDTH - THEME.padding() * 2;
        int textRows = font.split(help, width).size() + ERROR_ROWS;
        return INPUT_HEIGHT + textRows * font.lineHeight + GAP * 2;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        input.setBounds(x, y, width, INPUT_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        input.render(graphics, font, mouseX, mouseY);
        int errorY = drawLines(graphics, help, input.bounds().bottom() + GAP, THEME.palette().textSecondary()) + GAP;
        if (problem != null) {
            drawLines(graphics, problem, errorY, THEME.palette().danger());
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return input.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
