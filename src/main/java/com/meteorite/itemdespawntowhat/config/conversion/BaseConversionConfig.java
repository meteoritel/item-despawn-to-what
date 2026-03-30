package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.condition.ConditionCheckerUtil;
import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.event.ItemConversionEvent;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.JsonOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class BaseConversionConfig {
    protected static final Logger LOGGER = LogManager.getLogger();

    // 内部标识符，不会进行序列化
    protected transient String internalId;
    protected transient ConfigType configType;

    // ========== 缓存字段 ========== //
    // 缓存的起始物品实例（非标签模式）
    protected transient Item cachedStartItem;
    // 标签模式下展开的物品列表
    private transient List<Item> cachedTagItems;
    // 是否为标签模式（itemId 以 # 开头）
    private transient boolean isTagMode;
    // 缓存是否已初始化
    private transient boolean cacheInitialized = false;
    // 检查限制的范围
    protected static final int MAX_RADIUS = 6;

    // 物品注册名（支持 #tag:id 格式）
    @JsonOrder(1)
    @SerializedName("item")
    protected String itemId;
    // 消失的方式 - 自然消失 timeout， 岩浆烧毁 lava，暂时没有作用，未来再添加
    // @JsonOrder(2)
    // @SerializedName("disappear_cause")
    // protected String disappearCause;
    // 所处维度
    @JsonOrder(2)
    @SerializedName("dimension")
    protected String dimension = "";
    // 是否需要露天
    @JsonOrder(2)
    @SerializedName("need_outdoor")
    protected boolean needOutdoor = false;
    // 六个方向的方块
    @JsonOrder(2)
    @SerializedName("surrounding_blocks")
    protected SurroundingBlocks surroundingBlocks = new SurroundingBlocks();
    // 催化剂物品
    @JsonOrder(2)
    @SerializedName("catalyst_items")
    protected CatalystItems catalystItems = new CatalystItems();
    @JsonOrder(2)
    @SerializedName("inner_fluid")
    protected InnerFluid innerFluid = new InnerFluid();
    // 生成结果注册名
    @JsonOrder(3)
    @SerializedName("result")
    protected String resultId;
    // 转化的时间，单位为秒，默认原版300s
    @JsonOrder(3)
    @SerializedName("conversion_time")
    protected int conversionTime = 300;
    //生成的倍率，默认为1
    @JsonOrder(3)
    @SerializedName("result_multiple")
    protected int resultMultiple = 1;

    // 用来存储配置的空构造方法
    public BaseConversionConfig() {
        this.internalId = UUID.randomUUID().toString();
        this.configType = ConfigType.fromClass(this.getClass());
    }

    // 用来生成示例配置用的构造方法
    public BaseConversionConfig(String item, String result) {
        this();
        this.itemId = item;
        this.resultId = result;
        this.conversionTime = 5;
    }

    // ========== 缓存初始化 ========== //
    public final void initCache() {
        if (cacheInitialized) {
            return;
        }

        // 确保 internalId 存在
        if (this.internalId == null || this.internalId.isEmpty()) {
            this.internalId = UUID.randomUUID().toString();
        }

        // 缓存起始物品（标签模式下 cachedStartItem 在 expandTagItems 中填充）
        if (itemId != null && itemId.startsWith("#")) {
            isTagMode = true;
            cachedStartItem = Items.AIR; // 标签展开前占位，expandTagItems() 后更新
            cachedTagItems = new ArrayList<>();
        } else {
            isTagMode = false;
            ResourceLocation rl = ResourceLocation.tryParse(itemId != null ? itemId : "");
            cachedStartItem = (rl != null) ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
        }

        // 子类缓存各自的结果对象
        initResultCache();

        cacheInitialized = true;
        LOGGER.debug("Cache initialized for config: item = {}, internalId = {}", itemId, internalId);
    }

    // 服务端启动后由 ConfigExtractorManager 调用，展开标签到具体物品列表
    public void expandTagItems() {
        if (!isTagMode || itemId == null) return;
        ResourceLocation tagRl = ResourceLocation.tryParse(itemId.substring(1));
        if (tagRl == null) return;
        var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagRl);
        List<Item> expanded = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tagKey).ifPresent(holders ->
                holders.forEach(h -> expanded.add(h.value())));
        cachedTagItems = Collections.unmodifiableList(expanded);
        cachedStartItem = expanded.isEmpty() ? Items.AIR : expanded.getFirst();
    }

    protected void initResultCache() {
        // 默认空实现，子类按需重写
    }

    protected boolean isResultIdRequired() {
        return true;
    }

    // 是否已经缓存
    public boolean isCacheInitialized() {
        return cacheInitialized;
    }

    // ========== 限制条件，不符合条件的配置不会被读取 ========== //
    public final boolean shouldProcess() {
        if (!IdValidator.isValidItemId(itemId)) {
            LOGGER.warn("invalid item id: {} ", itemId);
            return false;
        }

        if (isResultIdRequired() && !IdValidator.isValidResultId(resultId)) {
            LOGGER.warn("invalid result resource location: {}", resultId);
            return false;
        }

        if (conversionTime <= 0) {
            LOGGER.warn("conversionTime should be at list 1, current is {}", conversionTime);
            return false;
        }

        if (resultMultiple <= 0) {
            LOGGER.warn("resultMultiple should be at least 1, current is {}", resultMultiple);
            return false;
        }

        if (!surroundingBlocks.isValid()) {
            LOGGER.warn("there is invalid blockId in surround blocks");
            return false;
        }

        // 催化剂不能与起始物品相同
        if (catalystItems.hasAnyCatalyst()) {
            boolean conflict = getCatalystItems().getCatalystList().stream()
                    .anyMatch(entry -> entry.itemId().equals(itemId));
            if (conflict) {
                LOGGER.warn("Catalyst item conflicts with source item: {}", itemId);
                return false;
            }
        }

        if (innerFluid != null && innerFluid.hasInnerFluid()) {
            if (!innerFluid.isValid()) {
                LOGGER.warn("Invalid fluid id in fluid condition: {}", innerFluid.getFluidId());
                return false;
            }
        }

        return additionalCheck();
    }

    // ========== 条件检查器 ========== //
    // 构建条件检查器
    public ConditionChecker buildConditionChecker() {
        ConditionContext ctx = new ConditionContext(getDimension(),
                isNeedOutdoor(),
                getSurroundingBlocks(),
                getCatalystItems(),
                getInnerFluid());
        return ConditionCheckerUtil.buildCombinedChecker(ctx);
    }

    // ========== 消耗相关逻辑 ========== //
    // 所有的额外消耗的方法
    protected void consumeAllOthers(ItemEntity itemEntity, int startItemCount) {
        consumeCatalysts(itemEntity, startItemCount);
        consumeFluid(itemEntity);
    }

    // 消耗催化剂的方法
    protected void consumeCatalysts(ItemEntity itemEntity, int startItemCount) {
        if (catalystItems == null || !catalystItems.hasAnyCatalyst() || !catalystItems.isCatalystConsume()) {
            return;
        }
        catalystItems.consumeFromLevel(itemEntity, startItemCount);
    }

    // 消耗流体的方法
    protected void consumeFluid(ItemEntity itemEntity) {
        if (innerFluid == null || !innerFluid.hasInnerFluid()) {
            return;
        }
        innerFluid.consumeFluidFromLevel(itemEntity);
    }

    // ========== 辅助方法 ========== //
    // 计算本次实际能转化的起始物品数量，考虑催化剂数量上限、结果数量上限
    protected int computeActualConvertCount(ItemEntity itemEntity, int originalStackSize) {
        // 催化剂的限制
        int catalystLimit = (catalystItems != null && catalystItems.hasAnyCatalyst())
                ? catalystItems.getMaxConvertible(itemEntity, originalStackSize)
                : originalStackSize;

        // 上限的限制
        int resultLimit = getResultCapacityInStartItems(itemEntity);

        // 取最小值，并确保不超过原始堆叠数
        int actual = Math.min(catalystLimit, resultLimit);
        actual = Math.min(actual, originalStackSize);
        return Math.max(0, actual);
    }

    // 将结果容量折算为起始物品数量，默认返回起始物品的最大支持堆叠数，由子类重写
    protected int getResultCapacityInStartItems(ItemEntity itemEntity) {
        return itemEntity.getItem().getMaxStackSize();
    }

    // 返还剩余物品
    public void addRemainingItems(ItemEntity itemEntity, ServerLevel serverLevel, int itemsRemaining) {
        if (itemsRemaining <= 0) {
            return;
        }
        addRemainingItems(itemEntity, serverLevel, itemsRemaining, 0, 0, 0);
    }

    public void addRemainingItems(ItemEntity itemEntity, ServerLevel serverLevel, int itemsRemaining,
                                  double offsetX, double offsetY, double offsetZ) {
        if (itemsRemaining <= 0) {
            return;
        }

        ItemStack returnStack = itemEntity.getItem().copy();
        returnStack.setCount(itemsRemaining);

        BlockPos pos = itemEntity.blockPosition();

        ItemEntity returnItem = new ItemEntity(
                serverLevel,
                pos.getX() + 0.5 + offsetX,
                pos.getY() + offsetY,
                pos.getZ() + 0.5 + offsetZ,
                returnStack
        );

        returnItem.getPersistentData().putBoolean(ItemConversionEvent.CHECK_LOCK_TAG, true);
        serverLevel.addFreshEntity(returnItem);

        LOGGER.debug("Returned {} unused items of {} with offset ({}, {}, {})",
                itemsRemaining, getItemId(), offsetX, offsetY, offsetZ);
    }

    public Item getStartItem() {
        if (cacheInitialized) {
            return cachedStartItem;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId != null ? itemId : "");
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
    }

    public ItemStack getStartItemIcon() {
        return getStartItem().getDefaultInstance();
    }

    // ========== 子类方法 ========== //
    public int countNearbyResult(ItemEntity itemEntity) {
        return 0;
    }
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return false;
    }

    public abstract String getResultDescriptionId();
    public abstract ItemStack getResultIcon();
    public abstract void performConversion(ItemEntity itemEntity, ServerLevel serverLevel);
    protected boolean additionalCheck() {return true;}
    public boolean isCacheValid() {return true;}

    // ========== setter 和 getter ========== //
    public int getConversionTime() {
        return conversionTime;
    }
    public int getResultMultiple() {
        return resultMultiple;
    }
    public void setConversionTime(int conversionTime) {
        this.conversionTime = conversionTime;
    }
    public String getDimension() {
        return dimension;
    }
    public void setDimension(String dimension) {
        this.dimension = dimension;
    }
    public void setResultMultiple(int resultMultiple) {
        this.resultMultiple = resultMultiple;
    }
    public boolean isNeedOutdoor() {
        return needOutdoor;
    }
    public void setNeedOutdoor(boolean needOutdoor) {
        this.needOutdoor = needOutdoor;
    }
    public String getItemId() {
        return itemId;
    }
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
    public String getResultId() {
        return resultId;
    }
    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public boolean isTagMode() {
        return isTagMode;
    }

    // 标签模式下展开的物品列表（服务端 expandTagItems() 后有效）
    public List<Item> getTagItems() {
        return cachedTagItems != null ? cachedTagItems : Collections.emptyList();
    }
    public SurroundingBlocks getSurroundingBlocks() {
        return surroundingBlocks;
    }
    public void setSurroundingBlocks(SurroundingBlocks surroundingBlocks) {
        this.surroundingBlocks = surroundingBlocks;
    }
    public CatalystItems getCatalystItems() {
        return catalystItems;
    }
    public void setCatalystItems(CatalystItems catalystItems) {
        this.catalystItems = catalystItems;
    }
    public InnerFluid getInnerFluid() {
        return innerFluid;
    }
    public void setInnerFluid(InnerFluid innerFluid) {
        this.innerFluid = innerFluid;
    }

    // config类型、uuid只能获取不能设置
    public ConfigType getConfigType() {
        return configType;
    }
    public String getInternalId() {
        return internalId;
    }
}
