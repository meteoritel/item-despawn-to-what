package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiIconStrip;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionGraphEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树工具栏与画布共享输入契约的宿主适配，负责排版与焦点目标转交。
 * <p>原语直接接收事件上下文，宿主在入口采集修饰键与作用域。</p>
 */
final class ConditionTreeChrome {

    // 工具类，禁止实例化
    private ConditionTreeChrome() {
    }

    // 指针事件只带坐标与键号，修饰键从原版静态查询补齐成 kit 的显式上下文


    /**
     * 图标条：水平功能栏与竖直工具列共用；方向键由 kit 自己处理，Enter/Space 走上层 activate。
     */
    static final class Strip implements UiWidget, UiFocusTarget, UiLayoutSource {

        /** 图标条与外壳共用实际命中。 */
        @Override
        public @Nullable UiPointerHit hitTest(double x, double y) {
            UiPointerHit hit = strip.hitTest(x, y);
            return hit == null ? null : hit.within(this);
        }
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

        @Override
        public UiRect bounds() {
            return strip.bounds();
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            strip.setBounds(x, y, width, height);
        }

        /** {@inheritDoc} */
        @Override
        public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
            UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
            strip.render(graphics, pointer);
        }

        @Override
        public boolean mousePressed(UiInputContext context) {
            return strip.mousePressed(context);
        }

        @Override
        public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
            return strip.mouseDragged(context, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(UiInputContext context) {
            return strip.mouseReleased(context);
        }

        @Override
        public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
            return strip.mouseScrolled(context, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return strip.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            return strip.keyReleased(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return strip.charTyped(codePoint, modifiers);
        }

        public UiFocusState focusState() {
            return strip.focusState();
        }

        @Override
        public boolean canFocus() {
            return strip.canFocus();
        }

        @Override
        public void focusChanged(boolean focused) {
            strip.focusChanged(focused);
        }

        @Override
        public boolean isFocused() {
            return strip.isFocused();
        }

        @Override
        public boolean activate() {
            return strip.activate();
        }

        @Override
        public @Nullable Component accessibleName() {
            return strip.accessibleName();
        }

        /**
         * 转发图标条快照：适配层不重写几何与文案，两端始终一致。
         */
        @Override
        public List<UiLayoutNode> layoutNodes() {
            return strip.layoutNodes();
        }
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

        /** 内联表单的叶子与画布共用输入身份。 */
        @Override
        public @Nullable UiPointerHit hitTest(double x, double y) {
            UiPointerHit hit = panel.hitTest(x, y);
            return hit == null ? null : hit.within(this);
        }
        private final LeafPanel panel;
        private final FormView form;
        // 当前帧鼠标下的字段提示（校验问题优先）：内联框契约没有 tooltip 通道，
        // 由适配器在 GUI 屏幕坐标下缓存，外壳随后绘制
        private @Nullable Component hoverTooltip;

        Inline(LeafPanel panel, FormView form) {
            this.panel = panel;
            this.form = form;
        }

        /**
         * 当前帧鼠标所在字段的提示；指针不在表单上或本帧未渲染时为 null。
         */
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

        @Override
        public UiRect bounds() {
            return panel.bounds();
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            panel.setBounds(x, y, width, height);
        }

        /** {@inheritDoc} */
        @Override
        public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
            UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
            int mouseX = (int) pointer.x();
            int mouseY = (int) pointer.y();
            hoverTooltip = pointer.allowsTooltip() ? form.tooltipAt(mouseX, mouseY) : null;
            panel.render(graphics, font, pointer);
        }

        @Override
        public boolean mousePressed(UiInputContext context) {
            return panel.mousePressed(context);
        }

        @Override
        public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
            return panel.mouseDragged(context, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(UiInputContext context) {
            return panel.mouseReleased(context);
        }

        @Override
        public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
            return panel.mouseScrolled(context, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return panel.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return panel.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean isVisible() {
            return panel.isVisible();
        }

        @Override
        public boolean capturesKeyboard() {
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
