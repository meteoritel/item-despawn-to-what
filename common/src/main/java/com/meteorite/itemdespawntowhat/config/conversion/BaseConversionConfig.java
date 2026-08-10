package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.condition.ConditionExpression;
import com.meteorite.itemdespawntowhat.config.condition.ConditionLeaf;
import com.meteorite.itemdespawntowhat.config.condition.type.BuiltinConditionParameters;
import com.meteorite.itemdespawntowhat.config.condition.type.BuiltinConditionTypes;
import com.meteorite.itemdespawntowhat.config.consumption.ConsumptionDirective;
import com.meteorite.itemdespawntowhat.config.io.ConfigMigrator;
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

    // 物品注册名（支持 #tag:id 格式）
    @JsonOrder(0)
    @SerializedName("schema_version")
    protected int schemaVersion = ConfigMigrator.CURRENT_SCHEMA_VERSION;

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

    // DNF 条件表达式，空表达式表示恒真
    @JsonOrder(3)
    @SerializedName("conditions")
    protected ConditionExpression conditionExpression = new ConditionExpression();

    @JsonOrder(4)
    @SerializedName("consumption")
    protected @Nullable ConsumptionDirective consumptionDirective;

    @JsonOrder(2)
    @SerializedName("priority")
    protected int priority;

    @JsonOrder(2)
    @SerializedName("enabled")
    protected boolean enabled = true;

    @JsonOrder(2)
    @SerializedName("notes")
    protected @Nullable String notes;

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
        return validateDefinition();
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
        return enabled && validateDefinition();
    }

    private boolean validateDefinition() {
        if (!IdValidator.isValidItemId(itemId)) {
            LOGGER.warn("invalid item id: {} ", itemId);
            return false;
        }

        if (isResultIdRequired() && !isResultIdValid()) {
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

        SurroundingBlocks surroundingBlocks = getSurroundingBlocks();
        if (surroundingBlocks != null && surroundingBlocks.hasAnySurroundBlock() && !surroundingBlocks.isValid()) {
            LOGGER.warn("Invalid blockId in surround blocks：");
            return false;
        }

        // 催化剂不能与起始物品相同，且列表中的每个条目都必须有效
        CatalystItems catalystItems = getCatalystItems();
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

        InnerFluid innerFluid = getInnerFluid();
        if (innerFluid != null && innerFluid.hasInnerFluid() && !innerFluid.isValid()) {
            LOGGER.warn("Invalid fluid id in fluid condition: {}", innerFluid.fluidId());
            return false;
        }

        try {
            getConditionExpression().compile(this);
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid condition expression for source item: {}", itemId, e);
            return false;
        }

        if (consumptionDirective != null && !consumptionDirective.isValid(itemId)) {
            LOGGER.warn("Invalid consumption directive for source item: {}", itemId);
            return false;
        }

        return additionalCheck();
    }

    // ========== 配置复杂度 ========== //
    // 条件叶总数仅作为相同显式优先级下的特异性兜底
    public int computeComplexity() {
        return getConditionExpression().leafCount();
    }

    public Item getStartItem() {
        if (cacheInitialized) {
            return cachedStartItem;
        }
        ResourceLocation rl = parseItemRl();
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
    }

    protected boolean additionalCheck() {return true;}

    protected boolean isResultIdValid() {
        return IdValidator.isValidResultId(resultId);
    }

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
        ConditionLeaf leaf = getConditionExpression().firstGroupLeaf(BuiltinConditionTypes.DIMENSION);
        if (leaf == null || leaf.negated()) {
            return null;
        }
        BuiltinConditionParameters.Dimension parameters =
                leaf.parametersAs(BuiltinConditionParameters.Dimension.class);
        return parameters == null ? null : parameters.dimension();
    }
    public void setDimension(@Nullable String dimension) {
        if (!getConditionExpression().supportsLegacyEditor()) {
            return;
        }
        getConditionExpression().setFirstGroupLeaf(BuiltinConditionTypes.DIMENSION,
                dimension == null || dimension.isBlank()
                        ? null : new BuiltinConditionParameters.Dimension(dimension));
    }

    public @Nullable String getBiome() {
        ConditionLeaf leaf = getConditionExpression().firstGroupLeaf(BuiltinConditionTypes.BIOME);
        if (leaf == null || leaf.negated()) {
            return null;
        }
        BuiltinConditionParameters.Biome parameters =
                leaf.parametersAs(BuiltinConditionParameters.Biome.class);
        return parameters == null ? null : parameters.biome();
    }

    public void setBiome(@Nullable String biome) {
        if (!getConditionExpression().supportsLegacyEditor()) {
            return;
        }
        getConditionExpression().setFirstGroupLeaf(BuiltinConditionTypes.BIOME,
                biome == null || biome.isBlank() ? null : new BuiltinConditionParameters.Biome(biome));
    }

    public BuiltinConditionParameters.WeatherMode getConditionWeatherMode() {
        ConditionLeaf leaf = getConditionExpression().firstGroupLeaf(BuiltinConditionTypes.WEATHER);
        if (leaf == null || leaf.negated()) {
            return BuiltinConditionParameters.WeatherMode.ANY;
        }
        BuiltinConditionParameters.Weather parameters =
                leaf.parametersAs(BuiltinConditionParameters.Weather.class);
        return parameters == null || parameters.weather() == null
                ? BuiltinConditionParameters.WeatherMode.ANY : parameters.weather();
    }

    public void setConditionWeatherMode(BuiltinConditionParameters.WeatherMode weather) {
        if (!getConditionExpression().supportsLegacyEditor()) {
            return;
        }
        getConditionExpression().setFirstGroupLeaf(BuiltinConditionTypes.WEATHER,
                weather == null || weather == BuiltinConditionParameters.WeatherMode.ANY
                        ? null : new BuiltinConditionParameters.Weather(weather));
    }
    public void setResultMultiple(int resultMultiple) {
        this.resultMultiple = resultMultiple;
    }
    public void setSourceMultiple(int sourceMultiple) {
        this.sourceMultiple = sourceMultiple;
    }
    public boolean isNeedOutdoor() {
        ConditionLeaf leaf = getConditionExpression().firstGroupLeaf(BuiltinConditionTypes.OUTDOOR);
        return leaf != null && !leaf.negated();
    }
    public void setNeedOutdoor(boolean needOutdoor) {
        if (!getConditionExpression().supportsLegacyEditor()) {
            return;
        }
        getConditionExpression().setFirstGroupLeaf(BuiltinConditionTypes.OUTDOOR,
                needOutdoor ? new BuiltinConditionParameters.Outdoor() : null);
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
        ConditionLeaf leaf = getConditionExpression().firstGroupLeaf(BuiltinConditionTypes.SURROUNDING_BLOCKS);
        if (leaf == null || leaf.negated()) {
            return null;
        }
        BuiltinConditionParameters.SurroundingBlocksParameter parameters =
                leaf.parametersAs(BuiltinConditionParameters.SurroundingBlocksParameter.class);
        return parameters == null ? null : parameters.blocks();
    }

    public void setSurroundingBlocks(@Nullable SurroundingBlocks surroundingBlocks) {
        if (!getConditionExpression().supportsLegacyEditor()) {
            return;
        }
        getConditionExpression().setFirstGroupLeaf(BuiltinConditionTypes.SURROUNDING_BLOCKS,
                surroundingBlocks == null || !surroundingBlocks.hasAnySurroundBlock()
                        ? null : new BuiltinConditionParameters.SurroundingBlocksParameter(surroundingBlocks));
    }

    public @Nullable CatalystItems getCatalystItems() {
        ConditionLeaf leaf = getConditionExpression().firstGroupLeaf(BuiltinConditionTypes.CATALYST_PRESENT);
        List<CatalystItems.CatalystEntry> entries = List.of();
        if (leaf != null && !leaf.negated()) {
            BuiltinConditionParameters.CatalystPresent parameters =
                    leaf.parametersAs(BuiltinConditionParameters.CatalystPresent.class);
            if (parameters != null && parameters.items() != null) {
                entries = parameters.items();
            }
        }
        if (entries.isEmpty() && consumptionDirective != null) {
            entries = consumptionDirective.catalystItems();
        }
        if (entries.isEmpty()) {
            return null;
        }
        CatalystItems catalystItems = new CatalystItems();
        catalystItems.setCatalystList(entries);
        catalystItems.setCatalystConsume(consumptionDirective != null
                && !consumptionDirective.catalystItems().isEmpty());
        return catalystItems;
    }

    public void setCatalystItems(@Nullable CatalystItems catalystItems) {
        if (!getConditionExpression().supportsLegacyEditor()) {
            return;
        }
        List<CatalystItems.CatalystEntry> entries = catalystItems == null
                ? List.of() : catalystItems.getCatalystList();
        getConditionExpression().setFirstGroupLeaf(BuiltinConditionTypes.CATALYST_PRESENT,
                entries.isEmpty() ? null : new BuiltinConditionParameters.CatalystPresent(entries));
        ConsumptionDirective directive = ensureConsumptionDirective();
        directive.setCatalystItems(catalystItems != null && catalystItems.isCatalystConsume()
                ? entries : null);
        clearEmptyConsumptionDirective();
    }

    public @Nullable InnerFluid getInnerFluid() {
        ConditionLeaf leaf = getConditionExpression().firstGroupLeaf(BuiltinConditionTypes.FLUID_PRESENT);
        String fluidId = null;
        boolean requireSource = true;
        if (leaf != null && !leaf.negated()) {
            BuiltinConditionParameters.FluidPresent parameters =
                    leaf.parametersAs(BuiltinConditionParameters.FluidPresent.class);
            if (parameters != null) {
                fluidId = parameters.fluid();
                requireSource = parameters.requireSource();
            }
        }
        ConsumptionDirective.FluidConsumption fluidConsumption = consumptionDirective == null
                ? null : consumptionDirective.innerFluid();
        if ((fluidId == null || fluidId.isBlank()) && fluidConsumption != null) {
            fluidId = fluidConsumption.fluid();
            requireSource = fluidConsumption.requireSource();
        }
        return fluidId == null || fluidId.isBlank() ? null
                : new InnerFluid(fluidId, requireSource, fluidConsumption != null);
    }

    public void setInnerFluid(@Nullable InnerFluid innerFluid) {
        if (!getConditionExpression().supportsLegacyEditor()) {
            return;
        }
        getConditionExpression().setFirstGroupLeaf(BuiltinConditionTypes.FLUID_PRESENT,
                innerFluid == null || !innerFluid.hasInnerFluid() ? null
                        : new BuiltinConditionParameters.FluidPresent(
                                innerFluid.fluidId(), innerFluid.requireSource()));
        ConsumptionDirective directive = ensureConsumptionDirective();
        directive.setInnerFluid(innerFluid != null && innerFluid.consumeFluid()
                ? new ConsumptionDirective.FluidConsumption(
                        innerFluid.fluidId(), innerFluid.requireSource()) : null);
        clearEmptyConsumptionDirective();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public ConditionExpression getConditionExpression() {
        if (conditionExpression == null) {
            conditionExpression = new ConditionExpression();
        }
        return conditionExpression;
    }

    public void setConditionExpression(@Nullable ConditionExpression conditionExpression) {
        this.conditionExpression = conditionExpression == null
                ? new ConditionExpression() : conditionExpression;
    }

    public @Nullable ConsumptionDirective getConsumptionDirective() {
        return consumptionDirective;
    }

    public void setConsumptionDirective(@Nullable ConsumptionDirective consumptionDirective) {
        this.consumptionDirective = consumptionDirective;
        clearEmptyConsumptionDirective();
    }

    public int getPriority() {
        return priority;
    }

    public boolean hasFluidCondition() {
        return getConditionExpression().containsType(BuiltinConditionTypes.FLUID_PRESENT);
    }

    public boolean consumesFluid() {
        return consumptionDirective != null && consumptionDirective.innerFluid() != null;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public void setNotes(@Nullable String notes) {
        this.notes = notes == null || notes.isBlank() ? null : notes;
    }

    private ConsumptionDirective ensureConsumptionDirective() {
        if (consumptionDirective == null) {
            consumptionDirective = new ConsumptionDirective();
        }
        return consumptionDirective;
    }

    private void clearEmptyConsumptionDirective() {
        if (consumptionDirective != null && consumptionDirective.isEmpty()) {
            consumptionDirective = null;
        }
    }

    // config类型、uuid只能获取不能设置
    public ConversionType getConversionType() {
        return conversionType;
    }

    public String getInternalId() {
        return internalId;
    }
}
