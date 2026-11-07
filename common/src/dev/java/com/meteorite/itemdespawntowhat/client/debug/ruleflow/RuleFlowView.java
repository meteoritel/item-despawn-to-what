package com.meteorite.itemdespawntowhat.client.debug.ruleflow;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 规则卡片合成器：卡片外框、三列几何、连接线与底部周期进度条都在本类统一计算并绘制，
 * 三个模块只在自己的面板矩形内画内容。
 *
 * <p>几何只算一次，绘制与 {@link #layoutNodes} 因此必然一致；模块拿到的只有面板矩形与
 * 时间轴帧，不读屏幕状态，暂停后同一个 elapsed 画出同一张图。</p>
 */
final class RuleFlowView {
    /** 卡片内边距。 */
    static final int CARD_PADDING = 12;
    /** 三列之间的间距，同时是连接线的可用长度。 */
    static final int COLUMN_GAP = 16;
    /** 面板内边距，模块内容画在这个范围内。 */
    static final int PANEL_PADDING = 8;
    /** 物品槽边长。 */
    static final int SLOT = 32;
    /** 列标题与面板之间的间距。 */
    static final int LABEL_GAP = 4;
    /** 面板顶部语义色细带的厚度。 */
    private static final int ACCENT_STRIP = 4;
    /** 卡片底缘周期进度条高度。 */
    static final int PROGRESS_HEIGHT = 4;
    /** 进度条分段之间的空隙。 */
    static final int PROGRESS_GAP = 2;
    /** 连接线粗细。 */
    static final int LINK_THICKNESS = 2;
    /** 输入与结果列的宽度占比，余量留给触发列。 */
    private static final float SIDE_SHARE = 0.32f;
    /** HOLD 阶段流动亮块走完两段连接线所需的毫秒数。 */
    private static final long FLOW_MILLIS = 900L;
    /** 卡片顶部信息行与列标题之间的距离。 */
    private static final int TOP_ROW_GAP = 8;
    /** 骨架行高：契约里的 layoutNodes 不带 Font，用原版固定行高保证节点矩形与绘制一致。 */
    private static final int LINE_HEIGHT = 9;
    /** easeOutBack 的回弹系数。 */
    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C3 = BACK_C1 + 1f;
    /** 每段周期在进度条上的结束时刻。 */
    private static final long[] SEGMENT_ENDS = { RuleFlowTimeline.INPUT_END, RuleFlowTimeline.CHARGE_END,
            RuleFlowTimeline.BURST_END, RuleFlowTimeline.RESULT_END, RuleFlowTimeline.CYCLE_MILLIS };

    /**
     * 一帧内三列面板的绝对矩形。
     *
     * <p>列宽按占比切分后把余量全部给触发列：圆环与三行条件文案需要比两侧槽位更宽的横向空间。</p>
     */
    private record Columns(UiRect input, UiRect trigger, UiRect result) { }

    private RuleFlowView() { }

    /** 绘制整张卡片：外框与信息行、连接线、三列面板与模块内容，最后是底缘进度条。 */
    static void render(GuiGraphics graphics, Font font, UiRect card, RuleFlowSpec spec,
                       RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        Columns columns = columns(card, font.lineHeight);
        drawCard(graphics, font, card, spec, frame, theme);
        drawLinks(graphics, columns, frame, theme);
        drawColumn(graphics, font, columns.input(), spec.inputTitle(), theme.palette().controls().accent(), theme);
        drawColumn(graphics, font, columns.trigger(), spec.triggerTitle(), theme.palette().status().warning(), theme);
        drawColumn(graphics, font, columns.result(), spec.resultTitle(), theme.palette().status().success(), theme);
        drawProgress(graphics, card, frame, theme);
        RuleFlowInputModule.render(graphics, font, columns.input(), spec, frame, theme);
        RuleFlowTriggerModule.render(graphics, font, columns.trigger(), spec, frame, theme);
        RuleFlowResultModule.render(graphics, font, columns.result(), spec, frame, theme);
    }

    /**
     * 导出卡片与三列面板的排版快照。
     *
     * <p>顶层 FRAME 用页面标题 key，子节点分别用三列标题 key，探针据此按文案定位整张卡片与
     * 每一列的矩形；按钮与提示由屏幕追加，不在这里重复。</p>
     */
    static List<UiLayoutNode> layoutNodes(UiRect card, RuleFlowSpec spec) {
        Columns columns = columns(card, LINE_HEIGHT);
        UiLayoutNode input = frameNode(columns.input(), spec.inputTitle());
        UiLayoutNode trigger = frameNode(columns.trigger(), spec.triggerTitle());
        UiLayoutNode result = frameNode(columns.result(), spec.resultTitle());
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.FRAME, card, Component.translatable(RuleFlowSpec.UI),
                0, 0, true, true, List.of(input, trigger, result)));
    }

    /** 当前阶段映射到的列标题：触发阶段包含充能与其后的爆发。 */
    static Component stageLabel(RuleFlowTimeline.Phase phase, RuleFlowSpec spec) {
        return switch (phase) {
            case INPUT -> spec.inputTitle();
            case CHARGE, BURST -> spec.triggerTitle();
            case RESULT, HOLD -> spec.resultTitle();
        };
    }

    /**
     * 连接线充能进度：INPUT 为 0，CHARGE 随阶段推进，之后保持充满。
     *
     * <p>连接线与触发列共用同一个进度，触发文案的出现时机因此不会与线填充脱节。</p>
     */
    static float chargeProgress(RuleFlowTimeline.Frame frame) {
        return switch (frame.phase()) {
            case INPUT -> 0f;
            case CHARGE -> frame.phaseProgress();
            case BURST, RESULT, HOLD -> 1f;
        };
    }

    private static Columns columns(UiRect card, int lineHeight) {
        int innerX = card.x() + CARD_PADDING;
        int innerWidth = Math.max(0, card.width() - CARD_PADDING * 2);
        int labelTop = card.y() + CARD_PADDING + lineHeight + TOP_ROW_GAP;
        int panelTop = labelTop + lineHeight + LABEL_GAP;
        int barTop = card.bottom() - CARD_PADDING - PROGRESS_HEIGHT;
        int panelHeight = Math.max(0, barTop - PANEL_PADDING - panelTop);
        int usable = Math.max(0, innerWidth - COLUMN_GAP * 2);
        int sideWidth = Math.max(0, Math.round(usable * SIDE_SHARE));
        int triggerWidth = Math.max(0, usable - sideWidth * 2);
        UiRect input = new UiRect(innerX, panelTop, sideWidth, panelHeight);
        UiRect trigger = new UiRect(input.right() + COLUMN_GAP, panelTop, triggerWidth, panelHeight);
        UiRect result = new UiRect(trigger.right() + COLUMN_GAP, panelTop, sideWidth, panelHeight);
        return new Columns(input, trigger, result);
    }

    private static void drawCard(GuiGraphics graphics, Font font, UiRect card, RuleFlowSpec spec,
                                 RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        var palette = theme.palette();
        // 卡片比页面更亮、三列面板沉回页面底色，用明度差代替额外边框来分层。
        theme.drawSurface(graphics, card, palette.canvas().canvasCardFill(), palette.canvas().canvasCardBorder());
        theme.drawBevel(graphics, card, palette.controls().controlBevelLight(), palette.controls().controlBevelDark());
        Component cue = Component.translatable(RuleFlowSpec.UI + ".cue");
        graphics.drawString(font, cue, card.x() + CARD_PADDING, card.y() + CARD_PADDING,
                palette.surfaces().secondaryText(), false);
        Component stage = stageLabel(frame.phase(), spec);
        graphics.drawString(font, stage, card.right() - CARD_PADDING - font.width(stage), card.y() + CARD_PADDING,
                palette.controls().accent(), false);
    }

    /** 列标题与面板：标题用本列语义色，面板顶部再压一条同色细带，三列在灰底上因此可一眼区分。 */
    private static void drawColumn(GuiGraphics graphics, Font font, UiRect panel, Component title, int accent,
                                   UiHostTheme theme) {
        var palette = theme.palette();
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panel.x() + Math.max(0, (panel.width() - titleWidth) / 2),
                panel.y() - LABEL_GAP - font.lineHeight, accent, false);
        theme.drawSurface(graphics, panel, palette.surfaces().panelFill(), palette.surfaces().panelBorder());
        theme.drawBevel(graphics, panel, palette.controls().controlBevelLight(), palette.controls().controlBevelDark());
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.y() + ACCENT_STRIP, accent);
    }

    /**
     * 两段连接线：底线始终可见，充能部分用流动色覆盖；HOLD 阶段再叠一个沿路径循环的亮块。
     *
     * <p>填充长度按两段总长乘以充能进度计算，因此线会先走完输入段再进入结果段。</p>
     */
    private static void drawLinks(GuiGraphics graphics, Columns columns, RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        var canvas = theme.palette().canvas();
        int y = columns.input().y() + columns.input().height() / 2 - LINK_THICKNESS / 2;
        int firstLength = Math.max(0, columns.trigger().x() - columns.input().right());
        int secondLength = Math.max(0, columns.result().x() - columns.trigger().right());
        fill(graphics, columns.input().right(), y, firstLength, withAlpha(canvas.canvasLink(), 0.3f));
        fill(graphics, columns.trigger().right(), y, secondLength, withAlpha(canvas.canvasLink(), 0.3f));
        int total = firstLength + secondLength;
        float charge = chargeProgress(frame);
        if (charge > 0f && total > 0) {
            int filled = Math.round(total * charge);
            fill(graphics, columns.input().right(), y, Math.min(firstLength, filled), withAlpha(canvas.canvasFlowLink(), 1f));
            if (filled > firstLength) {
                fill(graphics, columns.trigger().right(), y, Math.min(secondLength, filled - firstLength), withAlpha(canvas.canvasFlowLink(), 1f));
            }
        }
        if (frame.phase() != RuleFlowTimeline.Phase.HOLD || total <= 0) return;
        float flow = ((frame.elapsed() - RuleFlowTimeline.RESULT_END) % FLOW_MILLIS) / (float) FLOW_MILLIS;
        int position = Math.min(total - 1, Math.round(total * flow));
        int x = position < firstLength ? columns.input().right() + position : columns.trigger().right() + position - firstLength;
        graphics.fill(x, y - 2, x + 2, y + LINK_THICKNESS + 2, withAlpha(canvas.canvasAccent(), 1f));
    }

    /** 底缘周期进度条：按阶段时长切四段，段间留缝，已走过的部分用满透明度覆盖。 */
    private static void drawProgress(GuiGraphics graphics, UiRect card, RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        var palette = theme.palette();
        // 保持段用中性连接线色：它是循环末尾的停顿，不需要与四个动作段抢注意力。
        int[] colors = { palette.controls().accent(), palette.status().warning(),
                palette.canvas().canvasAccent(), palette.status().success(), palette.canvas().canvasLink() };
        int width = Math.max(0, card.width() - CARD_PADDING * 2);
        int top = card.bottom() - CARD_PADDING - PROGRESS_HEIGHT;
        int cursor = card.x() + CARD_PADDING;
        long start = 0L;
        for (int index = 0; index < SEGMENT_ENDS.length; index++) {
            long end = SEGMENT_ENDS[index];
            long duration = end - start;
            int span = Math.round(width * duration / (float) RuleFlowTimeline.CYCLE_MILLIS);
            int drawn = Math.max(0, index == SEGMENT_ENDS.length - 1 ? span : span - PROGRESS_GAP);
            if (drawn > 0) {
                graphics.fill(cursor, top, cursor + drawn, top + PROGRESS_HEIGHT, withAlpha(colors[index], 0.45f));
                float covered = Math.clamp((frame.elapsed() - start) / (float) duration, 0f, 1f);
                int coveredWidth = Math.round(drawn * covered);
                if (coveredWidth > 0) {
                    graphics.fill(cursor, top, cursor + coveredWidth, top + PROGRESS_HEIGHT, colors[index]);
                }
            }
            cursor += span;
            start = end;
        }
    }

    private static UiLayoutNode frameNode(UiRect rect, Component text) {
        return new UiLayoutNode(UiLayoutNode.Kind.FRAME, rect, text, 0, 0, true, true, List.of());
    }

    /** 面板内缩 {@value #PANEL_PADDING} 像素，负尺寸钳到零，避免小窗口下构造非法矩形。 */
    static UiRect inset(UiRect rect) {
        return new UiRect(rect.x() + PANEL_PADDING, rect.y() + PANEL_PADDING,
                Math.max(0, rect.width() - PANEL_PADDING * 2), Math.max(0, rect.height() - PANEL_PADDING * 2));
    }

    /** 在内容区内横向居中排一列槽位，返回每个槽位的左边。 */
    static int[] centeredSlots(UiRect content, int count, int gap) {
        int[] slots = new int[count];
        int total = count * SLOT + Math.max(0, count - 1) * gap;
        int start = content.x() + Math.max(0, (content.width() - total) / 2);
        for (int index = 0; index < count; index++) {
            slots[index] = start + index * (SLOT + gap);
        }
        return slots;
    }

    /** 槽位底与立体边；透明度用于入场渐入与弹出。 */
    static void drawSlot(GuiGraphics graphics, UiHostTheme theme, UiRect slot, float alpha) {
        if (slot.width() <= 0 || slot.height() <= 0) return;
        var palette = theme.palette();
        theme.drawSurface(graphics, slot, withAlpha(palette.surfaces().inputFill(), alpha),
                withAlpha(palette.surfaces().inputBorder(), alpha));
        theme.drawBevel(graphics, slot, withAlpha(palette.controls().controlBevelLight(), alpha),
                withAlpha(palette.controls().controlBevelDark(), alpha));
    }

    /**
     * 以槽位中心为原点按比例绘制物品，数量大于 1 时在右下角补数量角标。
     *
     * <p>物品渲染自带 16 倍缩放与 z=150 偏移，这里先把坐标系平移到槽位再缩放，
     * 让图标始终以槽位中心对齐；角标单独抬到物品之上，避免被图标压住。</p>
     */
    static void drawItem(GuiGraphics graphics, Font font, UiHostTheme theme, ItemStack stack, UiRect slot, float scale) {
        if (stack.isEmpty() || scale <= 0.01f || slot.width() <= 0 || slot.height() <= 0) return;
        float size = 16f * scale;
        float x = slot.x() + (slot.width() - size) / 2f;
        float y = slot.y() + (slot.height() - size) / 2f;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        pose.scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
        if (stack.getCount() <= 1) return;
        String count = Integer.toString(stack.getCount());
        pose.pushPose();
        pose.translate(0f, 0f, 200f);
        graphics.drawString(font, count, slot.right() - 1 - font.width(count), slot.bottom() - font.lineHeight,
                theme.palette().controls().textOnDark(), true);
        pose.popPose();
    }

    /** 产出槽外圈的收缩光晕：强度越大越贴近槽位，颜色随强度一起淡出。 */
    static void drawGlow(GuiGraphics graphics, UiHostTheme theme, UiRect slot, float strength) {
        float alpha = clamp01(strength);
        if (alpha <= 0f) return;
        int expand = Math.max(1, Math.round(10f * alpha));
        int color = withAlpha(theme.palette().status().success(), alpha * 0.8f);
        int left = slot.x() - expand;
        int top = slot.y() - expand;
        int right = slot.right() + expand;
        int bottom = slot.bottom() + expand;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    private static void fill(GuiGraphics graphics, int left, int top, int width, int color) {
        if (width <= 0) return;
        graphics.fill(left, top, left + width, top + LINK_THICKNESS, color);
    }

    /** 只调透明度，保留主题给出的 RGB。 */
    static int withAlpha(int color, float alpha) {
        int value = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
        return (value << 24) | (color & 0x00FFFFFF);
    }

    static float clamp01(float value) { return Math.clamp(value, 0f, 1f); }

    static float easeOutCubic(float value) {
        float inverse = 1f - clamp01(value);
        return 1f - inverse * inverse * inverse;
    }

    /** 回弹曲线：入场末尾轻微越过终点再回落，用于物品入位与产出弹出。 */
    static float easeOutBack(float value) {
        float offset = clamp01(value) - 1f;
        return 1f + BACK_C3 * offset * offset * offset + BACK_C1 * offset * offset;
    }
}
