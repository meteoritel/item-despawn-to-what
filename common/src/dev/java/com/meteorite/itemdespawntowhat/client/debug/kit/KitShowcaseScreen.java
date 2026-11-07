package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeContext;
import org.lwjgl.glfw.GLFW;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiThemedScreen;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemePublication;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewBudget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScenes;
import org.jetbrains.annotations.Nullable;

/** 只依赖 kit 与原版类型的开发接入示例；导航回调由工作台提供。 */
public final class KitShowcaseScreen extends UiThemedScreen implements UiLayoutSource {
    private final Runnable returnTo;
    private final UiThemeContext<UiStyleSource> theme;
    private final List<UiStyleSource> themes;
    private final @Nullable Function<PreviewBudget, PreviewScenes> previewFactory;
    private int themeIndex;
    private final UiThemePublication<UiStyleSource> themePublication;
    private KitShowcaseContent content;
    private UiLayers presentation;

    /** 创建独立本地场景，不要求世界或编辑工作区。 */
    public KitShowcaseScreen(Runnable returnTo) {
        this(returnTo, KitSkinSamples.themes());
    }

    /** 替换主题由工作台注入，独立内容仅接触 kit 契约。 */
    public KitShowcaseScreen(Runnable returnTo, List<UiStyleSource> themes) {
        this(returnTo, themes, null);
    }

    /** 已加载世界的宿主可注入 preview 工厂；场景所有者由展示内容关闭。 */
    public KitShowcaseScreen(Runnable returnTo, List<UiStyleSource> themes,
                             @Nullable Function<PreviewBudget, PreviewScenes> previewFactory) {
        this(returnTo, themes, previewFactory, UiThemeContext.minecraft());
    }

    private KitShowcaseScreen(Runnable returnTo, List<UiStyleSource> themes,
                              @Nullable Function<PreviewBudget, PreviewScenes> previewFactory,
                              UiThemeContext<UiStyleSource> context) {
        super(Component.translatable(KitShowcaseContent.UI + "kit_showcase"), context);
        theme = context;
        themePublication = new UiThemePublication<>(theme::current, theme::setTheme);
        this.returnTo = returnTo;
        this.previewFactory = previewFactory;
        this.themes = List.copyOf(themes);
        theme.setTheme(this.themes.getFirst());
    }

    @Override
    protected void init() {
        if (content == null) {
            content = new KitShowcaseContent(font, theme, this::onClose, this::nextTheme, previewFactory);
            UiInputScope scope = new UiInputScope(content);
            scope.setEscape(this::onClose);
            presentation = new UiLayers(new UiLayer(scope,
                    (graphics, renderFont, pointer, depth) -> content.render(graphics, pointer), content));
        }
        content.setOnScopeChanged(this::refreshUiState);
        content.layout(width, height);
        refreshUiState();
    }

    @Override protected void advanceUiFrame() { content.advanceFrame(); }

    private UiLayers layers() {
        presentation.setOverlays(content.overlayLayers());
        return presentation;
    }

    @Override protected void layoutUi() { content.layout(width, height); }

    @Override
    protected void renderThemedUi(@NotNull GuiGraphics graphics, int x, int y, float delta) {
        layers().render(graphics, font, x, y);
    }

    @Override
    public List<UiLayoutNode> layoutNodes() {
        return content == null ? List.of() : layers().layoutNodes();
    }

    private void nextTheme() { requestTheme(themes.get(themeIndex = (themeIndex + 1) % themes.size())); }

    /** 请求完整主题；根在手势正常完成后统一发布。 */
    public void requestTheme(UiStyleSource next) { themePublication.request(next); }

    @Override protected void prepareUi() { themePublication.prepare(inputRouter().active()); }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_G && content.controlsVisible()) { content.changeGrid(); return true; }
        if (key == GLFW.GLFW_KEY_R && content.controlsVisible() && minecraft != null) { minecraft.reloadResourcePacks(); return true; }
        if (key == GLFW.GLFW_KEY_F6) { nextTheme(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override protected UiInputScope inputScope() { return layers().activeScope(); }
    @Override protected void invalidateUiResources() { content.invalidateResources(); }
    @Override protected void releaseUiResources() { themePublication.discard(); content.setOnScopeChanged(() -> { }); content.releaseResources(); }
    @Override public void onClose() { returnTo.run(); }
    @Override public boolean isPauseScreen() { return false; }
}
