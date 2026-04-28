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
        BlockState blockState = level.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();

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
