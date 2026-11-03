package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeContext;
import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;

/** 直接复用 kit 原版主题，业务图标与画布语义由宿主扁平主题补齐。 */
final class UiMinecraftHostTheme extends UiThemeContext<UiStyleSource> implements UiHostTheme {
    static final UiHostTheme INSTANCE = new UiMinecraftHostTheme();
    private UiMinecraftHostTheme() { super(UiMinecraftTheme.INSTANCE); }
    private UiHostPalette palette;
    @Override public UiHostPalette palette() {
        if (palette == null) {
            UiHostPalette base = UiHostThemes.flatTheme().palette();
            palette = new UiHostPalette(
                    surfaces().text(),
                    surfaces().secondaryText(),
                    surfaces().disabledText(),
                    surfaces().inputText(),
                    base.controlFill(),
                    base.controlHover(),
                    base.controlSelected(),
                    base.controlDisabled(),
                    base.controlBevelLight(),
                    base.controlBevelDark(),
                    base.accent(),
                    base.accentHover(),
                    base.accentPressed(),
                    base.danger(),
                    surfaces().inputBorder(),
                    surfaces().inputFill(),
                    surfaces().headerText(),
                    base.listRowOdd(),
                    base.link(),
                    surfaces().modalDim(),
                    base.scrollTrack(),
                    base.scrollThumb(),
                    base.warning(),
                    base.success(),
                    surfaces().panelFill(),
                    base.codeKey(),
                    base.codeString(),
                    base.codeNumber(),
                    base.codeLiteral(),
                    base.codePunctuation(),
                    base.codeGutterText(),
                    base.codeGutterLine(),
                    base.codeSectionFill(),
                    base.codeSectionText(),
                    base.codePathText(),
                    base.chromeBorder(),
                    base.chromeDivider(),
                    base.statusText(),
                    base.statusTextMuted(),
                    base.statusTextDanger(),
                    base.statusTextWarning(),
                    base.helpText(),
                    base.helpTextMuted(),
                    surfaces().text(),
                    base.panelControlFill(),
                    base.canvasFill(),
                    base.canvasBorder(),
                    base.canvasGridDot(),
                    base.canvasHintText(),
                    base.canvasLink(),
                    base.canvasLinkSelected(),
                    base.canvasCardFill(),
                    base.canvasCardBand(),
                    base.canvasEndpointFill(),
                    base.canvasEndpointBand(),
                    base.canvasAllFill(),
                    base.canvasAllBand(),
                    base.canvasAllText(),
                    base.canvasAnyFill(),
                    base.canvasAnyBand(),
                    base.canvasAnyText(),
                    base.canvasNotFill(),
                    base.canvasNotBand(),
                    base.canvasNotText(),
                    base.canvasFlowLink(),
                    base.canvasCardHoverFill(),
                    base.canvasCardSelectedFill(),
                    base.canvasCardBorder(),
                    base.canvasCardBorderSelected(),
                    base.canvasText(),
                    base.canvasTextMuted(),
                    base.canvasTextDanger(),
                    base.canvasTextOnAccent(),
                    base.canvasAccent(),
                    base.canvasAccentHover(),
                    base.canvasHotspotFill(),
                    base.canvasHotspotBorder(),
                    base.canvasDragGhostFill(),
                    base.canvasRubberFill(),
                    base.canvasPickerFill(),
                    base.canvasPickerBorder(),
                    base.canvasPickerHeaderFill(),
                    base.canvasPickerHeaderText(),
                    base.canvasPickerInputFill(),
                    base.canvasPickerInputBorder(),
                    base.canvasPickerRowSelected(),
                    base.canvasPickerText(),
                    base.canvasPickerTextMuted(),
                    base.canvasPickerTextDisabled(),
                    surfaces().text(),
                    surfaces().secondaryText(),
                    surfaces().secondaryText());
        }
        return palette;
    }
    @Override public UiControlStyle stripStyle() { return UiHostThemes.flatTheme().stripStyle(); }
    @Override public UiIcon icon(int row, boolean disabled) { return UiHostThemes.flatTheme().icon(row, disabled); }
    @Override public void drawHelp(GuiGraphics graphics, UiRect rect) { UiHostThemes.flatTheme().drawHelp(graphics, rect); }
    @Override public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) { UiHostThemes.flatTheme().drawSurface(graphics, rect, fill, border); }
    @Override public void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) { UiHostThemes.flatTheme().drawBevel(graphics, rect, light, dark); }
    @Override public void drawSegment(GuiGraphics graphics, UiRect rect, boolean enabled, boolean selected, boolean hovered) { UiHostThemes.flatTheme().drawSegment(graphics, rect, enabled, selected, hovered); }
    @Override public void drawCard(GuiGraphics graphics, UiRect rect) { UiHostThemes.flatTheme().drawCard(graphics, rect); }
    @Override public void drawPanel(GuiGraphics graphics, UiRect rect) { UiHostThemes.flatTheme().drawPanel(graphics, rect); }
}
