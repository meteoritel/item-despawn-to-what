package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.Font;

/**
 * 字段控件注册表：{@link EditorFieldType} → 控件工厂。
 *
 * <p>新增一个字段控件只需两步：实现 {@link FormControl} 子类，并在 {@link #factories()} 追加一行登记；
 * 数值字段仍由 {@link FormControl#create} 先尝试「滑杆 + 行内精确输入」，域元数据不足时才落到本表。
 * 未登记的类型回退为只读 JSON 控件，与拆分前 switch 的 default 分支一致。</p>
 *
 * <p>登记表在类初始化时构建一次后只读，运行期不再增删；工厂签名与 {@link FormControl#create} 一致，
 * 不新增对外公开面。</p>
 */
final class FormControlRegistry {

    /** 字段控件工厂：参数顺序与 {@link FormControl#create} 一致。 */
    @FunctionalInterface
    interface Factory {
        FormControl create(Font font, FormView owner, EditorField field, Runnable onChanged);
    }

    // 纯文本字段的共用工厂：长度由输入框管理，过滤器不拒绝程序化回填
    private static final Factory TEXT_FACTORY = (font, owner, field, onChanged) -> new TextControl(font, field,
            FormControl.textFilter(), FormControl::literalValue, FormControl::textOf, onChanged,
            FormControl.ISSUE_PREFIX + "invalid_text");

    // 登记表：类型 → 工厂
    private static final Map<EditorFieldType, Factory> FACTORIES = factories();

    // 工具类，禁止实例化
    private FormControlRegistry() {
    }

    // 字段控件登记处：新增字段控件在此追加一行
    private static Map<EditorFieldType, Factory> factories() {
        Map<EditorFieldType, Factory> map = new EnumMap<>(EditorFieldType.class);
        map.put(EditorFieldType.ITEM_COUNTS, (font, owner, field, onChanged) ->
                new ItemCountsControl(font, field, onChanged));
        map.put(EditorFieldType.TEXT, TEXT_FACTORY);
        map.put(EditorFieldType.LONG_TEXT, TEXT_FACTORY);
        map.put(EditorFieldType.RESOURCE_LOCATION, (font, owner, field, onChanged) ->
                new TextControl(font, field, FormControl::idChars, text -> FormControl.referenceValue(text, false),
                        FormControl::textOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_id"));
        map.put(EditorFieldType.REGISTRY_ID, (font, owner, field, onChanged) ->
                new TextControl(font, field, FormControl::idChars, text -> FormControl.referenceValue(text, false),
                        FormControl::textOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_id"));
        map.put(EditorFieldType.TAG, (font, owner, field, onChanged) ->
                new TextControl(font, field, FormControl::tagChars, text -> FormControl.referenceValue(text, true),
                        FormControl::textOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_id"));
        map.put(EditorFieldType.INTEGER, (font, owner, field, onChanged) -> {
            int min = field.intMin(Integer.MIN_VALUE);
            int max = field.intMax(Integer.MAX_VALUE);
            return new TextControl(font, field, FormControl::integerChars, text -> FormControl.boundedInt(text, min, max),
                    FormControl::intOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
        });
        map.put(EditorFieldType.TICKS, (font, owner, field, onChanged) -> {
            // 刻数合法域为 0..INT_MAX；UI 侧不额外收紧，后端合法域才是提交依据
            int min = field.intMin(0);
            int max = field.intMax(Integer.MAX_VALUE);
            return new TextControl(font, field, FormControl::integerChars, text -> FormControl.boundedInt(text, min, max),
                    FormControl::intOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
        });
        map.put(EditorFieldType.DECIMAL, (font, owner, field, onChanged) -> {
            double min = field.doubleMin(-Double.MAX_VALUE);
            double max = field.doubleMax(Double.MAX_VALUE);
            return new TextControl(font, field, FormControl::decimalChars, text -> FormControl.boundedDouble(text, min, max),
                    FormControl::decimalOf, onChanged, FormControl.ISSUE_PREFIX + "invalid_number");
        });
        map.put(EditorFieldType.PERCENT, (font, owner, field, onChanged) ->
                new TextControl(font, field, FormControl::decimalChars, FormControl::percentValue, FormControl::percentText,
                        onChanged, FormControl.ISSUE_PREFIX + "invalid_number"));
        map.put(EditorFieldType.AMPLIFIER, (font, owner, field, onChanged) ->
                new TextControl(font, field, FormControl::integerChars, FormControl::amplifierValue, FormControl::amplifierText,
                        onChanged, FormControl.ISSUE_PREFIX + "invalid_number"));
        map.put(EditorFieldType.BOOLEAN, (font, owner, field, onChanged) -> new BoolControl(font, field, onChanged));
        map.put(EditorFieldType.ENUM, (font, owner, field, onChanged) -> new EnumControl(font, field, onChanged));
        map.put(EditorFieldType.TAG_LIST, (font, owner, field, onChanged) -> new ListControl(font, field, onChanged));
        map.put(EditorFieldType.RL_LIST, (font, owner, field, onChanged) -> new ListControl(font, field, onChanged));
        map.put(EditorFieldType.STRING_LIST, (font, owner, field, onChanged) -> new ListControl(font, field, onChanged));
        map.put(EditorFieldType.CLIMATE_RANGE, (font, owner, field, onChanged) -> new ClimateControl(font, field, onChanged));
        map.put(EditorFieldType.CONDITION_TREE, (font, owner, field, onChanged) ->
                new ConditionTreeControl(font, field, owner.conditionSupport()));
        map.put(EditorFieldType.SUBLIST, SublistControl::new);
        map.put(EditorFieldType.RAW_JSON, (font, owner, field, onChanged) -> new RawJsonControl(field));
        map.put(EditorFieldType.NOTE, (font, owner, field, onChanged) -> new NoteControl(field));
        return Map.copyOf(map);
    }

    /**
     * 按字段类型创建控件；未登记的类型回退为只读 JSON 控件。
     *
     * @return 该字段对应的控件，绝不为 null
     */
    static FormControl create(Font font, FormView owner, EditorField field, Runnable onChanged) {
        Factory factory = FACTORIES.get(field.type());
        return factory == null ? new RawJsonControl(field) : factory.create(font, owner, field, onChanged);
    }
}
