package com.meteorite.itemdespawntowhat.client.debug.ruleflow;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * 结果面板：两件产物错开弹出，弹出瞬间槽外有一圈收缩光晕；保持阶段做轻微浮动。
 *
 * <p>弹出先越过目标尺寸再回落，让「合成完成」有明确的视觉落点；
 * 光晕只在弹出早期存在，避免和保持阶段的浮动互相干扰。</p>
 */
final class RuleFlowResultModule {
    /** 两件产物弹出的错开时间。 */
    private static final long STAGGER_MILLIS = 140L;
    /** 单件产物从无到出现的时长。 */
    private static final long POP_MILLIS = 320L;
    /** 弹出历程中越过目标尺寸的时刻。 */
    private static final float OVERSHOOT_AT = 0.55f;
    /** 越过时的目标倍数 1.08 与回落后的 1.0 之间插值。 */
    private static final float OVERSHOOT_SCALE = 1.08f;
    /** 产物的基准倍率，与输入面板一致。 */
    private static final float ITEM_SCALE = 1.75f;
    /** 保持阶段的浮动幅度（像素）。 */
    private static final float FLOAT_PIXELS = 1f;
    /** 偶数件产物时的槽间空隙。 */
    private static final int SLOT_GAP = 6;

    private RuleFlowResultModule() { }

    static void render(GuiGraphics graphics, Font font, UiRect panel, RuleFlowSpec spec,
                       RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        if (spec.results().isEmpty()) return;
        UiRect content = RuleFlowView.inset(panel);
        int[] slots = RuleFlowView.centeredSlots(content, spec.results().size(), SLOT_GAP);
        int slotY = content.y() + Math.max(0, (content.height() - RuleFlowView.SLOT) / 2);
        for (int index = 0; index < spec.results().size(); index++) {
            ItemStack stack = spec.results().get(index);
            float progress = popProgress(frame, index);
            if (progress <= 0f) continue;
            int offset = floatOffset(frame, index);
            UiRect slot = new UiRect(slots[index], slotY + offset, RuleFlowView.SLOT, RuleFlowView.SLOT);
            RuleFlowView.drawGlow(graphics, theme, slot, glowStrength(frame, index));
            RuleFlowView.drawSlot(graphics, theme, slot, RuleFlowView.clamp01(progress * 2f));
            RuleFlowView.drawItem(graphics, font, theme, stack, slot, ITEM_SCALE * popScale(progress));
        }
    }

    /** 弹出进度：RESULT 阶段按错开时间推进，之后保持 1。 */
    private static float popProgress(RuleFlowTimeline.Frame frame, int index) {
        if (frame.phase() == RuleFlowTimeline.Phase.INPUT || frame.phase() == RuleFlowTimeline.Phase.CHARGE
                || frame.phase() == RuleFlowTimeline.Phase.BURST) {
            return 0f;
        }
        if (frame.phase() == RuleFlowTimeline.Phase.HOLD) return 1f;
        long start = RuleFlowTimeline.BURST_END + index * STAGGER_MILLIS;
        if (frame.elapsed() <= start) return 0f;
        return RuleFlowView.clamp01((frame.elapsed() - start) / (float) POP_MILLIS);
    }

    /** 0.2 起步，越过 1.08 后回落到 1.0；回弹段用两个分段保证峰值固定在 {@value #OVERSHOOT_AT}。 */
    private static float popScale(float progress) {
        if (progress < OVERSHOOT_AT) {
            float local = progress / OVERSHOOT_AT;
            return 0.2f + (OVERSHOOT_SCALE - 0.2f) * RuleFlowView.easeOutCubic(local);
        }
        float local = (progress - OVERSHOOT_AT) / (1f - OVERSHOOT_AT);
        return OVERSHOOT_SCALE - (OVERSHOOT_SCALE - 1f) * RuleFlowView.easeOutCubic(local);
    }

    /** 光晕强度只在 RESULT 阶段有效，随弹出结束收缩到 0。 */
    private static float glowStrength(RuleFlowTimeline.Frame frame, int index) {
        if (frame.phase() != RuleFlowTimeline.Phase.RESULT) return 0f;
        return 1f - popProgress(frame, index);
    }

    /** 保持阶段的浮动：两件产物反相位，振幅只有 1 像素，不遮挡相邻槽位。 */
    private static int floatOffset(RuleFlowTimeline.Frame frame, int index) {
        if (frame.phase() != RuleFlowTimeline.Phase.HOLD) return 0;
        double phase = (frame.elapsed() - RuleFlowTimeline.RESULT_END) / 600.0 + index * Math.PI;
        return Math.round(FLOAT_PIXELS * (float) Math.sin(phase));
    }
}
