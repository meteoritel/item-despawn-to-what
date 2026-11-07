package com.meteorite.itemdespawntowhat.client.debug.ruleflow;

import com.meteorite.itemdespawntowhat.client.debug.kit.WorkbenchLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 触发面板：点阵圆环表示充能进度，中心是 clock 槽，下方三行条件文案。
 *
 * <p>圆环用固定角步长的点阵而不是圆弧，低 guiScale 下也不会因为线条抗锯齿出现断点；
 * 充能只改变点的颜色，点位置不随进度变化，进度推进时圆环不会抖动。</p>
 */
final class RuleFlowTriggerModule {
    /** 圆环半径。 */
    private static final int RADIUS = 34;
    /** 爆发阶段圆环扩散到的半径。 */
    private static final int BURST_RADIUS = 52;
    /** 点阵角步长，60 个点刚好一圈。 */
    private static final float STEP_DEGREES = 6f;
    /** 每个点用 2×2 像素，避免 1px 点在高缩放下消失。 */
    private static final int DOT = 2;
    /** 中心 clock 槽边长。 */
    private static final int CLOCK_SLOT = 24;
    /** 圆环底部与首行文案之间的空隙。 */
    private static final int TEXT_GAP = 8;
    /** 三行文案的行距。 */
    private static final int TEXT_LINE_GAP = 1;

    private RuleFlowTriggerModule() { }

    static void render(GuiGraphics graphics, Font font, UiRect panel, RuleFlowSpec spec,
                       RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        UiRect content = RuleFlowView.inset(panel);
        if (content.width() <= 0 || content.height() <= 0) return;
        // 圆环中心直接取面板垂直中心：连接线与两侧物品都以面板中心为锚点，整块居中会让圆环偏高、线穿环。
        int centerX = content.x() + content.width() / 2;
        int centerY = content.y() + content.height() / 2;
        drawHighlight(graphics, panel, frame, theme);
        float charge = RuleFlowView.chargeProgress(frame);
        drawRing(graphics, theme, centerX, centerY, charge);
        drawBurstRing(graphics, theme, centerX, centerY, frame);
        drawClock(graphics, font, theme, centerX, centerY, frame);
        drawDetails(graphics, font, content, centerY + RADIUS + TEXT_GAP, spec, frame, theme);
    }

    /**
     * 点阵圆环：已充能的点用 accent，未充能用低透明连接线色。
     *
     * <p>爆发阶段在圆环外再叠一圈更大的扩散环，半径与透明度都随阶段进度变化，
     * 表现「能量释放」；扩散环不改变内圈，截图时仍能看到充能完成的圆环。</p>
     */
    private static void drawRing(GuiGraphics graphics, UiHostTheme theme, int centerX, int centerY, float charge) {
        var palette = theme.palette();
        int accent = palette.controls().accent();
        int idle = RuleFlowView.withAlpha(palette.canvas().canvasLink(), 0.35f);
        int points = Math.round(360f / STEP_DEGREES);
        for (int index = 0; index < points; index++) {
            float ratio = index / (float) points;
            int color = ratio < charge ? accent : idle;
            dot(graphics, centerX, centerY, RADIUS, index * STEP_DEGREES, color);
        }
    }

    private static void drawBurstRing(GuiGraphics graphics, UiHostTheme theme, int centerX, int centerY,
                                      RuleFlowTimeline.Frame frame) {
        if (frame.phase() != RuleFlowTimeline.Phase.BURST) return;
        float progress = frame.phaseProgress();
        int radius = Math.round(RADIUS + (BURST_RADIUS - RADIUS) * progress);
        int color = RuleFlowView.withAlpha(theme.palette().controls().accent(), 1f - progress);
        int points = Math.round(360f / STEP_DEGREES);
        for (int index = 0; index < points; index++) {
            dot(graphics, centerX, centerY, radius, index * STEP_DEGREES, color);
        }
        int flash = Math.round(6f * (1f - progress));
        if (flash > 0) {
            graphics.fill(centerX - flash, centerY - flash, centerX + flash, centerY + flash,
                    RuleFlowView.withAlpha(theme.palette().canvas().canvasAccent(), progress * 0.6f));
        }
    }

    private static void dot(GuiGraphics graphics, int centerX, int centerY, int radius, float degrees, int color) {
        double radians = Math.toRadians(degrees - 90f);
        int x = centerX + Math.round((float) Math.cos(radians) * radius) - DOT / 2;
        int y = centerY + Math.round((float) Math.sin(radians) * radius) - DOT / 2;
        graphics.fill(x, y, x + DOT, y + DOT, color);
    }

    /**
     * 中心 clock 槽：充能阶段做一次轻微缩放脉冲。
     *
     * <p>缩放幅度只有几个百分点，靠逐帧重复的同一 elapsed 驱动，暂停时不会继续呼吸。</p>
     */
    private static void drawClock(GuiGraphics graphics, Font font, UiHostTheme theme, int centerX, int centerY,
                                  RuleFlowTimeline.Frame frame) {
        UiRect slot = new UiRect(centerX - CLOCK_SLOT / 2, centerY - CLOCK_SLOT / 2, CLOCK_SLOT, CLOCK_SLOT);
        RuleFlowView.drawSlot(graphics, theme, slot, 1f);
        float pulse = frame.phase() == RuleFlowTimeline.Phase.CHARGE
                ? 1f + 0.06f * (float) Math.sin(frame.phaseProgress() * Math.PI * 2f)
                : 1f;
        // 16 像素的物品图标按 1.35 倍放大到 24 像素槽内，脉冲只在此基础上轻微呼吸。
        RuleFlowView.drawItem(graphics, font, theme, new ItemStack(Items.CLOCK), slot, 1.35f * pulse);
    }

    /**
     * 三行条件文案：延迟、条件、概率。
     *
     * <p>充能过半后才出现，与连接线的填充进度对齐；用开关而非渐隐，
     * 是为了在阶段中点定位时文案已经完整可截图。</p>
     */
    private static void drawDetails(GuiGraphics graphics, Font font, UiRect content, int top, RuleFlowSpec spec,
                                    RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        if (!detailsVisible(frame)) return;
        var palette = theme.palette();
        int lineTop = drawLine(graphics, font, content, top, spec.triggerDelay(), palette.surfaces().text());
        for (Component detail : spec.triggerDetails()) {
            lineTop = drawLine(graphics, font, content, lineTop, detail, palette.surfaces().secondaryText());
        }
    }

    private static int drawLine(GuiGraphics graphics, Font font, UiRect content, int top, Component text, int color) {
        if (top + font.lineHeight > content.bottom()) return content.bottom();
        Component fitted = WorkbenchLayout.ellipsize(font, text, content.width());
        int width = font.width(fitted);
        graphics.drawString(font, fitted, content.x() + Math.max(0, (content.width() - width) / 2), top, color, false);
        return top + font.lineHeight + TEXT_LINE_GAP;
    }

    private static boolean detailsVisible(RuleFlowTimeline.Frame frame) {
        return switch (frame.phase()) {
            case INPUT -> false;
            case CHARGE -> frame.phaseProgress() >= 0.5f;
            case BURST, RESULT, HOLD -> true;
        };
    }

    /** 爆发瞬间在触发面板顶部扫过一条高亮，提示能量从这一列释放。 */
    private static void drawHighlight(GuiGraphics graphics, UiRect panel, RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        if (frame.phase() != RuleFlowTimeline.Phase.BURST) return;
        float alpha = 1f - frame.phaseProgress();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.y() + 2,
                RuleFlowView.withAlpha(theme.palette().controls().accent(), alpha * 0.9f));
    }
}
