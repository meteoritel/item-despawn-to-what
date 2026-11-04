package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTextArea;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiNumberField;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiNumbers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiScalarSlider;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.function.Consumer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;

/** 文本、数值与泛型列表只依赖 kit、原版类型和本地值。 */
final class KitInputsExample implements UiWidget, UiLayoutSource {
    private static final int GUTTER = 12;
    private static final int STATE_LINES = 2;
    private final Font font;
    private final UiStyleSource theme;
    private final UiTextInput text;
    private final UiTextArea area;
    private final UiTextInput precise;
    private final UiScalarSlider slider = new UiScalarSlider(UiNumberPolicy.of(0, 100, 1, 0.1));
    private final UiNumberField number;
    private final UiListView<Component> list;
    private final UiChildren<UiWidget> widgets = new UiChildren<>();
    private final List<Caption> captions = new ArrayList<>();
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect stateBlock = new UiRect(0, 0, 0, 0);
    private Component stateValue = Component.empty();
    private List<String> stateLines = List.of();
    private int commits;
    private int overflows;

    private record Caption(Component text, int x, int y) {}

    KitInputsExample(UiStyleSource theme, Font font) {
        this.theme = theme;
        this.font = font;
        text = new UiTextInput(theme, font, label("text")).setMaxLength(32).setAccessibleName(label("text"));
        area = new UiTextArea(theme, font, label("area"));
        area.setAccessibleName(label("area"));
        area.setMaxLength(512);
        text.setOnOverflow(() -> overflows++);
        slider.setTheme(theme, UiSliderRole.COMPACT);
        slider.setValue(37);
        precise = new UiTextInput(theme, font, label("number")).setAccessibleName(label("number"));
        precise.setValue("37");
        number = new UiNumberField(slider, precise, UiNumbers::parseFinite, value -> UiNumbers.plain(value, 2),
                () -> commits++, () -> { }, null, null, new UiNumberField.Metrics(8, 40));
        number.setInputWidth(56);
        number.acceptText();
        list = new UiListView<>(theme, (graphics, rowFont, item, index, row, selected, hovered, focused) ->
                graphics.drawString(rowFont, item, row.x() + 2, row.y() + 3, theme.textColor(true), false));
        list.setItems(IntStream.range(0, 30).mapToObj(index ->
                (Component) Component.translatable(KitShowcaseContent.UI + "list.item", index)).toList());
        list.setSelectedIndex(0);
        widgets.addAll(List.of(text, area, number, list));
    }
    public void addFocusTargets(Consumer<UiFocusTarget> sink) {
        widgets.addFocusTargets(sink);
    }
    @Override public long focusRevision() { return widgets.focusRevision(); }
    @Override public UiRect bounds() { return bounds; }

    /**
     * 左右两列：左列单行输入、多行输入与数值，右列列表；底部一行展示输入状态。
     *
     * <p>多行输入吃掉左列剩余高度，列表对齐到同一底边，避免两列高度参差。</p>
     */
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int capHeight = font.lineHeight + 2;
        int stateHeight = font.lineHeight + 2 + STATE_LINES * (font.lineHeight + 2);
        int columnsBottom = Math.max(y, y + height - stateHeight - WorkbenchLayout.GROUP_GAP);
        int leftWidth = Math.max(80, (width - GUTTER) / 2);
        int rightX = x + leftWidth + GUTTER;
        int rightWidth = Math.max(0, width - leftWidth - GUTTER);
        captions.clear();
        captions.add(new Caption(label("text"), x, y));
        text.setBounds(x, y + capHeight, leftWidth, 20);
        int areaCaptionTop = text.bounds().bottom() + WorkbenchLayout.GAP;
        captions.add(new Caption(label("area"), x, areaCaptionTop));
        int areaTop = areaCaptionTop + capHeight;
        int areaHeight = Math.max(36, columnsBottom - areaTop - capHeight - 24 - WorkbenchLayout.GAP);
        area.setBounds(x, areaTop, leftWidth, areaHeight);
        int numberCaptionTop = area.bounds().bottom() + WorkbenchLayout.GAP;
        captions.add(new Caption(label("number"), x, numberCaptionTop));
        number.setBounds(x, numberCaptionTop + capHeight, leftWidth, 24);
        captions.add(new Caption(label("list"), rightX, y));
        list.setBounds(rightX, y + capHeight, rightWidth, Math.max(0, columnsBottom - (y + capHeight)));
        stateBlock = new UiRect(x, columnsBottom + WorkbenchLayout.GROUP_GAP, width, stateHeight);
        stateValue = Component.translatable(KitShowcaseContent.UI + "showcase.inputs.values", text.value(), area.value(),
                precise.value(), slider.value(), list.selectedIndex(), commits, overflows);
        stateLines = WorkbenchLayout.wrapLines(font, stateValue.getString(), width, STATE_LINES);
    }

    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        int captionColor = theme.surfaces().secondaryText();
        for (Caption caption : captions) {
            graphics.drawString(font, caption.text(), caption.x(), caption.y(), captionColor, false);
        }
        for (UiWidget widget : widgets) widget.render(graphics, font, pointer);
        graphics.drawString(font, label("inputs.state"), stateBlock.x(), stateBlock.y(), captionColor, false);
        int lineY = stateBlock.y() + font.lineHeight + 2;
        for (String line : stateLines) {
            graphics.drawString(font, line, stateBlock.x(), lineY, theme.textColor(true), false);
            lineY += font.lineHeight + 2;
        }
    }
    @Override public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = widgets.hitTest(x, y);
        return hit == null ? null : hit.within(this);
    }
    @Override public boolean mousePressed(UiInputContext context) {
        return widgets.mousePressed(context);
    }
    @Override public boolean mouseScrolled(UiInputContext context, double x, double y) { return widgets.mouseScrolled(context, x, y); }
    @Override public boolean keyPressed(int key, int scan, int mods) { return widgets.keyPressed(key, scan, mods); }
    @Override public boolean charTyped(char value, int mods) { return widgets.charTyped(value, mods); }
    @Override public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>(text.layoutNodes());
        nodes.addAll(area.layoutNodes());
        nodes.addAll(precise.layoutNodes());
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, list.bounds(), label("list"), 0, 0, true, true, List.of()));
        UiRect row = new UiRect(stateBlock.x(), stateBlock.y() + font.lineHeight + 2, stateBlock.width(),
                Math.max(0, stateBlock.height() - font.lineHeight - 2));
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, stateBlock, label("inputs.state"), 0, 0, true, true, List.of(
                new UiLayoutNode(UiLayoutNode.Kind.ROW, row, stateValue, 0, 0, true, true, List.of()))));
        return nodes;
    }
    private static Component label(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase." + key); }
}
