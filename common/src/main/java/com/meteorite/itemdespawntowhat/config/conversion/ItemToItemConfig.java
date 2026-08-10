package com.meteorite.itemdespawntowhat.config.conversion;

import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 物品到掉落物转换的配置数据。
 */
public class ItemToItemConfig extends BaseItemToEntityConfig{
    // 缓存的结果物品实例
    private transient Item cachedResultItem;
    private transient List<Item> cachedResultItems = List.of();

    public ItemToItemConfig() {
        super(BuiltinConversionTypes.ITEM_TO_ITEM);
    }

    public ItemToItemConfig(String item, String result) {
        super(BuiltinConversionTypes.ITEM_TO_ITEM, item, result);
    }

    private ResourceLocation parseResultRl() {
        return SafeParseUtil.parseResourceLocation(resultId);
    }

    // ========== 初始化缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        if (TagResolver.isTagId(resultId)) {
            cachedResultItems = TagResolver.resolveTagItems(
                    BuiltInRegistries.ITEM, Registries.ITEM, resultId);
            cachedResultItem = cachedResultItems.isEmpty() ? null : cachedResultItems.getFirst();
            return;
        }
        ResourceLocation resultRl = parseResultRl();
        cachedResultItem = resultRl != null ? BuiltInRegistries.ITEM.get(resultRl) : Items.AIR;
        if (cachedResultItem == Items.AIR) {
            LOGGER.warn("Could not find item for resultId='{}', config will be rejected", resultId);
            cachedResultItem = null;
        }
    }

    // 物品转化需要保证转化前后结果不同，防止循环转化
    @Override
    protected boolean additionalCheck() {
        if (itemId.equals(resultId)) {
            LOGGER.warn("Source and result item are the same: {}, this would cause infinite conversion", itemId);
            return false;
        }

        return super.additionalCheck();
    }

    @Override
    protected boolean isResultIdValid() {
        return TagResolver.isTagId(resultId)
                ? IdValidator.isValidTagId(resultId)
                : super.isResultIdValid();
    }

    @Override
    public boolean isCacheValid() {
        return cachedResultItem != null;
    }
    // ========== 结果相关方法 ========== //
    public Item getResultItem() {
        if (isCacheInitialized()) {
            return cachedResultItem;
        }
        ResourceLocation resultRl = parseResultRl();
        return resultRl != null ? BuiltInRegistries.ITEM.get(resultRl) : Items.AIR;
    }

    public Item getResultItem(RandomSource random) {
        if (!cachedResultItems.isEmpty()) {
            return cachedResultItems.get(random.nextInt(cachedResultItems.size()));
        }
        return getResultItem();
    }

    public boolean matchesResultItem(Item item) {
        return cachedResultItems.isEmpty() ? item == cachedResultItem : cachedResultItems.contains(item);
    }

}
