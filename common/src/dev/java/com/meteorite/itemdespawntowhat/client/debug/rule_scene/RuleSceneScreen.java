package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

import com.meteorite.itemdespawntowhat.client.debug.kit.WorkbenchLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiScreen;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiLightbox;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiIconLightboxContent;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 规则三维场景页：一条样例规则一个舞台，左侧是 3D 场景与输入槽，右侧是触发条件与产出。
 *
 * <p>屏幕只负责样例切换、时间推进与控件；画面交给 {@link RuleSceneView}。时间轴是唯一动效来源，
 * 暂停后同一时刻的绘制结果一致，探针因此可以稳定截图。</p>
 */
public final class RuleSceneScreen extends UiScreen implements UiLayoutSource {

    /** 阶段 chip 数量，与时间轴阶段一一对应。 */
    private static final int PHASE_COUNT = 4;
    /** chip 最小宽度。 */
    private static final int MIN_CHIP_WIDTH = 26;
    /** chip 文案左右内边距之和。 */
    private static final int CHIP_TEXT_PADDING = 20;
    /** chip 之间的间距。 */
    private static final int CHIP_GAP = 4;
    /** 提示行与样例计数之间的最小留白。 */
    private static final int HINT_GAP = 12;

    /** 翻页 chip 的文案，单字符保证宽度稳定。 */
    private static final String PREVIOUS_LABEL = "◀";
    private static final String NEXT_LABEL = "▶";

    private static final String LIGHTBOX_UI = "gui.itemdespawntowhat.debug.workbench.lightbox.";

    private final Runnable back;
    private final UiHostTheme theme = UiHostThemes.defaultTheme();
    private UiLayers layers;
    private UiLightbox lightbox;
    private final List<RuleSceneSamples.Sample> samples;
    private final List<UiButton> chips = new ArrayList<>();

    private RuleSceneView view;
    private int index;
    private long elapsed;
    private long anchor = Util.getMillis();
    private boolean paused;
    private UiButton previousButton;
    private UiButton nextButton;
    private UiButton pauseButton;
    private UiButton backButton;
    private UiInputScope scope;
    private UiRect counterRect;
    private UiRect hintRect;

    /** 进入时停在第一条样例，并从动画起点开始播放。 */
    public RuleSceneScreen(Runnable back) {
        super(Component.translatable(RuleSceneModel.TITLE));
        this.back = back;
        this.samples = RuleSceneSamples.all();
        this.view = new RuleSceneView(samples.getFirst().model(), samples.getFirst().id(), this::openLightbox);
    }

    /** 底页拥有舞台入口与全部 chip；灯箱通过呈现清单切换到独立活动域。 */
    @Override protected void init() {
        if (chips.isEmpty()) {
            chips.add(previousButton = chip(Component.literal(PREVIOUS_LABEL), () -> switchSample(-1)));
            chips.add(nextButton = chip(Component.literal(NEXT_LABEL), () -> switchSample(1)));
            chips.add(pauseButton = chip(Component.translatable(RuleSceneModel.UI + "pause"), this::togglePause));
            chips.add(chip(Component.translatable(RuleSceneModel.UI + "replay"), this::replay));
            for (int phase = 0; phase < PHASE_COUNT; phase++) {
                RuleSceneTimeline.Phase target = RuleSceneTimeline.Phase.values()[phase];
                chips.add(chip(Component.translatable(RuleSceneModel.UI + "phase." + target.name().toLowerCase(Locale.ROOT)),
                        () -> seek(target)));
            }
            backButton = new UiButton(theme, font, Component.translatable(RuleSceneModel.BACK_KEY),
                    UiButtonVariant.SECONDARY, this::onClose);
            scope = new UiInputScope(new UiInputTarget() {
                @Override public void addFocusTargets(Consumer<UiFocusTarget> out) {
                    out.accept(view.stageEntry());
                    chips.forEach(out);
                    out.accept(backButton);
                }
                @Override public UiPointerHit hitTest(double x, double y) {
                    UiPointerHit hit = UiPointerHit.topmost(x, y, targets());
                    return hit == null ? view.stageEntry().hitTest(x, y) : hit;
                }
                @Override public boolean mousePressed(UiInputContext context) {
                    for (UiButton button : targets()) {
                        if (button.mousePressed(context)) return true;
                    }
                    return view.stageEntry().mousePressed(context);
                }
                @Override public boolean keyPressed(int key, int scan, int modifiers) {
                    return baseKeyPressed(key);
                }
            });
            scope.focus().setEnterActivates(true);
            scope.focus().setSpaceActivates(true);
            scope.setEscape(this::onClose);
            layers = new UiLayers(new UiLayer(scope,
                    (graphics, renderFont, pointer, depth) -> renderBase(graphics, pointer), this::baseNodes));
        }
        layoutUi();
        refreshUiState();
    }

