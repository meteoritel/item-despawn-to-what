package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeViewModel;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeJsonCodec;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.JsonSummary;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeInspection;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 条件树（条件编辑器与效果编辑器共用） ----

final class ConditionTreeControl extends FormControl {

    // 条件树包含自己的标题和工具栏，卡片内铺满宽度。
    @Override
    boolean usesLabelColumn() {
        return false;
    }

    // 工具栏自动换行，树高度随内容增长；工具条按钮比表单区按钮矮一档
    private static final int TOOLBAR_BUTTON_H = 14;
    private final List<UiButton> toolbar = new ArrayList<>();
    private final Font font;
    private int toolbarHeight;
    private boolean enabled = true;
    // 工具栏流排布缓存：按钮集合、可用宽度、行高与语言决定换行结果，只有这些输入变化才重排；
    // 静止帧只按缓存矩形对齐按钮坐标，不再新建可见列表、宽度数组与换行结果。
    private final List<UiButton> visibleToolbar = new ArrayList<>();
    private int[] toolbarWidths = new int[0];
    private @Nullable UiFlowLayout.Wrap toolbarFlow;
    private int toolbarLayoutLimit = -1;
    private int toolbarLayoutButtonHeight = -1;
    private int toolbarVisibilityMask = -1;
    private @Nullable Object toolbarLayoutLanguage;

    private final ConditionTreeViewModel viewModel = new ConditionTreeViewModel();
    private final UiConditionTreeSummary editor;
    private final @Nullable ConditionEditingSupport support;
    private final @Nullable Supplier<ConditionEditContext> contextFactory;
    private @Nullable RuleValue fallbackRaw;

    /**
     * 创建条件树摘要控件。
     *
     * @param font           渲染字体
     * @param field          字段描述符
     * @param support        共享的条件定义能力
     * @param contextFactory 按当前绑定解析本字段局部编辑上下文的供应器；null 时摘要只读。
     *                       供应而非固定实例，因为嵌套表单重排后字段的绝对路径会变化
     */
    ConditionTreeControl(UiHostTheme theme, Font font, EditorField field, @Nullable ConditionEditingSupport support,
                         @Nullable Supplier<ConditionEditContext> contextFactory) {
        super(theme, field);
        this.support = support;
        this.contextFactory = contextFactory;
        this.font = font;
        this.editor = new UiConditionTreeSummary(THEME, font);
        this.editor.setViewModel(viewModel);
        this.editor.setTypeOptions(support == null ? List.of() : support.typeOptions());
        this.editor.setEmptyMessage(Component.translatable(TypeLabels.UI_PREFIX + "tree.add_placeholder"));
        // 摘要按「条件名（参数摘要）」展示，与规则列表悬停的条件树文案保持一致
        if (support != null) {
            ConditionTreeJsonCodec codec = new ConditionTreeJsonCodec(support.registry());
            this.editor.setLeafDetail(leaf -> {
                RuleValue raw = codec.encode(leaf);
                RuleValue condition = raw != null && raw.isMapping() ? raw.child(RuleFields.CONDITION) : null;
                return condition == null ? null : NaturalSummary.conditionParams(condition);
            });
        }
        // 摘要只读：点击节点进入画布（条件树唯一编辑入口），结构与参数都在画布内完成
        this.editor.setOnOpenCanvas(path -> {
            editor.selectPath(path);
            ConditionEditContext context = context();
            if (context != null) context.open(path, viewModel);
        });
        if (contextFactory != null) addButton(() -> {
            ConditionEditContext context = context();
            if (context != null) context.open(null, viewModel);
        });
    }

    // 条件卡片提供大屏编辑入口。
    private void addButton(Runnable action) {
        toolbar.add(new UiButton(THEME, font, Component.translatable(TypeLabels.UI_PREFIX + "tree.button.open"),
                UiButtonVariant.SECONDARY, action::run).setChipStyle(true));
    }

    private int treeHeight() {
        return Math.clamp((long) (editor.rowCount() + 1) * 12, 28, 96);
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
        int buttonHeight = usesCardSpacing() ? CARD_CONTROL_HEIGHT : TOOLBAR_BUTTON_H;
        int limit = Math.max(1, width);
        int mask = 0;
        for (int index = 0; index < toolbar.size(); index++) {
            if (toolbar.get(index).isVisible()) {
                mask |= 1 << index;
            }
        }
        Object language = Language.getInstance();
        if (toolbarFlow != null && toolbarLayoutLimit == limit && toolbarLayoutButtonHeight == buttonHeight
                && toolbarVisibilityMask == mask && toolbarLayoutLanguage == language) {
            placeToolbar(toolbarFlow, x, y);
            return toolbarHeight;
        }
        toolbarLayoutLimit = limit;
        toolbarLayoutButtonHeight = buttonHeight;
        toolbarVisibilityMask = mask;
        toolbarLayoutLanguage = language;
        visibleToolbar.clear();
        for (UiButton button : toolbar) {
            if (button.isVisible()) {
                visibleToolbar.add(button);
            }
        }
        if (visibleToolbar.isEmpty()) {
            toolbarFlow = null;
            toolbarHeight = buttonHeight + 2;
            return toolbarHeight;
        }
        int size = visibleToolbar.size();
        if (toolbarWidths.length != size) {
            toolbarWidths = new int[size];
        }
        for (int index = 0; index < size; index++) {
            toolbarWidths[index] = Math.clamp(visibleToolbar.get(index).preferredWidth(4), Math.min(24, limit), limit);
        }
        toolbarFlow = UiFlowLayout.wrap(toolbarWidths, limit, 2, buttonHeight);
        // 返回值含一行尾随间距，kit 的 height() 不含，这里补回
        toolbarHeight = toolbarFlow.height() + 2;
        placeToolbar(toolbarFlow, x, y);
        return toolbarHeight;
    }

