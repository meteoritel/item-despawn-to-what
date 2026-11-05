package com.meteorite.itemdespawntowhat.client.ui.screen;

import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;

import java.util.function.Function;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 变更清单的只读条目快照，与管理列表共用配方行和标题布局。
 */
final class RuleChangesView implements UiWidget, UiFocusTarget {
    private final UiHostTheme THEME;
    private static final String UI = TypeLabels.UI_PREFIX;
    private static final int NOTE_GAP = 4;
    private final Font font;
    private final UiListView<Change> list;
    private final int rowHeight;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    private record Change(String title, Component kind, RuleRecipeView recipe) {
    }

    RuleChangesView(UiHostTheme theme, Font font, RuleEditorModel model, Function<String, String> labels) {
        this.THEME = UiHostThemes.require(theme);
        this.font = font;
        rowHeight = font.lineHeight + 5 + RuleRecipeView.CONTENT_HEIGHT;
        list = new UiListView<>(THEME, (graphics, rowFont, change, index, row, selected, hovered, focused) ->
                RuleRecipeView.renderRow(graphics, rowFont, row, change.title(), change.kind(),
                        THEME.palette().surfaces().text(), THEME.palette().surfaces().secondaryText(), change.recipe(), hovered, THEME));
        list.setRowHeight(rowHeight);
        // 删除标志独立于规则体，因此仍展示待删除草稿的名称、输入和产出。
        list.setItems(model.dirtyRuleIds().stream().map(id -> {
            EditSession session = model.session(id);
            // 一条规则可以同时带内容动作与启停动作，清单里并排写出两段，避免只看到一半改动
            boolean contentDirty = model.isCreated(id)
                    || session != null && !model.isReadOnly(id) && session.isDirty();
            Component kind = Component.empty();
            if (contentDirty) {
                String contentKind = model.isCreated(id) ? "create"
                        : session != null && session.isDeleted() ? "remove" : "modify";
                kind = Component.translatable(UI + "changes." + contentKind);
            }
            RuleEdit.Action intent = model.enableIntent(id);
            if (intent != null) {
                Component stateKind = Component.translatable(UI + "changes." + intent.id());
                kind = kind.getString().isEmpty() ? stateKind : kind.copy().append(" + ").append(stateKind);
            }
            return new Change(labels.apply(id), kind, new RuleRecipeView(model.displayBody(id)));
        }).toList());
        list.setEmptyMessage(Component.translatable(UI + "notice.no_changes"));
    }

    int preferredHeight(int maximum) {
        return Math.min(maximum, font.lineHeight * 2 + NOTE_GAP + Math.max(1, list.size()) * rowHeight + 2);
    }

    /**
     * @return 弹窗内容的屏幕坐标范围
     */
    @Override
    public UiRect bounds() {
        return bounds;
    }

    /**
     * 按提示文字的换行高度为列表保留滚动区域。
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int noteHeight = font.split(Component.translatable(UI + "changes.note"), Math.max(1, width)).size()
                * font.lineHeight + NOTE_GAP;
        list.setBounds(x, y + noteHeight, width, Math.max(0, height - noteHeight));
    }

    /**
     * 绘制条目；配方提示由活动作用域根据统一命中结果呈现。
     */
    @Override
    public void render(GuiGraphics graphics, Font rowFont, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        int y = bounds.y();
        for (var line : rowFont.split(Component.translatable(UI + "changes.note"), Math.max(1, bounds.width()))) {
            graphics.drawString(rowFont, line, bounds.x(), y, THEME.palette().surfaces().secondaryText(), false);
            y += rowFont.lineHeight;
        }
        list.render(graphics, rowFont, pointer);
    }

    /**
     * 将鼠标按下交给列表处理选择和滚动条。
     */
    @Override
    public boolean mousePressed(UiInputContext context) {
        return list.mousePressed(context);
    }

    /**
     * 将拖动交给列表滚动条。
     */
    @Override
    public boolean mouseDragged(UiInputContext context, double dx, double dy) {
        return list.mouseDragged(context, dx, dy);
    }

    /**
     * 结束列表滚动条拖动。
     */
    @Override
    public boolean mouseReleased(UiInputContext context) {
        return list.mouseReleased(context);
    }

    /**
     * 将滚轮交给列表。
     */
    @Override
    public boolean mouseScrolled(UiInputContext context, double sx, double sy) {
        return list.mouseScrolled(context, sx, sy);
    }

    /**
     * 将键盘导航交给列表。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return list.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * {@inheritDoc}
     */
    public UiFocusState focusState() {
        return list.focusState();
    }

    @Override
    public boolean canFocus() {
        return list.canFocus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void focusChanged(boolean focused) {
        list.focusChanged(focused);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFocused() {
        return list.isFocused();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean activate() {
        return list.activate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Component accessibleName() {
        return Component.translatable(UI + "button.changes");
    }
    /** 使用当前布局解析实际命中，保留容器归属与叶子身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (!isVisible()) return null;
        UiPointerHit hit = list.hitTest(x, y);
        if (hit == null) return null;
        int index = hit.rowIndex(list);
        if (index >= 0) {
            Change change = list.items().get(index);
            Component tip = Component.literal(change.title()).append("\n").append(change.recipe().tooltip())
                    .append("\n").append(change.kind());
            hit = hit.withTextTooltip(List.of(tip), bounds);
        }
        return hit.within(this);
    }

}
