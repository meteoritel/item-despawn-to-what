package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControlGroup;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import java.util.List;
import java.util.function.Function;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewBudget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewMotion;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScenes;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewSubject;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.UiSceneStyle;
import net.minecraft.world.level.block.Blocks;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** 同条信息的混合绘制与前景重叠空间，供控件调整和前端布局快照使用。 */
final class KitLayersExample implements UiWidget, UiLayoutSource, AutoCloseable {
    private final UiControlGroup controls = new UiControlGroup();
    private final UiStyleSource theme;
    private final @Nullable Function<PreviewBudget, PreviewScenes> previewFactory;
    private PreviewScenes scenes;
    private final UiIcon sprite = new UiIcon.Sprite(ResourceLocation.withDefaultNamespace("textures/block/stone.png"), 0, 0, 16, 16, 16, 16);
    private final UiIcon item = new UiIcon.Item(new ItemStack(Items.DIAMOND));
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean foreground = true;
    private boolean overlap;

    KitLayersExample(UiStyleSource theme, Font font, @Nullable Function<PreviewBudget, PreviewScenes> previewFactory) {
        this.theme = theme;
        this.previewFactory = previewFactory;
        controls.setTheme(theme);
        controls.beginUpdate();
        controls.obtain(0).configure(font, text("foreground"), null, List.of(text("tooltip")), () -> foreground = !foreground);
        controls.obtain(1).configure(font, text("overlap"), null, List.of(text("tooltip")), () -> overlap = !overlap);
        controls.endUpdate();
    }
    @Override public UiRect bounds() { return bounds; }
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        for (int i = 0; i < controls.size(); i++) controls.controls().get(i).setBounds(x + i * 112, y, 108, 20);
    }
    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        controls.render(graphics, font, pointer);
        int x = bounds.x(), y = bounds.y() + 32;
        UiRenderLayers.draw(graphics, UiRenderLayers.CONTENT, () -> {
            sprite.render(graphics, x, y);
            item.render(graphics, x + (overlap ? 4 : 20), y);
            graphics.drawString(font, text("content"), x + (overlap ? 8 : 40), y + 4, theme.textColor(true), false);
        });
        if (previewFactory != null) {
            if (scenes == null) scenes = previewFactory.apply(PreviewBudget.DEFAULT);
            scenes.beginFrame();
            try {
                scenes.icon("mixed-stone", new PreviewSubject.Block(Blocks.STONE.defaultBlockState(), null), 32,
                        new PreviewMotion(0, 10_000), UiSceneStyle.icon(), sprite)
                        .render(graphics, x + (overlap ? 12 : 144), y);
            } finally { scenes.endFrame(); }
        }
        graphics.drawString(font, state(), x, y + 60, theme.textColor(true), false);
        if (foreground) UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND, () -> {
            UiRect panel = new UiRect(x + 8, y + 8, Math.clamp(bounds.width() - 8, 0, 180), 26);
            graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), theme.surfaces().panelFill());
            graphics.drawString(font, text("foreground"), panel.x() + 4, panel.y() + 6, theme.textColor(true), false);
        });
        controls.renderTooltip(graphics, font, pointer);
    }
    @Override public void addFocusTargets(Consumer<UiFocusTarget> out) { controls.addFocusTargets(out); }
    @Override public long focusRevision() { return controls.focusRevision(); }
    @Override public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = controls.hitTest(x, y);
        if (hit != null) return hit.within(this);
        UiRect content = contentBounds();
        return content.contains(x, y) ? UiPointerHit.leaf(this, x, y).preservingFocus()
                .withTextTooltip(List.of(text("tooltip")), content) : null;
    }
    @Override public boolean mousePressed(UiInputContext context) { return controls.mousePressed(context); }
    @Override public List<UiLayoutNode> layoutNodes() {
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.FRAME, bounds, text("title"), 0, 0, true, true,
                List.of(new UiLayoutNode(UiLayoutNode.Kind.ROW, contentBounds(), text("content"), 0, 0, true, true, List.of()),
                        new UiLayoutNode(UiLayoutNode.Kind.ROW, new UiRect(bounds.x(), bounds.y() + 92, bounds.width(), 12),
                                state(), 0, 0, true, true, List.of()),
                        new UiLayoutNode(UiLayoutNode.Kind.FRAME, bounds, text("actions"), 0, 0, true, true,
                                controls.controls().stream().flatMap(control -> control.layoutNodes().stream()).toList()))));
    }
    private UiRect contentBounds() { return new UiRect(bounds.x(), bounds.y() + 32, bounds.width(), 48); }
    private Component state() { return Component.translatable(KitShowcaseContent.UI + "showcase.layers.state", foreground, overlap, previewFactory != null); }
    void invalidateResources() { close(); }
    /** 仅释放本展示创建的场景，线程池归注入工厂的宿主所有。 */
    @Override public void close() { if (scenes != null) { scenes.close(); scenes = null; } }
    private static Component text(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase.layers." + key); }
}
