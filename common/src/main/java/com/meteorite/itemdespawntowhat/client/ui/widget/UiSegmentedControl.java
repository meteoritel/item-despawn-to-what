package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 分段选择控件：把少量互斥取值渲染成一排等宽按钮（枚举取值用）。
 * <p>选中段用主题的 selected 九宫格（中心色为 {@code CONTROL_SELECTED}）绘制，未选中段用 control 九宫格，
 * 两者底图不同；段内文字统一用 {@code TEXT_PRIMARY}，避免浅蓝选中底上的白字对比度不足。
 */
public final class UiSegmentedControl implements UiWidget, UiFocusTarget, UiLayoutSource {

    // 最外侧左右各留的缩进（像素）
    private static final int EDGE_INSET = 1;
    // 相邻两段之间的空白（像素），由两侧各 EDGE_INSET 缩进构成
    private static final int SEGMENT_GAP = EDGE_INSET * 2;
    // 段内文字左右各保留的像素，避免文字贴边
    private static final int LABEL_INSET = 4;
    // 键盘焦点指示点边长（像素）
    private static final int FOCUS_DOT_SIZE = 3;
    // 键盘焦点指示点距段左边界的像素
    private static final int FOCUS_DOT_INSET = 4;

    /**
     * 单个分段选项：{@code value} 为回传宿主的选择值且必填，{@code label} 为段内文案，为空时用 {@code value} 兜底。
     */
    public record Option(String value, Component label) {
        public Option {
            Objects.requireNonNull(value, "value");
            if (label == null) {
                label = Component.literal(value);
            }
        }
    }

    private final Font font;
    private List<Option> options = List.of();
    private int navigationIndex = -1;
    private String selected = "";
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private final UiFocusState focusState = new UiFocusState();
    private @Nullable Consumer<String> onChanged;

    // 注入的宿主主题
    private final UiHostTheme theme;

    public UiSegmentedControl(UiHostTheme theme, Font font, List<Option> options) {
        this.theme = UiHostThemes.require(theme);
        this.font = Objects.requireNonNull(font, "font");
        setOptions(options);
    }

    // 设置候选项；若当前选中值不在候选中，则回退到第一项
    public UiSegmentedControl setOptions(List<Option> next) {
        List<Option> copy = new ArrayList<>();
        if (next != null) {
            for (Option option : next) {
                if (option != null) {
                    copy.add(option);
                }
            }
        }
        this.options = List.copyOf(copy);
        boolean present = false;
        for (Option option : this.options) {
            if (option.value().equals(selected)) {
                present = true;
                break;
            }
        }
        if (!present) {
            this.selected = this.options.isEmpty() ? "" : this.options.getFirst().value();
        }
        return this;
    }

    public List<Option> options() {
        return options;
    }

    public String selected() {
        return selected;
    }

