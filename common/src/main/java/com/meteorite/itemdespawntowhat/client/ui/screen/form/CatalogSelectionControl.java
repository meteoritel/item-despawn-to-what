package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorP4Panels;
import com.meteorite.itemdespawntowhat.client.ui.screen.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/*** 输入页的紧凑物品卡片：按名称测量宽度并换行，保留滚动、键盘导航与标签轮播。 */
final class CatalogSelectionControl extends FormControl implements UiFocusTarget {
    private static final String UI = "gui.itemdespawntowhat.edit.";
    private static final int CHIP_HEIGHT = 24;
    private static final int GAP = 4;
    private static final int MAX_HEIGHT = 3 * (CHIP_HEIGHT + GAP) - GAP;
    private final Font font;
    private final UiScrollView scroll = new UiScrollView();
    private final List<Selection> values = new ArrayList<>();
    private final List<UiRect> chips = new ArrayList<>();
    private final Runnable onChanged;
    private int measuredWidth = -1;
    private int contentHeight = CHIP_HEIGHT;
    private int selected = -1;
    private boolean enabled = true;
    private boolean focused;

    /*** 完整保留既有元素，格式错误的条目不会因其它编辑而丢失。 */
    private record Selection(JsonElement value, String id, Component name, @Nullable UiIcon icon) { }

