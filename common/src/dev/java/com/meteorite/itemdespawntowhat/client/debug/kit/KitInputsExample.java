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
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLinearLayout;
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
    private final UiTextInput text;
    private final UiTextArea area;
    private final UiTextInput precise;
    private final UiScalarSlider slider = new UiScalarSlider(UiNumberPolicy.of(0, 100, 1, 0.1));
    private final UiNumberField number;
    private final UiListView<Component> list;
    private final UiChildren<UiWidget> widgets = new UiChildren<>();
    private final UiLinearLayout columns = new UiLinearLayout(UiLinearLayout.Axis.HORIZONTAL, 12, 0);
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private int commits;
    private int overflows;

    KitInputsExample(UiStyleSource theme, Font font) {
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
        columns.setChildren(List.of(UiLinearLayout.Child.remain(), UiLinearLayout.Child.remain()));
    }
    public void addFocusTargets(Consumer<UiFocusTarget> sink) {
        widgets.addFocusTargets(sink);
    }
    @Override public long focusRevision() { return widgets.focusRevision(); }
    @Override public UiRect bounds() { return bounds; }
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        columns.setBounds(x, y, width, height);
        UiRect left = columns.bounds(0);
        UiRect right = columns.bounds(1);
        text.setBounds(left.x(), y, left.width(), 20);
        area.setBounds(left.x(), y + 28, left.width(), Math.clamp(height - 100, 36, 100));
        number.setBounds(left.x(), area.bounds().bottom() + 12, left.width(), 24);
        list.setBounds(right.x(), y, right.width(), Math.max(0, height - 24));
    }
    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        for (UiWidget widget : widgets) widget.render(graphics, font, pointer);
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
        UiRect row = new UiRect(bounds.x(), bounds.bottom() - 16, bounds.width(), 12);
        Component values = Component.translatable(KitShowcaseContent.UI + "showcase.inputs.values", text.value(), area.value(),
                precise.value(), slider.value(), list.selectedIndex(), commits, overflows);
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, row, label("inputs.state"), 0, 0, true, true, List.of(
                new UiLayoutNode(UiLayoutNode.Kind.ROW, row, values, 0, 0, true, true, List.of()))));
        return nodes;
    }
    private static Component label(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase." + key); }
}
