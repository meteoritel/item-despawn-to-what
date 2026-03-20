package com.meteorite.itemdespawntowhat.ui.widget;

import com.meteorite.itemdespawntowhat.config.catalogue.PotionEffect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ArrowPotionEffectsWidget extends AbstractCompositeWidget{

    private static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.world_effect.";

    // ===== 本地化键 ===== //
    private static final String KEY_EFFECT_LABEL = LABEL_PREFIX + "effect_label";
    private static final String KEY_DURATION_LABEL = LABEL_PREFIX + "duration_label";
    private static final String KEY_AMPLIFIER_LABEL = LABEL_PREFIX + "amplifier_label";
    private static final String KEY_TIP = LABEL_PREFIX + "potion.tip";

    // ===== 默认值 ===== //
    private static final String DEFAULT_DURATION = "100";
    private static final String DEFAULT_AMPLIFIER = "0";

    // ===== 布局常量 ===== //
    private static final int TOTAL_WIDTH = 240;
    private static final int BOX_HEIGHT = 18;
    private static final int LABEL_HEIGHT = 9;
    private static final int TIP_HEIGHT = 9;
    private static final int H_GAP = 6;
    private static final int V_GAP = 3;

    // 列宽：effect_id 框最宽，其余两框等宽
    private static final int DURATION_WIDTH  = 60;
    private static final int AMPLIFIER_WIDTH = 50;
    private static final int EFFECT_WIDTH  = TOTAL_WIDTH - DURATION_WIDTH - AMPLIFIER_WIDTH - H_GAP * 2;

    private final Font font;
    private final LinkedBoxGroup linkedBoxGroup;

    public ArrowPotionEffectsWidget(Font font, int x, int y) {
        super(x, y, TOTAL_WIDTH, getTotalHeight(), Component.empty());
        this.font = font;

        // 从框 1：duration，默认 "100"
        EditBox durationBox = new EditBox(font, 0, 0, DURATION_WIDTH, BOX_HEIGHT, Component.empty());
        durationBox.setMaxLength(64);
        durationBox.setFilter(s -> s.matches("[\\d,]*"));

        // 从框 2：amplifier，默认 "0"
        EditBox amplifierBox = new EditBox(font, 0, 0, AMPLIFIER_WIDTH, BOX_HEIGHT, Component.empty());
        amplifierBox.setMaxLength(32);
        amplifierBox.setFilter(s -> s.matches("[\\d,]*"));

        this.linkedBoxGroup = LinkedBoxGroup
                .builder(font, EFFECT_WIDTH, 512)
                .follow(durationBox,  () -> DEFAULT_DURATION)
                .follow(amplifierBox, () -> DEFAULT_AMPLIFIER)
                .build();
    }

    public static int getTotalHeight() {
        return LABEL_HEIGHT + V_GAP
                + BOX_HEIGHT + V_GAP
                + TIP_HEIGHT;
    }

    // ========== 渲染 ========== //

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        // 第一行：列标签
        gfx.drawString(font, Component.translatable(KEY_EFFECT_LABEL),    x,                                               y, 0xAAAAAA, false);
        gfx.drawString(font, Component.translatable(KEY_DURATION_LABEL),  x + EFFECT_WIDTH + H_GAP,                        y, 0xAAAAAA, false);
        gfx.drawString(font, Component.translatable(KEY_AMPLIFIER_LABEL), x + EFFECT_WIDTH + H_GAP + DURATION_WIDTH + H_GAP, y, 0xAAAAAA, false);

        // 第二行：文本框
        int boxY = y + LABEL_HEIGHT + V_GAP;

        EditBox effectBox    = linkedBoxGroup.getPrimaryBox();
        EditBox durationBox  = linkedBoxGroup.getFollower(0);
        EditBox amplifierBox = linkedBoxGroup.getFollower(1);

        effectBox.setX(x);
        effectBox.setY(boxY);
        effectBox.render(gfx, mouseX, mouseY, partialTick);

        durationBox.setX(x + EFFECT_WIDTH + H_GAP);
        durationBox.setY(boxY);
        durationBox.render(gfx, mouseX, mouseY, partialTick);

        amplifierBox.setX(x + EFFECT_WIDTH + H_GAP + DURATION_WIDTH + H_GAP);
        amplifierBox.setY(boxY);
        amplifierBox.render(gfx, mouseX, mouseY, partialTick);

        // 第三行：提示文字
        gfx.drawString(font, Component.translatable(KEY_TIP), x, boxY + BOX_HEIGHT + V_GAP, 0xAAAAAA, false);
    }

    // ========== 事件路由 ========== //
    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return linkedBoxGroup.allBoxes();
    }

    // ========== 值绑定 ========== //
    public List<PotionEffect> getValue() {
        List<String> effectIds  = LinkedBoxGroup.splitTokens(linkedBoxGroup.getPrimaryBox().getValue());
        List<String> durations  = LinkedBoxGroup.splitValues(linkedBoxGroup.getFollower(0).getValue());
        List<String> amplifiers = LinkedBoxGroup.splitValues(linkedBoxGroup.getFollower(1).getValue());

        List<PotionEffect> result = new ArrayList<>();
        for (int i = 0; i < effectIds.size(); i++) {
            String effectId = effectIds.get(i);
            if (effectId.isBlank()) continue;

            int duration  = parseIntSafe(i < durations.size()  ? durations.get(i)  : null, 100);
            int amplifier = parseIntSafe(i < amplifiers.size() ? amplifiers.get(i) : null, 0);

            result.add(new PotionEffect(effectId, duration, amplifier));
        }
        return result;
    }

    public void setValue(@Nullable List<PotionEffect> entries) {
        if (entries == null || entries.isEmpty()) {
            clear();
            return;
        }

        StringBuilder effectSb    = new StringBuilder();
        StringBuilder durationSb  = new StringBuilder();
        StringBuilder amplifierSb = new StringBuilder();

        for (PotionEffect e : entries) {
            if (e == null || e.getEffectId() == null || e.getEffectId().isBlank()) continue;
            if (!effectSb.isEmpty()) {
                effectSb.append(",");
                durationSb.append(",");
                amplifierSb.append(",");
            }
            effectSb.append(e.getEffectId());
            durationSb.append(e.getDuration());
            amplifierSb.append(e.getAmplifier());
        }

        linkedBoxGroup.getPrimaryBox().setValue(effectSb.toString());
        linkedBoxGroup.getFollower(0).setValue(durationSb.toString());
        linkedBoxGroup.getFollower(1).setValue(amplifierSb.toString());
        // 手动对齐，确保初始状态一致
        linkedBoxGroup.syncAllFollowers();
    }

    public void clear() {
        linkedBoxGroup.clear();
    }

    // 返回 effect_id 主框
    public EditBox getEffectBox() {
        return linkedBoxGroup.getPrimaryBox();
    }

    // ========== 工具方法 ========== //
    private static int parseIntSafe(@Nullable String s, int def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
