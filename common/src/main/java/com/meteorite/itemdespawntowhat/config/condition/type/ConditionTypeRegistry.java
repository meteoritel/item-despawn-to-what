package com.meteorite.itemdespawntowhat.config.condition.type;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.condition.checker.CatalystConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.DimensionConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.InnerFluidConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.OutdoorConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.SurroundingBlocksConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.BiomeConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.WeatherConditionChecker;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.config.condition.ConditionLeaf;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 保存内置和第三方条件类型的可扩展注册表。
 */
public final class ConditionTypeRegistry {
    private static final Map<ResourceLocation, ConditionType> TYPES = new LinkedHashMap<>();

    static {
        registerBuiltin("dimension", "dimension", new ConditionTypeDefinition<>(
                BuiltinConditionParameters.Dimension.class,
                (parameters, config) -> new DimensionConditionChecker(parameters.dimension())));
        registerBuiltin("outdoor", "outdoor", new ConditionTypeDefinition<>(
                BuiltinConditionParameters.Outdoor.class,
                (parameters, config) -> new OutdoorConditionChecker()));
        registerBuiltin("surrounding_blocks", "surrounding_blocks", new ConditionTypeDefinition<>(
                BuiltinConditionParameters.SurroundingBlocksParameter.class,
                (parameters, config) -> surroundingChecker(parameters)));
        registerBuiltin("catalyst_present", "catalyst", new ConditionTypeDefinition<>(
                BuiltinConditionParameters.CatalystPresent.class,
                (parameters, config) -> catalystChecker(parameters.items(), config)));
        registerBuiltin("fluid_present", "inner_fluid", new ConditionTypeDefinition<>(
                BuiltinConditionParameters.FluidPresent.class,
                (parameters, config) -> fluidChecker(parameters)));
        registerBuiltin("biome", "biome", new ConditionTypeDefinition<>(
                BuiltinConditionParameters.Biome.class,
                (parameters, config) -> BiomeConditionChecker.create(parameters.biome())));
        registerBuiltin("weather", "weather", new ConditionTypeDefinition<>(
                BuiltinConditionParameters.Weather.class,
                (parameters, config) -> WeatherConditionChecker.create(parameters.weather())));
    }

    private ConditionTypeRegistry() {
    }

    public static synchronized <P> ConditionType register(
            ResourceLocation id,
            String debugName,
            ConditionTypeDefinition<P> definition
    ) {
        Objects.requireNonNull(id, "id");
        if (TYPES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate condition type: " + id);
        }
        ConditionType type = new ConditionType(id, debugName, definition);
        TYPES.put(id, type);
        return type;
    }

    public static synchronized List<ConditionType> all() {
        return List.copyOf(TYPES.values());
    }

    public static synchronized ConditionType byId(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static ConditionType require(ResourceLocation id) {
        ConditionType type = byId(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown condition type: " + id);
        }
        return type;
    }

    public static ConditionChecker createChecker(ConditionLeaf leaf, BaseConversionConfig config) {
        ConditionType type = require(leaf.typeId());
        return type.definition().createChecker(leaf.params(), config);
    }

    private static <P> void registerBuiltin(
            String path,
            String debugName,
            ConditionTypeDefinition<P> definition
    ) {
        register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path), debugName, definition);
    }

    private static ConditionChecker catalystChecker(
            List<CatalystItems.CatalystEntry> entries,
            BaseConversionConfig config
    ) {
        CatalystItems catalystItems = new CatalystItems();
        catalystItems.setCatalystList(entries == null ? List.of() : List.copyOf(entries));
        boolean conflictsWithSource = catalystItems.getCatalystList().stream()
                .anyMatch(entry -> entry.itemId().equals(config.getItemId()));
        return catalystItems.hasAnyCatalyst() && !conflictsWithSource
                ? new CatalystConditionChecker(catalystItems, config.getSourceMultiple())
                : null;
    }

    private static ConditionChecker surroundingChecker(
            BuiltinConditionParameters.SurroundingBlocksParameter parameters
    ) {
        return parameters.blocks() != null && parameters.blocks().isValid()
                ? SurroundingBlocksConditionChecker.from(parameters.blocks()) : null;
    }

    private static ConditionChecker fluidChecker(BuiltinConditionParameters.FluidPresent parameters) {
        return IdValidator.isValidFluidId(parameters.fluid())
                ? new InnerFluidConditionChecker(new InnerFluid(
                        parameters.fluid(), parameters.requireSource(), false))
                : null;
    }
}
