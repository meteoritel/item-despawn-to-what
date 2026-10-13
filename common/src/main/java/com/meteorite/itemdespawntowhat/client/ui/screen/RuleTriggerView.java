package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;

/** 配方箭头的触发信息快照：上方为自然触发秒数，下方为非自然触发图标。 */
final class RuleTriggerView {
    static final int HEIGHT = 28;
    private static final int ICON_SIZE = 8;
    private static final int ICON_GAP = 2;
    private static final int SIDE_PADDING = 2;
    private static final int TIME_HEIGHT = 7;
    private static final int ARROW_HEIGHT = 10;
    private static final float TIME_SCALE = 0.65F;
    private static final String UI = "gui.itemdespawntowhat.edit.";
    private static final ResourceLocation FIRE_TEXTURE = ResourceLocation.withDefaultNamespace("block/fire_0");
    private static final ResourceLocation LAVA_TEXTURE = ResourceLocation.withDefaultNamespace("block/lava_still");
    private static final UiIcon FIRE_ICON = textureIcon(FIRE_TEXTURE);
    private static final UiIcon LAVA_ICON = textureIcon(LAVA_TEXTURE);
    private static final UiIcon CACTUS_ICON = new UiIcon.Item(new ItemStack(Items.CACTUS));
    private static final UiIcon UNKNOWN_ICON = new UiIcon.Item(new ItemStack(Items.BARRIER));
    private final @Nullable String seconds;
    private final List<UiIcon> icons = new ArrayList<>();
    private final Component tooltip;

    RuleTriggerView(@Nullable RuleValue rule) {
        List<String> triggers = RuleTriggers.effective(rule);
        seconds = triggers.contains(RuleTriggers.NATURAL) ? Integer.toString(rule == null
                ? RuleCodecs.DEFAULT_TRIGGER_AFTER_SECONDS
                : rule.integer(RuleFields.TRIGGER_AFTER_SECONDS, RuleCodecs.DEFAULT_TRIGGER_AFTER_SECONDS)) : null;
        var details = Component.translatable(UI + "recipe.trigger_methods");
        // 缺省自然触发、显式空集合和未知触发不能都被画成同一种空白状态。
        if (triggers.isEmpty()) {
            icons.add(UNKNOWN_ICON);
            details.append("\n").append(Component.translatable(UI + "trigger.note"));
        }
        for (String trigger : triggers) {
            Component label;
            switch (trigger) {
                case RuleTriggers.NATURAL -> label = Component.translatable(UI + "trigger.natural");
                case RuleTriggers.FIRE -> { icons.add(FIRE_ICON); label = Component.translatable(UI + "trigger.fire"); }
                case RuleTriggers.LAVA -> { icons.add(LAVA_ICON); label = Component.translatable(UI + "trigger.lava"); }
                case RuleTriggers.CACTUS -> { icons.add(CACTUS_ICON); label = Component.translatable(UI + "trigger.cactus"); }
                default -> {
                    icons.add(UNKNOWN_ICON);
                    label = Component.translatable(UI + "recipe.trigger_unknown", trigger);
                }
            }
            details.append("\n").append(label);
        }
        if (seconds != null) details.append("\n").append(Component.translatable(UI + "recipe.trigger_seconds", seconds));
        tooltip = details;
    }

    Component tooltip() { return tooltip; }

    int width(Font font, Component arrow) {
        int timeWidth = seconds == null ? 0 : (int) Math.ceil(font.width(seconds) * TIME_SCALE);
        int iconWidth = icons.isEmpty() ? 0 : icons.size() * ICON_SIZE + (icons.size() - 1) * ICON_GAP;
        return Math.max(font.width(arrow), Math.max(timeWidth, iconWidth)) + SIDE_PADDING * 2;
    }

    void render(GuiGraphics graphics, Font font, UiRect area, Component arrow, int color, boolean active) {
        if (seconds != null) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(area.x() + (area.width() - font.width(seconds) * TIME_SCALE) / 2F, area.y(), 0);
                graphics.pose().scale(TIME_SCALE, TIME_SCALE, 1);
                graphics.drawString(font, seconds, 0, 0, color, false);
            } finally { graphics.pose().popPose(); }
        }
        graphics.drawString(font, arrow, area.x() + (area.width() - font.width(arrow)) / 2,
                area.y() + TIME_HEIGHT + (ARROW_HEIGHT - font.lineHeight) / 2, color, false);
        int iconWidth = icons.size() * ICON_SIZE + Math.max(0, icons.size() - 1) * ICON_GAP;
        int x = area.x() + (area.width() - iconWidth) / 2;
        for (UiIcon icon : icons) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, area.y() + TIME_HEIGHT + ARROW_HEIGHT, 0);
                graphics.pose().scale((float) ICON_SIZE / icon.width(), (float) ICON_SIZE / icon.height(), 1);
                RulePreviewIcons.render(graphics, icon, 0, 0, active);
            } finally { graphics.pose().popPose(); }
            x += ICON_SIZE + ICON_GAP;
        }
    }

    private static UiIcon textureIcon(ResourceLocation texture) {
        // 每次绘制从当前图集取 sprite，保留原版动画与资源重载后的材质。
        return new UiIcon.Rendered(ICON_SIZE, ICON_SIZE, (graphics, box) -> {
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                graphics.blit(box.x(), box.y(), 0, box.width(), box.height(),
                        Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture), 1, 1, 1, 1);
            } finally { graphics.flush(); RenderSystem.disableBlend(); }
        });
    }
}
