package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** 基于完整主题组合差异；所有配置在构造期解析，稳定帧只查询不可变定义。 */
public final class UiThemes {
    private UiThemes() { }

    /** 从 kit Minecraft 默认外观派生。 */
    public static Builder builder() { return builder(UiMinecraftTheme.INSTANCE); }

    /** 快照基底当前定义；调用顺序决定覆盖顺序，后一次修改作用于已解析的皮肤。 */
    public static Builder builder(UiStyleSource base) { return new Builder(base); }

    /** 配置期使用的组合器；build 后继续编辑不会改变已生成的主题。 */
    public static final class Builder {
        private final EnumMap<UiControlRole, UiControlSkin> controls = new EnumMap<>(UiControlRole.class);
        private final EnumMap<UiSurfaceRole, UiSurfaceSkin> surfaces = new EnumMap<>(UiSurfaceRole.class);
        private final EnumMap<UiSliderRole, UiSliderSkin> sliders = new EnumMap<>(UiSliderRole.class);
        private UiSurfaceStyle colors;
        private UiTooltipSkin tooltip;
        private UiThemeMetrics metrics;

        private Builder(UiStyleSource base) {
            for (UiControlRole role : UiControlRole.values()) controls.put(role, base.controlSkin(role));
            for (UiSurfaceRole role : UiSurfaceRole.values()) surfaces.put(role, base.surfaceSkin(role));
            for (UiSliderRole role : UiSliderRole.values()) sliders.put(role, base.sliderSkin(role));
            colors = base.surfaces();
            tooltip = base.tooltipSkin();
            metrics = base.metrics();
        }

        /** 替换一个完整控件皮肤；sprite 的像素配色由资源或染色描述决定。 */
        public Builder control(UiControlRole role, UiControlSkin skin) {
            controls.put(role, Objects.requireNonNull(skin, "skin"));
            return this;
        }

        /** 修改当前角色的结构、文字或 painter，保留未修改部分。 */
        public Builder control(UiControlRole role, UnaryOperator<UiControlSkin> edit) { return control(role, edit.apply(controls.get(role))); }

        /** 替换一个完整表面皮肤。 */
        public Builder surface(UiSurfaceRole role, UiSurfaceSkin skin) {
            surfaces.put(role, Objects.requireNonNull(skin, "skin"));
            return this;
        }

        /** 修改当前表面；纯色、渐变与装饰 painter 从修改后的皮肤取参数。 */
        public Builder surface(UiSurfaceRole role, UnaryOperator<UiSurfaceSkin> edit) { return surface(role, edit.apply(surfaces.get(role))); }

        /** 替换滑块的结构与画法。 */
        public Builder slider(UiSliderRole role, UiSliderSkin skin) {
            sliders.put(role, Objects.requireNonNull(skin, "skin"));
            return this;
        }

        /** 同步内容语义色、输入/面板/标题/选择表面、控件文字与提示颜色；保留各自 painter。 */
        public Builder colors(UiSurfaceStyle next) {
            Objects.requireNonNull(next, "colors");
            recolor(UiSurfaceRole.INSET, colors.inputFill(), next.inputFill(), colors.inputBorder(), next.inputBorder());
            recolor(UiSurfaceRole.PANEL, colors.panelFill(), next.panelFill(), colors.panelBorder(), next.panelBorder());
            if (colors.headerFill() != next.headerFill()) surface(UiSurfaceRole.HEADER, skin -> skin.withFill(next.headerFill()));
            if (colors.selectionFill() != next.selectionFill()) surface(UiSurfaceRole.SELECTION, skin -> skin.withFill(next.selectionFill()));
            if (colors.text() != next.text()) surface(UiSurfaceRole.SELECT_MARKER, skin -> skin.withFill(next.text()));
            if (colors.text() != next.text() || colors.disabledText() != next.disabledText())
                controls.replaceAll((role, skin) -> skin.withTextColors(
                        role == UiControlRole.DISABLED ? next.disabledText()
                                : colors.text() == next.text() ? skin.textColor() : next.text(),
                        next.disabledText()));
            tooltip = new UiTooltipSkin(next, tooltip.padding(), tooltip.painter());
            colors = next;
            return this;
        }

        /** 修改一个语义组，无需重写整份颜色记录。 */
        public Builder colors(UnaryOperator<UiSurfaceStyle> edit) { return colors(edit.apply(colors)); }

        /** 完整提示覆盖在本次调用后生效；后续 colors 会保留其 painter 并更新颜色。 */
        public Builder tooltip(UiTooltipSkin skin) {
            tooltip = Objects.requireNonNull(skin, "skin");
            colors = colors.withTooltip(skin.style().tooltipFill(), skin.style().tooltipBorderTop(),
                    skin.style().tooltipBorderBottom(), skin.style().tooltipText());
            return this;
        }

        /** 替换共享布局度量；发布后的重排由根负责。 */
        public Builder metrics(UiThemeMetrics next) { metrics = Objects.requireNonNull(next, "metrics"); return this; }

        /** 复制角色表生成不可变主题；skin 与资源描述本身不可变。 */
        public UiStyleSource build() {
            return new Theme(Map.copyOf(controls), Map.copyOf(surfaces), Map.copyOf(sliders), colors, tooltip, metrics);
        }

        private void recolor(UiSurfaceRole role, int previousFill, int fill, int previousBorder, int border) {
            if (previousFill == fill && previousBorder == border) return;
            surface(role, skin -> {
                UiSurfaceSkin changed = previousFill == fill ? skin : skin.withFill(fill);
                UiSurfaceSkin.Colors decoration = changed.colors();
                return changed.withColors(new UiSurfaceSkin.Colors(decoration.top(), decoration.bottom(),
                        previousBorder == border ? decoration.border() : border,
                        decoration.highlight(), decoration.shadow()));
            });
        }
    }

    private record Theme(Map<UiControlRole, UiControlSkin> controls, Map<UiSurfaceRole, UiSurfaceSkin> backgrounds,
                         Map<UiSliderRole, UiSliderSkin> sliders, UiSurfaceStyle surfaces,
                         UiTooltipSkin tooltipSkin, UiThemeMetrics metrics) implements UiStyleSource {
        @Override public UiControlSkin controlSkin(UiControlRole role) { return controls.get(role); }
        @Override public UiControlStyle controlStyle(UiControlRole role) { return controlSkin(role).style(); }
        @Override public int controlTextColor(UiControlRole role) {
            UiControlSkin skin = controlSkin(role);
            return role == UiControlRole.DISABLED ? skin.disabledTextColor() : skin.textColor();
        }
        @Override public UiSurfaceSkin surfaceSkin(UiSurfaceRole role) { return backgrounds.get(role); }
        @Override public UiSliderSkin sliderSkin(UiSliderRole role) { return sliders.get(role); }
    }
}
