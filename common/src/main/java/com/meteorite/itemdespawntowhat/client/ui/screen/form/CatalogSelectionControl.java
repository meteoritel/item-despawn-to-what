package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import java.util.Objects;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons.Mark;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 注册表字段的紧凑选择卡片：按名称测量宽度并换行，保留滚动、键盘导航与标签轮播。
 */
final class CatalogSelectionControl extends FormControl implements UiFocusTarget {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final String UI = TypeLabels.UI_PREFIX;
    private static final int CHIP_HEIGHT = 24;
    private static final int GAP = 4;
    private static final int MAX_HEIGHT = 3 * (CHIP_HEIGHT + GAP) - GAP;
    private static final int CHIP_MAX_WIDTH = 124;
    private static final int ICON_TEXT_OFFSET = 24;
    private static final int TEXT_PADDING = 4;
    private static final int REMOVE_AREA_WIDTH = 18;
    // 删除叉的命中与绘制区域：贴住 chip 右端
    private static final int REMOVE_HIT_WIDTH = 16;
    private final Font font;
    private final RuleCatalogType catalogType;
    private final UiScrollView scroll = new UiScrollView();
    private final List<Selection> values = new ArrayList<>();
    private final List<UiRect> chips = new ArrayList<>();
    private final Runnable onChanged;
    private int measuredWidth = -1;
    private int contentHeight = CHIP_HEIGHT;
    private int selected = -1;
    private boolean enabled = true;
    private final UiFocusState focusState = new UiFocusState();

    /**
     * 完整保留既有元素，格式错误的条目不会因其它编辑而丢失。
     */
    private record Selection(RuleValue value, String id, @Nullable TaggedId reference, Component name, @Nullable UiIcon icon) {
    }

    CatalogSelectionControl(UiHostTheme theme, Font font, EditorField field, Runnable onChanged) {
        super(theme, field);
        this.font = font;
        this.catalogType = Objects.requireNonNull(FieldCatalogTypes.catalogTypeOf(field),
                "Catalog selection requires a declared registry");
        this.onChanged = onChanged;
        scroll.setStep(CHIP_HEIGHT + GAP);
    }

    @Override
    void measure(int width) {
        if (measuredWidth == width) return;
        measuredWidth = width;
        arrange(width);
        if (contentHeight > MAX_HEIGHT) arrange(Math.max(0, width - UiScrollView.SCROLLBAR_WIDTH - GAP));
    }

    // 测量、绘制与命中共用矩形：只有一条时占满行宽，多条时每张卡片最多 124 像素；长名称可悬停读取。
    private void arrange(int width) {
        chips.clear();
        int x = 0;
        int y = 0;
        // 单条目不必为换行留余地，放宽到整行宽，长名称不再被裁剪
        int chipLimit = values.size() == 1 ? Math.max(CHIP_HEIGHT * 2, width) : CHIP_MAX_WIDTH;
        for (Selection entry : values) {
            int textOffset = entry.icon() == null ? TEXT_PADDING : ICON_TEXT_OFFSET;
            int chipWidth = Math.min(width, Math.clamp(font.width(entry.name()) + textOffset + REMOVE_AREA_WIDTH,
                    CHIP_HEIGHT * 2, chipLimit));
            if (x > 0 && x + chipWidth > width) {
                x = 0;
                y += CHIP_HEIGHT + GAP;
            }
            chips.add(new UiRect(x, y, chipWidth, CHIP_HEIGHT));
            x += chipWidth + GAP;
        }
        contentHeight = y + CHIP_HEIGHT;
    }

    @Override
    int height() {
        return Math.min(contentHeight, MAX_HEIGHT);
    }

