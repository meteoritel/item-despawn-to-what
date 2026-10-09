package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 物品数量对象的表单控件：复用数值控件的滑条、精确输入、撤销与错误校验。 */
final class ItemCountsControl extends FormControl {
    private static final int ROW_GAP = 6;
    private static final int HEADER_GAP = 4;
    private static final int NUMBER_HEIGHT = 20;
    private static final int DEFAULT_COUNT = 1;
    private static final int INITIAL_SLIDER_MAX = 64;
    private final Font font;
    private final Runnable onChanged;
    private final Map<String, NumberControl> controls = new LinkedHashMap<>();
    private final Map<String, Component> labels = new LinkedHashMap<>();
    private final Map<String, UiRect> headers = new LinkedHashMap<>();
    private JsonObject values = new JsonObject();
    private int defaultCount = DEFAULT_COUNT;
    private @Nullable NumberControl pressed;

    ItemCountsControl(Font font, EditorField field, Runnable onChanged) {
        super(field);
        this.font = font;
        this.onChanged = onChanged;
    }

    void setReferences(List<String> references) {
        List<String> unique = references.stream().distinct().toList();
        if (new ArrayList<>(controls.keySet()).equals(unique)) return;
        Map<String, NumberControl> next = new LinkedHashMap<>();
        labels.clear();
        for (String reference : unique) {
            NumberControl control = controls.get(reference);
            if (control == null) {
                EditorField number = EditorField.integerSlider(field.name(), field.labelKey(),
                        field.intMin(DEFAULT_COUNT), field.intMax(Integer.MAX_VALUE), DEFAULT_COUNT, INITIAL_SLIDER_MAX);
                control = java.util.Objects.requireNonNull(NumberControl.create(font, number, () -> {
                    markEdited();
                    onChanged.run();
                }));
                control.setMinimumHeight(NUMBER_HEIGHT);
                control.load(values.has(reference) ? values.get(reference) : new JsonPrimitive(defaultCount));
            }
            next.put(reference, control);
            ResourceLocation id = reference.startsWith("#") ? null : ResourceLocation.tryParse(reference);
            Component item = id == null ? Component.literal(reference) : BuiltInRegistries.ITEM.getOptional(id)
                    .map(value -> Component.translatable(value.getDescriptionId()))
                    .orElse(Component.literal(reference));
            labels.put(reference, Component.translatable("gui.itemdespawntowhat.edit.quantity.row", item, label()));
        }
        controls.clear();
        controls.putAll(next);
    }

    void setDefaultCount(int count) { defaultCount = count; }

    @Override boolean usesLabelColumn() { return false; }
    @Override int height() { return controls.size() * (font.lineHeight + HEADER_GAP + NUMBER_HEIGHT + ROW_GAP); }

    @Override
    void setBounds(int x, int y, int width) {
        headers.clear();
        for (var entry : controls.entrySet()) {
            headers.put(entry.getKey(), new UiRect(x, y, width, font.lineHeight));
            entry.getValue().setBounds(x, y + font.lineHeight + HEADER_GAP, width);
            y += font.lineHeight + HEADER_GAP + NUMBER_HEIGHT + ROW_GAP;
        }
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (var entry : controls.entrySet()) {
            UiRect header = headers.get(entry.getKey());
            if (header == null) continue;
            graphics.drawString(font, font.plainSubstrByWidth(labels.get(entry.getKey()).getString(), header.width()),
                    header.x(), header.y(), UiPalette.TEXT_SECONDARY, false);
            entry.getValue().render(graphics, font, mouseX, mouseY);
        }
    }

    @Override
    void load(@Nullable JsonElement value) {
        rememberLoaded(value);
        values = value != null && value.isJsonObject() ? value.getAsJsonObject().deepCopy() : new JsonObject();
        controls.forEach((reference, control) -> control.load(
                values.has(reference) ? values.get(reference) : new JsonPrimitive(defaultCount)));
    }

    @Override
    @Nullable JsonElement store() {
        if (!isEdited() && !hasPendingInput()) return originalElement();
        JsonObject result = new JsonObject();
        controls.forEach((reference, control) -> result.add(reference, control.store()));
        return result;
    }

    @Override
    List<FormIssue> issues(String path) {
        List<FormIssue> result = new ArrayList<>();
        controls.values().forEach(control -> result.addAll(control.issues(path)));
        return result;
    }

    @Override
    boolean mouseClicked(double x, double y, int button) {
        for (NumberControl control : controls.values()) if (control.mouseClicked(x, y, button)) {
            pressed = control;
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double x, double y, int button) {
        NumberControl owner = pressed;
        pressed = null;
        return owner != null && owner.mouseReleased(x, y, button);
    }

    @Override boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return pressed != null && pressed.mouseDragged(x, y, button, dx, dy);
    }
    @Override boolean keyPressed(int key, int scan, int modifiers) {
        for (NumberControl control : controls.values()) if (control.keyPressed(key, scan, modifiers)) return true;
        return false;
    }
    @Override boolean keyReleased(int key, int scan, int modifiers) {
        for (NumberControl control : controls.values()) if (control.keyReleased(key, scan, modifiers)) return true;
        return false;
    }
    @Override boolean charTyped(char point, int modifiers) {
        for (NumberControl control : controls.values()) if (control.charTyped(point, modifiers)) return true;
        return false;
    }
    @Override void addFocusTargets(List<UiFocusTarget> out) { controls.values().forEach(control -> control.addFocusTargets(out)); }
    @Override void setEnabled(boolean enabled) { controls.values().forEach(control -> control.setEnabled(enabled)); }
    @Override boolean hasPendingInput() { return controls.values().stream().anyMatch(NumberControl::hasPendingInput); }
    @Override boolean isEditing() { return controls.values().stream().anyMatch(NumberControl::isEditing); }
    @Override void finishInput() { controls.values().forEach(NumberControl::finishInput); }
    @Override void endInteractions(UiInputCapture.EndReason reason) { controls.values().forEach(control -> control.endInteractions(reason)); }
    @Override @Nullable Component tooltipAt(double x, double y) {
        for (var header : headers.entrySet()) if (header.getValue().contains(x, y)) return labels.get(header.getKey()).copy()
                .append("\n").append(header.getKey());
        return null;
    }
}
