package com.meteorite.itemdespawntowhat.config.conversion;

import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 物品到掉落物转换的配置数据。
 */
public class ItemToItemConfig extends BaseItemToEntityConfig{
    // 缓存的结果物品实例
    private transient Item cachedResultItem;
    // 缓存结果物品的最大堆叠数，避免重复查找
    private transient int cachedResultMaxStackSize;

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
        ResourceLocation resultRl = parseResultRl();
        cachedResultItem = resultRl != null ? BuiltInRegistries.ITEM.get(resultRl) : Items.AIR;
        if (cachedResultItem == Items.AIR) {
            LOGGER.warn("Could not find item for resultId='{}', config will be rejected", resultId);
            cachedResultItem = null;
            cachedResultMaxStackSize = 1;
        } else {
            cachedResultMaxStackSize = cachedResultItem.getDefaultMaxStackSize();
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
    // ========== 结果相关方法 ========== //
    public Item getResultItem() {
        if (isCacheInitialized()) {
            return cachedResultItem;
        }
        ResourceLocation resultRl = parseResultRl();
        return resultRl != null ? BuiltInRegistries.ITEM.get(resultRl) : Items.AIR;
    }

    public int getResultMaxStackSize() {
        return Math.max(1, cachedResultMaxStackSize);
    }

}
