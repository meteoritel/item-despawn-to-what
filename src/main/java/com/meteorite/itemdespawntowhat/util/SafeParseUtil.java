package com.meteorite.itemdespawntowhat.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class SafeParseUtil {

    private SafeParseUtil() {
    }

    public static int parseInt(@Nullable String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static float parseFloat(@Nullable String value, float defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static @Nullable ResourceLocation parseResourceLocation(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(value.trim());
        if (location == null || location.getPath().isEmpty()) {
            return null;
        }
        return location;
    }
}
