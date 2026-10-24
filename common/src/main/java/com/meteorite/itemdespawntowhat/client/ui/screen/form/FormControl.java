package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FieldNumbers;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumbers;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextArea;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 表单控件基类：一个字段对应一行控件。
 * <p>负责「JSON 值 &lt;-&gt; 控件值」的双向搬运与本地校验；
 * 写入草稿由 FormView 统一经由 EditSession 完成，控件本身不碰草稿。
 */
abstract class FormControl {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件；子类继承本字段取样式
    static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    // 行默认高度（与主题行高一致）
    static final int DEFAULT_HEIGHT = THEME.rowHeight();

    // 卡片表单的单行控件高度：FormView 的卡片密度与条件树工具条都按这个值对齐
    static final int CARD_CONTROL_HEIGHT = 20;

    // 校验问题所用的本地化 key 前缀
    static final String ISSUE_PREFIX = TypeLabels.UI_PREFIX + "issue.";
    // 即时提示（notice）key：字段名 + 问题文案
    static final String NOTICE_ISSUE = TypeLabels.UI_PREFIX + "notice.issue";

    // 定点显示精度与整数直出上界（与迁移前 trimDouble 的 %.4f、1.0E9D 一致）
    static final int DECIMAL_DECIMALS = 4;
    static final double DECIMAL_INTEGER_LIMIT = 1.0E9D;

    // 控件所描述的字段
    final EditorField field;

    // 行高覆盖值：>0 时生效（例如基本页的加高优先级控件），-1 表示用控件自然高度
    private int heightOverride = -1;
    private int minimumHeight;
    // 输入被上限拒绝时的即时提示出口（由 FormView 转给界面）
    private @Nullable Consumer<Component> rejectedNotice;

    // 装载时的原始值（键缺失记为 null）；未编辑时原样回写，applyToDraft 因此完全跳过该字段
    private @Nullable RuleValue loadedValue;
    // 用户是否真正交互过（点击/切换/提交/增删改）；程序化回填不置位
    private boolean edited;
    // 控件高度变化出口：条件树等控件的高度随内容/行数变化，由 FormView 装配行时登记，
    // 转发到 FormView.invalidateMeasure 使行测量与页面布局缓存失效
    private @Nullable Runnable geometryListener;

    FormControl(EditorField field) {
        this.field = field;
    }

