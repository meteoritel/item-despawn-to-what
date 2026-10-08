package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.EditorPreset;
import com.meteorite.itemdespawntowhat.client.edit.FieldNumbers;
import com.meteorite.itemdespawntowhat.client.edit.JsonSummary;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextArea;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 表单控件基类：一个字段对应一行控件。
 * <p>负责「JSON 值 &lt;-&gt; 控件值」的双向搬运与本地校验；
 * 写入草稿由 FormView 统一经由 EditSession 完成，控件本身不碰草稿。
 */
abstract class FormControl {

    // 行默认高度（与主题行高一致）
    static final int DEFAULT_HEIGHT = UiTheme.ROW_HEIGHT;

    // 校验问题所用的本地化 key 前缀
    static final String ISSUE_PREFIX = "gui.itemdespawntowhat.edit.issue.";
    // 即时提示（notice）key：字段名 + 问题文案
    static final String NOTICE_ISSUE = "gui.itemdespawntowhat.edit.notice.issue";

    // 控件所描述的字段
    final EditorField field;

    // 行高覆盖值：>0 时生效（例如基本页的加高优先级控件），-1 表示用控件自然高度
    private int heightOverride = -1;
    // 输入被上限拒绝时的即时提示出口（由 FormView 转给界面）
    private @Nullable Consumer<Component> rejectedNotice;

    // 装载时的原始 JSON 元素（键缺失记为 null）；未编辑时原样回写，applyToDraft 因此完全跳过该字段
    private @Nullable JsonElement loadedElement;
    // 用户是否真正交互过（点击/切换/提交/增删改）；程序化回填不置位
    private boolean edited;

    FormControl(EditorField field) {
        this.field = field;
    }

    // 由 load() 调用：记录原始元素并清除编辑标记
    final void rememberLoaded(@Nullable JsonElement value) {
        // 键缺失（null 引用）与显式 JsonNull 必须区分：前者保持 null，后者要原样保留，
        // 否则未编辑时 store() 返回 null，applyToDraft 会把显式 null 字段 removeAt 掉
        this.loadedElement = value == null ? null : value.deepCopy();
        this.edited = false;
    }

    // 由用户交互路径调用：标记该字段已被明确编辑
    final void markEdited() {
        this.edited = true;
    }

    // 用户是否明确编辑过该字段
    final boolean isEdited() {
        return edited;
    }

    // 装载原始元素的副本；键缺失返回 null
    final @Nullable JsonElement originalElement() {
        return loadedElement == null ? null : loadedElement.deepCopy();
    }

    // 字段标签
    Component label() {
        return Component.translatable(field.labelKey());
    }

    // 字段提示（工具提示），没有则返回 null
    @Nullable Component hint() {
        String key = field.hintKey();
        return key == null || key.isBlank() ? null : Component.translatable(key);
    }

    // 该行需要的高度
    abstract int height();

    // 设置控件矩形（高度按 height() 决定）
    abstract void setBounds(int x, int y, int width);

    // 绘制控件
    abstract void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    // 把 JSON 值装载进控件
    abstract void load(@Nullable JsonElement value);

    // 把控件值写回 JSON，返回 null 表示删除该字段
    abstract @Nullable JsonElement store();

    // 该控件最近一次改动对应的撤销操作 key（撤销按钮据此显示真实操作名，子类可覆写）
    String undoOpKey() {
        return EditSession.OP_SET_FIELD;
    }

    // 是否占用左侧标签列（说明行不占用）
    boolean usesLabelColumn() {
        return true;
    }

    // 是否正在编辑文本（编辑中不落盘，失焦后由 FormView 统一提交）
    boolean isEditing() {
        return false;
    }

    boolean isVisible() {
        return true;
    }

    // 列表等复合控件先结束尚未提交的条目输入。
    void finishInput() {
    }

    // 尚未写入草稿的非法文本不能在切换或重建时静默丢弃。
    boolean hasPendingInput() {
        return false;
    }

    // 长文本悬停时显示完整内容。
    @Nullable Component fullText() {
        return null;
    }

    // 本地校验问题清单
    List<FormIssue> issues(String path) {
        return List.of();
    }

    // 需要先知道宽度才能算出高度的控件（子列表）覆写
    void measure(int width) {
    }

    // 覆盖本控件占用的行高（<=0 表示恢复自然高度）
    final void setHeightOverride(int height) {
        this.heightOverride = height;
    }

    // 布局实际使用的行高：有覆盖用覆盖值，否则等于控件自然高度
    final int layoutHeight() {
        return heightOverride > 0 ? heightOverride : height();
    }

    // 输入被上限拒绝时的即时提示出口
    final void setRejectedNotice(@Nullable Consumer<Component> rejectedNotice) {
        this.rejectedNotice = rejectedNotice;
    }

    // 上报一条「字段名：问题」的即时提示；没有专属文案时不提示
    final void reportRejected(@Nullable String messageKey) {
        if (rejectedNotice != null && messageKey != null) {
            rejectedNotice.accept(Component.translatable(NOTICE_ISSUE, label(), Component.translatable(messageKey)));
        }
    }

