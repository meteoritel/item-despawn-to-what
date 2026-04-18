package com.meteorite.itemdespawntowhat.client.ui.panel.configlist;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ConfigTooltipBuilder {

    private ConfigTooltipBuilder() {
    }

    public static Component build(BaseConversionConfig config) {
        MutableComponent tooltip = Component.translatable(
                "gui.itemdespawntowhat.tooltip.conversion_time", config.getConversionTime());

        String dim = config.getDimension();
        if (dim != null && !dim.isEmpty()) {
            tooltip = tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.dimension", dim));
        }

        if (config.isNeedOutdoor()) {
            tooltip = tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.need_outdoor"));
        }

        SurroundingBlocks sb = config.getSurroundingBlocks();
        if (sb != null && sb.hasAnySurroundBlock()) {
            tooltip = tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.surrounding_blocks_header"));
            for (ConfigDirection dir : ConfigDirection.values()) {
                String val = sb.get(dir);
                if (val != null && !val.isEmpty()) {
                    tooltip = tooltip.append(Component.literal("\n"))
                            .append(Component.translatable("gui.itemdespawntowhat.tooltip.surrounding_block",
                                    dir.getDisplayName(), val));
                }
            }
        }

        CatalystItems ci = config.getCatalystItems();
        if (ci != null && ci.hasAnyCatalyst()) {
            tooltip = tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.catalyst_header"));
            for (CatalystItems.CatalystEntry entry : ci.getCatalystList()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.catalyst",
                                entry.itemId(), entry.count()));
            }
        }

        InnerFluid fluid = config.getInnerFluid();
        if (fluid != null && fluid.hasInnerFluid()) {
            tooltip = tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.inner_fluid",
                            fluid.getFluidId()));
            if (fluid.isRequireSource()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.inner_fluid_source"));
            }
            if (fluid.isConsumeFluid()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.inner_fluid_consume"));
            }
        }

        return tooltip;
    }
}
