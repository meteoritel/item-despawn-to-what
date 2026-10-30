package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import java.util.List;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * 规则名字输入区：解析 `<namespace>:<path>` 或单独的 `<path>`，并展示名称限制与校验错误位。
 */
final class RuleNameInput implements UiWidget, UiFocusTarget, UiLayoutSource {

    // 宿主默认主题：屏幕按主题取色与绘制原语
    private final UiHostTheme THEME;

    // 命名空间与路径的分隔符
    private static final char NAMESPACE_SEPARATOR = ':';

    // 输入框字符上限：完整 id（含命名空间）与单独路径共用同一上限
    static final int MAX_NAME_LENGTH = 128;
    static final int MODAL_WIDTH = 320;
    private static final int INPUT_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int ERROR_ROWS = 2;

    private final Font font;
    private final UiTextInput input;
    private final Component help = Component.translatable(TypeLabels.UI_PREFIX + "prompt.name_help");
    private @Nullable Component problem;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    RuleNameInput(UiHostTheme theme, Font font) {
        this.THEME = UiHostThemes.require(theme);
        this.font = font;
        input = new UiTextInput(THEME, font, Component.translatable(TypeLabels.UI_PREFIX + "prompt.name_hint"));
        input.setFilter(RuleNameInput::allowsCharacters);
    }

    /**
     * 解析玩家输入的规则名：完整的 `<namespace>:<path>` 保留输入命名空间，
     * 只输入 `<path>` 时补本模组命名空间；任一段为空或未通过字符限制时返回 null。
     *
     * @param value 输入框原文，允许为 null
     * @return 完整规则 id；非法时返回 null
     */
    static @Nullable ResourceLocation parseId(@Nullable String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_NAME_LENGTH || !allowsCharacters(value)) {
            return null;
        }
        int separator = value.indexOf(NAMESPACE_SEPARATOR);
        if (separator < 0) {
            return ResourceLocation.tryParse(TypeLabels.OWN_NAMESPACE + NAMESPACE_SEPARATOR + value);
        }
        // 冒号前后都必须有内容：单独的前导冒号不构成「带命名空间」的写法
        if (separator == 0 || separator == value.length() - 1 || value.indexOf(NAMESPACE_SEPARATOR, separator + 1) >= 0) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }

    /**
     * 输入字符限制：单个命名空间或路径段只允许小写字母、数字与下划线；
     * 完整 id 额外允许一个冒号作为命名空间分隔符，冒号本身不参与路径字符判定。
     *
     * @param name 待校验的输入文本
     * @return 是否允许输入该文本
     */
    static boolean allowsCharacters(String name) {
        return name.chars().allMatch(character -> character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9' || character == '_' || character == NAMESPACE_SEPARATOR);
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

    /** 导出真实命名输入框，与弹窗的输入和校验使用同一控件。 */
    @Override
    public List<UiLayoutNode> layoutNodes() { return input.layoutNodes(); }

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
    public boolean mousePressed(UiInputContext context) {
        return input.mousePressed(context);
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        return input.mouseReleased(context);
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        return input.mouseDragged(context, dragX, dragY);
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
    public UiFocusState focusState() {
        return input.focusState();
    }

    @Override
    public boolean canFocus() {
        return input.canFocus();
    }

    @Override
    public void focusChanged(boolean focused) {
        input.focusChanged(focused);
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