    // 按缓存的排布结果摆放按钮；按钮自身对相同矩形早退，因此静止帧不产生可见副作用
    private void placeToolbar(UiFlowLayout.Wrap flow, int x, int y) {
        for (int index = 0; index < visibleToolbar.size(); index++) {
            UiRect offset = flow.bounds(index);
            UiButton button = visibleToolbar.get(index);
            button.setBounds(x + offset.x(), y + offset.y(), offset.width(), offset.height());
            button.setEnabled(enabled);
        }
    }

    @Override
    void setBounds(int x, int y, int width) {
        toolbarHeight = layoutToolbar(x, y, width);
        editor.setBounds(x, y + toolbarHeight, width, treeHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (fallbackRaw != null) {
            THEME.drawInset(graphics, editor.bounds());
            String text = TextScroll.trimToWidth(font,
                    Component.translatable(TypeLabels.UI_PREFIX + "json.read_only").getString(),
                    Math.max(8, editor.bounds().width() - 4));
            graphics.drawString(font, text, editor.bounds().x() + 2, editor.bounds().y() + 2,
                    THEME.palette().textSecondary(), false);
            String json = JsonSummary.compact(fallbackRaw, Math.max(4, editor.bounds().width() - 6));
            graphics.drawString(font, json, editor.bounds().x() + 3, editor.bounds().y() + 14,
                    THEME.palette().textPrimary(), false);
            return;
        }
        for (UiButton button : toolbar) {
            button.render(graphics, font, mouseX, mouseY);
        }
        editor.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!enabled || fallbackRaw != null) {
            return false;
        }
        int heightBefore = height();
        boolean handled = false;
        for (UiButton target : toolbar) {
            if (target.mousePressed(context)) {
                handled = true;
                break;
            }
        }
        if (!handled) {
            // 摘要自身处理命中：点击节点进入画布，展开标记只做折叠展示
            handled = editor.mousePressed(context);
        }
        // 展开/折叠会改变摘要行数，进而改变本控件高度：真的变了才让行测量失效
        if (height() != heightBefore) {
            notifyGeometryChanged();
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        boolean consumed = false;
        for (UiButton target : toolbar) {
            consumed |= target.mouseReleased(context);
        }
        return consumed | (fallbackRaw == null && editor.mouseReleased(context));
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        return fallbackRaw == null && editor.mouseDragged(context, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        return fallbackRaw == null && editor.mouseScrolled(context, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        if (support == null) {
            this.fallbackRaw = value == null || value.isNull() ? null : value.deepCopy();
            editor.setExpression(ConditionExpression.EMPTY);
            return;
        }
        ConditionExpression expression = RuleDraft.decodeConditions(value, support.registry());
        if (expression == null) {
            this.fallbackRaw = value == null || value.isNull() ? null : value.deepCopy();
            editor.setExpression(ConditionExpression.EMPTY);
        } else {
            this.fallbackRaw = null;
            editor.setExpression(expression);
        }
        // 表达式行数决定控件高度：装载后通知宿主行重测
        notifyGeometryChanged();
    }

    @Override
    @Nullable RuleValue store() {
        // 用户没改过条件树：原样返回装载值，避免重编码把原 JSON 规范化成新值
        if (!isEdited()) {
            return originalValue();
        }
        if (fallbackRaw != null) {
            return fallbackRaw;
        }
        if (support == null) {
            return null;
        }
        return RuleDraft.encodeConditionsValue(editor.expression(), support.registry());
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
        for (ConditionTreeInspection.Issue issue : editor.issues()) {
            String relative = issue.path();
            String issuePath = relative == null || !relative.startsWith(ConditionTreeContract.ROOT_PATH) ? path
                    : path + relative.substring(ConditionTreeContract.ROOT_PATH.length());
            list.add(FormIssue.error(issuePath, label(), issue.label()));
        }
        if (list.isEmpty()) {
            list.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "incomplete_group")));
        }
        return list;
    }

    @Override
    String undoOpKey() {
        // 摘要不产生结构改动；条件树字段的撤销操作名沿用「编辑条件」
        return EditSession.OP_EDIT_CONDITIONS;
    }

    @Override
    @Nullable UiFocusTarget revealPath(String path, String basePath) {
        // 归属按字段段边界判定：等于本字段或本字段下的子项；仅字符串前缀相同（conditionsExtra）不算
        if (fallbackRaw != null || !(path.equals(basePath) || path.startsWith(basePath + "."))) return null;
        String treePath = ConditionTreeContract.ROOT_PATH + path.substring(basePath.length());
        ConditionEditContext context = context();
        if (context == null) {
            editor.selectPath(treePath);
            return editor;
        }
        // 先尝试打开画布；坐标无效或会话不可用时返回 null，由表单结束定位且不退回通用焦点
        if (!context.open(treePath, viewModel)) return null;
        editor.selectPath(treePath);
        return toolbar.isEmpty() ? null : toolbar.getFirst();
    }

    // 每次使用时解析当前绑定，避免嵌套表单重排后仍指向旧绝对路径
    private @Nullable ConditionEditContext context() {
        return contextFactory == null ? null : contextFactory.get();
    }

    @Override
    @Nullable Component tooltipAt(double x, double y) {
        return fallbackRaw == null ? editor.tooltipAt(x, y) : null;
    }

    @Override
    boolean isEditing() {
        return fallbackRaw == null && editor.isFocused();
    }
}
