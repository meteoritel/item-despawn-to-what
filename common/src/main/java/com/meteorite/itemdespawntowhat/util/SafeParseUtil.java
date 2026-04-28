package com.meteorite.itemdespawntowhat.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public static List<String> splitCommaSeparated(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.replace(" ", "").split(",", -1))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static List<String> splitCommaSeparatedValues(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        String[] parts = raw.replace(" ", "").split(",", -1);
        List<String> list = new ArrayList<>(Arrays.asList(parts));
        while (!list.isEmpty() && list.getLast().isEmpty()) {
            list.removeLast();
        }
        return list;
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
