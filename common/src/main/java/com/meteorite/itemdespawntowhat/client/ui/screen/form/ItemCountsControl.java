package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 物品数量对象的表单控件：复用数值控件的滑条、精确输入、撤销与错误校验。
 * <p>每个引用只占一行：行首是物品图标与本地化名称，随后是该物品的数量滑条与精确输入；
 * 图标列与名称列宽度全场统一，所有滑条从同一横坐标开始、到同一横坐标结束。
 * <p>名称超过列宽时在名称列内悬停滚动；悬停图标给出完整名称、字段名与引用注册名。
 */
final class ItemCountsControl extends FormControl {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    // 行间距
    private static final int ROW_GAP = 6;
    // 滑条行高（与卡片表单的单行高度一致）
    private static final int ROW_HEIGHT = 20;
    // 行首图标列宽度与图标到名称的间距
    private static final int ICON_COLUMN = 16;
    private static final int ICON_GAP = 6;
    // 名称列与滑条之间的间距
    private static final int NAME_GAP = 6;
    // 名称最长像素宽度：超出的名称截断为省略号
    private static final int NAME_MAX_WIDTH = 100;
    // 名称列占行宽的比例分母：窄窗口下优先保证滑条宽度
    private static final int NAME_COLUMN_SHARE = 3;
    private static final int DEFAULT_COUNT = 1;
    private static final int INITIAL_SLIDER_MAX = 64;
    // 悬停提示的「名称 · 字段名」格式 key
    private static final String QUANTITY_ROW_KEY = "gui.itemdespawntowhat.edit.quantity.row";

    private final Font font;
    private final Runnable onChanged;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private RuleValue values = RuleValue.mapping().build();
    private int defaultCount = DEFAULT_COUNT;
    // 最近一次布局使用的行高：命中判定与绘制共用同一份几何
    private int lastRowHeight = ROW_HEIGHT;
    private @Nullable NumberControl pressed;

    ItemCountsControl(Font font, EditorField field, Runnable onChanged) {
        super(field);
        this.font = font;
        this.onChanged = onChanged;
    }

    // 一个引用对应的一行：数量控件、行首名称与图标、当前布局位置
    private static final class Entry {
        private final String reference;
        private final NumberControl control;
        private final Component name;
        private final @Nullable UiIcon icon;
        private int rowY;
        private int iconX;
        private int iconY;
        private int nameX;
        private int nameY;
        private int nameWidth;
        // 名称滚动计时：只在悬停名称列时推进，移开后归零回到起点
        private int scrollTicks;

        private Entry(String reference, NumberControl control, Component name, @Nullable UiIcon icon) {
            this.reference = reference;
            this.control = control;
            this.name = name;
            this.icon = icon;
        }

        // 图标列命中：悬停读取完整名称与引用注册名
        private boolean iconHit(double x, double y, int rowHeight) {
            return x >= iconX && x < iconX + ICON_COLUMN && y >= rowY && y < rowY + rowHeight;
        }

        // 名称列命中：悬停滚动超宽名称
        private boolean nameHit(double x, double y, int rowHeight) {
            return nameWidth > 0 && x >= nameX && x < nameX + nameWidth && y >= rowY && y < rowY + rowHeight;
        }
    }

    void setReferences(List<String> references) {
        List<String> unique = references.stream().distinct().toList();
        if (new ArrayList<>(entries.keySet()).equals(unique)) return;
        Map<String, Entry> next = new LinkedHashMap<>();
        for (String reference : unique) {
            Entry entry = entries.get(reference);
            if (entry == null) {
                entry = new Entry(reference, createControl(), displayName(reference),
                        CatalogPreviewIcons.iconFor(RuleCatalogType.ITEM, reference));
                entry.control.load(values.childOr(reference, RuleValue.integer(defaultCount)));
            }
            next.put(reference, entry);
        }
        entries.clear();
        entries.putAll(next);
    }

    // 每行复用同规格的数值控件：初始数量 1，滑条常用窗口 1..64
    private NumberControl createControl() {
        EditorField number = EditorField.integerSlider(field.name(), field.labelKey(),
                field.intMin(DEFAULT_COUNT), field.intMax(Integer.MAX_VALUE), DEFAULT_COUNT, INITIAL_SLIDER_MAX);
        NumberControl control = java.util.Objects.requireNonNull(NumberControl.create(font, number, () -> {
            markEdited();
            onChanged.run();
        }));
        control.setMinimumHeight(ROW_HEIGHT);
        return control;
    }

    // 名称取本地化物品名；标签与未知引用保留原始注册名，悬停提示可读完整内容
    private static Component displayName(String reference) {
        if (reference.startsWith("#")) {
            return RuleDisplayLabels.tagLabel(reference);
        }
        ResourceLocation id = ResourceLocation.tryParse(reference);
        Component label = id == null ? null : RuleDisplayLabels.label(id);
        return label == null ? Component.literal(reference) : label;
    }

    void setDefaultCount(int count) { defaultCount = count; }

    @Override
    List<String> inputText() {
        List<String> text = new ArrayList<>();
        for (Entry entry : entries.values()) {
            text.add(entry.reference);
            text.addAll(entry.control.inputText());
        }
        return text;
    }

    @Override
    @Nullable UiFocusTarget revealPath(String path, String basePath) {
        for (Entry entry : entries.values()) {
            String referencePath = basePath + "." + entry.reference;
            if (path.equals(referencePath)) return entry.control.revealPath(referencePath, referencePath);
        }
        return null;
    }

    @Override
    void restoreInputText(List<String> text) {
        for (int index = 0; index + 1 < text.size(); index += 2) {
            Entry entry = entries.get(text.get(index));
            if (entry != null) entry.control.restoreInputText(List.of(text.get(index + 1)));
        }
        markEdited();
    }