    // 由 load() 调用：记录原始值并清除编辑标记
    final void rememberLoaded(@Nullable RuleValue value) {
        // 键缺失（null 引用）与显式 null 必须区分：前者保持 null，后者要原样保留，
        // 否则未编辑时 store() 返回 null，applyToDraft 会把显式 null 字段 removeAt 掉
        this.loadedValue = value == null ? null : value.deepCopy();
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

    // 装载原始值的副本；键缺失返回 null
    final @Nullable RuleValue originalValue() {
        return loadedValue == null ? null : loadedValue.deepCopy();
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

    abstract void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    // 把草稿值装载进控件
    abstract void load(@Nullable RuleValue value);

    // 把控件值写回草稿，返回 null 表示删除该字段
    abstract @Nullable RuleValue store();

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

    /**
     * 登记控件几何高度变化回调。
     *
     * <p>FormView 装配行时统一登记，转发到行测量失效；控件只在自身高度真的变化之后调用
     * {@link #notifyGeometryChanged()}。</p>
     *
     * @param listener 回调；null 表示取消登记
     */
    void setOnGeometryChanged(@Nullable Runnable listener) {
        this.geometryListener = listener;
    }

    /** 通知宿主行：本控件几何高度已变化，行测量与页面布局需要重算。 */
    protected void notifyGeometryChanged() {
        if (geometryListener != null) {
            geometryListener.run();
        }
    }

    // 列表等复合控件先结束尚未提交的条目输入。
    void finishInput() {
    }

    // 字段写入后更新复合控件的文本基线，不重载控件或打断焦点。
    void inputAccepted() {
    }

    // 尚未写入草稿的非法文本不能在切换或重建时静默丢弃。
    boolean hasPendingInput() {
        return false;
    }

    // 焦点目标与文本快照缓冲：同一控件每 tick 都要扫描一次输入文本，复用这两个列表
    // 可把「每行两个 ArrayList」降为零分配；复合控件的覆写实现按同样口径复用自身缓冲。
    private final List<UiFocusTarget> focusTargetBuffer = new ArrayList<>();
    private final List<String> inputTextBuffer = new ArrayList<>();

    /**
     * 文本快照使用控件固定焦点顺序，复合数量、气候输入同样覆盖。
     *
     * <p>返回本控件复用的缓冲：调用方只可立即比较或复制（会话保留与装载回填都会复制），
     * 不得跨调用持有——下一次扫描会清空重填。</p>
     *
     * @return 本控件当前输入文本（复用缓冲）
     */
    List<String> inputText() {
        inputTextBuffer.clear();
        appendInputText(inputTextBuffer);
        return inputTextBuffer;
    }

    /**
     * 按控件固定顺序把当前输入文本追加到 {@code out}。
     *
     * <p>复合控件覆写本方法把子控件内容并入同一缓冲，避免逐行新建列表；实现不得假设
     * {@code out} 为空，也不得持有它。</p>
     *
     * @param out 接收容器
     */
    void appendInputText(List<String> out) {
        focusTargetBuffer.clear();
        addFocusTargets(focusTargetBuffer);
        for (UiFocusTarget target : focusTargetBuffer) {
            if (target instanceof UiTextInput input) out.add(input.value());
            else if (target instanceof UiTextArea area) out.add(area.value());
        }
    }

    void restoreInputText(List<String> text) {
        focusTargetBuffer.clear();
        addFocusTargets(focusTargetBuffer);
        int index = 0;
        for (UiFocusTarget target : focusTargetBuffer) {
            if (index >= text.size()) break;
            if (target instanceof UiTextInput input) input.setValue(text.get(index++));
            else if (target instanceof UiTextArea area) area.setValue(text.get(index++));
        }
        markEdited();
    }

    void retainChildInputs() { }

    // 长文本悬停时显示完整内容。
    @Nullable Component fullText() {
        return null;
    }

    // 复合控件可按当前命中条目提供提示，注册名只在悬停时显示。
    @Nullable Component tooltipAt(double x, double y) { return null; }

    // 本地校验问题清单
    List<FormIssue> issues(String path) {
        return List.of();
    }

    // 复合控件可在字段行内部定位问题，并返回更准确的焦点目标。
    @Nullable UiFocusTarget revealPath(String path, String basePath) { return null; }

    // 需要先知道宽度才能算出高度的控件（子列表）覆写
    void measure(int width) {
    }

    // 普通控件使用整行宽度，复选框等可选择内容宽度。
    int preferredWidth(int available) { return available; }

    // 覆盖本控件占用的行高（<=0 表示恢复自然高度）
    final void setHeightOverride(int height) {
        this.heightOverride = height;
    }

    // 布局实际使用的行高：有覆盖用覆盖值，否则等于控件自然高度
    final int layoutHeight() {
        return Math.max(minimumHeight, heightOverride > 0 ? heightOverride : height());
    }

    // 卡片表单加高单行控件，复合控件继续按自然内容测量。
    final void setMinimumHeight(int height) {
        minimumHeight = Math.max(0, height);
    }

    // 复合控件不能用自然高度判断密度：条件树在紧凑表单里也可能很高。
    final boolean usesCardSpacing() { return minimumHeight >= CARD_CONTROL_HEIGHT; }

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
        return FormControlRegistry.create(font, owner, field, onChanged);
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

    static @Nullable RuleValue referenceValue(String text, boolean allowTag) {
        String id = allowTag && text.startsWith("#") ? text.substring(1) : text;
        return ResourceLocation.tryParse(id) == null ? null : RuleValue.text(text);
    }

    static RuleValue literalValue(String text) {
        return RuleValue.text(text);
    }

    static String textOf(RuleValue value) {
        return value.asText();
    }

    static @Nullable RuleValue boundedInt(String text, int min, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            return value < min || value > max ? null : RuleValue.integer(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static String intOf(RuleValue value) {
        return Integer.toString(intValue(value));
    }

    static @Nullable RuleValue boundedDouble(String text, double min, double max) {
        Double value = UiNumbers.parseFinite(text);
        if (value == null || value < min || value > max) {
            return null;
        }
        return RuleValue.decimal(value);
    }

    // 是否数值型字段（滑杆 + 行内精确输入）
    static boolean isNumericType(EditorFieldType type) {
        return type == EditorFieldType.INTEGER || type == EditorFieldType.TICKS
                || type == EditorFieldType.DECIMAL || type == EditorFieldType.PERCENT
                || type == EditorFieldType.AMPLIFIER;
    }

    // 界面百分比文本写回草稿小数（0..1）：十进制解析，保留原始精度
    static @Nullable RuleValue percentValue(String text) {
        Double value = FieldNumbers.parsePercent(text);
        if (value == null || value < 0.0D || value > 1.0D) {
            return null;
        }
        return RuleValue.decimal(value);
    }

    static String percentText(RuleValue value) {
        return FieldNumbers.percentText(doubleValue(value));
    }

    // 界面等级（1 起，1..256）写回后端 amplifier（0 起，0..255）
    static @Nullable RuleValue amplifierValue(String text) {
        Integer amplifier = FieldNumbers.amplifierOf(text);
        return amplifier == null ? null : RuleValue.integer(amplifier);
    }

    static String amplifierText(RuleValue value) {
        return Integer.toString(FieldNumbers.levelOf(intValue(value)));
    }

    static String decimalOf(RuleValue value) {
        return trimDouble(doubleValue(value));
    }

    // 标量取整数：数值形态原样，文本形态按字面量解析（与迁移前 JsonElement#getAsInt 的宽松解析一致）
    private static int intValue(RuleValue value) {
        if (value != null && value.isText()) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
        return value == null ? 0 : value.asInteger(0);
    }

    // 标量取小数：数值形态原样，文本形态按字面量解析（与迁移前 JsonElement#getAsDouble 的宽松解析一致）
    private static double doubleValue(RuleValue value) {
        if (value != null && value.isText()) {
            Double parsed = UiNumbers.parseFinite(value.asText());
            return parsed == null ? 0.0D : parsed;
        }
        return value == null ? 0.0D : value.asDecimal(0.0D);
    }

    // 去掉多余的小数尾零：整数（|v| <= 1e9）直出，否则定点 4 位再去尾零
    static String trimDouble(double value) {
        return UiNumbers.trimTrailingZeros(UiNumbers.plainExact(value, DECIMAL_DECIMALS, DECIMAL_INTEGER_LIMIT));
    }
}
