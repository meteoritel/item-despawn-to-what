package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;
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
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogManager.getLogger();

    @SerializedName("inner_fluid")
    private final ResourceLocation fluidId;
    @SerializedName("require_source")
    private final boolean requireSource;
    @SerializedName("consume_fluid")
    private final boolean consumeFluid;

    public InnerFluid() {
        this.fluidId = null;
        this.requireSource = true;
        this.consumeFluid = false;
    }

    public InnerFluid(ResourceLocation fluidId, boolean requireSource, boolean consumeFluid) {
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
        if (!consumeFluid || !hasInnerFluid()) {
            return;
        }

        Level level = itemEntity.level();
        if (level.isClientSide) {
            return;
        }

        BlockPos pos = itemEntity.blockPosition();
        BlockState blockState = level.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();

        if (fluidState.isEmpty()) {
            return;
        }

        Fluid targetFluid = BuiltInRegistries.FLUID.get(fluidId);
        if (!fluidState.getType().isSame(targetFluid)) {
            return;
        }

        Block block = blockState.getBlock();

        // 普通流体方块，直接置空
        if (block instanceof LiquidBlock) {
            // 直接替换为空气，只影响当前这一格
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        // 含水方块，取走流体
        if (block instanceof LiquidBlockContainer) {
            if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos,
                        blockState.setValue(BlockStateProperties.WATERLOGGED, false),
                        Block.UPDATE_ALL
                );
                return;
            }
        }

        // 当无法识别时，跳过
        LOGGER.warn("Could not consume fluid {} at {}: unrecognized block type {}",
                fluidId, pos, block.getDescriptionId());
    }

    // ========== getter ========== //
    public boolean hasInnerFluid() {
        return fluidId != null && !fluidId.getPath().isEmpty();
    }

    public ResourceLocation getFluidId() {
        return fluidId;
    }

    public boolean isRequireSource() {
        return requireSource;
    }

    public boolean isConsumeFluid() {
        return consumeFluid;
    }
}
