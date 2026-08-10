package com.meteorite.itemdespawntowhat.config.consumption;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述转化成功后需要实际消耗的催化剂和流体。
 */
public final class ConsumptionDirective {
    @SerializedName("catalyst_items")
    private List<CatalystItems.CatalystEntry> catalystItems;

    @SerializedName("inner_fluid")
    private FluidConsumption innerFluid;

    public List<CatalystItems.CatalystEntry> catalystItems() {
        return catalystItems == null ? List.of() : List.copyOf(catalystItems);
    }

    public void setCatalystItems(@Nullable List<CatalystItems.CatalystEntry> entries) {
        catalystItems = entries == null || entries.isEmpty() ? null : new ArrayList<>(entries);
    }

    public @Nullable FluidConsumption innerFluid() {
        return innerFluid;
    }

    public void setInnerFluid(@Nullable FluidConsumption innerFluid) {
        this.innerFluid = innerFluid;
    }

    public boolean isEmpty() {
        return catalystItems().isEmpty() && innerFluid == null;
    }

    public boolean isValid(String sourceItemId) {
        boolean catalystsValid = catalystItems().stream()
                .allMatch(entry -> entry != null && entry.isValid()
                        && !entry.itemId().equals(sourceItemId));
        return catalystsValid && (innerFluid == null || IdValidator.isValidFluidId(innerFluid.fluid()));
    }

    public int getMaxConvertibleRounds(ItemEntity entity, int sourceMultiple) {
        CatalystItems catalysts = catalystData();
        return catalysts == null ? Integer.MAX_VALUE
                : catalysts.getMaxConvertibleRounds(entity, sourceMultiple);
    }

    public void consume(ItemEntity entity, int consumedSourceItems) {
        CatalystItems catalysts = catalystData();
        if (catalysts != null) {
            catalysts.consumeFromLevel(entity, consumedSourceItems);
        }
        if (innerFluid != null) {
            new InnerFluid(innerFluid.fluid(), innerFluid.requireSource(), true)
                    .consumeFluidFromLevel(entity);
        }
    }

    private @Nullable CatalystItems catalystData() {
        if (catalystItems().isEmpty()) {
            return null;
        }
        CatalystItems catalysts = new CatalystItems();
        catalysts.setCatalystList(catalystItems());
        catalysts.setCatalystConsume(true);
        return catalysts;
    }

    /** 流体消耗目标。 */
    public record FluidConsumption(
            @SerializedName("fluid") String fluid,
            @SerializedName("require_source") boolean requireSource
    ) {
    }
}
