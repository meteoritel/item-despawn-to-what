package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

/**
 * JSON 摘要：把任意 JSON 压成一行短文本，供只读回退展示。
 * <p>只截断展示文本，绝不改写数据本体；
 * 超长时的省略提示走本地化 key。
 */
public final class JsonSummary {

    private JsonSummary() {
    }

    // 压缩成一行（Gson 的 toString 即紧凑形式）
    public static String compact(JsonElement element) {
        return element == null || element.isJsonNull() ? "null" : element.toString();
    }

    // 截断到 maxLength 个字符
    public static String compact(JsonElement element, int maxLength) {
        String full = compact(element);
        return full.length() <= maxLength ? full : full.substring(0, Math.max(0, maxLength)) + "\u2026";
    }

    // 生成摘要文本；被截断时追加本地化的省略提示
    public static Component describe(JsonElement element, int maxLength) {
        String full = compact(element);
        if (full.length() <= maxLength) {
            return Component.literal(full);
        }
        int omitted = full.length() - Math.max(0, maxLength);
        return Component.literal(compact(element, maxLength) + " ")
                .append(Component.translatable("gui.itemdespawntowhat.edit.json.truncated", omitted));
    }
}
