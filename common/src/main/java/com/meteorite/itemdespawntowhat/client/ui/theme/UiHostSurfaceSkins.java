package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import java.util.EnumMap;

/** 表面解析集中颜色与画法，稳定帧只读取不可变 skin。 */
final class UiHostSurfaceSkins {
    private UiHostSurfaceSkins() { }

    static EnumMap<UiHostSurface, UiControlSkin> create(UiHostPalette palette, boolean pixel, boolean minecraft) {
        EnumMap<UiHostSurface, UiControlSkin> skins = new EnumMap<>(UiHostSurface.class);
        for (UiHostSurface role : UiHostSurface.values()) {
            int fill = switch (role) {
                case WINDOW -> minecraft ? palette.surfaces().panelFill() : pixel ? UiPalette.WINDOW_FILL : UiFlatPalette.PANEL_FILL;
                case INSET -> minecraft ? palette.surfaces().inputFill() : UiPalette.SLOT_FILL;
                case HEADER -> minecraft ? palette.surfaces().headerFill() : pixel ? UiPalette.HEADER_FILL : UiFlatPalette.PANEL_HEADER_FILL;
                case PANEL -> palette.surfaces().panelFill();
                case CARD -> minecraft ? palette.surfaces().panelFill() : pixel ? UiPalette.CONTROL_FILL : palette.canvas().canvasCardFill();
                case SEGMENT -> minecraft ? palette.controls().controlFill() : pixel ? UiPalette.SLOT_FILL : UiFlatPalette.PANEL_CONTROL_FILL;
                case CHIP -> pixel ? 0xFFD6D6D6 : palette.controls().controlFill();
                case HELP -> minecraft ? palette.surfaces().panelFill() : UiFlatPalette.HELP_FILL;
            };
            int border = minecraft ? palette.surfaces().panelBorder() : switch (role) {
                case CARD -> pixel ? 0xFFA3A3A3 : palette.canvas().canvasCardBorder();
                case CHIP -> 0xFFB0B0B0;
                case SEGMENT -> pixel ? 0xFF737373 : UiFlatPalette.PANEL_BORDER;
                case HELP -> UiFlatPalette.HELP_BORDER;
                default -> palette.surfaces().panelBorder();
            };
            UiControlStyle style = new UiControlStyle(fill, palette.controls().controlHover(), fill,
                    palette.controls().controlSelected(), palette.controls().controlDisabled(),
                    palette.controls().controlBevelLight(), border, border);
            UiControlPainter painter;
            if (minecraft && (role == UiHostSurface.SEGMENT || role == UiHostSurface.CHIP)) {
                painter = UiMinecraftTheme.INSTANCE.controlSkin(UiControlRole.SECONDARY).painter();
            } else if (pixel && (role == UiHostSurface.CARD || role == UiHostSurface.CHIP || role == UiHostSurface.SEGMENT)) {
                painter = (graphics, frame) -> {
                    int outline = frame.state() == UiControlStyle.State.SELECTED ? palette.controls().accentPressed()
                            : frame.state() == UiControlStyle.State.DISABLED ? 0xFF929292
                            : frame.state() == UiControlStyle.State.HOVER ? 0xFFA3A3A3 : border;
                    UiTheme.drawRounded(graphics, frame.bounds(), role == UiHostSurface.CARD,
                            frame.style().background(frame.state()), outline);
                };
            } else {
                painter = (graphics, frame) -> {
                    UiRect rect = frame.bounds();
                    if (pixel && role == UiHostSurface.WINDOW) {
                        if (rect.width() < 2 || rect.height() < 2) return;
                        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.SHADOW);
                        UiRect inner = new UiRect(rect.x() + 1, rect.y() + 1, rect.width() - 2, rect.height() - 2);
                        graphics.fill(inner.x(), inner.y(), inner.right(), inner.bottom(), frame.style().background(frame.state()));
                        UiTheme.drawBevel(graphics, inner, UiPalette.WINDOW_BORDER_LIGHT, UiPalette.WINDOW_BORDER_DARK);
                    } else {
                        UiFlatSkin.drawSurface(graphics, rect, frame.style().background(frame.state()), border);
                        if (pixel && role != UiHostSurface.HELP) {
                            UiTheme.drawBevel(graphics, rect, role == UiHostSurface.INSET ? UiPalette.SLOT_BORDER_DARK : UiPalette.PANEL_BORDER_LIGHT,
                                    role == UiHostSurface.INSET ? UiPalette.SLOT_BORDER_LIGHT : UiPalette.PANEL_BORDER_DARK);
                        }
                    }
                };
            }
            skins.put(role, new UiControlSkin(style, painter, palette.surfaces().text(), palette.surfaces().disabledText()));
        }
        return skins;
    }
}
