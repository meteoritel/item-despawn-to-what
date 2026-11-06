package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceSkin;

import java.util.EnumMap;

/** 宿主像素与扁平表面定义；原版完整皮肤由 kit 拥有。 */
final class UiHostSurfaceSkins {
    private UiHostSurfaceSkins() { }

    static EnumMap<UiSurfaceRole, UiSurfaceSkin> pixel(UiHostPalette palette) {
        EnumMap<UiSurfaceRole, UiSurfaceSkin> skins = flat(palette);
        skins.put(UiSurfaceRole.WINDOW, surface(palette, UiPalette.WINDOW_FILL, palette.surfaces().panelBorder())
                .withPainter((g, r, state, skin) -> {
                    if (r.width() < 2 || r.height() < 2) return;
                    g.fill(r.x(), r.y(), r.right(), r.bottom(), UiPalette.SHADOW);
                    UiRect inner = new UiRect(r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2);
                    g.fill(inner.x(), inner.y(), inner.right(), inner.bottom(), skin.style().background(state));
                    UiTheme.drawBevel(g, inner, UiPalette.WINDOW_BORDER_LIGHT, UiPalette.WINDOW_BORDER_DARK);
                }));
        skins.put(UiSurfaceRole.INSET, bevel(palette, UiPalette.SLOT_FILL, UiPalette.SLOT_BORDER_DARK, UiPalette.SLOT_BORDER_LIGHT));
        skins.put(UiSurfaceRole.HEADER, bevel(palette, UiPalette.HEADER_FILL, UiPalette.PANEL_BORDER_LIGHT, UiPalette.PANEL_BORDER_DARK));
        skins.put(UiSurfaceRole.PANEL, bevel(palette, palette.surfaces().panelFill(), UiPalette.PANEL_BORDER_LIGHT, UiPalette.PANEL_BORDER_DARK));
        skins.put(UiSurfaceRole.CARD, rounded(palette, UiPalette.CONTROL_FILL, 0xFFA3A3A3, true));
        skins.put(UiSurfaceRole.CHIP, rounded(palette, 0xFFD6D6D6, 0xFFB0B0B0, false));
        skins.put(UiSurfaceRole.SEGMENT, rounded(palette, UiPalette.SLOT_FILL, 0xFF737373, false));
        skins.put(UiSurfaceRole.DIVIDER, UiSurfaceSkin.fill(UiPalette.PANEL_BORDER_DARK).withPainter((g, r, state, skin) -> {
            g.fill(r.x(), r.y(), r.right(), r.y() + 1, skin.style().background(state));
            g.fill(r.x(), r.y() + 1, r.right(), r.y() + 2, UiPalette.PANEL_BORDER_LIGHT);
        }));
        skins.put(UiSurfaceRole.SCROLL_TRACK, UiSurfaceSkin.fill(UiPalette.SCROLL_TRACK)
                .withPainter((g, r, state, skin) -> UiTheme.drawScrollTrack(g, r)));
        skins.put(UiSurfaceRole.SCROLL_THUMB, UiSurfaceSkin.fill(UiPalette.SCROLL_THUMB)
                .withPainter((g, r, state, skin) -> UiTheme.drawScrollThumb(g, r)));
        skins.put(UiSurfaceRole.SELECTION, UiSurfaceSkin.fill(UiPalette.CONTROL_SELECTED));
        skins.put(UiSurfaceRole.SELECT_MARKER, marker(UiPalette.TEXT_PRIMARY));
        skins.put(UiSurfaceRole.FOCUS, pixelFocus());
        return skins;
    }

