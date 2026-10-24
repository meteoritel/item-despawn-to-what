package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
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
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 物品数量对象的表单控件：复用数值控件的滑条、精确输入、撤销与错误校验。
 * <p>每个引用占用一条数量滑条；配对时物品名只出现一次，其下依次排门槛与消耗两条滑条，
 * 下行字段不再重复物品名。图标列、名称列与滑条起止横坐标全场统一。
 * <p>名称超过列宽时在名称列内悬停滚动；悬停图标给出完整名称、字段名与引用注册名。
 */
final class ItemCountsControl extends FormControl {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    // 行间距（未配对）：与卡片表单其它行保持一致
    private static final int ROW_GAP = 6;
    // 配对布局：物品名独占一行，其下门槛与消耗两条滑条紧邻，不同物品之间用条目间距拉开
    private static final int HEADER_HEIGHT = 16;
    private static final int HEADER_GAP = 2;
    private static final int PAIR_GAP = 3;
    private static final int ITEM_GAP = 10;
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
    private static final String QUANTITY_ROW_KEY = TypeLabels.UI_PREFIX + "quantity.row";

    private final Font font;
    private final Runnable onChanged;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private RuleValue values = RuleValue.mapping().build();
    private int defaultCount;
    // 配对角色：上行绘制物品名与两条滑条，下行只绘制自己的滑条
    private PairRole pairRole = PairRole.NONE;
    // 下行滑条的含义文案：由上行在第二条滑条前绘制
    private @Nullable Component secondRowLabel;
    // 配对下行沿用上行的名称列宽度（0 = 按本控件自身的名称宽度计算）
    private int labelColumnOverride;
    // 滑条列相对控件左边缘的偏移，由表单行布局注入（0 = 按名称宽度自行计算）
    private int sliderColumnOffset;
    // 含义文案的绘制列：与表单标签列同起点，宽度到滑条列之前
    private int labelTextX;
    private int labelTextWidth;

    /** 物品数量控件的配对角色；配对时物品名只出现一次，其下依次是门槛与消耗两道滑条。 */
    enum PairRole {
        /** 独立字段：每条滑条各带一次物品名。 */
        NONE,
        /** 配对上行：物品名独占一行，其下是上行滑条与下行滑条。 */
        UPPER,
        /** 配对下行：物品名、图标与第二行文案均由上行绘制，本控件只排自己的滑条。 */
        LOWER
    }

    void setPairRole(PairRole role) {
        pairRole = role;
        // 非下行不再沿用上行的列宽；下行在下一次布局时重新接收
        if (role != PairRole.LOWER) labelColumnOverride = 0;
    }

    /** 上行设置第二条滑条的含义文案（例如「消耗数量」）。 */
    void setSecondRowLabel(@Nullable Component label) {
        secondRowLabel = label;
    }

    /** 上行控件配对时，第二条滑条起始位置相对本控件顶部的偏移量。 */
    int pairOffset() { return HEADER_HEIGHT + HEADER_GAP + rowHeight() + PAIR_GAP; }

    /** 配对下行沿用上行的名称列宽度：两条滑条必须从同一横坐标开始。 */
    void setLabelColumnWidth(int width) {
        labelColumnOverride = Math.max(0, width);
    }

    /**
     * 表单行布局注入滑条列起点：滑条与同表单其它行的控件左边缘对齐，名称列与含义文案随之落位。
     *
     * @param offset 滑条列相对控件左边缘的偏移；0 表示按名称宽度自行计算
     */
    void setSliderColumnOffset(int offset) {
        sliderColumnOffset = Math.max(0, offset);
    }

    /** 实际使用的名称列宽度：注入了滑条列偏移时按偏移反推，否则按名称宽度计算。 */
    int resolvedLabelColumnWidth(int width) {
        if (sliderColumnOffset > 0) {
            return Math.clamp(sliderColumnOffset - NAME_GAP, 0, Math.max(0, width - NAME_GAP));
        }
        return labelColumnOverride > 0 ? Math.min(labelColumnOverride, width) : labelColumnWidth(width);
    }

