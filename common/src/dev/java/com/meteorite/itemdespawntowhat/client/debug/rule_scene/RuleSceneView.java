package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

import com.meteorite.itemdespawntowhat.client.debug.kit.WorkbenchLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControl;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 规则场景页的内容排版与动效：舞台卡、输入槽条、触发卡与结果卡。
 *
 * <p>持有稳定的舞台入口与同一套舞台绘制；屏幕负责输入路由、覆盖面、计时和帧预算。</p>
 */
public final class RuleSceneView implements AutoCloseable {

    private static final int GAP = 8;
    private static final int ARROW_GAP = 22;
    private static final int LEFT_PERCENT = 58;
    private static final int INPUT_CARD_HEIGHT = 70;
    private static final int TRIGGER_CARD_HEIGHT = 68;
    private static final int CARD_TITLE_HEIGHT = 15;
    private static final int SLOT = 32;
    private static final int SLOT_LABEL_MIN = 30;
    private static final int SLOT_LABEL_MAX = 96;
    private static final int RESULT_ICON = 24;
    private static final int RESULT_ROW = 34;
    private static final int CHIP_HEIGHT = 17;
    private static final int CHIP_GAP = 4;
    private static final int CHIP_MAX_WIDTH = 210;

    private final RuleSceneModel model;
    private final RuleSceneStage stage;
    private final UiControl stageEntry = new UiControl();
    private final Runnable onOpen;
    private UiRect stageCard = new UiRect(0, 0, 0, 0);
    private UiRect stageBox = new UiRect(0, 0, 0, 0);
    private UiRect inputCard = new UiRect(0, 0, 0, 0);
    private UiRect triggerCard = new UiRect(0, 0, 0, 0);
    private UiRect resultCard = new UiRect(0, 0, 0, 0);

    /** 以样例 id 建立舞台，入口激活交给宿主打开当前时间的查看器。 */
    public RuleSceneView(RuleSceneModel model, String id, Runnable onOpen) {
        this.model = model;
        this.stage = new RuleSceneStage(model, id);
        this.onOpen = onOpen;
        stageEntry.setFocusable(true);
    }

    // 布局在输入前完成，舞台绘制、入口命中和快照共用同一可见矩形。
    void layout(Font font, UiRect content) {
        UiRect left = leftColumn(content);
        stageCard = new UiRect(left.x(), left.y(), left.width(), Math.max(0, left.height() - INPUT_CARD_HEIGHT - GAP));
        inputCard = new UiRect(left.x(), stageCard.bottom() + GAP, left.width(), INPUT_CARD_HEIGHT);
        UiRect right = rightColumn(content);
        triggerCard = new UiRect(right.x(), right.y(), right.width(), TRIGGER_CARD_HEIGHT);
        resultCard = new UiRect(right.x(), triggerCard.bottom() + GAP, right.width(), resultCardHeight());
        int stageX = stageCard.x() + 6;
        int stageY = stageCard.y() + CARD_TITLE_HEIGHT + 3;
        stageBox = new UiRect(stageX, stageY,
                Math.max(0, Math.min(stageCard.right() - 6, content.right()) - stageX),
                Math.max(0, Math.min(stageCard.bottom() - 6, content.bottom()) - stageY));
        stageEntry.setBounds(stageBox.x(), stageBox.y(), stageBox.width(), stageBox.height());
        stageEntry.setEnabled(stageBox.width() > 0 && stageBox.height() > 0);
        stageEntry.configure(font, Component.empty(), null,
                List.of(Component.translatable(RuleSceneModel.UI + "stage.open_hint")), onOpen::run);
        stageEntry.setAccessibleName(Component.translatable(RuleSceneModel.UI + "stage.open"));
    }

    /** 绘制已经准备的内容区，入口只补焦点轮廓，舞台画面保持完整。 */
    public void render(GuiGraphics graphics, Font font, long elapsed, UiHostTheme theme, UiPointer pointer) {
        drawCard(graphics, theme, stageCard);
        drawCardTitle(graphics, font, theme, stageCard, RuleSceneModel.UI + "stage");
        renderStage(graphics, font, stageBox, elapsed, theme);
        Component open = Component.translatable(RuleSceneModel.UI + "stage.open");
        graphics.drawString(font, open, stageCard.right() - 8 - font.width(open), stageCard.y() + 5,
                theme.palette().controls().accent(), false);
        if (stageEntry.isFocusVisible() || pointer.forTarget(stageEntry).contains(stageBox)) {
            UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND, () -> theme.drawFocusOutline(graphics, stageBox));
        }