    // 选中下标，无选中返回 -1
    public int selectedIndex() {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    // 直接设置选中值（不回调），用于从草稿回填
    public UiSegmentedControl setSelected(@Nullable String value) {
        this.selected = "";
        if (value == null) {
            return this;
        }
        for (Option option : options) {
            if (option.value().equals(value)) {
                this.selected = value;
                return this;
            }
        }
        return this;
    }

    public UiSegmentedControl setOnChanged(@Nullable Consumer<String> listener) {
        this.onChanged = listener;
        return this;
    }

    public UiSegmentedControl setEnabled(boolean next) {
        this.enabled = next;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiSegmentedControl setVisible(boolean next) {
        this.visible = next;
        return this;
    }

    public int preferredWidth(int padding) {
        int total = 0;
        for (Option option : options) {
            total += font.width(option.label()) + padding * 2;
        }
        return total;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
    }

    // 第 index 段的矩形：按控件宽度均分，各段宽度相差不超过 1 像素（整数像素无法整除时的最小偏差）
    private UiRect segmentRect(int index) {
        int count = options.size();
        if (count == 0) {
            return new UiRect(bounds.x(), bounds.y(), 0, 0);
        }
        int left = bounds.x() + (int) ((long) bounds.width() * index / count);
        int right = bounds.x() + (int) ((long) bounds.width() * (index + 1) / count);
        return new UiRect(left, bounds.y(), Math.max(0, right - left), bounds.height());
    }

    // 鼠标位置落在哪一段，未命中返回 -1
    private int indexAt(double mouseX, double mouseY) {
        if (options.isEmpty() || !bounds.contains(mouseX, mouseY)) {
            return -1;
        }
        for (int i = 0; i < options.size(); i++) {
            if (segmentRect(i).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 导出分段控件的只读排版快照：每个候选项一个 CONTROL，矩形取该段均分后的真实命中区。
     *
     * <p>段矩形同时决定命中，快照里的中心点必然落在该段上，探针据此点击与真实输入走同一条路径；
     * 带宽按段宽扣掉段内左右留白，与绘制时的裁剪口径一致，因此 {@code clipped()} 反映画面。
     * 控件不可见或没有候选项时不报节点，调用方无需区分空控件与未实现。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (!visible || options.isEmpty()) {
            return List.of();
        }
        List<UiLayoutNode> nodes = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            UiRect segment = segmentRect(i);
            Component label = options.get(i).label();
            nodes.add(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, segment, label, font.width(label),
                    Math.max(0, segment.width() - LABEL_INSET), enabled, true, List.of()));
        }
        return nodes;
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible || options.isEmpty()) {
            return;
        }
        int hovered = enabled ? indexAt(mouseX, mouseY) : -1;
        int chosen = selectedIndex();
        for (int i = 0; i < options.size(); i++) {
            UiRect segment = segmentRect(i);
            // 底图矩形左右各缩进 EDGE_INSET：最外两侧各留 1 像素，相邻两段之间合计 SEGMENT_GAP 像素
            UiRect surface = new UiRect(segment.x() + EDGE_INSET, segment.y(),
                    Math.max(0, segment.width() - SEGMENT_GAP), segment.height());
            theme.drawSegment(graphics, surface, enabled, i == chosen, i == hovered);
            // 选中态只由底图区分：浅蓝选中底上的白字对比度不足，故两态段内文字统一用主要文本色
            int color = enabled ? theme.palette().textPrimary() : theme.palette().textDisabled();
            String label = TextScroll.trimToWidth(renderFont, options.get(i).label().getString(),
                    Math.max(0, segment.width() - LABEL_INSET));
            graphics.drawString(renderFont, label, segment.x() + Math.max(0, (segment.width() - renderFont.width(label)) / 2),
                    segment.y() + Math.max(0, (segment.height() - renderFont.lineHeight) / 2), color, false);
        }
        if (isFocusVisible()) {
            int index = Math.clamp(navigationIndex, 0, options.size() - 1);
            UiRect segment = segmentRect(index);
            // 焦点指示用深色点：选中段是浅蓝底，白色点在浅蓝底上不可辨
            int dotY = segment.y() + Math.max(0, (segment.height() - FOCUS_DOT_SIZE) / 2);
            int dotX = segment.x() + FOCUS_DOT_INSET;
            graphics.fill(dotX, dotY,
                    dotX + FOCUS_DOT_SIZE, dotY + FOCUS_DOT_SIZE, theme.palette().textPrimary());
        }
    }

    // 选中指定下标并回调
    private boolean select(int index) {
        if (index < 0 || index >= options.size()) {
            return false;
        }
        String next = options.get(index).value();
        if (next.equals(selected)) {
            return true;
        }
        selected = next;
        if (onChanged != null) {
            onChanged.accept(selected);
        }
        return true;
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (!visible || !enabled || button != 0) {
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        if (index < 0) return false;
        navigationIndex = index;
        context.scope().pointerHit(this);
        return select(index);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!canFocus() || !isFocused()) return false;
        int current = Math.clamp(navigationIndex < 0 ? selectedIndex() : navigationIndex, 0, options.size() - 1);
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> navigationIndex = Math.floorMod(current - 1, options.size());
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> navigationIndex = (current + 1) % options.size();
            case GLFW.GLFW_KEY_HOME -> navigationIndex = 0;
            case GLFW.GLFW_KEY_END -> navigationIndex = options.size() - 1;
            default -> {
                return false;
            }
        }
        UiNarration.focus(this);
        return true;
    }

    @Override
    public UiFocusState focusState() {
        return focusState;
    }

    @Override
    public boolean canFocus() {
        return visible && enabled && !options.isEmpty();
    }

    @Override
    public void focusChanged(boolean next) {
        if (next && (navigationIndex < 0 || navigationIndex >= options.size()))
            navigationIndex = Math.max(0, selectedIndex());
    }

    @Override
    public boolean isFocused() {
        return focusState.isFocused();
    }

    // 激活当前导航项，导航本身不改变业务选中。
    @Override
    public boolean activate() {
        if (!visible || !enabled || options.isEmpty()) {
            return false;
        }
        int current = Math.max(0, selectedIndex());
        return select(Math.clamp(navigationIndex < 0 ? current : navigationIndex, 0, options.size() - 1));
    }

    @Override
    public @Nullable Component accessibleName() {
        int index = isFocused() && navigationIndex >= 0 ? navigationIndex : selectedIndex();
        if (index < 0) {
            return Component.empty();
        }
        return options.get(index).label();
    }
}
