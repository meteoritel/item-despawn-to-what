package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * 注册七种内置条件的客户端显示定义。
 */
public final class BuiltinClientConditionTypes {
    private BuiltinClientConditionTypes() {
    }

    public static void registerAll() {
        register("dimension", font -> new TextConditionParamInput(font, "dimension",
                Component.translatable("gui.itemdespawntowhat.condition.dimension"),
                value -> ResourceLocation.tryParse(value) != null));
        register("outdoor", font -> new OutdoorParamInput());
        register("surrounding_blocks", SurroundingBlocksParamInput::new);
        register("catalyst_present", CatalystPresentParamInput::new);
        register("fluid_present", FluidPresentParamInput::new);
        register("biome", font -> new TextConditionParamInput(font, "biome",
                Component.translatable("gui.itemdespawntowhat.condition.biome"),
                value -> IdValidator.isValidTagId(value) || ResourceLocation.tryParse(value) != null));
        register("weather", font -> new WeatherParamInput());
    }

    private static void register(String path, Function<net.minecraft.client.gui.Font, ConditionParameterInput> factory) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
        ClientConditionTypeRegistry.register(new ClientConditionTypeDefinition(
                id, "condition.itemdespawntowhat.type." + path, factory));
    }
}
