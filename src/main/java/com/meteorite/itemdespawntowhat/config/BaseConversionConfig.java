package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.util.ConditionChecker;
import com.meteorite.itemdespawntowhat.util.ConditionCheckerUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Optional;
import java.util.UUID;


public abstract class BaseConversionConfig {
    // 内部唯一标识符，不会进行序列化
    protected transient String internalId;
    // 基础限制实体数量，没有规定实体数量的时候返回这个值
    protected static final int BASE_LIMIT = 30;
    // 检查限制的范围
    protected static final int RADIUS = 6;
    // 默认转化时间300秒
    private static final int DEFAULT_CONVERSION_TIME = 300;

    // ==========         所有的序列名都在父类中         ========= //
    // ========== 子类后续的判定如果不需要这个写上也不会生效 ========= //

    // 物品注册名
    @SerializedName("item")
    protected ResourceLocation itemId;

    // 消失的方式 - 自然消失 timeout， 岩浆烧毁 lava， 暂时没有作用
    @SerializedName("disappear_cause")
    protected String disappearCause;

    // 所处维度
    @SerializedName("dimension")
    protected String dimension;

    // 是否露天
    @SerializedName("is_outdoor")
    protected String isOutdoor;

    // 六个方向的方块
    @SerializedName("surrounding_blocks")
    protected SurroundingBlocks surroundingBlocks;

    // 生成结果注册名
    @SerializedName("result")
    protected ResourceLocation resultId;

    // 转化的时间，单位为秒
    @SerializedName("conversion_time")
    protected int conversionTime;

    //生成实体的数量
    @SerializedName("result_multiple")
    protected int resultMultiple;

    // 生成实体的数量限制，当周围同类型实体超过某个数值后便不会再生成
    @SerializedName("result_limit")
    private int resultLimit;

    // 生成实体的age（如果需要）
    @SerializedName("entity_age")
    protected int entityAge;

    public BaseConversionConfig() {
        this.internalId = UUID.randomUUID().toString();
    }

    public BaseConversionConfig(ResourceLocation item) {
        this();
        this.itemId = item;
        this.disappearCause = "timeout";
        this.dimension = "";
        this.isOutdoor = "false";
        this.surroundingBlocks = new SurroundingBlocks();
        this.conversionTime = 10;
        this.resultLimit = BASE_LIMIT;
        this.resultMultiple = 1;

    }

    // 限制条件，子类可覆盖，不符合条件的配置不会被读取
    public boolean shouldProcess() {
        // 输入输出不能为空
        if (itemId == null || resultId == null) {
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
        String isOutdoor = Optional.ofNullable(this.getIsOutdoor()).orElse("");

        return ConditionCheckerUtil.buildCombinedChecker(dimension, isOutdoor, this.getSurroundingBlocks());

    }

    // ========== 子类方法 ========== //
    // 获取当前配置输入物品周围的结果数量，由子类重写
    public abstract int countNearbyResult(ItemEntity itemEntity);
    // 当前的周围结果数量是否已经超过了上限
    public abstract boolean isResultLimitExceeded(ItemEntity itemEntity);

    // ========== setter 和 getter ========== //

    // 转化时间限制在1-300
    public int getConversionTime() {
        return (conversionTime <= 0 || conversionTime > DEFAULT_CONVERSION_TIME) ? DEFAULT_CONVERSION_TIME : conversionTime;
    }

    // 限制默认为30
    public int getResultLimit() {
        return resultLimit <= 0 ? BASE_LIMIT : resultLimit;
    }

    // 转化倍率最小为1
    public int getResultMultiple() {
        return  Math.max(1, resultMultiple);
    }

    public int getEntityAge() {
        return entityAge;
    }

    public void setEntityAge(int entityAge) {
        this.entityAge = entityAge;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
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

    public String getIsOutdoor() {
        return isOutdoor;
    }

    public void setIsOutdoor(String isOutdoor) {
        this.isOutdoor = isOutdoor;
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
        return surroundingBlocks;
    }

    public void setSurroundingBlocks(SurroundingBlocks surroundingBlocks) {
        this.surroundingBlocks = surroundingBlocks;
    }
}
