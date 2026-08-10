package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 检查物品实体所在位置的流体状态。
 */
public final class InnerFluidConditionChecker implements ConditionChecker {
    private static final Logger LOGGER = LogManager.getLogger();
    private final InnerFluid innerFluid;

    @Override
    public String debugName() {
        return "inner_fluid";
    }

    public InnerFluidConditionChecker(InnerFluid innerFluid) {
        this.innerFluid = innerFluid;
    }

    // ========== 核心检测逻辑 ========== //
    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        if (innerFluid == null || !innerFluid.hasInnerFluid()) {
            // 未配置流体条件，直接通过
            return true;
        }

        ResourceLocation targetFluidId = SafeParseUtil.parseResourceLocation(innerFluid.fluidId());
        if (targetFluidId == null) {
            return false;
        }
        boolean requireSource = innerFluid.requireSource();

        BlockPos pos = itemEntity.blockPosition();
        BlockState blockState = level.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();

        if (fluidState.isEmpty()) {
            LOGGER.debug("InnerFluid check failed at {}: no fluid present", pos);
            return false;
        }

        Fluid fluid = fluidState.getType();
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);

        if (requireSource) {
            if (!fluidState.isSource()) {
                LOGGER.debug("InnerFluid check failed at {}: fluid {} is flowing, source required",
                        pos, fluidId);
                return false;
            }
            if (!targetFluidId.equals(fluidId)) {
                LOGGER.debug("InnerFluid check failed at {}: expected source fluid {} but found {}",
                        pos, targetFluidId, fluidId);
                return false;
            }
        } else {
            Fluid targetFluid = BuiltInRegistries.FLUID.get(targetFluidId);
            if (!fluid.isSame(targetFluid)) {
                LOGGER.debug("InnerFluid check failed at {}: expected fluid family {} but found {}",
                        pos, targetFluidId, fluidId);
                return false;
            }
        }

        LOGGER.debug("InnerFluid check passed at {}: fluid={}, isSource={}",
                pos, fluidId, fluidState.isSource());
        return true;
    }
}
