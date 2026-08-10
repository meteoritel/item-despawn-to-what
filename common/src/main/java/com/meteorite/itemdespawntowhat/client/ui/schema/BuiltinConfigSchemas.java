package com.meteorite.itemdespawntowhat.client.ui.schema;

import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

/**
 * 三个简单内置转换类型的声明式字段 schema。
 */
public final class BuiltinConfigSchemas {
    private BuiltinConfigSchemas() {
    }

    public static SchemaConfigFormSection<ItemToItemConfig> itemToItem() {
        ConfigFieldSchema<ItemToItemConfig> resultLimit = ConfigFieldSchema.<ItemToItemConfig>builder(
                        "result_limit", "result_limit", ConfigFieldType.POSITIVE_INTEGER,
                        config -> Integer.toString(config.getResultLimit()),
                        (config, value) -> config.setResultLimit(SafeParseUtil.parseInt(value, 30)))
                .build();
        return new SchemaConfigFormSection<>(List.of(resultLimit), true,
                IdValidator::isValidResultId, SuggestionProvider.ofRegistry(BuiltInRegistries.ITEM));
    }

    public static SchemaConfigFormSection<ItemToMobConfig> itemToMob() {
        ConfigFieldSchema<ItemToMobConfig> resultLimit = ConfigFieldSchema.<ItemToMobConfig>builder(
                        "result_limit", "result_limit", ConfigFieldType.POSITIVE_INTEGER,
                        config -> Integer.toString(config.getResultLimit()),
                        (config, value) -> config.setResultLimit(SafeParseUtil.parseInt(value, 30)))
                .build();
        ConfigFieldSchema<ItemToMobConfig> entityAge = ConfigFieldSchema.<ItemToMobConfig>builder(
                        "entity_age", "result_age", ConfigFieldType.INTEGER,
                        config -> Integer.toString(config.getEntityAge()),
                        (config, value) -> config.setEntityAge(SafeParseUtil.parseInt(value, 0)))
                .build();
        return new SchemaConfigFormSection<>(List.of(resultLimit, entityAge), true,
                IdValidator::isValidEntityId, SuggestionProvider.ofMobEntityTypes());
    }

    public static SchemaConfigFormSection<ItemToExpOrbConfig> itemToExperience() {
        ConfigFieldSchema<ItemToExpOrbConfig> xpPerItem = ConfigFieldSchema.<ItemToExpOrbConfig>builder(
                        "xp_per_item", "xp_pre_item", ConfigFieldType.POSITIVE_INTEGER,
                        config -> Integer.toString(config.getXpPerItem()),
                        (config, value) -> config.setXpPerItem(SafeParseUtil.parseInt(value, 1)))
                .build();
        return new SchemaConfigFormSection<>(List.of(xpPerItem), false, null, null);
    }
}
