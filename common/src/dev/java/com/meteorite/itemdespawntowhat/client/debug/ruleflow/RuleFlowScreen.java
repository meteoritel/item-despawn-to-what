package com.meteorite.itemdespawntowhat.client.debug.ruleflow;

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
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 规则流三阶段动画场景：输入滑入、触发充能与爆发、产出弹出，循环播放。
 *
 * <p>屏幕只负责时间推进、按钮与键盘，画面全部交给 {@link RuleFlowView} 与三个模块；
 * 时间轴是唯一动效来源，暂停后同一帧的绘制结果逐像素一致，探针因此可以稳定截图。</p>
 */
public final class RuleFlowScreen extends Screen implements UiLayoutSource {
    /** 顶部控件行中每个 chip 的最小宽度。 */
    private static final int MIN_CHIP_WIDTH = 48;
    /** chip 文案左右内边距之和。 */
    private static final int CHIP_TEXT_PADDING = 24;
    /** chip 之间的间距。 */
    private static final int CHIP_GAP = 6;
    /** 卡片高度上限：三列内容按此高度排版，窗口再高也不再拉伸。 */
    private static final int MAX_CARD_HEIGHT = 280;

    private final Runnable back;
    private final UiHostTheme theme = UiHostThemes.defaultTheme();
    private final UiInputRouter input = new UiInputRouter();
    private final List<UiLayoutNode> nodes = new ArrayList<>();
    private final RuleFlowTimeline timeline = new RuleFlowTimeline();
    private final RuleFlowSpec spec;
    private final List<UiButton> chips = new ArrayList<>();
    private UiButton pauseButton;
    private UiButton backButton;
    private UiInputScope scope;
    private UiRect hintRect;

    /** 示例数据固定为两件输入与两件产出，让三列都能展示错开入场。 */
    public RuleFlowScreen(Runnable back) {
        super(Component.translatable(RuleFlowSpec.UI));
        this.back = back;
        this.spec = new RuleFlowSpec(
                List.of(new ItemStack(Items.PAPER, 4), new ItemStack(Items.EMERALD, 2)),
                List.of(new ItemStack(Items.PRISMARINE_SHARD, 2), new ItemStack(Items.GOLD_NUGGET, 2)),
                Component.translatable(RuleFlowSpec.UI + ".input"),
                Component.translatable(RuleFlowSpec.UI + ".trigger"),
                Component.translatable(RuleFlowSpec.UI + ".result"),
                Component.translatable(RuleFlowSpec.UI + ".trigger.delay", 300 / 20),
                List.of(Component.translatable(RuleFlowSpec.UI + ".trigger.condition"),
                        Component.translatable(RuleFlowSpec.UI + ".trigger.chance", 100)));
    }