    /** 按可用宽度算出名称列宽度；配对下行由 {@link #setLabelColumnWidth} 直接沿用上行的结果。 */
    int labelColumnWidth(int width) {
        // 名称列取全场最宽名称（封顶 NAME_MAX_WIDTH），保证滑条上下对齐；
        // 配对上行还要容纳两道滑条的含义文案，避免文案被名称列宽度截断
        int nameWidth = 0;
        for (Entry entry : entries.values()) {
            nameWidth = Math.max(nameWidth, Math.clamp(font.width(entry.name), 0, NAME_MAX_WIDTH));
        }
        if (pairRole != PairRole.NONE) {
            nameWidth = Math.max(nameWidth, Math.clamp(font.width(label()), 0, NAME_MAX_WIDTH));
            if (secondRowLabel != null) {
                nameWidth = Math.max(nameWidth, Math.clamp(font.width(secondRowLabel), 0, NAME_MAX_WIDTH));
            }
        }
        return Math.clamp(ICON_COLUMN + ICON_GAP + nameWidth, 0, Math.max(0, width / NAME_COLUMN_SHARE));
    }
    // 最近一次布局使用的行高：命中判定与绘制共用同一份几何
    private int lastRowHeight = ROW_HEIGHT;
    private @Nullable NumberControl pressed;

    ItemCountsControl(Font font, EditorField field, Runnable onChanged) {
        super(field);
        this.font = font;
        this.onChanged = onChanged;
        this.defaultCount = field.intMin(DEFAULT_COUNT);
    }

    // 一个引用对应的一行：数量控件、行首名称与图标、当前布局位置
    private static final class Entry {
        private final String reference;
        private final NumberControl control;
        private final Component name;
        private final @Nullable UiIcon icon;
        // 滑条行与图标/名称行可能分开：配对上行把物品名放在两条滑条上方
        private int rowY;
        private int nameRowY;
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
            return x >= iconX && x < iconX + ICON_COLUMN && y >= nameRowY && y < nameRowY + rowHeight;
        }