    CatalogSelectionControl(Font font, EditorField field, Runnable onChanged) {
        super(field);
        this.font = font;
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

    // 测量、绘制与命中共用矩形，每张卡片最多 124 像素；长名称可悬停读取。
    private void arrange(int width) {
        chips.clear();
        int x = 0;
        int y = 0;
        for (Selection entry : values) {
            int chipWidth = Math.min(width, Math.clamp(font.width(entry.name()) + 42, 48, 124));
            if (x > 0 && x + chipWidth > width) { x = 0; y += CHIP_HEIGHT + GAP; }
            chips.add(new UiRect(x, y, chipWidth, CHIP_HEIGHT));
            x += chipWidth + GAP;
        }
        contentHeight = y + CHIP_HEIGHT;
    }

    @Override
    int height() { return Math.min(contentHeight, MAX_HEIGHT); }

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
            if (values.isEmpty()) graphics.drawString(renderFont, Component.translatable(UI + "selection.empty"),
                    4, 8, UiPalette.TEXT_SECONDARY, false);
            for (int i = 0; i < values.size(); i++) {
                UiRect chip = chips.get(i);
                if (chip.bottom() <= scroll.offset() || chip.y() >= scroll.offset() + layoutHeight()) continue;
                Selection entry = values.get(i);
                boolean chosen = focused && i == selected;
                UiTheme.drawChip(graphics, chip, chosen, enabled && i == hovered);
                if (entry.icon() != null) entry.icon().render(graphics, chip.x() + 4, chip.y() + 4);
                graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, entry.name().getString(), Math.max(0, chip.width() - 42)),
                        chip.x() + 24, chip.y() + (CHIP_HEIGHT - renderFont.lineHeight) / 2,
                        !enabled ? UiPalette.TEXT_DISABLED : chosen ? UiPalette.TEXT_ON_DARK : UiPalette.TEXT_PRIMARY, false);
                int crossX = chip.right() - 11;
                int crossY = chip.y() + 9;
                int color = enabled && i == hovered ? UiPalette.DANGER : chosen ? UiPalette.TEXT_ON_DARK : UiPalette.TEXT_SECONDARY;
                for (int pixel = 0; pixel < 5; pixel++) {
                    graphics.fill(crossX + pixel, crossY + pixel, crossX + pixel + 1, crossY + pixel + 1, color);
                    graphics.fill(crossX + 4 - pixel, crossY + pixel, crossX + 5 - pixel, crossY + pixel + 1, color);
                }
            }
        } finally { scroll.pop(graphics); }
        scroll.renderScrollbar(graphics, UiTheme.secondaryStyle());
    }

    @Override
    void load(@Nullable JsonElement value) {
        rememberLoaded(value);
        values.clear();
        if (value instanceof JsonArray array) {
            for (JsonElement entry : array) values.add(selection(entry));
        } else if (value != null && !value.isJsonNull()) values.add(selection(value));
        measuredWidth = -1;
        selected = Math.min(selected, values.size() - 1);
        measure(scroll.viewport().width());
    }

    private Selection selection(JsonElement raw) {
        String id = raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString() ? raw.getAsString() : "";
        Component name = Component.translatable(UI + "selection.unknown");
        UiIcon icon;
        if (id.startsWith("#")) {
            var tag = TagPreviewIcons.resolve(RuleCatalogType.ITEM, id);
            name = Component.literal("# ").append(tag.label());
            icon = tag.icon();
        } else {
            ResourceLocation key = ResourceLocation.tryParse(id);
            Component label = key == null ? null : RuleDisplayLabels.label(key);
            if (label != null) name = label;
            icon = RuleEditorP4Panels.iconFor(RuleCatalogType.ITEM, id);
        }
        return new Selection(raw.deepCopy(), id, name, icon);
    }

    @Override
    @Nullable JsonElement store() {
        if (!isEdited()) return originalElement();
        boolean multiple = field.type() == EditorFieldType.TAG_LIST || field.type() == EditorFieldType.RL_LIST;
        if (!multiple) return values.isEmpty() ? null : values.getFirst().value().deepCopy();
        if (values.isEmpty() && field.nullable()) return null;
        JsonArray result = new JsonArray();
        values.forEach(entry -> result.add(entry.value().deepCopy()));
        return result;
    }

    @Override
    List<FormIssue> issues(String path) {
        if (field.required() && values.isEmpty()) return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
        for (Selection value : values) {
            String id = value.id().startsWith("#") ? value.id().substring(1) : value.id();
            if (ResourceLocation.tryParse(id) == null) return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "invalid_id")));
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
    boolean mouseClicked(double x, double y, int button) {
        if (!enabled || button != 0) return false;
        if (scroll.mousePressed(x, y, button)) return true;
        int index = indexAt(x, y);
        if (index < 0) return false;
        selected = index;
        if (scroll.toContentX(x) >= chips.get(index).right() - 16) remove(index);
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
    boolean mouseReleased(double x, double y, int button) {
        boolean dragging = scroll.isDragging();
        scroll.mouseReleased();
        return dragging;
    }

    @Override
    boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!enabled || !scroll.isDragging()) return false;
        scroll.mouseDragged(y);
        return true;
    }

    @Override
    boolean mouseScrolled(double x, double y, double dx, double dy) { return enabled && scroll.contains(x, y) && scroll.scrollBy(dy); }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!enabled || values.isEmpty()) return false;
        if (keyCode == GLFW.GLFW_KEY_DELETE && selected >= 0) { remove(selected); return true; }
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
                        if (chip.y() == targetY && delta < distance) { next = i; distance = delta; }
                    }
                }
            }
            default -> { return false; }
        }
        selected = Math.clamp(next, 0, values.size() - 1);
        scroll.ensureVisible(chips.get(selected));
        return true;
    }

    @Override
    void setEnabled(boolean next) {
        enabled = next;
        if (!next) { focused = false; scroll.mouseReleased(); }
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> targets) { targets.add(this); }

    @Override
    public boolean canFocus() { return enabled; }

    @Override
    public void setFocused(boolean next) {
        focused = next;
        if (next && selected < 0 && !values.isEmpty()) selected = 0;
        if (!next) scroll.mouseReleased();
    }

    @Override
    public boolean isFocused() { return focused; }

    @Override
    public boolean activate() { return false; }

    @Override
    public UiRect bounds() { return scroll.viewport(); }

    @Override
    public Component accessibleName() { return label(); }

    @Override
    @Nullable Component tooltipAt(double x, double y) {
        int index = indexAt(x, y);
        if (index < 0) return null;
        Selection entry = values.get(index);
        return entry.name().copy().append("\n").append(entry.id())
                .append("\n").append(Component.translatable(UI + "selection.remove_hint"));
    }
}
