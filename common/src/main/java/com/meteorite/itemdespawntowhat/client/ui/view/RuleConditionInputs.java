package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.client.ui.condition.ConditionJsonEditor;
import com.meteorite.itemdespawntowhat.client.ui.condition.ConditionParameterInput;
import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 新协议条件类型的客户端参数编辑器登记表（10 个内置条件）。
 * 复杂嵌套参数（biome 气候区间）退化为 JSON 编辑器；未登记的类型返回 null。
 */
public final class RuleConditionInputs {

    private static final List<ResourceLocation> ORDER = List.of(
            id("dimension"),
            id("biome"),
            id("weather"),
            id("outdoor"),
            id("surrounding_blocks"),
            id("catalyst_present"),
            id("fluid_present"),
            id("time_of_day"),
            id("y_level"),
            id("light_level")
    );

    private RuleConditionInputs() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 条件类型选择顺序（与 core 内置类型一致）
    public static List<ResourceLocation> all() {
        return ORDER;
    }

    // 类型显示名，沿用条件类型既有 i18n key
    public static Component displayName(ResourceLocation conditionType) {
        return Component.translatable("condition.itemdespawntowhat.type." + conditionType.getPath());
    }

    // 是否已登记（含 JSON 编辑器承载的类型）
    public static boolean isKnown(@Nullable ResourceLocation conditionType) {
        if (conditionType == null) {
            return false;
        }
        return ConditionParams.specsOf(conditionType) != null
                || ConditionParams.JSON_PARAM_PATHS.contains(conditionType.getPath());
    }

    // 创建参数控件；未登记类型返回 null（由调用方退化为只读 JSON 编辑器）
    public static @Nullable ConditionParameterInput create(@Nullable ResourceLocation conditionType, Font font) {
        if (conditionType == null) {
            return null;
        }
        List<ParamSpec> specs = ConditionParams.specsOf(conditionType);
        if (specs != null) {
            return new SpecConditionParamInput(font, specs);
        }
        if (ConditionParams.JSON_PARAM_PATHS.contains(conditionType.getPath())) {
            return new ConditionJsonEditor(font);
        }
        return null;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}
