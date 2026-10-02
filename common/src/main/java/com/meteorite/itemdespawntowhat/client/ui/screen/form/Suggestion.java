package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import net.minecraft.network.chat.Component;

/**
 * 输入建议项：值写回字段，标签给玩家看。
 */
public record Suggestion(String value, Component label) {

    // 紧凑构造器：值不允许为空
    public Suggestion {
        if (value == null) {
            value = "";
        }
        if (label == null) {
            label = Component.literal(value);
        }
    }
}
