package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.JsonOrder;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 所有物品转换规则共享的数据、校验与引用解析基类。
 */
public abstract class BaseConversionConfig extends ConversionConfig {
    protected static final Logger LOGGER = LogManager.getLogger();

    // 内部标识符，不会进行序列化
    protected transient String internalId;
    protected transient ConversionType conversionType;

    // ========== 缓存字段 ========== //
    // 缓存的起始物品实例（非标签模式）
    private transient Item cachedStartItem;
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
    // 生成结果注册名
    @JsonOrder(1)
    @SerializedName("result")
    protected String resultId;
    // 每轮转化消耗的起始物品数量，默认为1
    @JsonOrder(2)
    @SerializedName("source_multiple")
    protected int sourceMultiple = 1;
    //生成的倍率，默认为1
    @JsonOrder(2)
    @SerializedName("result_multiple")
    protected int resultMultiple = 1;
    // 转化的时间，单位为秒，默认原版300s
    @JsonOrder(2)
    @SerializedName("conversion_time")
    protected int conversionTime = 300;
    // 是否需要露天
    @JsonOrder(3)
    @SerializedName("need_outdoor")
    protected boolean needOutdoor = false;
    // 所处维度
    @JsonOrder(3)
    @SerializedName("dimension")
    protected @Nullable String dimension;
    // 六个方向的方块
    @JsonOrder(3)
    @SerializedName("surrounding_blocks")
    protected @Nullable SurroundingBlocks surroundingBlocks;
    // 催化剂物品
    @JsonOrder(3)
    @SerializedName("catalyst_items")
    protected @Nullable CatalystItems catalystItems;
    // 浸润流体信息
    @JsonOrder(3)
    @SerializedName("inner_fluid")
    protected @Nullable InnerFluid innerFluid;

    // 用来存储配置的空构造方法
    protected BaseConversionConfig(ConversionType conversionType) {
        this.internalId = UUID.randomUUID().toString();
        this.conversionType = conversionType;
    }

    // 用来生成示例配置用的构造方法
    protected BaseConversionConfig(ConversionType type, String item, String result) {
        this(type);
        this.itemId = item;
        this.resultId = result;
        this.conversionTime = 5;
    }

    @Override
    public final boolean validate() {
        return shouldProcess();
    }

    @Override
    public final int complexity() {
        return computeComplexity();
    }

    @Override
    public final void resolve() {
        initCache();
    }

    private ResourceLocation parseItemRl() {
        return SafeParseUtil.parseResourceLocation(itemId);
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
        if (TagResolver.isTagId(itemId)) {
            isTagMode = true;
            cachedStartItem = Items.AIR; // 标签展开前占位，expandTagItems() 后更新
            cachedTagItems = List.of();
        } else {
            isTagMode = false;
            ResourceLocation rl = parseItemRl();
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

        cachedTagItems = TagResolver.resolveTagItems(BuiltInRegistries.ITEM, Registries.ITEM, itemId);
        cachedStartItem = cachedTagItems.isEmpty() ? Items.AIR : cachedTagItems.getFirst();
    }

    // 提前缓存结果实例，默认空实现，子类按需重写
    protected void initResultCache() {
    }

    // 结果ID是否允许为空，子类按需重写
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

        // 并没有限制转化时间小于300s，兼容修改时间上限的模组
        if (conversionTime <= 0) {
            LOGGER.warn("conversionTime should be at list 1, current is {}", conversionTime);
            return false;
        }

        if (resultMultiple <= 0 || resultMultiple > ConversionLimits.MAX_RESULT_MULTIPLE) {
            LOGGER.warn("resultMultiple should be in range [1, {}], current is {}",
                    ConversionLimits.MAX_RESULT_MULTIPLE, resultMultiple);
            return false;
        }

        if (sourceMultiple <= 0 || sourceMultiple > ConversionLimits.MAX_SOURCE_MULTIPLE) {
            LOGGER.warn("sourceMultiple should be in range [1, {}], current is {}",
                    ConversionLimits.MAX_SOURCE_MULTIPLE, sourceMultiple);
            return false;
        }

        if (surroundingBlocks != null && surroundingBlocks.hasAnySurroundBlock() && !surroundingBlocks.isValid()) {
            LOGGER.warn("Invalid blockId in surround blocks：");
            return false;
        }

        // 催化剂不能与起始物品相同，且列表中的每个条目都必须有效
        if (catalystItems != null && !catalystItems.getCatalystList().isEmpty()) {
            if (!catalystItems.isAllEntryValid()) {
                LOGGER.warn("Invalid catalyst item list for source item: {}", itemId);
                return false;
            }
            boolean conflict = catalystItems.getCatalystList().stream()
                    .anyMatch(entry -> entry.itemId().equals(itemId));
            if (conflict) {
                LOGGER.warn("Catalyst item conflicts with source item: {}", itemId);
                return false;
            }
        }

        if (innerFluid != null && innerFluid.hasInnerFluid() && !innerFluid.isValid()) {
            LOGGER.warn("Invalid fluid id in fluid condition: {}", innerFluid.getFluidId());
            return false;
        }

        return additionalCheck();
    }

    // ========== 配置复杂度 ========== //
    // 根据条件字段的非空/非false状态计算复杂度，用于多配置优先级选择
    public int computeComplexity() {
        int complexity = 0;
        if (dimension != null) complexity++;
        if (needOutdoor) complexity++;
        if (surroundingBlocks != null) complexity++;
        if (catalystItems != null) complexity++;
        if (innerFluid != null) complexity++;
        return complexity;
    }

    public Item getStartItem() {
        if (cacheInitialized) {
            return cachedStartItem;
        }
        ResourceLocation rl = parseItemRl();
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
    }

    protected boolean additionalCheck() {return true;}

    public boolean isCacheValid() {return true;}

    // ========== setter 和 getter ========== //
    public int getConversionTime() {
        return conversionTime;
    }
    public int getResultMultiple() {
        return resultMultiple;
    }
    public int getSourceMultiple() {
        return sourceMultiple;
    }
    public void setConversionTime(int conversionTime) {
        this.conversionTime = conversionTime;
    }
    public @Nullable String getDimension() {
        return dimension;
    }
    public void setDimension(@Nullable String dimension) {
        this.dimension = dimension;
    }
    public void setResultMultiple(int resultMultiple) {
        this.resultMultiple = resultMultiple;
    }
    public void setSourceMultiple(int sourceMultiple) {
        this.sourceMultiple = sourceMultiple;
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
        return cachedTagItems != null ? cachedTagItems : List.of();
    }

    public @Nullable SurroundingBlocks getSurroundingBlocks() {
        return surroundingBlocks;
    }

    public void setSurroundingBlocks(@Nullable SurroundingBlocks surroundingBlocks) {
        this.surroundingBlocks = surroundingBlocks;
    }

    public @Nullable CatalystItems getCatalystItems() {
        return catalystItems;
    }

    public void setCatalystItems(@Nullable CatalystItems catalystItems) {
        this.catalystItems = catalystItems;
    }

    public @Nullable InnerFluid getInnerFluid() {
        return innerFluid;
    }

    public void setInnerFluid(@Nullable InnerFluid innerFluid) {
        this.innerFluid = innerFluid;
    }

    // config类型、uuid只能获取不能设置
    public ConversionType getConversionType() {
        return conversionType;
    }

    public String getInternalId() {
        return internalId;
    }
}
