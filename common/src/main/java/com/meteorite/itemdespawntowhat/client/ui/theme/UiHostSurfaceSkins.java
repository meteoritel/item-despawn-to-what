package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import java.util.EnumMap;

/**
 * 表面解析集中颜色与画法，稳定帧只读取不可变 skin。
 *
 * <p>原版方案（minecraft）用 {@link UiMinecraftPalette} 的半透明表面：窗口与标题带画纵向渐变，
 * 内凹补顶部阴影与底部高光，面板保留 1 像素顶部高光，其余表面是半透明底加 1 像素描边；
 * 控件色块（SEGMENT/CHIP）继续复用 kit 的原版 button sprite。像素与扁平方案保持原有取色与画法，
 * 本类的像素 / 扁平分支不因原版方案改动而变化。</p>
 */
final class UiHostSurfaceSkins {
    private UiHostSurfaceSkins() {
    }

    static EnumMap<UiHostSurface, UiControlSkin> create(UiHostPalette palette, boolean pixel, boolean minecraft) {
        EnumMap<UiHostSurface, UiControlSkin> skins = new EnumMap<>(UiHostSurface.class);
        for (UiHostSurface role : UiHostSurface.values()) {
            int fill = switch (role) {
                case WINDOW -> minecraft ? UiMinecraftPalette.WINDOW_BOTTOM : pixel ? UiPalette.WINDOW_FILL : UiFlatPalette.PANEL_FILL;
                case INSET -> minecraft ? UiMinecraftPalette.INSET_FILL : UiPalette.SLOT_FILL;
                case HEADER -> minecraft ? UiMinecraftPalette.HEADER_BOTTOM : pixel ? UiPalette.HEADER_FILL : UiFlatPalette.PANEL_HEADER_FILL;
                case PANEL -> minecraft ? UiMinecraftPalette.PANEL_FILL : palette.surfaces().panelFill();
                case CARD -> minecraft ? UiMinecraftPalette.CARD_FILL : pixel ? UiPalette.CONTROL_FILL : palette.canvas().canvasCardFill();
                case SEGMENT -> minecraft ? palette.controls().controlFill() : pixel ? UiPalette.SLOT_FILL : UiFlatPalette.PANEL_CONTROL_FILL;
                case CHIP -> pixel ? 0xFFD6D6D6 : palette.controls().controlFill();
                case HELP -> minecraft ? UiMinecraftPalette.HELP_FILL : UiFlatPalette.HELP_FILL;
            };
            int border = minecraft ? minecraftBorder(role) : switch (role) {
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
            if (minecraft) {
                painter = minecraftPainter(role);
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

    // 原版方案按角色给描边：内凹与卡片比面板更亮一档，标题带只要底边线
    private static int minecraftBorder(UiHostSurface role) {
        return switch (role) {
            case WINDOW -> UiMinecraftPalette.WINDOW_BORDER;
            case HEADER -> UiMinecraftPalette.HEADER_EDGE;
            case INSET -> UiMinecraftPalette.INSET_BORDER;
            case CARD -> UiMinecraftPalette.CARD_BORDER;
            case HELP -> UiMinecraftPalette.HELP_BORDER;
            default -> UiMinecraftPalette.PANEL_BORDER;
        };
    }

    /**
     * 原版方案的表面画法。
     *
     * <p>窗口与标题带用纵向渐变拉开整屏与标题区，内凹补顶部阴影与底部高光模拟原版输入框，
     * 面板只有 1 像素顶部高光。渐变与描边都取常量色，绘制期不分配对象；
     * 控件色块仍交给 kit 的原版 button sprite，宿主不重绘控件。</p>
     */
    private static UiControlPainter minecraftPainter(UiHostSurface role) {
        return switch (role) {
            case WINDOW -> (graphics, frame) -> {
                UiRect rect = frame.bounds();
                if (rect.width() <= 0 || rect.height() <= 0) return;
                graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(),
                        UiMinecraftPalette.WINDOW_TOP, UiMinecraftPalette.WINDOW_BOTTOM);
                UiFlatSkin.drawOutline(graphics, rect, UiMinecraftPalette.WINDOW_BORDER);
            };
            case HEADER -> (graphics, frame) -> {
                UiRect rect = frame.bounds();
                if (rect.width() <= 0 || rect.height() <= 0) return;
                graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(),
                        UiMinecraftPalette.HEADER_TOP, UiMinecraftPalette.HEADER_BOTTOM);
                graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), UiMinecraftPalette.HEADER_EDGE);
            };
            case INSET -> (graphics, frame) -> {
                UiRect rect = frame.bounds();
                UiFlatSkin.drawSurface(graphics, rect, frame.style().background(frame.state()), UiMinecraftPalette.INSET_BORDER);
                if (rect.width() > 2 && rect.height() > 2) {
                    graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, UiMinecraftPalette.INSET_SHADOW);
                    graphics.fill(rect.x() + 1, rect.bottom() - 2, rect.right() - 1, rect.bottom() - 1,
                            UiMinecraftPalette.INSET_HIGHLIGHT);
                }
            };
            case PANEL -> (graphics, frame) -> {
                UiRect rect = frame.bounds();
                UiFlatSkin.drawSurface(graphics, rect, frame.style().background(frame.state()), UiMinecraftPalette.PANEL_BORDER);
                if (rect.width() > 2 && rect.height() > 2) {
                    graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 2, UiMinecraftPalette.PANEL_HIGHLIGHT);
                }
            };
            case CARD -> (graphics, frame) -> UiFlatSkin.drawSurface(graphics, frame.bounds(),
                    frame.style().background(frame.state()), UiMinecraftPalette.CARD_BORDER);
            case HELP -> (graphics, frame) -> UiFlatSkin.drawSurface(graphics, frame.bounds(),
                    frame.style().background(frame.state()), UiMinecraftPalette.HELP_BORDER);
            case SEGMENT, CHIP -> UiMinecraftTheme.INSTANCE.controlSkin(UiControlRole.SECONDARY).painter();
        };
    }
}