    /** 整页共用一个输入域：五个 chip 与页脚返回都在其中，Esc 交回原版关闭。 */
    @Override protected void init() {
        if (chips.isEmpty()) {
            pauseButton = chip(Component.translatable(RuleFlowSpec.UI + ".pause"), this::togglePause);
            chips.add(pauseButton);
            chips.add(chip(Component.translatable(RuleFlowSpec.UI + ".restart"), this::restart));
            chips.add(chip(spec.inputTitle(), () -> seek(RuleFlowTimeline.Phase.INPUT)));
            chips.add(chip(spec.triggerTitle(), () -> seek(RuleFlowTimeline.Phase.CHARGE)));
            chips.add(chip(spec.resultTitle(), () -> seek(RuleFlowTimeline.Phase.RESULT)));
            backButton = new UiButton(theme, font, Component.translatable(RuleFlowSpec.BACK_KEY),
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
        long now = Util.getMillis();
        timeline.update(now);
        nodes.clear();
        RuleFlowTimeline.Frame frame = timeline.frame();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        WorkbenchLayout.drawPage(graphics, font, theme, width, height, title);
        WorkbenchLayout.drawSubtitle(graphics, font, theme, width,
                Component.translatable(RuleFlowSpec.UI + ".description"));
        WorkbenchLayout.drawFooterRule(graphics, theme, width, height);
        UiRect card = cardRect();
        RuleFlowView.render(graphics, font, card, spec, frame, theme);
        updatePauseLabel();
        layoutChips();
        var pointer = scope.pointer(mouseX, mouseY).resolve(scope.content());
        for (UiButton button : targets()) {
            button.render(graphics, font, pointer);
        }
        drawHint(graphics);
        nodes.addAll(RuleFlowView.layoutNodes(card, spec));
        nodes.add(hintNode());
        for (UiButton button : targets()) {
            nodes.addAll(button.layoutNodes());
        }
    }

    /** 只读导出卡片、三列模块、提示行与全部按钮。 */
    @Override public List<UiLayoutNode> layoutNodes() { return List.copyOf(nodes); }

    /**
     * 键盘：Space 暂停/继续，R 从头重播，1/2/3 定位到三阶段中点。
     *
     * <p>阶段定位统一先暂停再 seek，截图不依赖按下后经过的时间。</p>
     */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        long now = Util.getMillis();
        switch (key) {
            case GLFW.GLFW_KEY_SPACE -> { togglePause(); return true; }
            case GLFW.GLFW_KEY_R -> { timeline.restart(now); timeline.setPaused(false, now); return true; }
            case GLFW.GLFW_KEY_1 -> { seek(RuleFlowTimeline.Phase.INPUT); return true; }
            case GLFW.GLFW_KEY_2 -> { seek(RuleFlowTimeline.Phase.CHARGE); return true; }
            case GLFW.GLFW_KEY_3 -> { seek(RuleFlowTimeline.Phase.RESULT); return true; }
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

    @Override public void removed() { input.close(); }

    /** 动画需要持续推进游戏 tick，不能按暂停界面处理。 */
    @Override public boolean isPauseScreen() { return false; }

    private UiButton chip(Component label, Runnable action) {
        return new UiButton(theme, font, label, UiButtonVariant.SECONDARY, action::run)
                .setChipStyle(true);
    }

    private List<UiButton> targets() {
        List<UiButton> buttons = new ArrayList<>(chips);
        buttons.add(backButton);
        return buttons;
    }

    /** 暂停按钮的文案跟随状态切换，宽度固定取较宽的一方，避免暂停时整行 chip 左右跳动。 */
    private void updatePauseLabel() {
        Component label = Component.translatable(RuleFlowSpec.UI + (timeline.paused() ? ".resume" : ".pause"));
        pauseButton.setLabel(label);
    }

    private void togglePause() {
        timeline.setPaused(!timeline.paused(), Util.getMillis());
    }

    /** 重播从头连续播放：若保持暂停会停在物品尚未入位的空帧，看不出重播效果。 */
    private void restart() {
        long now = Util.getMillis();
        timeline.restart(now);
        timeline.setPaused(false, now);
    }

    private void seek(RuleFlowTimeline.Phase phase) {
        long now = Util.getMillis();
        timeline.setPaused(true, now);
        timeline.seekToPhase(phase);
    }

    /** 卡片宽度占满内容区，高度封顶后在内容区垂直居中，避免三列面板里留下大片空白。 */
    private UiRect cardRect() {
        int top = WorkbenchLayout.CONTENT_TOP;
        int bottom = WorkbenchLayout.contentBottom(height) - WorkbenchLayout.CONTROL_HEIGHT - WorkbenchLayout.GAP;
        int available = Math.max(0, bottom - top);
        int cardHeight = Math.min(available, MAX_CARD_HEIGHT);
        return new UiRect(WorkbenchLayout.INSET, top + (available - cardHeight) / 2,
                WorkbenchLayout.contentWidth(width), cardHeight);
    }

    /**
     * 控件行与卡片底缘对齐：chip 左排，提示文字占满右剩空间。
     *
     * <p>先设 bounds 再绘制，UiInputScope 的命中测试依赖本帧矩形，否则点击会落到上一帧位置。</p>
     */
    private void layoutChips() {
        int y = WorkbenchLayout.contentBottom(height) - WorkbenchLayout.CONTROL_HEIGHT;
        int x = WorkbenchLayout.INSET;
        for (UiButton button : chips) {
            int width = chipWidth(button);
            button.setBounds(x, y, width, WorkbenchLayout.CONTROL_HEIGHT);
            x += width + CHIP_GAP;
        }
        int hintWidth = Math.max(0, width - WorkbenchLayout.INSET - x);
        hintRect = new UiRect(x, y, hintWidth, WorkbenchLayout.CONTROL_HEIGHT);
    }

    /** 暂停 chip 的宽度取「暂停/继续」中较宽的一方，切换文案时控件行不会左右抖动。 */
    private int chipWidth(UiButton button) {
        if (button == pauseButton) {
            int pause = font.width(Component.translatable(RuleFlowSpec.UI + ".pause"));
            int resume = font.width(Component.translatable(RuleFlowSpec.UI + ".resume"));
            return Math.max(MIN_CHIP_WIDTH, Math.max(pause, resume) + CHIP_TEXT_PADDING);
        }
        return Math.max(MIN_CHIP_WIDTH, button.preferredWidth(CHIP_TEXT_PADDING / 2));
    }

    /** 提示文字与 chip 同行右对齐；宽度不足时省略，避免压到左侧 chip。 */
    private void drawHint(GuiGraphics graphics) {
        if (hintRect == null || hintRect.width() <= 0) return;
        Component hint = WorkbenchLayout.ellipsize(font, Component.translatable(RuleFlowSpec.UI + ".hint"), hintRect.width());
        int y = hintRect.y() + Math.max(0, (hintRect.height() - font.lineHeight) / 2);
        graphics.drawString(font, hint, hintRect.right() - font.width(hint), y,
                theme.palette().surfaces().secondaryText(), false);
    }

    /** 提示文字与 chip 同行右对齐，所在整行导出一个 ROW 节点供探针定位。 */
    private UiLayoutNode hintNode() {
        Component hint = Component.translatable(RuleFlowSpec.UI + ".hint");
        int budget = hintRect == null ? 0 : hintRect.width();
        UiRect rect = hintRect == null ? new UiRect(0, 0, 0, 0) : hintRect;
        return new UiLayoutNode(UiLayoutNode.Kind.ROW, rect, hint, font.width(hint), budget, true, true, List.of());
    }
}
