package com.meteorite.itemdespawntowhat.client.debug.ruleflow;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * 输入面板：两件输入物品从左侧滑入并放大到位，槽位底在滑入过程中保持低透明。
 *
 * <p>动画只依赖 {@link RuleFlowTimeline.Frame#elapsed()}，暂停后同一帧重复绘制结果完全一致。</p>
 */
final class RuleFlowInputModule {
    /** 两件物品入场的错开时间。 */
    private static final long STAGGER_MILLIS = 120L;
    /** 单件物品的滑入时长。 */
    private static final long SLIDE_MILLIS = 360L;
    /** 滑入起点相对槽位中心的水平偏移。 */
    private static final float SLIDE_OFFSET = 14f;
    /** 偶数件物品时的槽间空隙。 */
    private static final int SLOT_GAP = 6;

    private RuleFlowInputModule() { }

    static void render(GuiGraphics graphics, Font font, UiRect panel, RuleFlowSpec spec,
                       RuleFlowTimeline.Frame frame, UiHostTheme theme) {
        if (spec.inputs().isEmpty()) return;
        UiRect content = RuleFlowView.inset(panel);
        int[] slots = RuleFlowView.centeredSlots(content, spec.inputs().size(), SLOT_GAP);
        int slotY = content.y() + Math.max(0, (content.height() - RuleFlowView.SLOT) / 2);
        for (int index = 0; index < spec.inputs().size(); index++) {
            ItemStack stack = spec.inputs().get(index);
            float progress = slideProgress(frame, index);
            RuleFlowView.drawSlot(graphics, theme,
                    new UiRect(slots[index], slotY, RuleFlowView.SLOT, RuleFlowView.SLOT), 0.35f + 0.65f * progress);
            if (progress <= 0f) continue;
            float scale = 1.75f * (0.6f + 0.4f * RuleFlowView.easeOutBack(progress));
            float offset = SLIDE_OFFSET * (1f - RuleFlowView.easeOutCubic(progress));
            UiRect target = new UiRect(Math.round(slots[index] - offset), slotY, RuleFlowView.SLOT, RuleFlowView.SLOT);
            RuleFlowView.drawItem(graphics, font, theme, stack, target, scale);
        }
    }

    /**
     * 单件物品的入场进度。
     *
     * <p>第二件物品晚 {@value #STAGGER_MILLIS} 毫秒起步，让「逐个入位」可见；
     * 跨阶段后保持 1，重播时从 0 重新开始。</p>
     */
    private static float slideProgress(RuleFlowTimeline.Frame frame, int index) {
        long start = index * STAGGER_MILLIS;
        if (frame.elapsed() <= start) return 0f;
        return RuleFlowView.clamp01((frame.elapsed() - start) / (float) SLIDE_MILLIS);
    }
}