    @Override
    void setBounds(int x, int y, int width) {
        measure(width);
        scroll.setViewport(x, y, width, layoutHeight());
        scroll.setContentHeight(contentHeight);
        scroll.setScrollbarVisible(contentHeight > layoutHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        int hovered = indexAt(mouseX, mouseY);
        scroll.push(graphics);
        try {
            if (values.isEmpty()) graphics.drawString(renderFont, Component.translatable(UI +
                            (catalogType() == RuleCatalogType.FLUID ? "selection.any_fluid" : "selection.empty")),
                    4, 8, THEME.palette().textSecondary(), false);
            for (int i = 0; i < values.size(); i++) {
                UiRect chip = chips.get(i);
                if (chip.bottom() <= scroll.offset() || chip.y() >= scroll.offset() + layoutHeight()) continue;
                Selection entry = values.get(i);
                boolean chosen = isFocused() && i == selected;
                THEME.drawChip(graphics, chip, chosen, enabled && i == hovered);
                if (entry.icon() != null) entry.icon().render(graphics, chip.x() + 4, chip.y() + 4);
                int textOffset = entry.icon() == null ? TEXT_PADDING : ICON_TEXT_OFFSET;
                graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, entry.name().getString(),
                                Math.max(0, chip.width() - textOffset - REMOVE_AREA_WIDTH)),
                        chip.x() + textOffset, chip.y() + (CHIP_HEIGHT - renderFont.lineHeight) / 2,
                        !enabled ? THEME.palette().textDisabled() : chosen ? THEME.palette().textOnDark() : THEME.palette().textPrimary(), false);
                // 叉号统一用次要文本灰：与行为卡片的叉号同色，悬停才转危险色；选中卡的浅蓝底上白叉号几乎看不见。
                int color = enabled && i == hovered ? THEME.palette().danger() : THEME.palette().textSecondary();
                UiIcon close = UiMarkIcons.icon(Mark.CLOSE, color);
                close.render(graphics, chip.right() - REMOVE_HIT_WIDTH + (REMOVE_HIT_WIDTH - close.width()) / 2,
                        chip.y() + (CHIP_HEIGHT - close.height()) / 2);
            }
        } finally {
            scroll.pop(graphics);
        }
        scroll.renderScrollbar(graphics, THEME.controlStyle(UiControlRole.SECONDARY));
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        values.clear();
        if (value != null && value.isList()) {
            for (RuleValue entry : value.items()) values.add(selection(entry));
        } else if (value != null && !value.isNull()) values.add(selection(value));
        measuredWidth = -1;
        selected = Math.min(selected, values.size() - 1);
        measure(scroll.viewport().width());
    }

    private Selection selection(RuleValue raw) {
        RuleCatalogType type = catalogType();
        String id = raw.isText() ? raw.asText() : "";
        Component name = Component.translatable(UI + "selection.unknown");
        UiIcon icon;
        TaggedId reference = TaggedId.tryParse(id);
        if (reference != null && reference.tag()) {
            var tag = TagPreviewIcons.resolve(type, reference);
            name = tag.label();
            icon = tag.icon();
        } else {
            ResourceLocation key = reference == null ? null : reference.id();
            Component label = key == null ? null : RuleDisplayLabels.catalogLabel(type, key);
            if (label != null) {
                name = label;
            } else if (!id.isEmpty()) {
                // 查不到语言键的维度、群系按路径给出人话名，完整注册名仍由悬停提示给出
                name = key == null ? Component.literal(id) : RuleDisplayLabels.idFallback(key);
            }
            icon = CatalogPreviewIcons.iconFor(type, reference);
        }
        return new Selection(raw.deepCopy(), id, reference, name, icon);
    }

    private RuleCatalogType catalogType() {
        return catalogType;
    }

    @Override
    @Nullable RuleValue store() {
        if (!isEdited()) return originalValue();
        boolean multiple = field.isMultiCatalog();
        if (!multiple) return values.isEmpty() ? null : values.getFirst().value().deepCopy();
        if (values.isEmpty() && field.nullable()) return null;
        RuleValue.Sequence result = RuleValue.list();
        values.forEach(entry -> result.add(entry.value().deepCopy()));
        return result.build();
    }

    @Override
    List<FormIssue> issues(String path) {
        if (field.required() && values.isEmpty()) return List.of(FormIssue.required(path, label()));
        for (Selection value : values) {
            TaggedId reference = value.reference();
            if (reference == null || reference.tag() && !field.allowsTags())
                return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "invalid_id")));
        }
        return List.of();
    }

    private int indexAt(double x, double y) {
        if (!scroll.canHoverContent(x, y)) return -1;
        for (int i = 0; i < chips.size(); i++) {
            if (chips.get(i).contains(scroll.toContentX(x), scroll.toContentY(y))) return i;
        }
        return -1;
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        double x = context.pointerX();
        double y = context.pointerY();
        int button = context.button();
        if (!enabled || button != 0) return false;
        if (scroll.mousePressed(context)) return true;
        int index = indexAt(x, y);
        if (index < 0) return false;
        selected = index;
        if (scroll.toContentX(x) >= chips.get(index).right() - REMOVE_HIT_WIDTH) remove(index);
        return true;
    }

    private void remove(int index) {
        if (index < 0 || index >= values.size()) return;
        values.remove(index);
        selected = Math.min(index, values.size() - 1);
        measuredWidth = -1;
        measure(scroll.viewport().width());
        markEdited();
        onChanged.run();
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        boolean dragging = scroll.isDragging();
        scroll.endInteraction(UiInteractionSession.EndReason.CANCEL);
        return dragging;
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dx, double dy) {
        double y = context.pointerY();
        if (!enabled || !scroll.isDragging()) return false;
        scroll.mouseDragged(y);
        return true;
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double dx, double dy) {
        double x = context.pointerX();
        double y = context.pointerY();
        return enabled && scroll.contains(x, y) && scroll.scrollBy(dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!enabled || values.isEmpty()) return false;
        if (keyCode == GLFW.GLFW_KEY_DELETE && selected >= 0) {
            remove(selected);
            return true;
        }
        int next;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> next = selected - 1;
            case GLFW.GLFW_KEY_RIGHT -> next = selected + 1;
            case GLFW.GLFW_KEY_HOME -> next = 0;
            case GLFW.GLFW_KEY_END -> next = values.size() - 1;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                next = Math.max(0, selected);
                if (selected >= 0) {
                    UiRect current = chips.get(selected);
                    int targetY = current.y() + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1) * (CHIP_HEIGHT + GAP);
                    int distance = Integer.MAX_VALUE;
                    for (int i = 0; i < chips.size(); i++) {
                        UiRect chip = chips.get(i);
                        int delta = Math.abs((chip.x() + chip.width() / 2) - (current.x() + current.width() / 2));
                        if (chip.y() == targetY && delta < distance) {
                            next = i;
                            distance = delta;
                        }
                    }
                }
            }
            default -> {
                return false;
            }
        }
        selected = Math.clamp(next, 0, values.size() - 1);
        scroll.ensureVisible(chips.get(selected));
        return true;
    }

    @Override
    void setEnabled(boolean next) {
        enabled = next;
        if (!next) {
            focusState.clear();
            scroll.endInteraction(UiInteractionSession.EndReason.CANCEL);
        }
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> targets) {
        targets.add(this);
    }

    @Override
    public UiFocusState focusState() {
        return focusState;
    }

    @Override
    public boolean canFocus() {
        return enabled;
    }

    @Override
    public void focusChanged(boolean next) {
        if (next && selected < 0 && !values.isEmpty()) selected = 0;
        if (!next) scroll.endInteraction(UiInteractionSession.EndReason.CANCEL);
    }

    @Override
    public boolean isFocused() {
        return focusState.isFocused();
    }

    @Override
    public boolean activate() {
        return false;
    }

    @Override
    public UiRect bounds() {
        return scroll.viewport();
    }

    @Override
    public Component accessibleName() {
        return label();
    }

}
