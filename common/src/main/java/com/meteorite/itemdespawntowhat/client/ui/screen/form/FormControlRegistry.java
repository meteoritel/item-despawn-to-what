package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import net.minecraft.client.gui.Font;

/**
 * 字段类型到控件工厂的完整映射；新增类型必须为穷举 switch 提供控件。
 * 数值字段仍先由 {@link FormControl#create} 尝试滑杆，元数据不足时使用本表的文本控件。
 * 第三方参数只读展示使用显式的 RAW_JSON 类型。
 */
final class FormControlRegistry {

    @FunctionalInterface
    interface Factory {
        FormControl create(Font font, FormView owner, EditorField field, Runnable onChanged);
    }

    // 纯文本字段的共用工厂：长度由输入框管理，过滤器不拒绝程序化回填
    private static final Factory TEXT_FACTORY = (font, owner, field, onChanged) -> new TextControl(owner.theme(), font, field,
            FormControl.textFilter(), FormControl::literalValue, FormControl::textOf, onChanged,
            FormControl.ISSUE_PREFIX + "invalid_text");

    // 工具类，禁止实例化
    private FormControlRegistry() {
    }

    // 字段类型全覆盖；控件创建仍属于 UI 层。
    private static Factory factory(EditorFieldType type) {
        return switch (type) {
            case ITEM_COUNTS -> (font, owner, field, onChanged) ->
                    new ItemCountsControl(owner.theme(), font, field, onChanged);
            case TEXT, LONG_TEXT -> TEXT_FACTORY;
            case RESOURCE_LOCATION, REGISTRY_ID -> (font, owner, field, onChanged) ->
                    new TextControl(owner.theme(), font, field, FormControl::idChars, text -> FormControl.referenceValue(text, false),
                            FormControl::textOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_id");
            case TAG -> (font, owner, field, onChanged) ->
                    new TextControl(owner.theme(), font, field, FormControl::tagChars, text -> FormControl.referenceValue(text, true),
                            FormControl::textOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_id");
            case INTEGER -> (font, owner, field, onChanged) -> {
                int min = field.intMin(Integer.MIN_VALUE);
                int max = field.intMax(Integer.MAX_VALUE);
                return new TextControl(owner.theme(), font, field, FormControl::integerChars, text -> FormControl.boundedInt(text, min, max),
                        FormControl::intOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case TICKS -> (font, owner, field, onChanged) -> {
                // 刻数合法域为 0..INT_MAX；UI 侧不额外收紧，后端合法域才是提交依据
                int min = field.intMin(0);
                int max = field.intMax(Integer.MAX_VALUE);
                return new TextControl(owner.theme(), font, field, FormControl::integerChars, text -> FormControl.boundedInt(text, min, max),
                        FormControl::intOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case DECIMAL -> (font, owner, field, onChanged) -> {
                double min = field.doubleMin(-Double.MAX_VALUE);
                double max = field.doubleMax(Double.MAX_VALUE);
                return new TextControl(owner.theme(), font, field, FormControl::decimalChars, text -> FormControl.boundedDouble(text, min, max),
                        FormControl::decimalOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case PERCENT -> (font, owner, field, onChanged) -> {
                double min = field.doubleMin(0.0D);
                double max = field.doubleMax(1.0D);
                return new TextControl(owner.theme(), font, field, FormControl::decimalChars,
                        text -> FormControl.percentValue(text, min, max), FormControl::percentText,
                        onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            };
            case AMPLIFIER -> (font, owner, field, onChanged) ->
                    new TextControl(owner.theme(), font, field, FormControl::integerChars, FormControl::amplifierValue, FormControl::amplifierText,
                            onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
            case BOOLEAN -> (font, owner, field, onChanged) -> new BoolControl(owner.theme(), font, field, onChanged);
            case ENUM -> (font, owner, field, onChanged) -> new EnumControl(owner.theme(), font, field, onChanged);
            case TAG_LIST, RL_LIST, STRING_LIST ->
                    (font, owner, field, onChanged) -> new ListControl(owner.theme(), font, field, onChanged);
            case CLIMATE_RANGE -> (font, owner, field, onChanged) -> new ClimateControl(owner.theme(), font, field, onChanged);
            case CONDITION_TREE -> (font, owner, field, onChanged) ->
                    new ConditionTreeControl(owner.theme(), font, field, owner.conditionEditingSupport(), () -> owner.conditionContext(field));
            case SUBLIST -> SublistControl::new;
            case CARD_LIST -> CardListControl::new;
            case OBJECT -> NestedObjectControl::new;
            case RAW_JSON -> (font, owner, field, onChanged) -> new RawJsonControl(owner.theme(), field);
            case NOTE -> (font, owner, field, onChanged) -> new NoteControl(owner.theme(), field);
        };
    }

    /**
     * 按字段类型创建控件，所有已知字段都有显式工厂。
     *
     * @return 该字段对应的控件，绝不为 null
     */
    static FormControl create(Font font, FormView owner, EditorField field, Runnable onChanged) {
        return factory(field.type()).create(font, owner, field, onChanged);
    }
}
