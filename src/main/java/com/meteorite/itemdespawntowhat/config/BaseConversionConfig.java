package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionChecker;
import com.meteorite.itemdespawntowhat.condition.ConditionCheckerUtil;
import com.meteorite.itemdespawntowhat.util.JsonOrder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

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

        // 确保内部ID存在
        if (this.getInternalId() == null || this.getInternalId().isEmpty()) {
            this.setInternalId(UUID.randomUUID().toString());
        }

        return true;
    }

    // 构建条件检查器
    public ConditionChecker buildConditionChecker() {
        if (!this.shouldProcess()) {
            return null;
        }

        String dimension = Optional.ofNullable(this.getDimension()).orElse("");
        return ConditionCheckerUtil.buildCombinedChecker(dimension, this.isNeedOutdoor(), this.getSurroundingBlocks());
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
    // 获取当前配置输入物品周围的结果数量
    public abstract int countNearbyResult(ItemEntity itemEntity);
    // 当前的周围结果数量是否已经超过了上限
    public abstract boolean isResultLimitExceeded(ItemEntity itemEntity);
    // 获得结果对应的对象键名称
    public abstract String getResultDescriptionId();
    public abstract ItemStack getResultIcon();
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
        return dimension;
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

    // config类型只能获取不能设置
    public ConfigType getConfigType() {
        return configType;
    }
}