    /** 统一路由使用当前呈现清单的顶层作用域。 */
    @Override protected UiInputScope inputScope() { return layers.activeScope(); }

    /** 输入与绘制共享布局；只在实际 GUI 帧推进时间。 */
    @Override protected void layoutUi() {
        view.layout(font, contentRect());
        WorkbenchLayout.placeFooterBack(backButton, height);
        updatePauseLabel();
        layoutChips();
        layoutCounter();
        if (lightbox != null) lightbox.prepare(font, width, height);
    }

    /** 两个资源所有者各自只推进一次，包住底页和灯箱的全部绘制。 */
    @Override protected void renderUi(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        PreviewIcons.renderFrame(() -> view.renderFrame(() -> layers.render(graphics, font, mouseX, mouseY)));
    }

    private void renderBase(GuiGraphics graphics, UiPointer pointer) {
        RuleSceneSamples.Sample sample = samples.get(index);
        WorkbenchLayout.drawPage(graphics, font, theme, width, height, sample.title());
        WorkbenchLayout.drawSubtitle(graphics, font, theme, width, sample.subtitle());
        WorkbenchLayout.drawFooterRule(graphics, theme, width, height);
        view.render(graphics, font, elapsed, theme, pointer);
        drawCounter(graphics);
        for (UiButton button : targets()) button.render(graphics, font, pointer);
        if (pointer.allowsTooltip() && pointer.hit() != null) {
            pointer.hit().renderTooltip(graphics, font, theme.tooltipSkin(), (int) pointer.x(), (int) pointer.y());
        }
    }

