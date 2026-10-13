package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.JsonSummary;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeInspection;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 条件树（条件编辑器与效果编辑器共用） ----

final class ConditionTreeControl extends FormControl {

    // 条件树包含自己的标题和工具栏，卡片内铺满宽度。
    @Override
    boolean usesLabelColumn() { return false; }

    // 工具栏自动换行，树高度随内容增长
    private static final int BUTTON_HEIGHT = 14;
    private final List<UiButton> toolbar = new ArrayList<>();
    private final Font font;
    private int toolbarHeight;
    private boolean enabled = true;

    private final UiConditionTreeSummary editor;
    private final @Nullable ConditionSupport support;
    private @Nullable RuleValue fallbackRaw;

    ConditionTreeControl(Font font, EditorField field, @Nullable ConditionSupport support) {
        super(field);
        this.support = support;
        this.font = font;
        this.editor = new UiConditionTreeSummary(THEME, font);
        this.editor.setTypeOptions(support == null ? List.of() : support.typeOptions());
        this.editor.setEmptyMessage(Component.translatable("gui.itemdespawntowhat.edit.tree.add_placeholder"));
        // 摘要只读：点击节点进入画布（条件树唯一编辑入口），结构与参数都在画布内完成
        this.editor.setOnOpenCanvas(path -> {
            editor.selectPath(path);
            if (support != null) support.openTree(editor, path);
        });
        if (support != null) addButton("open", () -> support.openTree(editor, null));
    }

    // 条件卡片提供大屏编辑入口。
    private void addButton(String name, Runnable action) {
        toolbar.add(new UiButton(THEME, font, Component.translatable("gui.itemdespawntowhat.edit.tree.button." + name),
                UiButtonVariant.SECONDARY, action::run));
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
        int buttonHeight = usesCardSpacing() ? 20 : BUTTON_HEIGHT;
        int limit = Math.max(1, width);
        List<UiButton> visible = new ArrayList<>();
        for (UiButton button : toolbar) {
            if (button.isVisible()) visible.add(button);
        }
        if (visible.isEmpty()) {
            return buttonHeight + 2;
        }
        int[] widths = new int[visible.size()];
        for (int index = 0; index < visible.size(); index++) {
            widths[index] = Math.clamp(visible.get(index).preferredWidth(4), Math.min(24, limit), limit);
        }
        UiFlowLayout.Wrap flow = UiFlowLayout.wrap(widths, limit, 2, buttonHeight);
        for (int index = 0; index < visible.size(); index++) {
            UiRect offset = flow.bounds(index);
            visible.get(index).setBounds(x + offset.x(), y + offset.y(), offset.width(), offset.height());
            visible.get(index).setEnabled(enabled);
        }
        // 返回值含一行尾随间距，kit 的 height() 不含，这里补回
        return flow.height() + 2;
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
                    Component.translatable("gui.itemdespawntowhat.edit.json.read_only").getString(),
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
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || fallbackRaw != null) {
            return false;
        }
        for (UiButton target : toolbar) {
            if (target.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        // 摘要自身处理命中：点击节点进入画布，展开标记只做折叠展示
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
        if (fallbackRaw != null || !path.startsWith(basePath)) return null;
        editor.selectPath(ConditionTreeContract.ROOT_PATH + path.substring(basePath.length()));
        if (support != null && support.openTree(editor, path)) return toolbar.isEmpty() ? null : toolbar.getFirst();
        return editor;
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
