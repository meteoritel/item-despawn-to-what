package com.meteorite.itemdespawntowhat.client.ui.presentation;

import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.consumption.ConsumptionDirective;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 将消耗指令转换为 tooltip 行。
 */
public final class ConsumptionPresenter {
    private ConsumptionPresenter() {
    }

    public static Component summary(ConsumptionDirective directive) {
        if (directive == null || directive.isEmpty()) {
            return Component.empty();
        }
        MutableComponent result = Component.translatable("gui.itemdespawntowhat.tooltip.consumption");
        for (CatalystItems.CatalystEntry entry : directive.catalystItems()) {
            result.append(Component.literal(" "))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.catalyst",
                            entry.itemId(), entry.count()));
        }
        if (directive.innerFluid() != null) {
            result.append(Component.literal(" "))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.inner_fluid",
                            directive.innerFluid().fluid()));
        }
        return result;
    }
}