    private List<UiLayoutNode> baseNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>(view.layoutNodes());
        nodes.add(counterNode());
        nodes.add(hintNode());
        for (UiButton button : targets()) nodes.addAll(button.layoutNodes());
        return nodes;
    }

    /** 导出当前呈现的底页与灯箱真实布局。 */
    @Override public List<UiLayoutNode> layoutNodes() { return layers.layoutNodes(); }

    // 此入口只由底页活动域调用；Space 在舞台持焦时交回焦点激活。
    private boolean baseKeyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> { switchSample(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { switchSample(1); return true; }
            case GLFW.GLFW_KEY_SPACE -> {
                if (view.stageEntry().isFocused()) return false;
                togglePause();
                return true;
            }
            case GLFW.GLFW_KEY_R -> { replay(); return true; }
            case GLFW.GLFW_KEY_1 -> { seek(RuleSceneTimeline.Phase.BUILD); return true; }
            case GLFW.GLFW_KEY_2 -> { seek(RuleSceneTimeline.Phase.ITEMS); return true; }
            case GLFW.GLFW_KEY_3 -> { seek(RuleSceneTimeline.Phase.TRIGGER); return true; }
            case GLFW.GLFW_KEY_4 -> { seek(RuleSceneTimeline.Phase.RESULT); return true; }
            default -> { return false; }
        }
    }

    private void openLightbox() {
        UiIconLightboxContent content = new UiIconLightboxContent(view.snapshot(font, elapsed, theme), theme);
        lightbox = new UiLightbox(new UiLightbox.Labels() {
            @Override public Component close() { return lightboxLabel("close"); }
            @Override public Component zoomIn() { return lightboxLabel("zoom_in"); }
            @Override public Component zoomOut() { return lightboxLabel("zoom_out"); }
            @Override public Component fit() { return lightboxLabel("fit"); }
            @Override public Component previous() { return lightboxLabel("previous"); }
            @Override public Component next() { return lightboxLabel("next"); }
            @Override public Component position(int index, int total) { return lightboxLabel("position", index, total); }
            @Override public Component zoomReadout(int percent) { return lightboxLabel("zoom_readout", percent); }
        }, content, this::closeLightbox);
        lightbox.setTheme(theme);
        lightbox.setText(samples.get(index).title(), Component.translatable(RuleSceneModel.UI + "lightbox.description"));
        lightbox.setBarHint(lightboxLabel("hint"));
        lightbox.prepare(font, width, height);
        layers.setOverlays(List.of(lightbox.layer()));
        refreshUiState();
    }

    private void closeLightbox() {
        if (lightbox == null) return;
        UiLightbox closing = lightbox;
        layers.setOverlays(List.of());
        refreshUiState();
        closing.onClosed();
        lightbox = null;
        anchor = Util.getMillis() - elapsed;
    }

    private static Component lightboxLabel(String key, Object... args) {
        return Component.translatable(LIGHTBOX_UI + key, args);
    }

    /** 返回工作台目录；目录会重建自己的输入域，这里不再保留状态。 */
    @Override public void onClose() { back.run(); }

    /** 语言与资源缓存变化时，灯箱按当前文案重新测量；舞台世代在帧入口处理。 */
    @Override protected void invalidateUiResources() {
        if (lightbox != null) lightbox.invalidateResources();
    }

    /** UiScreen 已关闭路由，此处撤下内容并释放宿主资源。 */
    @Override protected void releaseUiResources() {
        layers.setOverlays(List.of());
        if (lightbox != null) {
            lightbox.onClosed();
            lightbox = null;
        }
        view.close();
        PreviewIcons.retainRecent();
    }

    /** 动画需要持续推进游戏 tick，不能按暂停界面处理。 */
    @Override public boolean isPauseScreen() { return false; }

    /** 每个 GUI 帧推进一次时间；灯箱保持打开时的时间快照与原暂停状态。 */
    @Override protected void advanceUiFrame() {
        long now = Util.getMillis();
        if (paused || lightbox != null) return;
        elapsed = Math.floorMod(now - anchor, RuleSceneTimeline.CYCLE_MILLIS);
    }

    /** 切换样例：换舞台、重置时间轴，保持当前暂停状态，逐条对比时不自行播放。 */
    private void switchSample(int step) {
        if (samples.size() <= 1) return;
        closeLightbox();
        index = Math.floorMod(index + step, samples.size());
        view.close();
        RuleSceneSamples.Sample sample = samples.get(index);
        view = new RuleSceneView(sample.model(), sample.id(), this::openLightbox);
        scope.invalidateFocus();
        elapsed = 0;
        anchor = Util.getMillis();
        if (previousButton != null) previousButton.setEnabled(true);
        if (nextButton != null) nextButton.setEnabled(true);
    }

    private UiButton chip(Component label, Runnable action) {
        return new UiButton(theme, font, label, UiButtonVariant.SECONDARY, action::run).setChipStyle(true);
    }

    private List<UiButton> targets() {
        List<UiButton> buttons = new ArrayList<>(chips);
        buttons.add(backButton);
        return buttons;
    }

    /** 暂停按钮的文案跟随状态切换。 */
    private void updatePauseLabel() {
        pauseButton.setLabel(Component.translatable(RuleSceneModel.UI + (paused ? "resume" : "pause")));
    }

    private void togglePause() {
        paused = !paused;
        anchor = Util.getMillis() - elapsed;
    }

    /** 重播从头连续播放：若保持暂停会停在场景尚未建立的空帧。 */
    private void replay() {
        paused = false;
        elapsed = 0;
        anchor = Util.getMillis();
    }

    private void seek(RuleSceneTimeline.Phase phase) {
        paused = true;
        elapsed = RuleSceneTimeline.phaseMidpoint(phase);
        anchor = Util.getMillis() - elapsed;
    }

    /** 内容区留出底排控件高度，卡片不压住 chip。 */
    private UiRect contentRect() {
        int top = WorkbenchLayout.CONTENT_TOP;
        int bottom = WorkbenchLayout.contentBottom(height) - WorkbenchLayout.CONTROL_HEIGHT - WorkbenchLayout.GAP;
        return new UiRect(WorkbenchLayout.INSET, top, WorkbenchLayout.contentWidth(width), Math.max(0, bottom - top));
    }

    /** 控件行贴内容区底缘：chip 左排，样例计数右对齐。 */
    private void layoutChips() {
        int y = WorkbenchLayout.contentBottom(height) - WorkbenchLayout.CONTROL_HEIGHT;
        int x = WorkbenchLayout.INSET;
        for (UiButton button : chips) {
            int chipWidth = chipWidth(button);
            button.setBounds(x, y, chipWidth, WorkbenchLayout.CONTROL_HEIGHT);
            x += chipWidth + CHIP_GAP;
        }
        int counterWidth = Math.max(0, width - WorkbenchLayout.INSET - x);
        counterRect = new UiRect(x, y, counterWidth, WorkbenchLayout.CONTROL_HEIGHT);
    }

    private int chipWidth(UiButton button) {
        if (button == pauseButton) {
            int pause = font.width(Component.translatable(RuleSceneModel.UI + "pause"));
            int resume = font.width(Component.translatable(RuleSceneModel.UI + "resume"));
            return Math.max(MIN_CHIP_WIDTH, Math.max(pause, resume) + CHIP_TEXT_PADDING);
        }
        return Math.max(MIN_CHIP_WIDTH, button.preferredWidth(CHIP_TEXT_PADDING / 2));
    }

    private void layoutCounter() {
        Component text = Component.translatable(RuleSceneModel.UI + "counter", index + 1, samples.size());
        Component hint = Component.translatable(RuleSceneModel.UI + "hint");
        int hintWidth = font.width(hint);
        int counterX = counterRect.right() - font.width(text);
        int hintX = counterX - HINT_GAP - hintWidth;
        boolean hintVisible = hintX > counterRect.x();
        hintRect = new UiRect(hintVisible ? hintX : counterRect.x(), counterRect.y(),
                hintVisible ? hintWidth : 0, counterRect.height());
    }

    /** 右下角显示「当前样例 / 总数」与操作提示，不占用内容区。 */
    private void drawCounter(GuiGraphics graphics) {
        Component text = Component.translatable(RuleSceneModel.UI + "counter", index + 1, samples.size());
        int y = counterRect.y() + (counterRect.height() - font.lineHeight) / 2;
        graphics.drawString(font, text, counterRect.right() - font.width(text), y, theme.surfaces().secondaryText(), false);
        if (hintRect.width() > 0) {
            graphics.drawString(font, Component.translatable(RuleSceneModel.UI + "hint"), hintRect.x(), y,
                    theme.surfaces().secondaryText(), false);
        }
    }

    /** 提示行只在右侧留白够宽时绘制，节点宽度随之收缩，探针据此判断是否可见。 */
    private UiLayoutNode hintNode() {
        Component text = Component.translatable(RuleSceneModel.UI + "hint");
        return new UiLayoutNode(UiLayoutNode.Kind.ROW, hintRect, text, 0, hintRect.width(),
                hintRect.width() > 0, true, List.of());
    }

    private UiLayoutNode counterNode() {
        Component text = Component.translatable(RuleSceneModel.UI + "counter", index + 1, samples.size());
        return new UiLayoutNode(UiLayoutNode.Kind.ROW, counterRect, text, 0, counterRect.width(), true, true, List.of());
    }
}
