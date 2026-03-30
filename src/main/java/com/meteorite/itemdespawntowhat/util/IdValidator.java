package com.meteorite.itemdespawntowhat.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public final class IdValidator {
    private IdValidator() {}

    // 纯字符串格式校验：非空、含 ':' 分隔符、':' 两侧均非空
    public static boolean isValidString(String s) {
        if (s == null || s.isEmpty()) return false;
        int colon = s.indexOf(':');
        return colon > 0 && colon < s.length() - 1;
    }

    // ResourceLocation 实例校验：非 null、path 非空
    public static boolean isValidResourceLocation(ResourceLocation rl) {
        return rl != null && !rl.getPath().isEmpty();
    }

    // tag 字符串校验（以 # 开头）：去掉 # 后做格式校验 + tryParse
    public static boolean isValidTagId(String s) {
        if (s == null || !s.startsWith("#")) return false;
        String inner = s.substring(1);
        return isValidString(inner) && ResourceLocation.tryParse(inner) != null;
    }

    // itemId 完整校验：支持 #tag 格式，普通 id 需格式合法且不为 minecraft:air
    public static boolean isValidItemId(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.startsWith("#")) return isValidTagId(s);
        if (!isValidString(s)) return false;
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null) return false;
        return BuiltInRegistries.ITEM.get(rl) != Items.AIR;
    }

    // resultId 校验（纯格式，不查注册表，不排除 air，不支持 tag）
    public static boolean isValidResultId(String s) {
        return isValidString(s) && ResourceLocation.tryParse(s) != null;
    }
}
