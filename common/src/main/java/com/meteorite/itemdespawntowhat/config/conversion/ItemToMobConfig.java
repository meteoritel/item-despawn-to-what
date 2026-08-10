package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.List;

/**
 * 物品到生物实体转换的配置数据。
 */
public class ItemToMobConfig extends BaseItemToEntityConfig{

    // 生成实体的age（如果需要）
    @SerializedName("entity_age")
    private int entityAge = -24000;

    // 缓存的结果实体类型
    private transient EntityType<?> cachedResultEntityType;
    private transient List<EntityType<?>> cachedResultEntityTypes = List.of();

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
        cachedResultEntityType = null;
        cachedResultEntityTypes = List.of();
        if (TagResolver.isTagId(resultId)) {
            cachedResultEntityTypes = TagResolver.resolveTagItems(
                            BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, resultId).stream()
                    .filter(type -> type.getCategory() != MobCategory.MISC)
                    .toList();
            cachedResultEntityType = cachedResultEntityTypes.isEmpty()
                    ? null : cachedResultEntityTypes.getFirst();
            return;
        }
        cachedResultEntityType = BuiltInRegistries.ENTITY_TYPE.get(resultRl());
    }

    // 确保实体不为空，名字没有拼写错
    @Override
    protected boolean validateTypeSpecificFields() {
        if (!TagResolver.isTagId(resultId) && !BuiltInRegistries.ENTITY_TYPE.containsKey(resultRl())) {
            LOGGER.warn("Unknown entity type: resultId='{}'", resultId);
            return false;
        }

        return super.validateTypeSpecificFields();
    }

    @Override
    protected boolean isResultIdValid() {
        return TagResolver.isTagId(resultId)
                ? IdValidator.isValidTagId(resultId)
                : super.isResultIdValid();
    }

    @Override
    public boolean isCacheValid() {
        return cachedResultEntityType != null;
    }

    // ========== 结果相关方法 ========== //
    public EntityType<?> getResultEntityType() {
        if (isCacheInitialized()) {
            return cachedResultEntityType;
        }
        return BuiltInRegistries.ENTITY_TYPE.get(resultRl());
    }

    public EntityType<?> getResultEntityType(RandomSource random) {
        if (!cachedResultEntityTypes.isEmpty()) {
            return cachedResultEntityTypes.get(random.nextInt(cachedResultEntityTypes.size()));
        }
        return getResultEntityType();
    }

    public List<EntityType<?>> getResultEntityTypes() {
        return cachedResultEntityTypes.isEmpty()
                ? (cachedResultEntityType == null ? List.of() : List.of(cachedResultEntityType))
                : cachedResultEntityTypes;
    }

    public int getEntityAge() {
        return entityAge;
    }

    public void setEntityAge(int entityAge) {
        this.entityAge = entityAge;
    }
}
