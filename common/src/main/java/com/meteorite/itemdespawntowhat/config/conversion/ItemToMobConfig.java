package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * 物品到生物实体转换的配置数据。
 */
public class ItemToMobConfig extends BaseItemToEntityConfig{

    // 生成实体的age（如果需要）
    @SerializedName("entity_age")
    private int entityAge = -24000;

    // 缓存的结果实体类型
    private transient EntityType<?> cachedResultEntityType;

    public ItemToMobConfig() {
        super(BuiltinConversionTypes.ITEM_TO_MOB);
    }

    public ItemToMobConfig(String item, String result) {
        super(BuiltinConversionTypes.ITEM_TO_MOB, item, result);
    }

    private ResourceLocation resultRl() {
        return SafeParseUtil.parseResourceLocation(resultId);
    }

    // ========== 缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        cachedResultEntityType = BuiltInRegistries.ENTITY_TYPE.get(resultRl());
    }

    // 确保实体不为空，名字没有拼写错
    @Override
    protected boolean additionalCheck() {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(resultRl())) {
            LOGGER.warn("Unknown entity type: resultId='{}'", resultId);
            return false;
        }

        return super.additionalCheck();
    }

    // ========== 结果相关方法 ========== //
    public EntityType<?> getResultEntityType() {
        if (isCacheInitialized()) {
            return cachedResultEntityType;
        }
        return BuiltInRegistries.ENTITY_TYPE.get(resultRl());
    }

    public int getEntityAge() {
        return entityAge;
    }

    public void setEntityAge(int entityAge) {
        this.entityAge = entityAge;
    }
}
