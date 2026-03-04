package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionChecker;
import com.meteorite.itemdespawntowhat.condition.ConditionCheckerUtil;
import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.CatalystItems;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.InnerFluid;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.event.ItemConversionEvent;
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

import java.util.Optional;
import java.util.UUID;

public abstract class BaseConversionConfig {
    protected static final Logger LOGGER = LogManager.getLogger();
    // 内部标识符，不会进行序列化
    protected transient String internalId;
    protected transient ConfigType configType;
    // 基础限制实体数量，没有规定实体数量的时候返回这个值
    protected static final int DEFAULT_RESULT_LIMIT = 30;
    // 检查限制的范围
    protected static final int MAX_RADIUS = 6;
    // 默认转化时间300秒
    protected static final int DEFAULT_CONVERSION_TIME = 300;

    // 物品注册名
    @JsonOrder(1)
    @SerializedName("item")
    protected ResourceLocation itemId;
    // 消失的方式 - 自然消失 timeout， 岩浆烧毁 lava，暂时没有作用，未来再添加
    // @JsonOrder(2)
    // @SerializedName("disappear_cause")
    protected String disappearCause;
    // 所处维度
    @JsonOrder(2)
    @SerializedName("dimension")
    protected String dimension;
    // 是否需要露天
    @JsonOrder(2)
    @SerializedName("need_outdoor")
    protected boolean needOutdoor;
    // 六个方向的方块
    @JsonOrder(2)
    @SerializedName("surrounding_blocks")
    protected SurroundingBlocks surroundingBlocks;
    // 催化剂物品
    @JsonOrder(2)
    @SerializedName("catalyst_items")
    protected CatalystItems catalystItems;
    @JsonOrder(2)
    @SerializedName("inner_fluid")
    protected InnerFluid innerFluid;
    // 生成结果注册名
    @JsonOrder(3)
    @SerializedName("result")
    protected ResourceLocation resultId;
    // 转化的时间，单位为秒
    @JsonOrder(3)
    @SerializedName("conversion_time")
    protected int conversionTime;
    //生成的倍率
    @JsonOrder(3)
    @SerializedName("result_multiple")
    protected int resultMultiple;

    public BaseConversionConfig() {
        this.internalId = UUID.randomUUID().toString();
    }

    public BaseConversionConfig(ResourceLocation item, ResourceLocation result) {
        this();
        this.itemId = item;
        //this.disappearCause = "timeout";
        this.dimension = "";
        this.needOutdoor = false;
        this.surroundingBlocks = new SurroundingBlocks();
        this.catalystItems = new CatalystItems();
        this.resultId = result;
        this.conversionTime = 5;
        this.resultMultiple = 1;
    }

    // 限制条件，子类可覆盖，不符合条件的配置不会被读取
    public boolean shouldProcess() {
        if (!isValidResourceLocation(itemId)
                ||!isValidResourceLocation(resultId)) {
            LOGGER.warn("invalid resource location, itemId is {} or resultId is {}",itemId, resultId);
            return false;
        }

        if (conversionTime <= 0 || conversionTime> 300) {
            LOGGER.warn("conversionTime should limit in 1-300, current is {}", conversionTime);
            return false;
        }

        if (resultMultiple <= 0) {
            LOGGER.warn("resultMultiple should be at least 1, current is {}", resultMultiple);
            return false;
        }

        // 催化剂不能与起始物品相同
        if (catalystItems.hasAnyCatalyst()) {
            return catalystItems.getCatalystList().stream().noneMatch(
                    entry -> entry.getItemId().equals(itemId));
        }

        // 确保内部ID存在
        if (this.getInternalId() == null || this.getInternalId().isEmpty()) {
            this.setInternalId(UUID.randomUUID().toString());
        }

        if (innerFluid != null && innerFluid.hasInnerFluid()) {
            if (!isValidResourceLocation(innerFluid.getFluidId())) {
                LOGGER.warn("Invalid fluid id in fluid condition: {}", innerFluid.getFluidId());
                return false;
            }
        }

        return true;
    }

    // 构建条件检查器
    public ConditionChecker buildConditionChecker() {
        if (!this.shouldProcess()) {
            return null;
        }

        ConditionContext ctx = new ConditionContext(getDimension(),
                isNeedOutdoor(),
                getSurroundingBlocks(),
                getCatalystItems(),
                getInnerFluid());
        return ConditionCheckerUtil.buildCombinedChecker(ctx);
    }

    // 给子类用的方法
    protected void consumeAllOthers(ItemEntity itemEntity, int startItemCount) {
        consumeCatalysts(itemEntity, startItemCount);
        consumeFluid(itemEntity);
    }

    // 消耗催化剂的方法
    protected void consumeCatalysts(ItemEntity itemEntity, int startItemCount) {
        if (catalystItems == null || !catalystItems.hasAnyCatalyst()) {
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
        int catalystLimit = (catalystItems != null)
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
    protected void addRemainingItems(ItemEntity itemEntity, ServerLevel serverLevel, int itemsRemaining) {
        if (itemsRemaining <= 0) {
            return;
        }
        ItemStack returnStack = itemEntity.getItem().copy();
        BlockPos pos = itemEntity.blockPosition();
        returnStack.setCount(itemsRemaining);

        ItemEntity returnItem = new ItemEntity(
                serverLevel,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                returnStack
        );
        returnItem.getPersistentData().putBoolean(ItemConversionEvent.CHECK_LOCK_TAG, true);
        serverLevel.addFreshEntity(returnItem);
        LOGGER.debug("Returned {} unused items of {}", itemsRemaining, getItemId());
    }

    public Item getStartItem() {
        return BuiltInRegistries.ITEM.get(itemId);
    }

    protected boolean isValidResourceLocation(ResourceLocation rl) {
        return rl != null && !rl.getPath().isEmpty();
    }

    public ItemStack getStartItemIcon() {
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    // ========== 子类方法 ========== //
    public abstract int countNearbyResult(ItemEntity itemEntity);
    public abstract boolean isResultLimitExceeded(ItemEntity itemEntity);
    public abstract String getResultDescriptionId();
    public abstract ItemStack getResultIcon();
    public abstract void performConversion(ItemEntity itemEntity, ServerLevel serverLevel);

    // ========== setter 和 getter ========== //
    // 转化时间限制在1-300
    public int getConversionTime() {
        return (conversionTime <= 0 || conversionTime > DEFAULT_CONVERSION_TIME) ? DEFAULT_CONVERSION_TIME : conversionTime;
    }

    // 转化倍率最小为1
    public int getResultMultiple() {
        return Math.max(1, resultMultiple);
    }

    public String getInternalId() {
        return internalId;
    }

    public void setInternalId(String internalId) {
        this.internalId = internalId;
    }

    public void setConversionTime(int conversionTime) {
        this.conversionTime = conversionTime;
    }

    public String getDimension() {
        return Optional.ofNullable(dimension).orElse("");
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getDisappearCause() {
        return disappearCause;
    }

    public void setDisappearCause(String disappearCause) {
        this.disappearCause = disappearCause;
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

    public ResourceLocation getItemId() {
        return itemId;
    }

    public void setItemId(ResourceLocation itemId) {
        this.itemId = itemId;
    }

    public ResourceLocation getResultId() {
        return resultId;
    }

    public void setResultId(ResourceLocation resultId) {
        this.resultId = resultId;
    }

    public SurroundingBlocks getSurroundingBlocks() {
        if (surroundingBlocks == null) {
            return new SurroundingBlocks();
        }
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

    // config类型只能获取不能设置
    public ConfigType getConfigType() {
        return configType;
    }
}
