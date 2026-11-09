package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import net.minecraft.network.chat.Component;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * 字段类型到控件工厂的完整映射；新增类型必须为穷举 switch 提供控件。
 * 数值字段仍先由 {@link FormControl#create} 尝试滑杆，元数据不足时使用本表的文本控件。
 * 第三方参数只读展示使用显式的 RAW_JSON 类型。
 */
final class FormControlRegistry {

    @FunctionalInterface
    interface Factory {
        FormControl create(UiHostTheme theme, Font font, EditorField field, Runnable onChanged);
    }

    // 纯文本字段的共用工厂：长度由输入框管理，过滤器不拒绝程序化回填
    private static final Factory TEXT_FACTORY = (theme, font, field, onChanged) -> new TextControl(theme, font, field,
            FormControl.textFilter(), FormControl::literalValue, FormControl::textOf, onChanged,
            FormControl.ISSUE_PREFIX + "invalid_text");

    // 工具类，禁止实例化
    private FormControlRegistry() {
    }

    // 字段类型全覆盖；控件创建仍属于 UI 层。
    private static Factory factory(EditorFieldType type) {
        return switch (type) {
            case ITEM_COUNTS -> (theme, font, field, onChanged) ->
                    new ItemCountsControl(theme, font, field, onChanged);
            case TEXT, LONG_TEXT -> TEXT_FACTORY;
            case RESOURCE_LOCATION, REGISTRY_ID -> (theme, font, field, onChanged) ->
                    new TextControl(theme, font, field, FormControl::idChars, text -> FormControl.referenceValue(text, false),
                            FormControl::textOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_id");
            case TAG -> (theme, font, field, onChanged) ->
                    new TextControl(theme, font, field, FormControl::tagChars, text -> FormControl.referenceValue(text, true),
                            FormControl::textOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_id");
            case INTEGER -> (theme, font, field, onChanged) -> {
                int min = field.intMin(Integer.MIN_VALUE);
                int max = field.intMax(Integer.MAX_VALUE);
                return new TextControl(theme, font, field, FormControl::integerChars, text -> FormControl.boundedInt(text, min, max),
                        FormControl::intOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case TICKS -> (theme, font, field, onChanged) -> {
                // 刻数合法域为 0..INT_MAX；UI 侧不额外收紧，后端合法域才是提交依据
                int min = field.intMin(0);
                int max = field.intMax(Integer.MAX_VALUE);
                return new TextControl(theme, font, field, FormControl::integerChars, text -> FormControl.boundedInt(text, min, max),
                        FormControl::intOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case DECIMAL -> (theme, font, field, onChanged) -> {
                double min = field.doubleMin(-Double.MAX_VALUE);
                double max = field.doubleMax(Double.MAX_VALUE);
                return new TextControl(theme, font, field, FormControl::decimalChars, text -> FormControl.boundedDouble(text, min, max),
                        FormControl::decimalOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case PERCENT -> (theme, font, field, onChanged) -> {
                double min = field.doubleMin(0.0D);
                double max = field.doubleMax(1.0D);
                return new TextControl(theme, font, field, FormControl::decimalChars,
                        text -> FormControl.percentValue(text, min, max), FormControl::percentText,
                        onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case AMPLIFIER -> (theme, font, field, onChanged) ->
                    new TextControl(theme, font, field, FormControl::integerChars, FormControl::amplifierValue, FormControl::amplifierText,
                            onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            case BOOLEAN -> (theme, font, field, onChanged) -> new BoolControl(theme, font, field, onChanged);
            case ENUM -> (theme, font, field, onChanged) -> new EnumControl(theme, font, field, onChanged);
            case TAG_LIST, RL_LIST, STRING_LIST ->
                    (theme, font, field, onChanged) -> new ListControl(theme, font, field, onChanged);
            case CLIMATE_RANGE -> (theme, font, field, onChanged) -> new ClimateControl(theme, font, field, onChanged);
            case CONDITION_TREE, SUBLIST, CARD_LIST, OBJECT -> throw new IllegalArgumentException("Composite field: " + type);
            case RAW_JSON -> (theme, font, field, onChanged) -> new RawJsonControl(theme, field);
            case NOTE -> (theme, font, field, onChanged) -> new NoteControl(theme, field);
        };
    }

    /**
     * 按字段类型创建控件，所有已知字段都有显式工厂。
     *
     * @return 该字段对应的控件，绝不为 null
     */
    static FormControl create(UiHostTheme theme, Font font, EditorField field, Runnable onChanged) {
        if (field.isCatalogReference() && FieldCatalogTypes.catalogTypeOf(field) == null) {
            return new UnavailableCatalogControl(theme, field);
        }
        return factory(field.type()).create(theme, font, field, onChanged);
    }

    private static final class UnavailableCatalogControl extends FormControl {
        private int x;
        private int y;
        private int width;

        private UnavailableCatalogControl(UiHostTheme theme,
                                          EditorField field) {
            super(theme, field);
        }

        @Override int height() { return defaultHeight(); }

        @Override void setBounds(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        @Override void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
            String text = Component.translatable(TypeLabels.UI_PREFIX + "catalog.unavailable").getString();
            String visible = TextScroll.trimToWidth(font, text, Math.max(0, width - 4));
            graphics.drawString(font, visible, x + 2, y + 2, THEME.palette().surfaces().secondaryText(), false);
        }

        @Override void load(@Nullable RuleValue value) {
            rememberLoaded(value);
        }

        @Override @Nullable RuleValue draftValue() {
            return originalValue();
        }
    }
}
