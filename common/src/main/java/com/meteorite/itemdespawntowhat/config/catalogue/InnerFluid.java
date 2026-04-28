package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class InnerFluid implements ConditionSerializable<InnerFluid> {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private static final Logger LOGGER = LogManager.getLogger();

    @SerializedName("inner_fluid")
    private final String fluidId;
    @SerializedName("require_source")
    private final boolean requireSource;
    @SerializedName("consume_fluid")
    private final boolean consumeFluid;

    public InnerFluid() {
        this.fluidId = null;
        this.requireSource = true;
        this.consumeFluid = false;
    }

    public InnerFluid(String fluidId, boolean requireSource, boolean consumeFluid) {
        this.fluidId = fluidId;
        this.requireSource = requireSource;
        this.consumeFluid = consumeFluid;
    }

    @Override
    public InnerFluid fromConditionMap(Map<String, String> conditions, String conditionKey) {
        String json = conditions.get(conditionKey);
        if (json == null || json.isEmpty()) {
            return null;
        }

        InnerFluid parsed = GSON.fromJson(json, InnerFluid.class);
        return (parsed != null && parsed.hasInnerFluid()) ? parsed : null;
    }

    @Override
    public void toConditionMap(Map<String, String> out, String conditionKey) {
        if (hasInnerFluid()) {
            out.put(conditionKey, GSON.toJson(this));
        }
    }

    // 在世界上消耗流体
    public void consumeFluidFromLevel(ItemEntity itemEntity) {
        if (!isConsumeFluid()) {
            return;
        }

        Level level = itemEntity.level();
        if (level.isClientSide) {
            return;
        }

        BlockPos pos = itemEntity.blockPosition();
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty()) {
            return;
        }

        ResourceLocation fluidRl = SafeParseUtil.parseResourceLocation(fluidId);
        if (fluidRl == null) {
            return;
        }

        Fluid targetFluid = BuiltInRegistries.FLUID.get(fluidRl);
        if (!fluidState.getType().isSame(targetFluid)) {
            return;
        }
        if (requireSource && !fluidState.isSource()) {
            return;
        }

        BlockState blockState = level.getBlockState(pos);

        // 含水方块：移除含水状态，保留方块本身
        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos,
                    blockState.setValue(BlockStateProperties.WATERLOGGED, false),
                    Block.UPDATE_ALL
            );
            return;
        }

        // 流体方块（水/熔岩源头或流动）：直接设为空气
        // 非流体方块但包含流体（气泡柱、海带等依赖流体存在的方块）：
        // 流体状态非空，但方块本身既不是 LiquidBlock 也没有含水属性，
        // 移除整个方块
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }


    public boolean hasInnerFluid() {
        return fluidId != null && !fluidId.isBlank();
    }

    public boolean isConsumeFluid() {
        return hasInnerFluid() && consumeFluid;
    }

    public boolean isValid() {
        return IdValidator.isValidFluidId(fluidId);
    }

    public String getFluidId() {
        return fluidId;
    }

    public boolean isRequireSource() {
        return requireSource;
    }
}
