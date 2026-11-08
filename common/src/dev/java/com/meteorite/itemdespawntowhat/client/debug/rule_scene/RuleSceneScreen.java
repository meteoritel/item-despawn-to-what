package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

import com.meteorite.itemdespawntowhat.client.debug.kit.WorkbenchLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputRouter;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 规则三维场景页：一条样例规则一个舞台，左侧是 3D 场景与输入槽，右侧是触发条件与产出。
 *
 * <p>屏幕只负责样例切换、时间推进与控件；画面交给 {@link RuleSceneView}。时间轴是唯一动效来源，
 * 暂停后同一时刻的绘制结果一致，探针因此可以稳定截图。</p>
 */
public final class RuleSceneScreen extends Screen implements UiLayoutSource {

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

    private final Runnable back;
    private final UiHostTheme theme = UiHostThemes.defaultTheme();
    private final UiInputRouter input = new UiInputRouter();
    private final List<UiLayoutNode> nodes = new ArrayList<>();
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
        this.view = new RuleSceneView(samples.getFirst().model(), samples.getFirst().id());
    }

    /** 整页共用一个输入域：底排 chip、页脚返回都在其中，Esc 交回原版关闭。 */
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
                    chips.forEach(out);
                    out.accept(backButton);
                }
                @Override public UiPointerHit hitTest(double x, double y) {
                    return UiPointerHit.topmost(x, y, targets());
                }
                @Override public boolean mousePressed(UiInputContext context) {
                    for (UiButton button : targets()) {
                        if (button.mousePressed(context)) return true;
                    }
                    return false;
                }
            });
            scope.setEscape(this::onClose);
        }
        WorkbenchLayout.placeFooterBack(backButton, height);
        scope.refreshFocus();
        input.select(scope);
    }

    @Override public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        advance();
        nodes.clear();
        RuleSceneSamples.Sample sample = samples.get(index);
        renderBackground(graphics, mouseX, mouseY, partialTick);
        WorkbenchLayout.drawPage(graphics, font, theme, width, height, sample.title());
        WorkbenchLayout.drawSubtitle(graphics, font, theme, width, sample.subtitle());
        WorkbenchLayout.drawFooterRule(graphics, theme, width, height);
        UiRect content = contentRect();
        PreviewIcons.beginFrame();
        try {
            view.render(graphics, font, content, elapsed, theme);
        } finally {
            PreviewIcons.endFrame();
        }
        updatePauseLabel();
        layoutChips();
        drawCounter(graphics);
        var pointer = scope.pointer(mouseX, mouseY).resolve(scope.content());
        for (UiButton button : targets()) {
            button.render(graphics, font, pointer);
        }
        nodes.addAll(view.layoutNodes());
        nodes.add(counterNode());
        nodes.add(hintNode());
        for (UiButton button : targets()) {
            nodes.addAll(button.layoutNodes());
        }
    }

    /** 只读导出四个内容区块、样例计数与全部按钮。 */
    @Override public List<UiLayoutNode> layoutNodes() { return List.copyOf(nodes); }

    /**
     * 键盘：←/→ 切换样例，Space 暂停，R 重播，1..4 定位阶段。
     *
     * <p>阶段定位统一先暂停再 seek，截图不依赖按下后经过的时间。</p>
     */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> { switchSample(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { switchSample(1); return true; }
            case GLFW.GLFW_KEY_SPACE -> { togglePause(); return true; }
            case GLFW.GLFW_KEY_R -> { replay(); return true; }
            case GLFW.GLFW_KEY_1 -> { seek(RuleSceneTimeline.Phase.BUILD); return true; }
            case GLFW.GLFW_KEY_2 -> { seek(RuleSceneTimeline.Phase.ITEMS); return true; }
            case GLFW.GLFW_KEY_3 -> { seek(RuleSceneTimeline.Phase.TRIGGER); return true; }
            case GLFW.GLFW_KEY_4 -> { seek(RuleSceneTimeline.Phase.RESULT); return true; }
            default -> { }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return input.mousePressed(UiInputContext.of(scope, x, y, button, hasShiftDown(), hasControlDown(), hasAltDown()));
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return input.mouseReleased(UiInputContext.of(scope, x, y, button, hasShiftDown(), hasControlDown(), hasAltDown()));
    }

    /** 返回工作台目录；目录会重建自己的输入域，这里不再保留状态。 */
    @Override public void onClose() { back.run(); }

    @Override public void removed() {
        input.close();
        view.close();
        PreviewIcons.retainRecent();
    }

    /** 动画需要持续推进游戏 tick，不能按暂停界面处理。 */
    @Override public boolean isPauseScreen() { return false; }

    /** 推进时间轴；暂停时停在当前时刻。 */
    private void advance() {
        long now = Util.getMillis();
        if (paused) return;
        elapsed = Math.floorMod(now - anchor, RuleSceneTimeline.CYCLE_MILLIS);
    }

    /** 切换样例：换舞台、重置时间轴，保持当前暂停状态，逐条对比时不自行播放。 */
    private void switchSample(int step) {
        if (samples.size() <= 1) return;
        index = Math.floorMod(index + step, samples.size());
        view.close();
        RuleSceneSamples.Sample sample = samples.get(index);
        view = new RuleSceneView(sample.model(), sample.id());
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

    /** 右下角显示「当前样例 / 总数」与操作提示，不占用内容区。 */
    private void drawCounter(GuiGraphics graphics) {
        if (counterRect == null) return;
        Component text = Component.translatable(RuleSceneModel.UI + "counter", index + 1, samples.size());
        Component hint = Component.translatable(RuleSceneModel.UI + "hint");
        int hintWidth = font.width(hint);
        int y = counterRect.y() + (counterRect.height() - font.lineHeight) / 2;
        int counterX = counterRect.right() - font.width(text);
        graphics.drawString(font, text, counterX, y, theme.surfaces().secondaryText(), false);
        int hintX = counterX - HINT_GAP - hintWidth;
        boolean hintVisible = hintX > counterRect.x();
        hintRect = new UiRect(hintVisible ? hintX : counterRect.x(), counterRect.y(),
                hintVisible ? hintWidth : 0, counterRect.height());
        if (hintVisible) {
            graphics.drawString(font, hint, hintX, y, theme.surfaces().secondaryText(), false);
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