    // 应用候选值（输入建议选择框回调）
    void setValueFromSuggestion(String value) {
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    // 按键抬起：方向键重复在抬起时合并为一次提交
    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    void addFocusTargets(List<UiFocusTarget> out) {
    }

    void setEnabled(boolean enabled) {
    }

    // 统一结束入口：宿主隐藏/禁用/卸载/失焦作用域切换/关闭时回退未提交的交互
    void endInteractions(UiInputCapture.EndReason reason) {
    }

    // ---- 工厂：按字段类型挑选控件 ----

    static FormControl create(Font font, FormView owner, EditorField field, Runnable onChanged) {
        // 数值字段优先走「滑杆 + 行内精确输入」，域元数据不足时仍回退文本控件
        if (isNumericType(field.type())) {
            NumberControl numeric = NumberControl.create(font, field, onChanged);
            if (numeric != null) {
                return numeric;
            }
        }
        switch (field.type()) {
            case TEXT:
            case LONG_TEXT:
                return new TextControl(font, field, textFilter(),
                        FormControl::literalValue, FormControl::textOf, onChanged, ISSUE_PREFIX + "invalid_text");
            case RESOURCE_LOCATION:
                return new TextControl(font, field, FormControl::idChars,
                        FormControl::literalValue, FormControl::textOf, onChanged, ISSUE_PREFIX + "invalid_id");
            case REGISTRY_ID:
                return new TextControl(font, field, FormControl::idChars,
                        FormControl::literalValue, FormControl::textOf, onChanged, ISSUE_PREFIX + "invalid_id");
            case TAG:
                return new TextControl(font, field, FormControl::tagChars,
                        FormControl::literalValue, FormControl::textOf, onChanged, ISSUE_PREFIX + "invalid_id");
            case INTEGER: {
                int min = field.intMin(Integer.MIN_VALUE);
                int max = field.intMax(Integer.MAX_VALUE);
                return new TextControl(font, field, FormControl::integerChars,
                        text -> boundedInt(text, min, max), FormControl::intOf, onChanged,
                        ISSUE_PREFIX + "invalid_number");
            }
            case TICKS: {
                // 刻数与字段规格 §4 一致：0..INT_MAX，旧 72000 只是 UI 人为上限，不得充当后端合法域
                int min = field.intMin(0);
                int max = field.intMax(Integer.MAX_VALUE);
                return new TextControl(font, field, FormControl::integerChars,
                        text -> boundedInt(text, min, max), FormControl::intOf, onChanged,
                        ISSUE_PREFIX + "invalid_number");
            }
            case DECIMAL: {
                double min = field.doubleMin(-Double.MAX_VALUE);
                double max = field.doubleMax(Double.MAX_VALUE);
                return new TextControl(font, field, FormControl::decimalChars,
                        text -> boundedDouble(text, min, max), FormControl::decimalOf, onChanged,
                        ISSUE_PREFIX + "invalid_number");
            }
            case PERCENT:
                return new TextControl(font, field, FormControl::decimalChars,
                        FormControl::percentValue, FormControl::percentText, onChanged,
                        ISSUE_PREFIX + "invalid_number");
            case AMPLIFIER:
                return new TextControl(font, field, FormControl::integerChars,
                        FormControl::amplifierValue, FormControl::amplifierText, onChanged,
                        ISSUE_PREFIX + "invalid_number");
            case BOOLEAN:
                return new BoolControl(font, field, onChanged);
            case ENUM:
                return new EnumControl(font, field, onChanged);
            case TAG_LIST:
            case RL_LIST:
            case STRING_LIST:
                return new ListControl(font, field, onChanged);
            case CLIMATE_RANGE:
                return new ClimateControl(font, field, onChanged);
            case CONDITION_TREE:
                return new ConditionTreeControl(font, field, owner.conditionSupport(), onChanged);
            case SUBLIST:
                return new SublistControl(font, owner, field, onChanged);
            case RAW_JSON:
                return new RawJsonControl(field);
            case NOTE:
                return new NoteControl(field);
            default:
                return new RawJsonControl(field);
        }
    }

    // ---- 文本过滤器与解析工具 ----

    static Predicate<String> textFilter() {
        // 长度由输入框管理，过滤器不拒绝已有长值的程序化回填。
        return text -> true;
    }

    // id 字符集（不含标签前缀）
    static boolean idChars(String text) {
        return allowedIdChars(text, false);
    }

    // 允许 # 前缀的 id 字符集
    static boolean tagChars(String text) {
        return allowedIdChars(text, true);
    }

    private static boolean allowedIdChars(String text, boolean allowTag) {
        if (text == null) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == ':' || c == '/'
                    || c == '-' || c == '*';
            if (allowTag && c == '#') {
                ok = true;
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static boolean integerChars(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = Character.isDigit(c) || (c == '-' && i == 0);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static boolean decimalChars(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = Character.isDigit(c) || c == '.' || (c == '-' && i == 0);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static JsonElement literalValue(String text) {
        return new JsonPrimitive(text);
    }

    static String textOf(JsonElement element) {
        return element.getAsString();
    }

    static @Nullable JsonElement boundedInt(String text, int min, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            return value < min || value > max ? null : new JsonPrimitive(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static String intOf(JsonElement element) {
        return Integer.toString(element.getAsInt());
    }

    static @Nullable JsonElement boundedDouble(String text, double min, double max) {
        Double value = parseDouble(text);
        if (value == null || value < min || value > max) {
            return null;
        }
        return new JsonPrimitive(value);
    }

    // 是否数值型字段（滑杆 + 行内精确输入）
    static boolean isNumericType(EditorFieldType type) {
        return type == EditorFieldType.INTEGER || type == EditorFieldType.TICKS
                || type == EditorFieldType.DECIMAL || type == EditorFieldType.PERCENT
                || type == EditorFieldType.AMPLIFIER;
    }

    // 界面百分比文本写回 JSON 小数（0..1）：十进制解析，保留原始精度
    static @Nullable JsonElement percentValue(String text) {
        Double value = FieldNumbers.parsePercent(text);
        if (value == null || value < 0.0D || value > 1.0D) {
            return null;
        }
        return new JsonPrimitive(value);
    }

    static String percentText(JsonElement element) {
        return FieldNumbers.percentText(element.getAsDouble());
    }

    // 界面等级（1 起，1..256）写回后端 amplifier（0 起，0..255）
    static @Nullable JsonElement amplifierValue(String text) {
        Integer amplifier = FieldNumbers.amplifierOf(text);
        return amplifier == null ? null : new JsonPrimitive(amplifier);
    }

    static String amplifierText(JsonElement element) {
        return Integer.toString(FieldNumbers.levelOf(element.getAsInt()));
    }

    static String decimalOf(JsonElement element) {
        return trimDouble(element.getAsDouble());
    }

    static @Nullable Double parseDouble(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(trimmed);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // 去掉多余的小数尾零
    static String trimDouble(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E9D) {
            return Long.toString((long) value);
        }
        String text = String.format(Locale.ROOT, "%.4f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    // ---- 单行文本控件（含数值、id、百分比等全部文本型字段） ----

    static final class TextControl extends FormControl {

        // 备注的行数：2–3 行内直接读完，不再依赖单行横向滚动
        private static final int NOTES_ROWS = 3;

        private final Font font;
        private final UiTextInput input;
        // 备注专用多行编辑框；单行字段为 null
        private final @Nullable UiTextArea area;
        private final List<UiButton> presetButtons = new ArrayList<>();
        private final Function<String, JsonElement> parse;
        private final Function<JsonElement, String> format;
        private final String invalidKey;
        private @Nullable JsonElement loaded;
        // 装载时写入输入框的文本；用户未改动且未提交时视为「未编辑」
        private String loadedText = "";
        // 本次输入因超出码点上限被拒绝（下一次被接受的输入变化时清除）
        private boolean rejectedOverflow;

        TextControl(Font font, EditorField field, Predicate<String> filter,
                Function<String, JsonElement> parse, Function<JsonElement, String> format,
                Runnable onChanged, String invalidKey) {
            super(field);
            this.font = font;
            this.parse = parse;
            this.format = format;
            this.invalidKey = invalidKey;
            Component hint = hint();
            this.input = new UiTextInput(font, hint == null ? Component.empty() : hint);
            this.input.setMaxLength(textLimit());
            this.input.setFilter(filter);
            this.input.setOnCommit(text -> {
                markEdited();
                onChanged.run();
            });
            this.input.setOnOverflow(this::notifyTooLong);
            // 下一次被接受的输入变化即清除超限提示
            this.input.setOnValueChanged(() -> this.rejectedOverflow = false);
            if (field.type() == EditorFieldType.LONG_TEXT) {
                // 备注使用真正的多行编辑框：支持换行、选择与滚动；单行输入框隐藏但保留装载基线
                this.area = new UiTextArea(font, hint);
                this.area.setRows(NOTES_ROWS);
                this.area.setOnChanged(() -> {
                    this.rejectedOverflow = false;
                    markEdited();
                    onChanged.run();
                });
                this.area.setOnOverflow(this::notifyTooLong);
                this.input.setVisible(false);
            } else {
                this.area = null;
            }
            for (EditorPreset preset : field.presets()) {
                UiButton button = new UiButton(font, Component.translatable(preset.labelKey()), UiButtonVariant.SECONDARY,
                        () -> {
                            markEdited();
                            setText(preset.value());
                            onChanged.run();
                        });
                presetButtons.add(button);
            }
        }

        @Override
        int height() {
            return area != null ? area.height() : DEFAULT_HEIGHT;
        }

        @Override
        void setBounds(int x, int y, int width) {
            if (area != null) {
                // 多行备注不支持预设按钮：直接铺满整行（presetButtons 恒为空，加预设需另行布局）
                area.setBounds(x, y, width, area.height());
                return;
            }
            int reserved = 0;
            for (UiButton button : presetButtons) {
                reserved += buttonWidth(button) + 2;
            }
            int inputWidth = Math.max(16, width - reserved);
            input.setBounds(x, y, inputWidth, DEFAULT_HEIGHT);
            int cursor = x + inputWidth + 2;
            for (UiButton button : presetButtons) {
                int buttonWidth = buttonWidth(button);
                button.setBounds(cursor, y, buttonWidth, DEFAULT_HEIGHT);
                cursor += buttonWidth + 2;
            }
        }

        // 预设按钮宽度：文本宽度 + 内边距
        private int buttonWidth(UiButton button) {
            return Math.max(16, font.width(button.label()) + 8);
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            if (area != null) {
                area.render(graphics, font, mouseX, mouseY);
                return;
            }
            input.render(graphics, font, mouseX, mouseY);
            for (UiButton button : presetButtons) {
                button.render(graphics, font, mouseX, mouseY);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (UiButton preset : presetButtons) {
                if (preset.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return area != null ? area.mouseClicked(mouseX, mouseY, button) : input.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        boolean mouseReleased(double mouseX, double mouseY, int button) {
            return area != null ? area.mouseReleased(mouseX, mouseY, button) : input.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return area != null && area.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return area != null && area.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return area != null ? area.keyPressed(keyCode, scanCode, modifiers) : input.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        boolean charTyped(char codePoint, int modifiers) {
            return area != null ? area.charTyped(codePoint, modifiers) : input.charTyped(codePoint, modifiers);
        }

        @Override
        void addFocusTargets(List<UiFocusTarget> out) {
            if (area != null) {
                out.add(area);
                return;
            }
            out.add(input);
        }

        @Override
        void setEnabled(boolean enabled) {
            if (area != null) {
                area.setEditable(enabled);
            }
            input.setEditable(enabled);
            for (UiButton preset : presetButtons) {
                preset.setEnabled(enabled);
            }
        }

        @Override
        boolean hasPendingInput() {
            return !text().equals(loadedText);
        }

        @Override
        @Nullable Component fullText() {
            return text().isEmpty() ? null : Component.literal(text());
        }

        @Override
        boolean isEditing() {
            return area != null ? area.isFocused() : input.isFocused();
        }

        @Override
        boolean isVisible() {
            return area != null ? area.isVisible() : input.isVisible();
        }

        // 当前文本：备注取自多行编辑框
        private String text() {
            return area != null ? area.value() : input.value();
        }

        // 程序化写入文本（预设值与建议值）
        private void setText(String value) {
            if (area != null) {
                area.setValue(value);
            } else {
                input.setValue(value);
            }
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            this.loaded = value == null || value.isJsonNull() ? null : value.deepCopy();
            this.loadedText = loaded == null ? "" : format.apply(loaded);
            this.rejectedOverflow = false;
            if (area != null) {
                // 原值超出编辑长度也必须完整回填；上限按码点，避免多字节字符被算成多个
                area.setMaxLength(Math.max(textLimit(), loadedText.codePointCount(0, loadedText.length())));
                area.setValue(loadedText);
                // 回填后光标与滚动停在开头：先回填再按窄宽度布局也不会把显示推到末尾
                area.moveCursorToStart();
                return;
            }
            // 原值超长也必须完整回填，浏览或修改其他字段不能截短它；上限按码点抬高（既有超长原文只提示不改写）
            input.setMaxLength(Math.max(textLimit(), loadedText.codePointCount(0, loadedText.length())));
            input.setValue(loadedText);
            // 单行同样从开头展示，避免窄宽度下只看到末尾几个字符
            input.moveCursorToStart();
        }

        // 普通文本和备注分别使用声明的编辑长度，资源 ID 沿用原输入范围。
        private int textLimit() {
            return field.type() == EditorFieldType.LONG_TEXT ? 1024
                    : field.type() == EditorFieldType.TEXT ? 128 : 256;
        }

        @Override
        @Nullable JsonElement store() {
            String value = text();
            if (value.equals(loadedText)) {
                return originalElement();
            }
            String text = field.type() == EditorFieldType.LONG_TEXT ? value : value.trim();
            // 未编辑且文本仍等于装载基线：原样回写原始元素（缺失即 null），applyToDraft 跳过该字段
            if (field.type() != EditorFieldType.LONG_TEXT && !isEdited() && text.equals(loadedText.trim())) {
                return originalElement();
            }
            if (text.isEmpty()) {
                // 必填/不可空字段清空时不删除既有值，交由 issues() 提示（阻止应用）
                boolean keepExisting = loaded != null && (field.required() || !field.nullable());
                return keepExisting ? loaded : null;
            }
            JsonElement parsed = parse.apply(text);
            // 输入非法时保留原值，交由 issues() 提示玩家修正
            return parsed != null ? parsed : loaded;
        }

        @Override
        List<FormIssue> issues(String path) {
            // 长度超限做行内提示（warning）：被拒时缓冲值本身仍在合法范围，
            // 既有原文超限由规则级校验给出阻塞错误
            String raw = text();
            if (rejectedOverflow || raw.codePointCount(0, raw.length()) > textLimit()) {
                String key = tooLongKey();
                if (key != null) {
                    return List.of(FormIssue.warning(path, label(), Component.translatable(key)));
                }
            }
            String text = raw.trim();
            if (text.isEmpty()) {
                if (field.required()) {
                    return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
                }
                return List.of();
            }
            if (parse.apply(text) == null) {
                return List.of(FormIssue.error(path, label(), Component.translatable(invalidKey)));
            }
            return List.of();
        }

        // 超限提示 key：备注与显示名各有专属文案；其它文本字段没有专属文案，不提示
        private @Nullable String tooLongKey() {
            if (field.type() == EditorFieldType.LONG_TEXT) {
                return ISSUE_PREFIX + "notes_too_long";
            }
            return RuleFields.DISPLAY_NAME.equals(field.name()) ? ISSUE_PREFIX + "display_name_too_long" : null;
        }

        // 输入超限被拒：置瞬时标记并给出一条即时提示
        private void notifyTooLong() {
            this.rejectedOverflow = true;
            reportRejected(tooLongKey());
        }

        @Override
        void setValueFromSuggestion(String value) {
            markEdited();
            setText(value);
        }
    }

    // ---- 布尔开关 ----

    static final class BoolControl extends FormControl {

        private final UiCheckBox checkBox;

        BoolControl(Font font, EditorField field, Runnable onChanged) {
            super(field);
            this.checkBox = new UiCheckBox(font, label(), false);
            this.checkBox.setOnChanged(checked -> {
                markEdited();
                onChanged.run();
            });
        }

        @Override
        int height() {
            return DEFAULT_HEIGHT;
        }

        @Override
        void setBounds(int x, int y, int width) {
            checkBox.setBounds(x, y, width, DEFAULT_HEIGHT);
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            checkBox.render(graphics, font, mouseX, mouseY);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return checkBox.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return checkBox.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        void addFocusTargets(List<UiFocusTarget> out) {
            out.add(checkBox);
        }

        @Override
        void setEnabled(boolean enabled) {
            checkBox.setEnabled(enabled);
        }

        @Override
        boolean isVisible() {
            return checkBox.isVisible();
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            boolean checked = value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                    && value.getAsBoolean();
            checkBox.setChecked(checked);
        }

        @Override
        @Nullable JsonElement store() {
            // 用户没点过开关：保持 JSON 原样（省略 enabled 的后端默认 true 不会被写成 false）
            if (!isEdited()) {
                return originalElement();
            }
            return new JsonPrimitive(checkBox.isChecked());
        }
    }

    // ---- 枚举分段选择 ----

    static final class EnumControl extends FormControl {

        private final UiSegmentedControl segments;

        EnumControl(Font font, EditorField field, Runnable onChanged) {
            super(field);
            String group = field.enumGroup() == null || field.enumGroup().isBlank() ? field.name() : field.enumGroup();
            List<UiSegmentedControl.Option> options = new ArrayList<>();
            for (String value : field.enumValues()) {
                options.add(new UiSegmentedControl.Option(value,
                        Component.translatable("gui.itemdespawntowhat.edit.enum." + group + "." + value)));
            }
            this.segments = new UiSegmentedControl(font, options);
            this.segments.setOnChanged(value -> {
                markEdited();
                onChanged.run();
            });
        }

        @Override
        int height() {
            return DEFAULT_HEIGHT;
        }

        @Override
        void setBounds(int x, int y, int width) {
            segments.setBounds(x, y, width, DEFAULT_HEIGHT);
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            segments.render(graphics, font, mouseX, mouseY);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return segments.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return segments.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        void addFocusTargets(List<UiFocusTarget> out) {
            out.add(segments);
        }

        @Override
        void setEnabled(boolean enabled) {
            segments.setEnabled(enabled);
        }

        @Override
        boolean isVisible() {
            return segments.isVisible();
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            segments.setSelected(value != null && value.isJsonPrimitive() ? value.getAsString() : field.enumValues().isEmpty() ? "" : field.enumValues().getFirst());
        }

        @Override
        @Nullable JsonElement store() {
            // 用户没点过分段：保持 JSON 原样（省略 shape/pickup 时不会写入或写成空串）
            if (!isEdited()) {
                return originalElement();
            }
            String selected = segments.selected();
            return selected == null || selected.isEmpty() ? null : new JsonPrimitive(selected);
        }

        @Override
        List<FormIssue> issues(String path) {
            if (field.required() && segments.selected().isBlank()) {
                return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
            }
            return List.of();
        }
    }

    // ---- 字符串 / id 列表 ----

    static final class ListControl extends FormControl {

        private final UiListEditor editor;
        // 装载时的条目基线；用户未改动且条目未变时视为「未编辑」
        private List<String> loadedItems = List.of();

        ListControl(Font font, EditorField field, Runnable onChanged) {
            super(field);
            UiListEditor.Mode mode;
            switch (field.type()) {
                case TAG_LIST:
                    mode = UiListEditor.Mode.TAG;
                    break;
                case STRING_LIST:
                    mode = UiListEditor.Mode.TEXT;
                    break;
                default:
                    mode = UiListEditor.Mode.ID;
                    break;
            }
            this.editor = new UiListEditor(font, mode, field.registry());
            this.editor.setOnChanged(items -> {
                markEdited();
                onChanged.run();
            });
        }

        @Override
        void finishInput() {
            editor.commitPendingInput();
        }

        @Override
        boolean hasPendingInput() {
            return !editor.items().equals(loadedItems);
        }

        @Override
        int height() {
            return UiListEditor.preferredHeight(Math.max(1, editor.items().size()));
        }

        @Override
        void setBounds(int x, int y, int width) {
            editor.setBounds(x, y, width, height());
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            editor.render(graphics, font, mouseX, mouseY);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return editor.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        boolean mouseReleased(double mouseX, double mouseY, int button) {
            return editor.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return editor.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return editor.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return editor.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        boolean charTyped(char codePoint, int modifiers) {
            return editor.charTyped(codePoint, modifiers);
        }

        @Override
        void addFocusTargets(List<UiFocusTarget> out) {
            out.add(editor);
            out.add(editor.input());
        }

        @Override
        void setEnabled(boolean enabled) {
            editor.setEnabled(enabled);
        }

        @Override
        boolean isEditing() {
            return editor.input().isFocused();
        }

        @Override
        boolean isVisible() {
            return editor.isVisible();
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            List<String> items = new ArrayList<>();
            if (value != null && value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element != null && !element.isJsonNull()) {
                        items.add(element.getAsString());
                    }
                }
            }
            editor.setItems(items);
            this.loadedItems = List.copyOf(editor.items());
        }

        @Override
        @Nullable JsonElement store() {
            // 未编辑且条目未变：保持 JSON 原样（缺失键不会变成空数组）
            if (!isEdited() && editor.items().equals(loadedItems)) {
                return originalElement();
            }
            if (editor.size() == 0) {
                return field.nullable() ? null : new JsonArray();
            }
            JsonArray array = new JsonArray();
            for (String item : editor.items()) {
                array.add(item);
            }
            return array;
        }

        @Override
        List<FormIssue> issues(String path) {
            List<FormIssue> list = new ArrayList<>();
            if (field.required() && editor.size() == 0) {
                list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
            }
            List<String> seen = new ArrayList<>();
            for (String item : editor.items()) {
                if (seen.contains(item)) {
                    list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "duplicate")));
                    break;
                }
                seen.add(item);
            }
            return list;
        }

        @Override
        void setValueFromSuggestion(String value) {
            markEdited();
            editor.addItem(value);
        }
    }

    // ---- 气候区间（-1..1，两端可空） ----

    static final class ClimateControl extends FormControl {

        private final UiTextInput minInput;
        private final UiTextInput maxInput;
        // 装载时写入两个端点的文本基线
        private String loadedMin = "";
        private String loadedMax = "";

        ClimateControl(Font font, EditorField field, Runnable onChanged) {
            super(field);
            this.minInput = new UiTextInput(font,
                    Component.translatable("gui.itemdespawntowhat.edit.field.climate.min"));
            this.maxInput = new UiTextInput(font,
                    Component.translatable("gui.itemdespawntowhat.edit.field.climate.max"));
            this.minInput.setFilter(FormControl::decimalChars);
            this.maxInput.setFilter(FormControl::decimalChars);
            this.minInput.setOnCommit(text -> {
                markEdited();
                onChanged.run();
            });
            this.maxInput.setOnCommit(text -> {
                markEdited();
                onChanged.run();
            });
        }

        @Override
        int height() {
            return DEFAULT_HEIGHT;
        }

        @Override
        void setBounds(int x, int y, int width) {
            int half = Math.max(16, (width - 4) / 2);
            minInput.setBounds(x, y, half, DEFAULT_HEIGHT);
            maxInput.setBounds(x + half + 4, y, Math.max(16, width - half - 4), DEFAULT_HEIGHT);
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            minInput.render(graphics, font, mouseX, mouseY);
            maxInput.render(graphics, font, mouseX, mouseY);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return minInput.mouseClicked(mouseX, mouseY, button) || maxInput.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        boolean mouseReleased(double mouseX, double mouseY, int button) {
            return minInput.mouseReleased(mouseX, mouseY, button) || maxInput.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return minInput.keyPressed(keyCode, scanCode, modifiers) || maxInput.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        boolean charTyped(char codePoint, int modifiers) {
            return minInput.charTyped(codePoint, modifiers) || maxInput.charTyped(codePoint, modifiers);
        }

        @Override
        void addFocusTargets(List<UiFocusTarget> out) {
            out.add(minInput);
            out.add(maxInput);
        }

        @Override
        void setEnabled(boolean enabled) {
            minInput.setEditable(enabled);
            maxInput.setEditable(enabled);
        }

        @Override
        boolean hasPendingInput() {
            return !minInput.value().equals(loadedMin) || !maxInput.value().equals(loadedMax);
        }

        @Override
        boolean isEditing() {
            return minInput.isFocused() || maxInput.isFocused();
        }

        @Override
        boolean isVisible() {
            return minInput.isVisible();
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            Double min = null;
            Double max = null;
            if (value != null && value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                if (object.has("min") && object.get("min").isJsonPrimitive() && object.get("min").getAsJsonPrimitive().isNumber()) {
                    min = object.get("min").getAsDouble();
                }
                if (object.has("max") && object.get("max").isJsonPrimitive() && object.get("max").getAsJsonPrimitive().isNumber()) {
                    max = object.get("max").getAsDouble();
                }
            }
            this.loadedMin = min == null ? "" : trimDouble(min);
            this.loadedMax = max == null ? "" : trimDouble(max);
            minInput.setValue(loadedMin);
            maxInput.setValue(loadedMax);
        }

        @Override
        @Nullable JsonElement store() {
            // 未编辑且两端文本未变：保持 JSON 原样（缺失键不会变成空对象）
            if (!isEdited() && minInput.value().trim().equals(loadedMin.trim())
                    && maxInput.value().trim().equals(loadedMax.trim())) {
                return originalElement();
            }
            JsonObject object = new JsonObject();
            Double min = parseDouble(minInput.value());
            Double max = parseDouble(maxInput.value());
            if (min != null) {
                object.addProperty("min", min);
            }
            if (max != null) {
                object.addProperty("max", max);
            }
            return object.isEmpty() ? null : object;
        }

        @Override
        List<FormIssue> issues(String path) {
            Double min = parseDouble(minInput.value());
            Double max = parseDouble(maxInput.value());
            if ((!minInput.value().isBlank() && min == null) || (!maxInput.value().isBlank() && max == null)) {
                return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "invalid_number")));
            }
            if (min != null && max != null && min > max) {
                return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "range_order")));
            }
            return List.of();
        }
    }

    // ---- 条件树（条件编辑器与效果编辑器共用） ----

    static final class ConditionTreeControl extends FormControl {

        // 工具栏自动换行，树高度随内容增长
        private static final int BUTTON_HEIGHT = 14;
        private final List<UiButton> toolbar = new ArrayList<>();
        private final Font font;
        private int toolbarHeight;
        private boolean enabled = true;
        private boolean advancedTools;

        private final UiConditionTreeEditor editor;
        private final @Nullable ConditionSupport support;
        private @Nullable JsonElement fallbackRaw;

        ConditionTreeControl(Font font, EditorField field, @Nullable ConditionSupport support, Runnable onChanged) {
            super(field);
            this.support = support;
            this.font = font;
            this.editor = new UiConditionTreeEditor(font);
            this.editor.setTypeOptions(support == null ? List.of() : support.typeOptions());
            if (support != null) {
                this.editor.setLeafFactory(support.leafFactory());
                this.editor.setOnEditLeaf(leaf -> support.onEditLeaf().accept(leaf));
            }
            this.editor.setListener(expression -> {
                markEdited();
                onChanged.run();
            });
            this.editor.setEmptyMessage(Component.translatable("gui.itemdespawntowhat.edit.tree.unrestricted"));
            if (support != null) {
                addButton("add", editor::beginAddCondition);
                addButton("all", () -> editor.addGroup(true));
                addButton("any", () -> editor.addGroup(false));
                addButton("not", editor::wrapSelectedInNot);
                addButton("edit", editor::editSelectedLeaf);
                addButton("remove", editor::deleteSelected);
                addButton("up", () -> editor.moveSelected(-1));
                addButton("down", () -> editor.moveSelected(1));
                addButton("advanced", () -> advancedTools = !advancedTools);
            }
        }

        // 鼠标按钮和原有快捷键共用条件树操作。
        private void addButton(String name, Runnable action) {
            toolbar.add(new UiButton(font, Component.translatable("gui.itemdespawntowhat.edit.tree.button." + name),
                    UiButtonVariant.SECONDARY, action::run));
        }

        private int treeHeight() {
            return editor.isPickerOpen() ? 140 : Math.clamp((long) (editor.rowCount() + 1) * 12, 28, 96);
        }

        @Override
        int height() {
            return treeHeight() + toolbarHeight;
        }

        @Override
        void measure(int width) {
            toolbarHeight = layoutToolbar(0, 0, width);
        }

        private int layoutToolbar(int x, int y, int width) {
            if (toolbar.isEmpty() || fallbackRaw != null) {
                return 0;
            }
            boolean any = editor.rowCount() > 0;
            for (int i = 0; i < toolbar.size(); i++) {
                boolean show = i == 0 || any && (i == 8 || i == 5 && editor.selectedNode() != null
                        || i == 4 && editor.selectedNode() instanceof com.meteorite.itemdespawntowhat.core.model.ConditionNode.Leaf
                        || advancedTools && i <= 7);
                toolbar.get(i).setVisible(show);
            }
            int cursorX = x;
            int cursorY = y;
            for (UiButton button : toolbar) {
                if (!button.isVisible()) continue;
                int limit = Math.max(1, width);
                int buttonWidth = Math.clamp(button.preferredWidth(4), Math.min(24, limit), limit);
                if (cursorX > x && cursorX + buttonWidth > x + width) {
                    cursorX = x;
                    cursorY += BUTTON_HEIGHT + 2;
                }
                button.setBounds(cursorX, cursorY, buttonWidth, BUTTON_HEIGHT);
                button.setEnabled(enabled);
                cursorX += buttonWidth + 2;
            }
            return cursorY - y + BUTTON_HEIGHT + 2;
        }

        @Override
        void setBounds(int x, int y, int width) {
            toolbarHeight = layoutToolbar(x, y, width);
            editor.setBounds(x, y + toolbarHeight, width, treeHeight());
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            if (fallbackRaw != null) {
                UiTheme.drawInset(graphics, editor.bounds());
                String text = TextScroll.trimToWidth(font,
                        Component.translatable("gui.itemdespawntowhat.edit.json.read_only").getString(),
                        Math.max(8, editor.bounds().width() - 4));
                graphics.drawString(font, text, editor.bounds().x() + 2, editor.bounds().y() + 2,
                        UiPalette.TEXT_SECONDARY, false);
                String json = JsonSummary.compact(fallbackRaw, Math.max(4, editor.bounds().width() - 6));
                graphics.drawString(font, json, editor.bounds().x() + 3, editor.bounds().y() + 14,
                        UiPalette.TEXT_PRIMARY, false);
                return;
            }
            for (UiButton button : toolbar) {
                button.render(graphics, font, mouseX, mouseY);
            }
            editor.render(graphics, font, mouseX, mouseY);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!enabled || fallbackRaw != null) {
                return false;
            }
            for (UiButton target : toolbar) {
                if (target.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return editor.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean consumed = false;
            for (UiButton target : toolbar) {
                consumed |= target.mouseReleased(mouseX, mouseY, button);
            }
            return consumed | (fallbackRaw == null && editor.mouseReleased(mouseX, mouseY, button));
        }

        @Override
        boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return fallbackRaw == null && editor.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return fallbackRaw == null && editor.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return fallbackRaw == null && editor.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        void addFocusTargets(List<UiFocusTarget> out) {
            if (fallbackRaw == null && enabled) {
                out.addAll(toolbar);
                out.add(editor);
            }
        }

        @Override
        void setEnabled(boolean enabled) {
            this.enabled = enabled;
            editor.setEnabled(enabled);
            toolbar.forEach(button -> button.setEnabled(enabled));
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            if (support == null) {
                this.fallbackRaw = value == null || value.isJsonNull() ? null : value.deepCopy();
                editor.setExpression(ConditionExpression.EMPTY);
                return;
            }
            ConditionExpression expression = RuleDraft.decodeConditions(value, support.registry());
            if (expression == null) {
                this.fallbackRaw = value == null || value.isJsonNull() ? null : value.deepCopy();
                editor.setExpression(ConditionExpression.EMPTY);
            } else {
                this.fallbackRaw = null;
                editor.setExpression(expression);
            }
        }

        @Override
        @Nullable JsonElement store() {
            // 用户没改过条件树：原样返回装载元素，避免重编码把原 JSON 规范化成新值
            if (!isEdited()) {
                return originalElement();
            }
            if (fallbackRaw != null) {
                return fallbackRaw;
            }
            if (support == null) {
                return null;
            }
            return RuleDraft.encodeConditions(editor.expression(), support.registry());
        }

        @Override
        List<FormIssue> issues(String path) {
            if (fallbackRaw != null) {
                // 未注册 / 第三方类型或参数读不出来：原数据整体保留，只提醒不阻塞（服务端才是权威校验方）
                return List.of(FormIssue.warning(path, label(),
                        Component.translatable(ISSUE_PREFIX + "unparsable")));
            }
            if (editor.isValid()) {
                return List.of();
            }
            List<FormIssue> list = new ArrayList<>();
            for (UiConditionTreeEditor.Issue issue : editor.issues()) {
                String relative = issue.path();
                String issuePath = relative == null || !relative.startsWith(UiConditionTreeEditor.ROOT_PATH) ? path
                        : path + relative.substring(UiConditionTreeEditor.ROOT_PATH.length());
                String key = ISSUE_PREFIX + issue.kind().name().toLowerCase(Locale.ROOT);
                list.add(FormIssue.error(issuePath, label(), Component.translatable(key)));
            }
            if (list.isEmpty()) {
                list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "incomplete_group")));
            }
            return list;
        }

        @Override
        String undoOpKey() {
            return editor.undoOpKey();
        }

        @Override
        boolean isEditing() {
            return fallbackRaw == null && editor.isFocused();
        }
    }

    // ---- 只读原始 JSON（第三方未注册类型回退，原样保留） ----

    static final class RawJsonControl extends FormControl {

        private @Nullable JsonElement raw;

        RawJsonControl(EditorField field) {
            super(field);
        }

        @Override
        int height() {
            return DEFAULT_HEIGHT;
        }

        @Override
        void setBounds(int x, int y, int width) {
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            this.raw = value == null ? null : value.deepCopy();
        }

        @Override
        @Nullable JsonElement store() {
            // 只读控件：未编辑时原样返回装载元素（显式 JsonNull 也必须保留），避免被 applyToDraft 误删
            if (!isEdited()) {
                return originalElement();
            }
            // 原样返回，保证未识别字段不丢失
            return raw;
        }

        // 该控件把 JSON 摘要画在标签右侧，由 FormView 调用
        String summary(int maxWidth) {
            return raw == null || raw.isJsonNull() ? "" : JsonSummary.compact(raw, Math.max(4, maxWidth));
        }
    }

    // ---- 只读说明行（不写入 JSON） ----

    static final class NoteControl extends FormControl {

        NoteControl(EditorField field) {
            super(field);
        }

        @Override
        int height() {
            return DEFAULT_HEIGHT;
        }

        @Override
        void setBounds(int x, int y, int width) {
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        }

        @Override
        void load(@Nullable JsonElement value) {
        }

        @Override
        @Nullable JsonElement store() {
            return null;
        }

        @Override
        boolean usesLabelColumn() {
            return false;
        }

        // 说明文本就是 labelKey 本身
        Component note() {
            return Component.translatable(field.labelKey());
        }
    }

    // ---- 子列表（对象数组，例如 arrow_rain.potion_effects） ----

    static final class SublistControl extends FormControl {

        private final Font font;
        private final FormView owner;
        private final List<EditorField> subFields;
        private final List<FormView> children = new ArrayList<>();
        private final List<UiButton> removeButtons = new ArrayList<>();
        private final UiButton addButton;
        private int measuredHeight = DEFAULT_HEIGHT;

        SublistControl(Font font, FormView owner, EditorField field, Runnable onChanged) {
            super(field);
            this.font = font;
            this.owner = owner;
            this.subFields = field.subFields();
            this.addButton = new UiButton(font, Component.translatable("gui.itemdespawntowhat.edit.list.add"),
                    UiButtonVariant.SECONDARY, () -> {
                        markEdited();
                        appendChild(null);
                        onChanged.run();
                    });
        }

        // 追加一个子表单
        private void appendChild(@Nullable JsonElement value) {
            FormView child = owner.newChildForm(childPath(children.size()), subFields);
            if (value != null && value.isJsonObject()) {
                child.reloadWith(value.getAsJsonObject());
            }
            // 子表单里任何用户改动都要把整个子列表标记为已编辑
            child.setOnChanged(() -> {
                markEdited();
                owner.notifyChanged();
            });
            children.add(child);
            removeButtons.add(createRemoveButton(child));
        }

        // 每个子项右侧的删除按钮
        private UiButton createRemoveButton(FormView child) {
            return new UiButton(font, Component.translatable("gui.itemdespawntowhat.edit.list.remove"),
                    UiButtonVariant.DANGER, () -> {
                        int index = children.indexOf(child);
                        if (index >= 0) {
                            markEdited();
                            children.remove(index);
                            removeButtons.remove(index);
                            owner.notifyChanged();
                        }
                    });
        }

        // 子项相对路径：字段名[下标]
        private String childPath(int index) {
            return field.name() + "[" + index + "]";
        }

        @Override
        int height() {
            return measuredHeight;
        }

        @Override
        void measure(int width) {
            layoutAt(0, 0, width);
        }

        @Override
        void setBounds(int x, int y, int width) {
            layoutAt(x, y, width);
        }

        // 依次排列「添加按钮 + 每个子表单（含删除按钮）」，同时算出总高度
        private void layoutAt(int x, int y, int width) {
            int cursor = y;
            addButton.setBounds(x, cursor, Math.max(40, addButton.preferredWidth(6)), DEFAULT_HEIGHT);
            cursor += DEFAULT_HEIGHT + 2;
            for (int i = 0; i < children.size(); i++) {
                UiButton remove = removeButtons.get(i);
                int removeWidth = Math.max(28, remove.preferredWidth(4));
                remove.setBounds(x + Math.max(0, width - removeWidth), cursor, removeWidth, DEFAULT_HEIGHT);
                cursor += DEFAULT_HEIGHT + 2;
                int used = children.get(i).layoutUnbounded(x + 4, cursor, Math.max(16, width - 8));
                cursor += used + 2;
            }
            this.measuredHeight = Math.max(DEFAULT_HEIGHT, cursor - y);
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            addButton.render(graphics, font, mouseX, mouseY);
            for (int i = 0; i < children.size(); i++) {
                removeButtons.get(i).render(graphics, font, mouseX, mouseY);
                children.get(i).render(graphics, font, mouseX, mouseY);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (addButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (int i = 0; i < children.size(); i++) {
                if (removeButtons.get(i).mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (children.get(i).mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean consumed = addButton.mouseReleased(mouseX, mouseY, button);
            for (int i = 0; i < children.size(); i++) {
                consumed |= removeButtons.get(i).mouseReleased(mouseX, mouseY, button);
                consumed |= children.get(i).mouseReleased(mouseX, mouseY, button);
            }
            return consumed;
        }

        @Override
        boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            boolean consumed = false;
            for (FormView child : children) {
                consumed |= child.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            return consumed;
        }

        @Override
        boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            boolean consumed = false;
            for (FormView child : children) {
                consumed |= child.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            return consumed;
        }

        @Override
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean consumed = false;
            for (FormView child : children) {
                consumed |= child.keyPressed(keyCode, scanCode, modifiers);
            }
            return consumed;
        }

        @Override
        boolean charTyped(char codePoint, int modifiers) {
            boolean consumed = false;
            for (FormView child : children) {
                consumed |= child.charTyped(codePoint, modifiers);
            }
            return consumed;
        }

        @Override
        void addFocusTargets(List<UiFocusTarget> out) {
            out.add(addButton);
            for (FormView child : children) {
                child.addFocusTargets(out);
            }
        }

        @Override
        void setEnabled(boolean enabled) {
            addButton.setEnabled(enabled);
            for (UiButton remove : removeButtons) {
                remove.setEnabled(enabled);
            }
            for (FormView child : children) {
                child.setEnabled(enabled);
            }
        }

        @Override
        void load(@Nullable JsonElement value) {
            rememberLoaded(value);
            children.clear();
            removeButtons.clear();
            if (value != null && value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    appendChild(element);
                }
            }
            layoutAt(0, 0, 1);
        }

        @Override
        JsonElement store() {
            // 未编辑（含未增删子项、子表单未改动）：原样返回装载数组
            if (!isEdited()) {
                return originalElement();
            }
            JsonArray array = new JsonArray();
            for (FormView child : children) {
                child.applyToDraft();
                JsonObject object = child.storeObject();
                array.add(object);
            }
            return array;
        }

        @Override
        List<FormIssue> issues(String path) {
            List<FormIssue> list = new ArrayList<>();
            for (FormView child : children) {
                list.addAll(child.issues());
            }
            return list;
        }

        @Override
        boolean isEditing() {
            for (FormView child : children) {
                if (child.isEditing()) {
                    return true;
                }
            }
            return false;
        }
    }
}
