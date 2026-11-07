package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.Map;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 模态弹窗栈：负责层次、遮罩、淡入淡出、排版与关闭通知。
 * <p>每个模态拥有独立输入作用域；栈变化通知宿主重新选域，输入与焦点由 kit 路由器维护。
 * <p>调用方先渲染自己的底层界面，再调用 {@link #render}，遮罩会盖在底层之上。
 */
public final class UiModalStack implements UiWidget, UiLayoutSource {

    // 淡入帧数
    public static final int FADE_TICKS = 4;
    // 多层弹窗的偏移像素
    private static final int STACK_OFFSET = 6;

    // 弹窗栈（末尾为最上层）
    private final List<UiModal> modals = new ArrayList<>();
    private final Map<UiModal, UiLayer> presentations = new IdentityHashMap<>();
    private List<UiLayer> layers = List.of();
    // 弹窗内部的焦点管理
    // 可用屏幕区域
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 剩余淡入帧数
    private int fadeTicks;
    private Runnable onScopeChanged = () -> {
    };
    private @Nullable Function<UiHistoryShortcut, Boolean> historyTarget;

    public void setHistoryTarget(@Nullable Function<UiHistoryShortcut, Boolean> target) {
        historyTarget = target;
    }

    // 注入的宿主主题
    private final UiStyleSource theme;

    private UiFocusManager.Listener focusListener = (previous, current) -> { };

    /** 创建原版主题模态栈。 */
    public UiModalStack() { this(UiMinecraftTheme.INSTANCE); }

    /** 创建共享根主题的模态栈。 */
    public UiModalStack(UiStyleSource theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /** 由宿主注入旁白或其它焦点效果，覆盖已有及后续模态。 */
    public void setFocusListener(UiFocusManager.Listener listener) {
        focusListener = Objects.requireNonNull(listener, "listener");
        for (UiModal modal : modals) modal.inputScope().focus().setListener(listener);
    }

    public UiRect bounds() {
        return bounds;
    }

    // 屏幕区域未变时不再重排：弹窗打开期间的静止帧若反复 relayout，会逐帧重排内容控件（目录网格布局成本很高）。
    // push/close 与窗口 resize 都走显式 relayout 或传入新矩形，因此早退不会漏掉这三种必须重排的时机。
    @Override
    public void setBounds(int x, int y, int width, int height) {
        int nextWidth = Math.max(0, width);
        int nextHeight = Math.max(0, height);
        if (bounds.x() == x && bounds.y() == y && bounds.width() == nextWidth && bounds.height() == nextHeight) {
            return;
        }
        this.bounds = new UiRect(x, y, nextWidth, nextHeight);
        relayout();
    }

    public boolean isEmpty() {
        return modals.isEmpty();
    }

    // 弹窗层数
    public int size() {
        return modals.size();
    }

    // 最上层弹窗
    public UiModal top() {
        return modals.isEmpty() ? null : modals.getLast();
    }

    // 全部弹窗（只读，底到顶）
    public List<UiModal> modals() {
        return Collections.unmodifiableList(modals);
    }

    /**
     * 导出弹窗栈的排版快照：按层序（底到顶）展开每个弹窗。
     *
     * <p>栈空时返回空列表，调用方无需判断界面是否有模态层。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (modals.isEmpty()) {
            return List.of();
        }
        List<UiLayoutNode> nodes = new ArrayList<>(modals.size());
        for (UiModal modal : modals) {
            nodes.addAll(modal.layoutNodes());
        }
        return nodes;
    }

    // 弹窗内部焦点管理器
    public UiFocusManager focusManager() {
        UiModal modal = top();
        return modal == null ? null : modal.inputScope().focus();
    }

    // 压入一个弹窗
    public UiModal push(UiModal modal) {
        modal.setCloseHandler(this::remove);
        modal.inputScope().focus().setListener(focusListener);
        modals.add(modal);
        presentations.put(modal, new UiLayer(modal.inputScope(), (graphics, font, pointer, depth) ->
                modal.renderOverlay(graphics, bounds, depth, dimColor(),
                        () -> modal.render(graphics, font, pointer)), modal::layoutNodes));
        refreshLayers();
        modal.inputScope().setHistory(this::requestHistory);
        onScopeChanged.run();
        fadeTicks = FADE_TICKS;
        relayout();
        refreshFocus();
        return modal;
    }

    // 宿主在打开模态时结束底层的拖动捕获，避免释放只交给弹窗后残留拖动状态。
    public void setOnScopeChanged(Runnable handler) {
        onScopeChanged = handler;
    }

    public boolean closeTop() {
        if (modals.isEmpty()) {
            return false;
        }
        return modals.getLast().requestClose();
    }

    /** 正常关闭遵守模态政策。 */
    public void close(UiModal modal) { if (modals.contains(modal)) modal.requestClose(); }

    /** 宿主终态或销毁时强制移除，不执行用户动作。 */
    public void remove(UiModal modal) {
        if (modals.remove(modal)) {
            presentations.remove(modal);
            refreshLayers();
            onScopeChanged.run();
            modal.closed();
            relayout();
            refreshFocus();
        }
    }

    public void clear() {
        List<UiModal> closing = List.copyOf(modals);
        modals.clear();
        presentations.clear();
        refreshLayers();
        onScopeChanged.run();
        closing.reversed().forEach(UiModal::closed);

    }

    // 推进淡入动画，每帧调用一次
    public void tick() {
        if (fadeTicks > 0) {
            fadeTicks--;
        }
    }

    // 鼠标是否位于最上层弹窗内
    public boolean isMouseOverModal(double mouseX, double mouseY) {
        UiModal modal = top();
        return modal != null && modal.bounds().contains(mouseX, mouseY);
    }

    // 逐层居中排列，每层向右下偏移一点以便看出层叠关系
    /** 主题度量变化后重排存量模态，不重建内容。 */
    public void refreshLayout() { relayout(); }

    private void relayout() {
        for (int i = 0; i < modals.size(); i++) {
            UiModal modal = modals.get(i);
            int width = modal.preferredWidth();
            int height = modal.preferredHeight();
            int x = (bounds.width() - width) / 2 + i * STACK_OFFSET;
            int y = (bounds.height() - height) / 2 + i * STACK_OFFSET;
            modal.layoutAt(x, y, width, height);
        }
    }

    // 焦点只保留在最上层弹窗内
    private void refreshFocus() {
        UiModal modal = top();
        if (modal != null) {
            modal.inputScope().refreshFocus();
            if (modal.inputScope().focus().focused() == null) modal.inputScope().focus().focusFirst();
        }
    }

    // ---- 渲染 ----

    @Override
    public boolean isVisible() {
        return !modals.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        for (int depth = 0; depth < layers.size(); depth++) {
            layers.get(depth).render(graphics, font, pointer.x(), pointer.y(), depth);
        }
    }

    /** 引用栈当前拥有的模态，顺序随栈结构变化更新。 */
    public List<UiLayer> layers() { return layers; }

    private void refreshLayers() {
        layers = modals.stream().map(presentations::get).toList();
    }

    private int dimColor() {
        int color = theme.surfaces().modalDim();
        int alpha = ((color >>> 24) & 0xFF) * (FADE_TICKS - fadeTicks) / FADE_TICKS;
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * 以编程方式请求一次撤销或重做，链路与 Ctrl+Z 完全一致：先交给宿主历史目标，
     * 成功后再通知所有已打开的模态。界面按钮用它，避免绕过宿主页面重建与模态的失效守卫。
     */
    public void requestHistory(UiHistoryShortcut shortcut) {
        if (historyTarget == null || !historyTarget.apply(shortcut)) {
            return;
        }
        for (UiModal current : List.copyOf(modals)) {
            current.historyChanged(shortcut);
        }
    }

}
