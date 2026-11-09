package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiProgressBar;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiOverlay;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditMessages;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveProgress;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 保存模态的宿主适配；取消只发送信号，终态回执到达前不提供关闭入口。
 */
final class RuleSaveOverlay implements UiInputContainer, UiLayoutSource {
    private static final String UI = TypeLabels.UI_PREFIX + "save.";
    private static final int PANEL_WIDTH = 340;
    private static final int COLLAPSED_HEIGHT = 128;
    private static final int EXPANDED_HEIGHT = 192;
    private static final int MARGIN = 8;
    private static final int PADDING = 12;
    private static final int BAR_HEIGHT = 12;
    private static final int ROW_GAP = 5;
    private final Font font;
    private final UiHostTheme theme;
    private final RuleEditClientWorkspace workspace;
    private final UiProgressBar bar;
    private final UiChildren<UiButton> buttons = new UiChildren<>();
    private final UiOverlay overlay = new UiOverlay(this);
    private final UiButton advancedButton;
    private final UiButton cancelButton;
    private RuleSaveProgress.Stage lastStage;
    private boolean advanced;
    private long themeRevision = -1;
    private RuleSaveProgress progress = new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING, 0, 0, "", false);
    private UiRect screen = new UiRect(0, 0, 0, 0);
    private UiRect barBounds = new UiRect(0, 0, 0, 0);
    private final List<SaveLine> lines = new ArrayList<>();
    private final UiLayer layer = new UiLayer(overlay.inputScope(), this::render, this);
    private record SaveLine(Component text, UiRect bounds, boolean primary) { }

    UiLayer layer() { return layer; }

    private UiRect panel = new UiRect(0, 0, 0, 0);

    RuleSaveOverlay(Font font, UiHostTheme theme, RuleEditClientWorkspace workspace) {
        this.font = font;
        this.theme = theme;
        this.workspace = workspace;
        var colors = theme.palette();
        bar = new UiProgressBar(new UiProgressBar.Style(colors.controls().controlFill(), colors.controls().accent(),
                colors.controls().controlBevelDark(), colors.controls().accentHover()));
        advancedButton = new UiButton(theme, font, Component.translatable(UI + "advanced.show"),
                UiButtonVariant.SECONDARY, () -> {
            advanced = !advanced;
            advancedButtonLabel();
        });
        cancelButton = new UiButton(theme, font, Component.translatable(UI + "cancel"), UiButtonVariant.SECONDARY,
                this::requestCancel);
        buttons.add(advancedButton);
        buttons.add(cancelButton);
    }

    /** 同一按钮结构派生命中、焦点与普通输入。 */
    @Override public UiChildren<UiButton> inputChildren() { return buttons; }

    private void requestCancel() {
        if (workspace.cancelSave()) {
            cancelButton.setEnabled(false);
        }
    }

    private void advancedButtonLabel() {
        advancedButton.setLabel(Component.translatable(UI + (advanced ? "advanced.hide" : "advanced.show")));
    }

    private void render(GuiGraphics graphics, Font renderFont, UiPointer pointer, int depth) {
        overlay.render(graphics, screen, depth, theme.surfaces().modalDim(),
                () -> renderContent(graphics, pointer, Util.getMillis()));
    }

    void layout(int screenWidth, int screenHeight) {
        screen = new UiRect(0, 0, screenWidth, screenHeight);
        RuleSaveProgress current = workspace.saving().progress();
        progress = current == null
                ? new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING, 0, 0, "", false) : current;
        boolean cancelling = workspace.saving().cancelRequested() || progress.stage() == RuleSaveProgress.Stage.CANCELLING
                || progress.stage() == RuleSaveProgress.Stage.ROLLING_BACK;
        cancelButton.setEnabled(progress.cancellable() && !cancelling);
        cancelButton.setLabel(Component.translatable(UI + (cancelling ? "cancelling" : "cancel")));
        if (themeRevision != theme.revision()) {
            var colors = theme.palette().controls();
            bar.setStyle(new UiProgressBar.Style(colors.controlFill(), colors.accent(),
                    colors.controlBevelDark(), colors.accentHover()));
            themeRevision = theme.revision();
        }
        int width = Math.clamp(screenWidth - MARGIN * 2, 1, PANEL_WIDTH);
        int height = Math.clamp(screenHeight - MARGIN * 2, 1, advanced ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT);
        panel = new UiRect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        int innerWidth = Math.max(1, width - PADDING * 2);
        int buttonY = panel.y() + PADDING + (font.lineHeight + ROW_GAP) * 3 + BAR_HEIGHT + ROW_GAP;
        advancedButton.setBounds(panel.x() + PADDING, buttonY, innerWidth, RuleEditorPageSupport.DIALOG_BUTTON_H);
        cancelButton.setBounds(panel.x() + PADDING, panel.bottom() - PADDING - RuleEditorPageSupport.DIALOG_BUTTON_H,
                innerWidth, RuleEditorPageSupport.DIALOG_BUTTON_H);
        lines.clear();
        int left = panel.x() + PADDING;
        int y = panel.y() + PADDING;
        addLine(Component.translatable(UI + "title"), left, y, innerWidth, true);
        y += font.lineHeight + ROW_GAP;
        addLine(Component.translatable(UI + (cancelling ? "waiting_cancel"
                : progress.cancellable() ? "keep_open" : "finishing")), left, y, innerWidth, false);
        y += font.lineHeight + ROW_GAP;
        barBounds = new UiRect(left, y, innerWidth, BAR_HEIGHT);
        y += BAR_HEIGHT + ROW_GAP;
        addLine(progress.total() == 0 ? Component.translatable(UI + "unknown_count")
                : Component.translatable(UI + "count", progress.completed(), progress.total()), left, y, innerWidth, false);
        y = advancedButton.bounds().bottom() + ROW_GAP;
        if (advanced) {
            addLine(Component.translatable(UI + "step", Component.translatable(RuleEditMessages.progressKey(progress.stage()))),
                    left, y, innerWidth, false);
            y += font.lineHeight + ROW_GAP;
            Component content = Component.translatable(UI + "current", progress.current().isEmpty()
                    ? Component.translatable(UI + "whole_batch") : Component.literal(progress.current()));
            for (var line : font.split(content, innerWidth).stream().limit(2).toList()) {
                StringBuilder text = new StringBuilder();
                line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
                addLine(Component.literal(text.toString()), left, y, innerWidth, false);
                y += font.lineHeight;
            }
        }
    }

    private void addLine(Component text, int x, int y, int width, boolean primary) {
        lines.add(new SaveLine(text, new UiRect(x, y, width, font.lineHeight), primary));
    }

    private void renderContent(GuiGraphics graphics, UiPointer pointer, long milliseconds) {
        theme.drawSurface(graphics, panel, UiSurfaceRole.PANEL);
        graphics.enableScissor(panel.x(), panel.y(), panel.right(), panel.bottom());
        try {
            for (SaveLine line : lines) {
                drawLine(graphics, line.text(), line.bounds().x(), line.bounds().y(), line.bounds().width(), line.primary());
            }
            double fraction = progress.total() == 0 ? 0.0 : progress.completed() / (double) progress.total();
            if (lastStage != progress.stage()) {
                bar.reset(fraction, milliseconds);
                lastStage = progress.stage();
            }
            bar.render(graphics, barBounds, fraction, progress.total() == 0, milliseconds);
            advancedButton.render(graphics, font, pointer);
            cancelButton.render(graphics, font, pointer);
        } finally {
            graphics.disableScissor();
        }
    }

    /** 保存面板与文字、进度条和真实按钮共享已准备的几何。 */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>();
        for (SaveLine line : lines) {
            nodes.add(new UiLayoutNode(UiLayoutNode.Kind.ROW, line.bounds(), line.text(), font.width(line.text()),
                    line.bounds().width(), true, true, List.of()));
        }
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, barBounds, Component.empty(), 0, barBounds.width(),
                true, true, List.of()));
        nodes.addAll(advancedButton.layoutNodes());
        nodes.addAll(cancelButton.layoutNodes());
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.MODAL, panel, Component.translatable(UI + "title"),
                0, panel.width(), true, true, List.copyOf(nodes)));
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int width, boolean primary) {
        graphics.drawString(font, TextScroll.trimToWidth(font, text.getString(), width), x, y,
                primary ? theme.palette().surfaces().text() : theme.palette().surfaces().secondaryText(), false);
    }

}
