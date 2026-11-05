package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import org.lwjgl.glfw.GLFW;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewBudget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScenes;
import org.jetbrains.annotations.Nullable;

/** 只依赖 kit 与原版类型的开发接入示例；导航回调由工作台提供。 */
public final class KitShowcaseScreen extends UiScreen implements UiLayoutSource {
    private final Runnable returnTo;
    private final UiThemeContext<UiStyleSource> theme = UiThemeContext.minecraft();
    private final List<UiStyleSource> themes;
    private final @Nullable Function<PreviewBudget, PreviewScenes> previewFactory;
    private int themeIndex;
    private @Nullable UiStyleSource pendingTheme;
    private KitShowcaseContent content;
    private UiInputScope scope;

    /** 创建独立本地场景，不要求世界或编辑工作区。 */
    public KitShowcaseScreen(Runnable returnTo) {
        this(returnTo, List.of(UiMinecraftTheme.INSTANCE));
    }

    /** 替换主题由工作台注入，独立内容仅接触 kit 契约。 */
    public KitShowcaseScreen(Runnable returnTo, List<UiStyleSource> themes) {
        this(returnTo, themes, null);
    }

    /** 已加载世界的宿主可注入 preview 工厂；场景所有者由展示内容关闭。 */
    public KitShowcaseScreen(Runnable returnTo, List<UiStyleSource> themes,
                             @Nullable Function<PreviewBudget, PreviewScenes> previewFactory) {
        super(Component.translatable(KitShowcaseContent.UI + "kit_showcase"));
        this.returnTo = returnTo;
        this.previewFactory = previewFactory;
        this.themes = List.copyOf(themes);
        theme.setTheme(this.themes.getFirst());
    }

    @Override
    protected void init() {
        if (content == null) {
            content = new KitShowcaseContent(font, theme, this::onClose, this::nextTheme, previewFactory);
            scope = new UiInputScope(content);
            scope.setEscape(this::onClose);
        }
        content.layout(width, height);
        refreshUiState();
    }

    @Override protected void layoutUi() { content.layout(width, height); }

    @Override
    protected void renderUi(@NotNull GuiGraphics graphics, int x, int y, float delta) {
        renderBackground(graphics, x, y, delta);
        content.render(graphics, scope.pointer(x, y).resolve(content));
    }

    @Override
    public List<UiLayoutNode> layoutNodes() {
        return content == null ? List.of() : content.layoutNodes();
    }

    private void nextTheme() { requestTheme(themes.get(themeIndex = (themeIndex + 1) % themes.size())); }

    /** 请求完整主题；根在手势正常完成后统一发布。 */
    public void requestTheme(UiStyleSource next) { pendingTheme = next == theme.current() ? null : next; }

    @Override protected void prepareUi() {
        if (pendingTheme == null) return;
        UiInputScope active = inputRouter().active();
        if (active != null && active.interaction().pointerTarget() != null) return;
        theme.setTheme(pendingTheme);
        pendingTheme = null;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_G && content.controlsVisible()) { content.changeGrid(); return true; }
        if (key == GLFW.GLFW_KEY_R && content.controlsVisible() && minecraft != null) { minecraft.reloadResourcePacks(); return true; }
        if (key == GLFW.GLFW_KEY_F6) { nextTheme(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override protected UiInputScope inputScope() { return content.activeScope(scope); }
    @Override protected void invalidateUiResources() { content.invalidateResources(); }
    @Override protected void releaseUiResources() { pendingTheme = null; content.releaseResources(); }
    @Override public void onClose() { returnTo.run(); }
    @Override public boolean isPauseScreen() { return false; }
}
