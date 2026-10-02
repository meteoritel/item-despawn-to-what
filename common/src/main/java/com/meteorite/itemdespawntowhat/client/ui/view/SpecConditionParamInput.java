package com.meteorite.itemdespawntowhat.client.ui.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.client.ui.condition.ConditionParameterInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 由声明式规格生成的条件参数控件：一行内并排渲染若干文本框/选择按钮，读写协议 JSON。
 * 复用既有条件子屏的 ConditionParameterInput 契约，布局风格与旧参数控件一致。
 */
public final class SpecConditionParamInput extends AbstractCompositeWidget implements ConditionParameterInput {

    private static final int GAP = 2;

    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<EditBox> editBoxes = new ArrayList<>();
    private final List<Binding> bindings = new ArrayList<>();

    public SpecConditionParamInput(Font font, List<ParamSpec> specs) {
        super(0, 0, 240, 20, Component.empty());
        if (specs.isEmpty()) {
            EditBox placeholder = new EditBox(font, 0, 0, 240, 20, Component.empty());
            placeholder.setHint(Component.translatable("gui.itemdespawntowhat.condition.no_params"));
            placeholder.setEditable(false);
            placeholder.active = false;
            widgets.add(placeholder);
            editBoxes.add(placeholder);
            return;
        }
        for (ParamSpec spec : specs) {
            switch (spec.kind()) {
                case BOOLEAN -> {
                    CycleButton<Boolean> button = CycleButton
                            .booleanBuilder(Component.translatable("gui.itemdespawntowhat.edit.on"),
                                    Component.translatable("gui.itemdespawntowhat.edit.off"))
                            .withInitialValue(Boolean.parseBoolean(spec.defaultValue()))
                            .create(0, 0, 60, 20, Component.translatable(spec.labelKey()));
                    button.setTooltip(Tooltip.create(Component.translatable(spec.labelKey())));
                    widgets.add(button);
                    bindings.add(new Binding(spec.key(), spec.required(),
                            () -> new JsonPrimitive(button.getValue()),
                            json -> button.setValue(json.has(spec.key()) && json.get(spec.key()).isJsonPrimitive()
                                    ? json.get(spec.key()).getAsBoolean()
                                    : Boolean.parseBoolean(spec.defaultValue()))));
                }
                case ENUM -> {
                    CycleButton<String> button = CycleButton
                            .<String>builder(value -> Component.translatable(spec.labelKey() + "." + value))
                            .withValues(spec.enumValues())
                            .withInitialValue(spec.defaultValue())
                            .create(0, 0, 60, 20, Component.translatable(spec.labelKey()));
                    button.setTooltip(Tooltip.create(Component.translatable(spec.labelKey())));
                    widgets.add(button);
                    bindings.add(new Binding(spec.key(), spec.required(),
                            () -> new JsonPrimitive(button.getValue()),
                            json -> button.setValue(json.has(spec.key()) && json.get(spec.key()).isJsonPrimitive()
                                    ? json.get(spec.key()).getAsString()
                                    : spec.defaultValue())));
                }
                default -> {
                    EditBox box = new EditBox(font, 0, 0, 80, 20, Component.empty());
                    box.setMaxLength(256);
                    box.setHint(Component.translatable(spec.labelKey()));
                    applyFilter(box, spec.kind());
                    widgets.add(box);
                    editBoxes.add(box);
                    bindings.add(bindingFor(spec, box));
                }
            }
        }
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int count = widgets.size();
        int width = Math.max(24, (getWidth() - GAP * (count - 1)) / Math.max(1, count));
        for (int index = 0; index < count; index++) {
            AbstractWidget widget = widgets.get(index);
            widget.setX(getX() + index * (width + GAP));
            widget.setY(getY());
            widget.setWidth(width);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return editBoxes;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AbstractWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                if (widget instanceof EditBox editBox) {
                    setInternalFocused(editBox);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public JsonObject value() {
        JsonObject json = new JsonObject();
        for (Binding binding : bindings) {
            JsonElement element = binding.reader().get();
            if (element != null) {
                json.add(binding.key(), element);
            }
        }
        return json;
    }

    @Override
    public void setValue(JsonObject value) {
        JsonObject source = value == null ? new JsonObject() : value;
        for (Binding binding : bindings) {
            binding.writer().accept(source);
        }
    }

    @Override
    public boolean isValid() {
        for (Binding binding : bindings) {
            if (binding.required() && binding.reader().get() == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setEditorWidth(int width) {
        setWidth(width);
    }

    @Override
    public void setEditable(boolean editable) {
        active = editable;
        for (AbstractWidget widget : widgets) {
            if (widget instanceof EditBox editBox) {
                editBox.setEditable(editable);
            } else {
                widget.active = editable;
            }
        }
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
        setValue(new JsonObject());
    }

    // 按规格种类生成读写绑定，控件无有效值时读取返回 null（写回时跳过该字段）
    private static Binding bindingFor(ParamSpec spec, EditBox box) {
        return switch (spec.kind()) {
            case LIST -> new Binding(spec.key(), spec.required(),
                    () -> {
                        JsonArray array = new JsonArray();
                        for (String piece : box.getValue().split(",")) {
                            String trimmed = piece.trim();
                            if (!trimmed.isEmpty()) {
                                array.add(trimmed);
                            }
                        }
                        return array.isEmpty() ? null : array;
                    },
                    json -> box.setValue(readListText(json, spec.key())));
            case INTEGER -> new Binding(spec.key(), spec.required(),
                    () -> {
                        String text = box.getValue().trim();
                        if (text.isEmpty()) {
                            return null;
                        }
                        try {
                            return new JsonPrimitive(Integer.parseInt(text));
                        } catch (NumberFormatException ignored) {
                            return null;
                        }
                    },
                    json -> box.setValue(readPrimitiveText(json, spec.key())));
            case DECIMAL -> new Binding(spec.key(), spec.required(),
                    () -> {
                        String text = box.getValue().trim();
                        if (text.isEmpty()) {
                            return null;
                        }
                        try {
                            return new JsonPrimitive(Double.parseDouble(text));
                        } catch (NumberFormatException ignored) {
                            return null;
                        }
                    },
                    json -> box.setValue(readPrimitiveText(json, spec.key())));
            default -> new Binding(spec.key(), spec.required(),
                    () -> box.getValue().isBlank() ? null : new JsonPrimitive(box.getValue().trim()),
                    json -> box.setValue(readPrimitiveText(json, spec.key())));
        };
    }

    private static void applyFilter(EditBox box, ParamSpec.Kind kind) {
        switch (kind) {
            case INTEGER -> box.setFilter(value -> value.matches("-?\\d*"));
            case DECIMAL -> box.setFilter(value -> value.matches("-?\\d*\\.?\\d*"));
            default -> { }
        }
    }

    private static String readPrimitiveText(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static String readListText(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(element.getAsString());
        }
        return builder.toString();
    }

    // 单个参数的读写绑定
    private record Binding(String key, boolean required, Supplier<JsonElement> reader, Consumer<JsonObject> writer) {
    }
}
