package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigPresenter;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigTooltipProvider;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.ConfigJsonCodec;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 客户端转换类型的可扩展 UI 定义。
 */
public final class ClientConversionTypeDefinition<T extends BaseConversionConfig> {
    private final ResourceLocation id;
    private final Supplier<T> configFactory;
    private final ConfigJsonCodec<T> codec;
    private final Supplier<? extends ConfigFormSection<T>> formSectionFactory;
    private final Function<ClientConversionTypeDefinition<T>, ? extends Screen> screenFactory;
    private final ConfigPresenter<T> presenter;
    private final ConfigTooltipProvider<T> tooltipProvider;

    public ClientConversionTypeDefinition(ResourceLocation id,
                                          Type listType,
                                          Supplier<T> configFactory,
                                          Supplier<? extends ConfigFormSection<T>> formSectionFactory,
                                          Function<ClientConversionTypeDefinition<T>, ? extends Screen> screenFactory,
                                          ConfigPresenter<T> presenter,
                                          ConfigTooltipProvider<T> tooltipProvider) {
        this.id = Objects.requireNonNull(id, "id");
        this.configFactory = Objects.requireNonNull(configFactory, "configFactory");
        this.codec = new ConfigJsonCodec<>(Objects.requireNonNull(listType, "listType"));
        this.formSectionFactory = Objects.requireNonNull(formSectionFactory, "formSectionFactory");
        this.screenFactory = Objects.requireNonNull(screenFactory, "screenFactory");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.tooltipProvider = Objects.requireNonNull(tooltipProvider, "tooltipProvider");
    }

    public ResourceLocation id() {
        return id;
    }

    public String fileName() {
        return id.getNamespace() + "/" + id.getPath() + ".json";
    }

    public Screen createScreen() {
        return screenFactory.apply(this);
    }

    public T createConfig() {
        return configFactory.get();
    }

    public Supplier<T> configFactory() {
        return configFactory;
    }

    public ConfigJsonCodec<T> codec() {
        return codec;
    }

    public ConfigFormSection<T> createFormSection() {
        return formSectionFactory.get();
    }

    public ConfigPresenter<T> presenter() {
        return presenter;
    }

    public ConfigTooltipProvider<T> tooltipProvider() {
        return tooltipProvider;
    }
}
