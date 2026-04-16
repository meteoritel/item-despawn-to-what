package com.meteorite.itemdespawntowhat.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TagResolver {
    private TagResolver() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isTagId(@Nullable String value) {
        return value != null && value.startsWith("#");
    }

    public static String stripTagPrefix(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return isTagId(value) ? value.substring(1) : value;
    }

    public static <T> List<T> resolveTagItems(Registry<T> registry,
                                             ResourceKey<? extends Registry<T>> registryKey,
                                             @Nullable String value) {
        if (registry == null || !isTagId(value)) {
            return List.of();
        }

        ResourceLocation tagLocation = SafeParseUtil.parseResourceLocation(stripTagPrefix(value));
        if (tagLocation == null) {
            return List.of();
        }

        TagKey<T> tagKey = TagKey.create(registryKey, tagLocation);
        List<T> resolved = new ArrayList<>();
        registry.getTag(tagKey).ifPresent(holders -> holders.forEach(holder -> resolved.add(holder.value())));
        return resolved.isEmpty() ? List.of() : List.copyOf(resolved);
    }

    public static <T> List<T> resolveTagItems(@Nullable RegistryAccess registryAccess,
                                             ResourceKey<? extends Registry<T>> registryKey,
                                             @Nullable Registry<T> fallbackRegistry,
                                             @Nullable String value) {
        Registry<T> registry = resolveRegistry(registryAccess, registryKey);
        if (registry == null) {
            registry = fallbackRegistry;
        }
        return resolveTagItems(registry, registryKey, value);
    }

    @Nullable
    public static <T> Registry<T> resolveRegistry(@Nullable RegistryAccess registryAccess,
                                                  ResourceKey<? extends Registry<T>> registryKey) {
        if (registryAccess == null) {
            return null;
        }
        return registryAccess.registry(registryKey).orElse(null);
    }

    @Nullable
    public static <T> Either<ResourceLocation, TagKey<T>> parseRegistryEntry(@Nullable String value,
                                                                             ResourceKey<? extends Registry<T>> registryKey) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        if (isTagId(value)) {
            ResourceLocation tagLocation = SafeParseUtil.parseResourceLocation(stripTagPrefix(value));
            if (tagLocation == null) {
                return null;
            }
            return Either.right(TagKey.create(registryKey, tagLocation));
        }

        ResourceLocation location = SafeParseUtil.parseResourceLocation(value);
        if (location == null) {
            return null;
        }
        return Either.left(location);
    }
}