    static EnumMap<UiSurfaceRole, UiSurfaceSkin> flat(UiHostPalette palette) {
        EnumMap<UiSurfaceRole, UiSurfaceSkin> skins = new EnumMap<>(UiSurfaceRole.class);
        for (UiSurfaceRole role : UiSurfaceRole.values()) {
            int fill = switch (role) {
                case WINDOW -> UiFlatPalette.PANEL_FILL;
                case INSET -> UiPalette.SLOT_FILL;
                case HEADER -> UiFlatPalette.PANEL_HEADER_FILL;
                case PANEL -> palette.surfaces().panelFill();
                case CARD -> palette.canvas().canvasCardFill();
                case SEGMENT, SCROLL_THUMB -> UiFlatPalette.PANEL_CONTROL_FILL;
                case CHIP -> palette.controls().controlFill();
                case HELP -> UiFlatPalette.HELP_FILL;
                case DIVIDER -> UiFlatPalette.PANEL_BORDER;
                case SCROLL_TRACK -> UiFlatPalette.PANEL_INSET_FILL;
                case SELECTION -> UiFlatPalette.PANEL_CONTROL_SELECTED;
                case SELECT_MARKER -> UiFlatPalette.PANEL_TEXT;
                case FOCUS -> UiFlatPalette.FOCUS_OUTLINE;
            };
            int border = switch (role) {
                case CARD -> palette.canvas().canvasCardBorder();
                case CHIP -> 0xFFB0B0B0;
                case SEGMENT -> UiFlatPalette.PANEL_BORDER;
                case HELP -> UiFlatPalette.HELP_BORDER;
                default -> palette.surfaces().panelBorder();
            };
            skins.put(role, switch (role) {
                case DIVIDER, SCROLL_TRACK, SCROLL_THUMB, SELECTION -> UiSurfaceSkin.fill(fill);
                case SELECT_MARKER -> marker(fill);
                case FOCUS -> UiSurfaceSkin.fill(fill).withPainter((g, r, state, skin) ->
                        UiFlatSkin.drawOutline(g, r, skin.style().background(state)));
                default -> surface(palette, fill, border);
            });
        }
        return skins;
    }

    private static UiSurfaceSkin surface(UiHostPalette palette, int fill, int border) {
        return UiSurfaceSkin.flat(fill, border).withStyle(new UiControlStyle(fill, palette.controls().controlHover(), fill,
                palette.controls().controlSelected(), palette.controls().controlDisabled(),
                palette.controls().controlBevelLight(), border, border));
    }

    private static UiSurfaceSkin bevel(UiHostPalette palette, int fill, int light, int dark) {
        return surface(palette, fill, palette.surfaces().panelBorder()).withPainter((g, r, state, skin) -> {
            UiFlatSkin.drawSurface(g, r, skin.style().background(state), skin.colors().border());
            UiTheme.drawBevel(g, r, light, dark);
        });
    }

    private static UiSurfaceSkin rounded(UiHostPalette palette, int fill, int border, boolean card) {
        return surface(palette, fill, border).withPainter((g, r, state, skin) -> {
            int outline = switch (state) {
                case SELECTED -> palette.controls().accentPressed();
                case DISABLED -> 0xFF929292;
                case HOVER -> 0xFFA3A3A3;
                default -> skin.colors().border();
            };
            UiTheme.drawRounded(g, r, card, skin.style().background(state), outline);
        });
    }

    private static UiSurfaceSkin marker(int color) {
        return UiSurfaceSkin.fill(color).withPainter((g, r, state, skin) -> {
            int x = r.x() + 2;
            int mid = r.y() + r.height() / 2;
            int fill = skin.style().background(state);
            g.fill(x, mid, x + 1, mid + 1, fill);
            g.fill(x + 1, mid - 1, x + 2, mid + 2, fill);
            g.fill(x + 2, mid - 2, x + 3, mid + 3, fill);
        });
    }

    private static UiSurfaceSkin pixelFocus() {
        return UiSurfaceSkin.fill(UiPalette.FOCUS_OUTLINE).withPainter((g, r, state, skin) -> {
            if (r.width() >= 4 && r.height() >= 4)
                g.renderOutline(r.x(), r.y(), r.width(), r.height(), skin.style().background(state));
        });
    }
}
