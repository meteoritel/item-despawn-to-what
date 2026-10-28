package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIconStrip;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionGraphEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * kit 原语接入 {@link UiWidget} 分发的一层薄适配（约每个原语十行）。
 *
 * <p>kit 不允许反向依赖 widget 包，所以 {@link UiIconStrip} 走 kit 自己的
 * {@code UiInputTarget} 与 {@code UiFocusTarget} 契约，指针坐标与修饰键要由宿主装进
 * {@link UiInputContext}。条件树外壳只与本类交互：转发与上下文构造集中在这里，
 * 不在外壳各处散落 kit 调用，kit 签名调整时也只需改这一个文件。</p>
 *
 * <p>叶子参数表单由 {@link Inline} 适配成画布右侧的参数编辑框：
     * 几何、动画与裁剪由画布负责，适配器只把 GUI 屏幕坐标下的事件转发给
 * {@link LeafPanel}。</p>
 */
final class ConditionTreeChrome {

    // 工具类，禁止实例化
    private ConditionTreeChrome() {
    }

    // 指针事件只带坐标与键号，修饰键从原版静态查询补齐成 kit 的显式上下文
    private static UiInputContext pointer(double x, double y, int button) {
        int modifiers = 0;
        if (Screen.hasShiftDown()) modifiers |= GLFW.GLFW_MOD_SHIFT;
        if (Screen.hasControlDown()) modifiers |= GLFW.GLFW_MOD_CONTROL;
        if (Screen.hasAltDown()) modifiers |= GLFW.GLFW_MOD_ALT;
        return UiInputContext.pointer(x, y, button, modifiers);
    }

    /** 图标条：水平功能栏与竖直工具列共用；方向键由 kit 自己处理，Enter/Space 走上层 activate。 */
    static final class Strip implements UiWidget, UiFocusTarget, UiLayoutSource {

        private final UiIconStrip strip;

        Strip(UiHostTheme theme, Font font, UiIconStrip.Orientation orientation) {
            this.strip = new UiIconStrip(font, orientation);
            // 画布页外壳使用扁平宿主主题的条带样式（仅显式取用时生效）
            strip.setStyle(theme.stripStyle());
        }

        UiIconStrip.Item add(UiIcon normal, UiIcon disabled, Component tooltip, UiAction action) {
            return strip.add(normal, disabled, tooltip, action);
        }

        // 取当前指针下的格子提示；kit 只提供 hit，绘制由外壳负责
        @Nullable Component tooltipAt(double x, double y) {
            UiTarget target = strip.hit(x, y);
            return target == null || target.tooltip().isEmpty() ? null : target.tooltip().getFirst();
        }

        @Override public UiRect bounds() { return strip.bounds(); }

        @Override public void setBounds(int x, int y, int width, int height) {
            strip.setBounds(x, y, width, height);
        }

        @Override public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            strip.render(graphics, mouseX, mouseY);
        }

        @Override public boolean mouseClicked(double x, double y, int button) {
            return strip.mousePressed(pointer(x, y, button));
        }

        @Override public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
            return strip.mouseDragged(pointer(x, y, button));
        }

        @Override public boolean mouseReleased(double x, double y, int button) {
            return strip.mouseReleased(pointer(x, y, button));
        }

        @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
            return strip.mouseScrolled(pointer(x, y, -1), scrollY);
        }

        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return strip.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            return strip.keyReleased(keyCode, scanCode, modifiers);
        }

        @Override public boolean charTyped(char codePoint, int modifiers) {
            return strip.charTyped(codePoint, modifiers);
        }

        @Override public boolean canFocus() { return strip.canFocus(); }

        @Override public void setFocused(boolean focused) { strip.setFocused(focused); }

        @Override public boolean isFocused() { return strip.isFocused(); }

        @Override public boolean activate() { return strip.activate(); }

        @Override public @Nullable Component accessibleName() { return strip.accessibleName(); }

        /** 转发图标条快照：适配层不重写几何与文案，两端始终一致。 */
        @Override public List<UiLayoutNode> layoutNodes() { return strip.layoutNodes(); }
    }

    /**
     * 内联参数框：把 {@link LeafPanel}（内部承载 {@link FormView}、目录按钮、
     * 区间条与说明行）接进画布冻结的 {@link UiConditionGraphEditor.InlineEditor} 契约。
     *
     * <p>坐标一律为 GUI 屏幕坐标：参数框固定在画布右侧，不参与画布的平移与缩放。</p>
     */
    static final class Inline implements UiConditionGraphEditor.InlineEditor {

        // 测量用的临时高度：取足够大的值，避免表单按可用高度折算滚动条宽度而低估自然高度
        private static final int MEASURE_PROBE_HEIGHT = Integer.MAX_VALUE / 4;

        private final LeafPanel panel;
        private final FormView form;
        // 当前帧鼠标下的字段提示（校验问题优先）：内联框契约没有 tooltip 通道，
        // 由适配器在 GUI 屏幕坐标下缓存，外壳随后绘制
        private @Nullable Component hoverTooltip;

        Inline(LeafPanel panel, FormView form) {
            this.panel = panel;
            this.form = form;
        }

        /** 当前帧鼠标所在字段的提示；指针不在表单上或本帧未渲染时为 null。 */
        @Nullable Component hoverTooltip() {
            return hoverTooltip;
        }

        @Override
        public int preferredHeight(int width) {
            // LeafPanel.contentHeight 依赖表单按目标宽度完成一次测量：先按候选宽度干跑一次布局
            UiRect current = panel.bounds();
            panel.setBounds(current.x(), current.y(), width, MEASURE_PROBE_HEIGHT);
            return panel.contentHeight();
        }

        @Override public UiRect bounds() { return panel.bounds(); }

        @Override public void setBounds(int x, int y, int width, int height) {
            panel.setBounds(x, y, width, height);
        }

        @Override public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            hoverTooltip = form.tooltipAt(mouseX, mouseY);
            panel.render(graphics, font, mouseX, mouseY);
        }

        @Override public boolean mouseClicked(double x, double y, int button) {
            return panel.mouseClicked(x, y, button);
        }

        @Override public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
            return panel.mouseDragged(x, y, button, dragX, dragY);
        }

        @Override public boolean mouseReleased(double x, double y, int button) {
            return panel.mouseReleased(x, y, button);
        }

        @Override public boolean mouseScrolled(double x, double y, double scrollY) {
            return panel.mouseScrolled(x, y, 0.0D, scrollY);
        }

        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return panel.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override public boolean charTyped(char codePoint, int modifiers) {
            return panel.charTyped(codePoint, modifiers);
        }

        @Override public boolean isVisible() {
            return panel.isVisible();
        }

        @Override public boolean capturesKeyboard() {
            return panel.capturesKeyboard();
        }

        /**
         * 参数表单的只读快照：面板可见时才导出，收起动画中的表单矩形已在画面之外。
         *
         * <p>表单自身给出 GUI 屏幕坐标，适配层只做可见性判断，不重算几何；内联框契约没有快照通道，
         * 由 {@link ConditionTreeOverlay} 在组装快照时按需调用本方法。</p>
         */
        List<UiLayoutNode> layoutNodes() {
            return panel.isVisible() ? form.layoutNodes() : List.of();
        }
    }
}
