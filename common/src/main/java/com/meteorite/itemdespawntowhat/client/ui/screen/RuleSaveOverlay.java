package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostSurface;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
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
final class RuleSaveOverlay implements UiInputTarget {
    /** 只把实际按钮报告给作用域；遮罩仍按背景点击处理。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        return UiPointerHit.first(x, y, cancelButton, advancedButton);
    }

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
    private final UiOverlay overlay = new UiOverlay(this);
    private final UiButton advancedButton;
    private final UiButton cancelButton;
    private RuleSaveProgress.Stage lastStage;
    private boolean advanced;
    private long themeRevision = -1;
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
    }

    /** 保存操作的可用动作来自实际按钮。 */
    @Override public void addFocusTargets(Consumer<UiFocusTarget> out) {
        advancedButton.addFocusTargets(out);
        cancelButton.addFocusTargets(out);
    }

    private void requestCancel() {
        if (workspace.cancelSave()) {
            cancelButton.setEnabled(false);
        }
    }

    private void advancedButtonLabel() {
        advancedButton.setLabel(Component.translatable(UI + (advanced ? "advanced.hide" : "advanced.show")));
    }

    void render(GuiGraphics graphics, int screenWidth, int screenHeight, UiPointer pointer, long milliseconds) {
        RuleSaveProgress current = workspace.saving().progress();
        RuleSaveProgress progress = current == null
                ? new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING, 0, 0, "", false) : current;
        boolean cancelling = workspace.saving().cancelRequested() || progress.stage() == RuleSaveProgress.Stage.CANCELLING
                || progress.stage() == RuleSaveProgress.Stage.ROLLING_BACK;
        cancelButton.setEnabled(progress.cancellable() && !cancelling);
        cancelButton.setLabel(Component.translatable(UI + (cancelling ? "cancelling" : "cancel")));
        int width = panel.width();
        overlay.render(graphics, new UiRect(0, 0, screenWidth, screenHeight), 0, theme.surfaces().modalDim(),
                () -> renderContent(graphics, pointer, milliseconds, progress, cancelling, width, panel));
    }

    void layout(int screenWidth, int screenHeight) {
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
    }

    private void renderContent(GuiGraphics graphics, UiPointer pointer, long milliseconds, RuleSaveProgress progress,
                               boolean cancelling, int width, UiRect panel) {
        theme.drawSurface(graphics, panel, UiHostSurface.PANEL);
        graphics.enableScissor(panel.x(), panel.y(), panel.right(), panel.bottom());
        try {
            int left = panel.x() + PADDING;
            int innerWidth = Math.max(1, width - PADDING * 2);
            int y = panel.y() + PADDING;
            drawLine(graphics, Component.translatable(UI + "title"), left, y, innerWidth, true);
            y += font.lineHeight + ROW_GAP;
            drawLine(graphics, Component.translatable(UI + (cancelling ? "waiting_cancel"
                    : progress.cancellable() ? "keep_open" : "finishing")), left, y, innerWidth, false);
            y += font.lineHeight + ROW_GAP;
            double fraction = progress.total() == 0 ? 0.0 : progress.completed() / (double) progress.total();
            if (lastStage != progress.stage()) {
                bar.reset(fraction, milliseconds);
                lastStage = progress.stage();
            }
            bar.render(graphics, new UiRect(left, y, innerWidth, BAR_HEIGHT), fraction, progress.total() == 0, milliseconds);
            y += BAR_HEIGHT + ROW_GAP;
            Component count = progress.total() == 0 ? Component.translatable(UI + "unknown_count")
                    : Component.translatable(UI + "count", progress.completed(), progress.total());
            drawLine(graphics, count, left, y, innerWidth, false);
            y += font.lineHeight + ROW_GAP;
            advancedButton.render(graphics, font, pointer);
            y += RuleEditorPageSupport.DIALOG_BUTTON_H + ROW_GAP;
            if (advanced) {
                drawLine(graphics, Component.translatable(UI + "step", Component.translatable(RuleEditMessages.progressKey(progress.stage()))),
                        left, y, innerWidth, false);
                y += font.lineHeight + ROW_GAP;
                Component content = Component.translatable(UI + "current", progress.current().isEmpty()
                        ? Component.translatable(UI + "whole_batch") : Component.literal(progress.current()));
                for (var line : font.split(content, innerWidth).stream().limit(2).toList()) {
                    graphics.drawString(font, line, left, y, theme.palette().surfaces().secondaryText(), false);
                    y += font.lineHeight;
                }
            }
            cancelButton.render(graphics, font, pointer);
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int width, boolean primary) {
        graphics.drawString(font, TextScroll.trimToWidth(font, text.getString(), width), x, y,
                primary ? theme.palette().surfaces().text() : theme.palette().surfaces().secondaryText(), false);
    }

    UiInputScope inputScope() {
        return overlay.inputScope();
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (context.hits(advancedButton)) return advancedButton.mousePressed(context);
        return context.hits(cancelButton) && cancelButton.mousePressed(context);
    }
}
