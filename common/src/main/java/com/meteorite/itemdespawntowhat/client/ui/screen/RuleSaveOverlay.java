package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiProgressBar;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveProgress;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 保存模态的宿主适配；取消只发送信号，终态回执到达前不提供关闭入口。 */
final class RuleSaveOverlay {
    private static final String UI = "gui.itemdespawntowhat.edit.save.";
    private static final int PANEL_WIDTH = 340;
    private static final int COLLAPSED_HEIGHT = 128;
    private static final int EXPANDED_HEIGHT = 192;
    private static final int MARGIN = 8;
    private static final int PADDING = 12;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BAR_HEIGHT = 12;
    private static final int ROW_GAP = 5;
    private final Font font;
    private final UiHostTheme theme;
    private final EditorWorkspaceView workspace;
    private final UiProgressBar bar;
    private final UiFocusManager focus = new UiFocusManager();
    private final UiButton advancedButton;
    private final UiButton cancelButton;
    private @Nullable UiButton pressed;
    private RuleSaveProgress.Stage lastStage;
    private boolean advanced;

    RuleSaveOverlay(Font font, UiHostTheme theme, EditorWorkspaceView workspace) {
        this.font = font;
        this.theme = theme;
        this.workspace = workspace;
        var colors = theme.palette();
        bar = new UiProgressBar(new UiProgressBar.Style(colors.controlFill(), colors.accent(),
                colors.controlBevelDark(), colors.accentHover()));
        advancedButton = new UiButton(theme, font, Component.translatable(UI + "advanced.show"),
                UiButtonVariant.SECONDARY, () -> {
                    advanced = !advanced;
                    advancedButtonLabel();
                });
        cancelButton = new UiButton(theme, font, Component.translatable(UI + "cancel"), UiButtonVariant.SECONDARY,
                this::requestCancel);
        focus.beginUpdate();
        focus.add(advancedButton);
        focus.add(cancelButton);
        focus.endUpdate();
    }

    private void requestCancel() {
        if (workspace.cancelSave()) { cancelButton.setEnabled(false); }
    }

    private void advancedButtonLabel() {
        advancedButton.setLabel(Component.translatable(UI + (advanced ? "advanced.hide" : "advanced.show")));
    }

    void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY, long milliseconds) {
        RuleSaveProgress progress = workspace.saveProgress();
        if (progress == null) { progress = new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING, 0, 0, "", false); }
        boolean cancelling = workspace.cancelRequested() || progress.stage() == RuleSaveProgress.Stage.CANCELLING
                || progress.stage() == RuleSaveProgress.Stage.ROLLING_BACK;
        cancelButton.setEnabled(progress.cancellable() && !cancelling);
        cancelButton.setLabel(Component.translatable(UI + (cancelling ? "cancelling" : "cancel")));
        int width = Math.clamp(screenWidth - MARGIN * 2, 1, PANEL_WIDTH);
        int height = Math.clamp(screenHeight - MARGIN * 2, 1, advanced ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT);
        UiRect panel = new UiRect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        graphics.fill(0, 0, screenWidth, screenHeight, theme.palette().modalDim());
        theme.drawPanel(graphics, panel);
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
            if (lastStage != progress.stage()) { bar.reset(fraction, milliseconds); lastStage = progress.stage(); }
            bar.render(graphics, new UiRect(left, y, innerWidth, BAR_HEIGHT), fraction, progress.total() == 0, milliseconds);
            y += BAR_HEIGHT + ROW_GAP;
            Component count = progress.total() == 0 ? Component.translatable(UI + "unknown_count")
                    : Component.translatable(UI + "count", progress.completed(), progress.total());
            drawLine(graphics, count, left, y, innerWidth, false);
            y += font.lineHeight + ROW_GAP;
            advancedButton.setBounds(left, y, innerWidth, BUTTON_HEIGHT);
            advancedButton.render(graphics, font, mouseX, mouseY);
            y += BUTTON_HEIGHT + ROW_GAP;
            if (advanced) {
                drawLine(graphics, Component.translatable(UI + "step", Component.translatable(progress.stage().messageKey())),
                        left, y, innerWidth, false);
                y += font.lineHeight + ROW_GAP;
                Component content = Component.translatable(UI + "current", progress.current().isEmpty()
                        ? Component.translatable(UI + "whole_batch") : Component.literal(progress.current()));
                for (var line : font.split(content, innerWidth).stream().limit(2).toList()) {
                    graphics.drawString(font, line, left, y, theme.palette().textSecondary(), false);
                    y += font.lineHeight;
                }
            }
            cancelButton.setBounds(left, panel.bottom() - PADDING - BUTTON_HEIGHT, innerWidth, BUTTON_HEIGHT);
            cancelButton.render(graphics, font, mouseX, mouseY);
        } finally { graphics.disableScissor(); }
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int width, boolean primary) {
        graphics.drawString(font, TextScroll.trimToWidth(font, text.getString(), width), x, y,
                primary ? theme.palette().textPrimary() : theme.palette().textSecondary(), false);
    }

    void mouseClicked(double x, double y, int button) {
        pressed = null;
        if (advancedButton.mouseClicked(x, y, button)) { pressed = advancedButton; }
        else if (cancelButton.mouseClicked(x, y, button)) { pressed = cancelButton; }
    }

    void mouseReleased(double x, double y, int button) {
        UiButton owner = pressed;
        pressed = null;
        if (owner != null) { owner.mouseReleased(x, y, button); }
    }

    void keyPressed(int key, int scan, int modifiers) { focus.keyPressed(key, scan, modifiers); }
}