        drawCard(graphics, theme, inputCard);
        drawInputs(graphics, font, theme, inputCard, elapsed);

        drawCard(graphics, theme, triggerCard);
        drawTrigger(graphics, font, theme, triggerCard, elapsed);

        drawCard(graphics, theme, resultCard);
        drawResults(graphics, font, theme, resultCard, elapsed);

        drawFlowArrows(graphics, theme, stageCard, triggerCard, resultCard, elapsed);
    }

    /** 当前布局保留四个卡片 Frame，并在舞台 Frame 中公开真实可聚焦入口。 */
    public List<UiLayoutNode> layoutNodes() {
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.FRAME, stageCard, Component.translatable(RuleSceneModel.UI + "stage"),
                        0, stageCard.width(), true, true, stageEntry.layoutNodes()),
                frame(inputCard, RuleSceneModel.UI + "inputs"), frame(triggerCard, RuleSceneModel.UI + "trigger"),
                frame(resultCard, RuleSceneModel.UI + "result"));
    }

    UiControl stageEntry() { return stageEntry; }
    void renderFrame(Runnable painter) { stage.renderFrame(painter); }

    // 捕获逻辑画布和时间；放大后仍使用同一舞台所有者，三维工作区按实际像素重新绘制。
    UiIcon snapshot(Font font, long elapsed, UiHostTheme theme) {
        UiRect canvas = new UiRect(0, 0, stageBox.width(), stageBox.height());
        return new UiIcon.Themed(canvas.width(), canvas.height(), (graphics, target, source) -> {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(target.x(), target.y(), 0);
                graphics.pose().scale((float) target.width() / canvas.width(), (float) target.height() / canvas.height(), 1);
                renderStage(graphics, font, canvas, elapsed, theme);
            } finally {
                graphics.pose().popPose();
            }
        }, () -> { });
    }

    private void renderStage(GuiGraphics graphics, Font font, UiRect box, long elapsed, UiHostTheme theme) {
        if (box.width() == 0 || box.height() == 0) return;
        stage.render(graphics, box, elapsed, theme);
        UiRenderLayers.enableScissor(graphics, box);
        try {
            UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND, () -> drawConditions(graphics, font, theme, box, elapsed));
        } finally {
            UiRenderLayers.disableScissor(graphics);
        }
    }

    /** 关闭本视图拥有的舞台资源。 */
    @Override
    public void close() {
        stageEntry.endInteraction(UiInteractionSession.EndReason.UNMOUNTED);
        stage.close();
    }

    // ---- 卡片骨架 ----

    /** 结果卡只按条目数撑高，剩余页面留白，避免出现大块空面板。 */
    private int resultCardHeight() {
        int rows = Math.max(1, model.effects().size());
        return CARD_TITLE_HEIGHT + 6 + rows * RESULT_ROW + 6;
    }

    private static UiRect leftColumn(UiRect content) {
        int width = Math.max(260, content.width() * LEFT_PERCENT / 100);
        return new UiRect(content.x(), content.y(), width, content.height());
    }

    private static UiRect rightColumn(UiRect content) {
        UiRect left = leftColumn(content);
        int x = left.right() + ARROW_GAP;
        return new UiRect(x, content.y(), Math.max(120, content.right() - x), content.height());
    }

    private static void drawCard(GuiGraphics graphics, UiHostTheme theme, UiRect rect) {
        theme.drawSurface(graphics, rect, UiSurfaceRole.CARD);
    }

    private static void drawCardTitle(GuiGraphics graphics, Font font, UiHostTheme theme, UiRect card, String key) {
        Component title = Component.translatable(key);
        graphics.drawString(font, title, card.x() + 8, card.y() + 5, theme.surfaces().secondaryText(), false);
        theme.drawDivider(graphics, card.x() + 6, card.y() + CARD_TITLE_HEIGHT, card.width() - 12);
    }

    // ---- 条件 chips ----

    // 条件以胶囊标签叠在舞台底部：每行最多两个，放不下时汇总为 +N
    private void drawConditions(GuiGraphics graphics, Font font, UiHostTheme theme, UiRect box, long elapsed) {
        List<RuleSceneModel.Condition> conditions = model.conditions();
        if (conditions.isEmpty()) return;
        float progress = RuleSceneTimeline.progress(elapsed, RuleSceneTimeline.Phase.BUILD);
        boolean built = RuleSceneTimeline.phaseOf(elapsed) != RuleSceneTimeline.Phase.BUILD;
        int visible = built ? conditions.size() : (int) (progress * (conditions.size() + 0.99F));
        int x = box.x() + 8;
        int y = box.bottom() - 8 - CHIP_HEIGHT;
        int row = 0;
        int drawn = 0;
        for (int index = 0; index < conditions.size() && drawn < visible; index++) {
            RuleSceneModel.Condition condition = conditions.get(index);
            Component label = RuleSceneModel.conditionLabel(condition);
            Component detail = RuleSceneModel.conditionDetail(condition);
            Component text = detail.getString().isBlank() ? label : Component.literal(label.getString() + " · " + detail.getString());
            int width = Math.min(CHIP_MAX_WIDTH, font.width(text) + 12);
            if (x + width > box.right() - 8) {
                if (row >= 1) {
                    drawChip(graphics, font, new UiRect(x, y, Math.min(52, box.right() - 8 - x), CHIP_HEIGHT),
                            Component.literal("+" + (conditions.size() - drawn)), theme.palette().controls().accent());
                    break;
                }
                row++;
                x = box.x() + 8;
                y -= CHIP_HEIGHT + CHIP_GAP;
            }
            drawChip(graphics, font, new UiRect(x, y, width, CHIP_HEIGHT), text, accentOf(condition, theme));
            x += width + CHIP_GAP;
            drawn++;
        }
    }

    private static int accentOf(RuleSceneModel.Condition condition, UiHostTheme theme) {
        if (condition instanceof RuleSceneModel.FluidPresent) return theme.palette().canvas().canvasLink();
        if (condition instanceof RuleSceneModel.Weather) return theme.palette().canvas().canvasLinkSelected();
        if (condition instanceof RuleSceneModel.TimeOfDay) return theme.palette().status().warning();
        if (condition instanceof RuleSceneModel.LightLevel) return theme.palette().status().warning();
        if (condition instanceof RuleSceneModel.Biome) return theme.palette().status().success();
        if (condition instanceof RuleSceneModel.Dimension) return theme.palette().canvas().canvasAccent();
        return theme.palette().controls().accent();
    }

    private static void drawChip(GuiGraphics graphics, Font font, UiRect rect, Component text, int accent) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xC8121418);
        graphics.fill(rect.x(), rect.y(), rect.x() + 2, rect.bottom(), accent);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), 0x55FFFFFF);
        graphics.drawString(font, WorkbenchLayout.ellipsize(font, text, rect.width() - 8),
                rect.x() + 6, rect.y() + (rect.height() - font.lineHeight) / 2, 0xFFF2F2F2, false);
    }

    // ---- 输入槽 ----

    // 输入与催化剂同排展示；数量角标常驻，消耗项底部加警示色条
    private void drawInputs(GuiGraphics graphics, Font font, UiHostTheme theme, UiRect card, long elapsed) {
        drawCardTitle(graphics, font, theme, card, RuleSceneModel.UI + "inputs");
        float progress = RuleSceneTimeline.progress(elapsed, RuleSceneTimeline.Phase.ITEMS);
        boolean started = RuleSceneTimeline.phaseOf(elapsed) != RuleSceneTimeline.Phase.BUILD;
        int x = card.x() + 8;
        int y = card.y() + CARD_TITLE_HEIGHT + 5;
        List<RuleSceneModel.ItemSlot> slots = new ArrayList<>(model.inputs());
        slots.addAll(model.catalysts());
        int labelWidth = labelWidth(font, slots);
        int pitch = SLOT + labelWidth + 6;
        for (int index = 0; index < slots.size(); index++) {
            RuleSceneModel.ItemSlot slot = slots.get(index);
            boolean visible = started || progress * (slots.size() + 0.99F) > index;
            if (!visible) { x += pitch; continue; }
            if (x + SLOT > card.right() - 6) break;
            drawSlot(graphics, font, theme, new UiRect(x, y, SLOT, SLOT), slot);
            Component name = WorkbenchLayout.ellipsize(font, slot.name(), labelWidth);
            graphics.drawString(font, name, x, y + SLOT + 2, theme.surfaces().secondaryText(), false);
            x += pitch;
        }
    }

    // 标签宽度按最宽名称取，避免短名留白与长名过度截断
    private static int labelWidth(Font font, List<RuleSceneModel.ItemSlot> slots) {
        int widest = 0;
        for (RuleSceneModel.ItemSlot slot : slots) {
            widest = Math.max(widest, font.width(slot.name()));
        }
        return Math.clamp(widest, SLOT_LABEL_MIN, SLOT_LABEL_MAX);
    }

    private static void drawSlot(GuiGraphics graphics, Font font, UiHostTheme theme, UiRect rect,
                                 RuleSceneModel.ItemSlot slot) {
        theme.drawSurface(graphics, rect, UiSurfaceRole.INSET);
        if (!slot.stack().isEmpty()) {
            drawScaledItem(graphics, slot.stack(), rect);
        }
        if (slot.consumed()) {
            graphics.fill(rect.x(), rect.bottom() - 2, rect.right(), rect.bottom(), theme.palette().status().warning());
        }
        String count = "×" + slot.count();
        graphics.pose().pushPose();
        graphics.pose().translate(rect.right() - 1, rect.bottom() - 8, UiRenderLayers.FOREGROUND);
        graphics.pose().scale(0.8F, 0.8F, 1.0F);
        int width = font.width(count);
        graphics.drawString(font, count, -width, 0, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private static void drawScaledItem(GuiGraphics graphics, ItemStack stack, UiRect box) {
        float scale = 1.5F;
        graphics.pose().pushPose();
        try {
            float size = 16.0F * scale;
            graphics.pose().translate(box.x() + (box.width() - size) / 2.0F, box.y() + (box.height() - size) / 2.0F,
                    UiRenderLayers.CONTENT);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    // ---- 触发 ----

    private void drawTrigger(GuiGraphics graphics, Font font, UiHostTheme theme, UiRect card, long elapsed) {
        drawCardTitle(graphics, font, theme, card, RuleSceneModel.UI + "trigger");
        int x = card.x() + 8;
        int y = card.y() + CARD_TITLE_HEIGHT + 10;
        RuleSceneTimeline.Phase phase = RuleSceneTimeline.phaseOf(elapsed);
        boolean active = phase == RuleSceneTimeline.Phase.TRIGGER;
        int accent = active ? theme.palette().controls().accent() : theme.surfaces().text();
        Component delay = Component.translatable(RuleSceneModel.UI + "trigger.delay", model.delaySeconds());
        graphics.drawString(font, delay, x, y, accent, false);
        Component chance = Component.translatable(RuleSceneModel.UI + "trigger.chance", model.chancePercent());
        graphics.drawString(font, chance, card.right() - 8 - font.width(chance), y,
                theme.surfaces().secondaryText(), false);
        int chipY = y + font.lineHeight + 6;
        if (model.triggers().isEmpty()) {
            drawTextChip(graphics, font, theme, x, chipY, Component.translatable(RuleSceneModel.UI + "trigger.none"),
                    theme.palette().status().statusTextMuted(), false);
        } else {
            int chipX = x;
            for (String trigger : model.triggers()) {
                Component label = Component.translatable(RuleSceneModel.UI + "trigger." + trigger);
                chipX = drawTextChip(graphics, font, theme, chipX, chipY, label,
                        "natural".equals(trigger) ? theme.palette().controls().accent() : theme.palette().status().warning(),
                        active);
            }
        }
    }

    private static int drawTextChip(GuiGraphics graphics, Font font, UiHostTheme theme, int x, int y,
                                    Component text, int accent, boolean highlight) {
        int width = font.width(text) + 10;
        int fill = highlight ? theme.palette().controls().controlSelected() : theme.surfaces().panelFill();
        graphics.fill(x, y, x + width, y + 15, fill);
        graphics.fill(x, y + 14, x + width, y + 15, accent);
        graphics.drawString(font, text, x + 5, y + 4, theme.surfaces().text(), false);
        return x + width + 4;
    }

    // ---- 结果 ----

    private void drawResults(GuiGraphics graphics, Font font, UiHostTheme theme, UiRect card, long elapsed) {
        drawCardTitle(graphics, font, theme, card, RuleSceneModel.UI + "result");
        List<RuleSceneModel.EffectSlot> effects = model.effects();
        if (effects.isEmpty()) {
            graphics.drawString(font, Component.translatable(RuleSceneModel.UI + "result.none"),
                    card.x() + 8, card.y() + CARD_TITLE_HEIGHT + 8, theme.surfaces().secondaryText(), false);
            return;
        }
        RuleSceneTimeline.Phase phase = RuleSceneTimeline.phaseOf(elapsed);
        float progress = RuleSceneTimeline.progress(elapsed, RuleSceneTimeline.Phase.RESULT);
        boolean revealed = phase == RuleSceneTimeline.Phase.HOLD
                || (phase == RuleSceneTimeline.Phase.RESULT && progress > 0.0F);
        int rows = effects.size();
        int listTop = card.y() + CARD_TITLE_HEIGHT + 6;
        int x = card.x() + 8;
        for (int index = 0; index < rows; index++) {
            RuleSceneModel.EffectSlot effect = effects.get(index);
            int rowTop = listTop + index * RESULT_ROW;
            if (rowTop + RESULT_ROW > card.bottom() - 4) break;
            boolean shown = revealed && (phase == RuleSceneTimeline.Phase.HOLD
                    || progress * (rows + 0.99F) > index);
            int textColor = shown ? theme.surfaces().text() : theme.surfaces().secondaryText();
            UiIcon icon = effect.icon();
            if (icon != null) icon.render(graphics, x, rowTop + (RESULT_ROW - RESULT_ICON) / 2, theme);
            else graphics.drawString(font, "?", x + 6, rowTop + 10, textColor, false);
            Component name = WorkbenchLayout.ellipsize(font, effect.name(), card.width() - 82);
            graphics.drawString(font, name, x + RESULT_ICON + 8, rowTop + 10, textColor, false);
            if (shown) {
                String count = "×" + effect.count();
                graphics.drawString(font, count, card.right() - 8 - font.width(count), rowTop + 10,
                        theme.palette().status().success(), false);
            }
        }
    }

    // ---- 场景到结果的箭头 ----

    // 水平箭头从舞台指向触发卡；触发完成后竖直箭头落到结果卡
    private static void drawFlowArrows(GuiGraphics graphics, UiHostTheme theme, UiRect stageCard,
                                       UiRect triggerCard, UiRect resultCard, long elapsed) {
        RuleSceneTimeline.Phase phase = RuleSceneTimeline.phaseOf(elapsed);
        int y = triggerCard.y() + triggerCard.height() / 2;
        int start = stageCard.right() + 2;
        int end = triggerCard.x() - 2;
        int color = phase == RuleSceneTimeline.Phase.TRIGGER ? theme.palette().controls().accent()
                : theme.palette().controls().chromeDivider();
        graphics.fill(start, y - 1, end - 4, y + 1, color);
        for (int step = 0; step < 5; step++) {
            graphics.fill(end - 5 + step, y - 5 + step, end - 4 + step, y - 4 + step, color);
            graphics.fill(end - 5 + step, y + 4 - step, end - 4 + step, y + 5 - step, color);
        }
        if (phase == RuleSceneTimeline.Phase.TRIGGER) {
            float progress = RuleSceneTimeline.progress(elapsed, phase);
            int pulse = start + (int) ((end - start) * progress);
            graphics.fill(pulse - 2, y - 2, pulse + 2, y + 2, 0xFFFFFFFF);
        }
        int downColor = phase == RuleSceneTimeline.Phase.RESULT || phase == RuleSceneTimeline.Phase.HOLD
                ? theme.palette().status().success() : theme.palette().controls().chromeDivider();
        int centerX = triggerCard.x() + triggerCard.width() / 2;
        int top = triggerCard.bottom() + 1;
        int bottom = resultCard.y() - 1;
        graphics.fill(centerX - 1, top, centerX + 1, bottom - 4, downColor);
        for (int step = 0; step < 4; step++) {
            graphics.fill(centerX - 4 + step, bottom - 5 + step, centerX - 3 + step, bottom - 4 + step, downColor);
            graphics.fill(centerX + 3 - step, bottom - 5 + step, centerX + 4 - step, bottom - 4 + step, downColor);
        }
    }

    private static UiLayoutNode frame(UiRect rect, String text) {
        return new UiLayoutNode(UiLayoutNode.Kind.FRAME, rect, Component.translatable(text), 0,
                rect.width(), true, true, List.of());
    }
}
