package com.meteorite.itemdespawntowhat.client.ui.panel.configlist;

import com.meteorite.itemdespawntowhat.client.register.ClientConversionTypeRegistry;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigPresentation;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConditionExpressionPresenter;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConsumptionPresenter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 构建配置列表中的通用与类型专属 tooltip。
 */
public final class ConfigTooltipBuilder {

    private ConfigTooltipBuilder() {
    }

    public static Component build(BaseConversionConfig config, ConfigPresentation presentation) {
        MutableComponent tooltip = presentation.summary().copy()
                .append(Component.literal("\n"))
                .append(Component.translatable(
                        "gui.itemdespawntowhat.tooltip.conversion_time", config.getConversionTime()));

        tooltip = tooltip.append(Component.literal("\n"))
                .append(Component.translatable("gui.itemdespawntowhat.tooltip.conditions"))
                .append(Component.literal(" "))
                .append(ConditionExpressionPresenter.summary(config.getConditionExpression()));

        Component consumption = ConsumptionPresenter.summary(config.getConsumptionDirective());
        if (!consumption.getString().isEmpty()) {
            tooltip = tooltip.append(Component.literal("\n")).append(consumption);
        }
        tooltip = tooltip.append(Component.literal("\n"))
                .append(Component.translatable("gui.itemdespawntowhat.tooltip.priority", config.getPriority()));
        if (!config.isEnabled()) {
            tooltip = tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.disabled"));
        }
        if (config.getNotes() != null) {
            tooltip = tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.notes", config.getNotes()));
        }

        if (ClientConversionTypeRegistry.contains(config.getConversionType().id())) {
            ClientConversionTypeRegistry.appendTooltip(config.getConversionType().id(), config, tooltip);
        }

        return tooltip;
    }
}