    @Override boolean usesLabelColumn() { return false; }

    @Override
    int height() {
        return entries.isEmpty() ? 0 : entries.size() * (rowHeight() + ROW_GAP) - ROW_GAP;
    }

    // 行高与内层数值控件一致：卡片表单为 20，其它密度随控件自身最小高度
    private int rowHeight() {
        int height = ROW_HEIGHT;
        for (Entry entry : entries.values()) height = Math.max(height, entry.control.layoutHeight());
        return height;
    }

    @Override
    void setBounds(int x, int y, int width) {
        int rowHeight = rowHeight();
        // 名称列取全场最宽名称（封顶 NAME_MAX_WIDTH），保证滑条上下对齐
        int nameWidth = 0;
        for (Entry entry : entries.values()) {
            nameWidth = Math.max(nameWidth, Math.clamp(font.width(entry.name), 0, NAME_MAX_WIDTH));
        }
        int labelWidth = Math.clamp(ICON_COLUMN + ICON_GAP + nameWidth, 0, Math.max(0, width / NAME_COLUMN_SHARE));
        int contentX = x + labelWidth + NAME_GAP;
        int contentWidth = Math.max(0, width - labelWidth - NAME_GAP);
        // 精确输入框宽度按各行最大值统一，滑条右端也随之对齐
        int inputWidth = 0;
        for (Entry entry : entries.values()) {
            inputWidth = Math.max(inputWidth, entry.control.preferredInputWidth(contentWidth));
        }
        this.lastRowHeight = rowHeight;
        int rowY = y;
        for (Entry entry : entries.values()) {
            entry.control.setInputWidthOverride(inputWidth);
            entry.rowY = rowY;
            entry.iconX = x;
            entry.iconY = rowY + Math.max(0, (rowHeight - (entry.icon == null ? ICON_COLUMN : entry.icon.height())) / 2);
            entry.nameX = x + ICON_COLUMN + ICON_GAP;
            entry.nameY = rowY + Math.max(0, (rowHeight - font.lineHeight) / 2);
            entry.nameWidth = Math.max(0, labelWidth - ICON_COLUMN - ICON_GAP);
            entry.control.setBounds(contentX, rowY, contentWidth);
            rowY += rowHeight + ROW_GAP;
        }
    }

    @Override
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        for (Entry entry : entries.values()) {
            if (entry.icon != null) entry.icon.render(graphics, entry.iconX, entry.iconY);
            // 名称超宽时只有悬停才平移：起点与终点各停顿一次后往返，移开即回到起点
            boolean hovered = entry.nameHit(mouseX, mouseY, lastRowHeight);
            entry.scrollTicks = hovered ? entry.scrollTicks + 1 : 0;
            TextScroll.draw(graphics, renderFont, entry.name.getString(), entry.nameX, entry.nameY,
                    entry.nameWidth, THEME.palette().textSecondary(), hovered, entry.scrollTicks, false);
            entry.control.render(graphics, renderFont, mouseX, mouseY);
        }
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        values = value != null && value.isMapping() ? value.deepCopy() : RuleValue.mapping().build();
        for (Entry entry : entries.values()) {
            entry.control.load(values.childOr(entry.reference, RuleValue.integer(defaultCount)));
        }
    }

    @Override
    @Nullable RuleValue store() {
        if (!isEdited() && !hasPendingInput()) return originalValue();
        RuleValue.Mapping result = RuleValue.mapping();
        for (Entry entry : entries.values()) {
            result.putIfPresent(entry.reference, entry.control.store());
        }
        return result.build();
    }

    @Override
    List<FormIssue> issues(String path) {
        List<FormIssue> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            result.addAll(entry.control.issues(path + "." + entry.reference));
        }
        return result;
    }

    @Override
    boolean mouseClicked(double x, double y, int button) {
        for (Entry entry : entries.values()) {
            if (entry.control.mouseClicked(x, y, button)) {
                pressed = entry.control;
                return true;
            }
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
        for (Entry entry : entries.values()) if (entry.control.keyPressed(key, scan, modifiers)) return true;
        return false;
    }
    @Override boolean keyReleased(int key, int scan, int modifiers) {
        for (Entry entry : entries.values()) if (entry.control.keyReleased(key, scan, modifiers)) return true;
        return false;
    }
    @Override boolean charTyped(char point, int modifiers) {
        for (Entry entry : entries.values()) if (entry.control.charTyped(point, modifiers)) return true;
        return false;
    }
    @Override void addFocusTargets(List<UiFocusTarget> out) {
        entries.values().forEach(entry -> entry.control.addFocusTargets(out));
    }
    @Override void setEnabled(boolean enabled) {
        entries.values().forEach(entry -> entry.control.setEnabled(enabled));
    }
    @Override boolean hasPendingInput() {
        return entries.values().stream().anyMatch(entry -> entry.control.hasPendingInput());
    }
    @Override boolean isEditing() {
        return entries.values().stream().anyMatch(entry -> entry.control.isEditing());
    }
    @Override void finishInput() {
        entries.values().forEach(entry -> entry.control.finishInput());
    }
    @Override void endInteractions(UiInputCapture.EndReason reason) {
        entries.values().forEach(entry -> entry.control.endInteractions(reason));
    }
    @Override @Nullable Component tooltipAt(double x, double y) {
        // 提示只在图标列弹出：名称列留给悬停滚动，避免提示框盖住正在滚动的名称
        for (Entry entry : entries.values()) {
            if (entry.iconHit(x, y, lastRowHeight)) {
                return Component.translatable(QUANTITY_ROW_KEY, entry.name, label()).copy()
                        .append("\n").append(entry.reference);
            }
        }
        return null;
    }
}