        // 名称列命中：悬停滚动超宽名称
        private boolean nameHit(double x, double y, int rowHeight) {
            return nameWidth > 0 && x >= nameX && x < nameX + nameWidth && y >= nameRowY && y < nameRowY + rowHeight;
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

    // 每行复用同规格的数值控件：滑条常用窗口 1..64，滑条停在下界 1；精确输入仍按字段合法域校验
    private NumberControl createControl() {
        // 每行代表一次真实支付，0 不构成「消耗」：滑条窗口下界取 DEFAULT_COUNT
        int windowMin = Math.max(DEFAULT_COUNT, field.intMin(DEFAULT_COUNT));
        EditorField number = EditorField.integerSlider(field.name(), field.labelKey(),
                field.intMin(DEFAULT_COUNT), field.intMax(Integer.MAX_VALUE), windowMin, INITIAL_SLIDER_MAX);
        NumberControl control = Objects.requireNonNull(NumberControl.create(font, number, () -> {
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
            String referencePath = DraftPaths.child(basePath, entry.reference);
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
        if (entries.isEmpty()) return 0;
        return entries.size() * itemHeight() + (entries.size() - 1) * itemGap();
    }

    // 单个物品占用的高度：配对上行含物品名行与两条滑条；下行沿用同一节拍，保证两条滑条逐物品对齐
    private int itemHeight() {
        if (pairRole == PairRole.NONE) return rowHeight();
        return HEADER_HEIGHT + HEADER_GAP + rowHeight() + PAIR_GAP + rowHeight();
    }

    // 物品间距：独立字段沿用卡片行距；配对时上下行共用同一节拍，避免滑条错位
    private int itemGap() {
        return pairRole == PairRole.NONE ? ROW_GAP : ITEM_GAP;
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
        int labelWidth = resolvedLabelColumnWidth(width);
        // 含义文案从控件左边缘起画：与同表单普通字段的标签同列
        this.labelTextX = x;
        this.labelTextWidth = Math.max(0, labelWidth - NAME_GAP);
        int contentX = x + labelWidth + NAME_GAP;
        int contentWidth = Math.max(0, width - labelWidth - NAME_GAP);
        // 精确输入框宽度按各行最大值统一，滑条右端也随之对齐
        int inputWidth = 0;
        for (Entry entry : entries.values()) {
            inputWidth = Math.max(inputWidth, entry.control.preferredInputWidth(contentWidth));
        }
        this.lastRowHeight = rowHeight;
        int itemY = y;
        for (Entry entry : entries.values()) {
            entry.control.setInputWidthOverride(inputWidth);
            // 配对上行：物品名独占顶部一行，滑条从名称行下方开始；
            // 配对下行：滑条对齐上行第二条滑条（由 FormRowLayout 按 pairOffset 摆放），此处从顶部排起
            int sliderY = pairRole == PairRole.UPPER ? itemY + HEADER_HEIGHT + HEADER_GAP : itemY;
            entry.rowY = sliderY;
            // 名称行高度：配对上行用它放物品名；独立字段与下行直接沿用滑条行高
            int nameRowHeight = pairRole == PairRole.UPPER ? HEADER_HEIGHT : rowHeight;
            entry.iconX = x;
            entry.iconY = itemY + Math.max(0, (nameRowHeight - (entry.icon == null ? ICON_COLUMN : entry.icon.height())) / 2);
            entry.nameX = x + ICON_COLUMN + ICON_GAP;
            entry.nameY = itemY + Math.max(0, (nameRowHeight - font.lineHeight) / 2);
            entry.nameWidth = Math.max(0, labelWidth - ICON_COLUMN - ICON_GAP);
            entry.nameRowY = itemY;
            entry.control.setBounds(contentX, sliderY, contentWidth);
            itemY += itemHeight() + itemGap();
        }
    }

    @Override
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        for (Entry entry : entries.values()) {
            // 配对下行只画滑条：物品名、图标与两行文案都由上行统一绘制
            if (pairRole != PairRole.LOWER) {
                if (entry.icon != null) entry.icon.render(graphics, entry.iconX, entry.iconY);
                // 配对上行时名称行固定高度；独立字段沿用滑条行高，保持原有居中
                int nameRowHeight = pairRole == PairRole.UPPER ? HEADER_HEIGHT : lastRowHeight;
                // 名称超宽时只有悬停才平移：起点与终点各停顿一次后往返，移开即回到起点
                boolean hovered = entry.nameHit(mouseX, mouseY, nameRowHeight);
                entry.scrollTicks = hovered ? entry.scrollTicks + 1 : 0;
                TextScroll.draw(graphics, renderFont, entry.name.getString(), entry.nameX, entry.nameY,
                        entry.nameWidth, THEME.palette().textSecondary(), hovered, entry.scrollTicks, false);
            }
            if (pairRole == PairRole.UPPER) {
                // 两条滑条各配一行含义文案：与各自滑条同一垂直中线，避免上下滑条含义混淆
                drawRowLabel(graphics, renderFont, label(), entry.rowY);
                if (secondRowLabel != null && !secondRowLabel.getString().isEmpty()) {
                    drawRowLabel(graphics, renderFont, secondRowLabel, entry.rowY + lastRowHeight + PAIR_GAP);
                }
            }
            entry.control.render(graphics, renderFont, mouseX, mouseY);
        }
    }

    // 配对行的含义文案画在标签列：滑条列整体让给滑条，两条滑条起止一致且与其它字段同行对齐
    private void drawRowLabel(GuiGraphics graphics, Font renderFont, Component text, int rowY) {
        int labelY = rowY + Math.max(0, (lastRowHeight - renderFont.lineHeight) / 2);
        TextScroll.draw(graphics, renderFont, text.getString(), labelTextX, labelY,
                labelTextWidth, THEME.palette().textDisabled(), false, 0, false);
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
            result.addAll(entry.control.issues(DraftPaths.child(path, entry.reference)));
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
        // 配对下行不绘制图标，提示与悬停滚动都留给上行
        if (pairRole == PairRole.LOWER) return null;
        // 提示只在图标列弹出：名称列留给悬停滚动，避免提示框盖住正在滚动的名称
        int nameRowHeight = pairRole == PairRole.UPPER ? HEADER_HEIGHT : lastRowHeight;
        // 图标命中矩形以名称行为准，与绘制保持同一份几何
        for (Entry entry : entries.values()) {
            if (entry.iconHit(x, y, nameRowHeight)) {
                return Component.translatable(QUANTITY_ROW_KEY, entry.name, label()).copy()
                        .append("\n").append(entry.reference);
            }
        }
        return null;
    }
}
