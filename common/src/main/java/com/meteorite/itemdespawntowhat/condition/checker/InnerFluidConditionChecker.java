package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
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

import java.util.Map;

public class InnerFluidConditionChecker extends AbstractConditionChecker{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String KEY = "inner_fluid";
    private InnerFluid innerFluid;

    public InnerFluidConditionChecker() {}

    public InnerFluidConditionChecker(InnerFluid innerFluid) {
        this.innerFluid = innerFluid;
    }

    // ========== 父类抽象方法实现 ========== //
    @Override
    public String getConditionKey() {
        return KEY;
    }

    @Override
    public boolean shouldApply(ConditionContext ctx) {
        return ctx.innerFluid() != null && ctx.innerFluid().hasInnerFluid();
    }

    @Override
    public void applyCondition(Map<String, String> conditions, ConditionContext ctx) {
        ctx.innerFluid().toConditionMap(conditions, getConditionKey());
    }

    @Override
    public AbstractConditionChecker parse(Map<String, String> conditions) {
        try {
            InnerFluid parsed = new InnerFluid().fromConditionMap(conditions, getConditionKey());
            return parsed != null ? new InnerFluidConditionChecker(parsed) : null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse InnerFluid from condition map: {}", e.getMessage());
            return null;
        }
    }

    // ========== 核心检测逻辑 ========== //
    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        if (innerFluid == null || !innerFluid.hasInnerFluid()) {
            // 未配置流体条件，直接通过
            return true;
        }

        ResourceLocation targetFluidId = SafeParseUtil.parseResourceLocation(innerFluid.getFluidId());
        if (targetFluidId == null) {
            return false;
        }
        boolean requireSource = innerFluid.isRequireSource();

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
