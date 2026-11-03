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
import net.minecraft.core.component.DataComponents;
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
    private final List<UiIcon> items = List.of(new UiIcon.Item(new ItemStack(Items.STONE)),
            new UiIcon.Item(new ItemStack(Items.DIAMOND)), new UiIcon.Item(new ItemStack(Items.GLASS)), glintItem());
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
        if (previewFactory != null) {
            if (scenes == null) scenes = previewFactory.apply(PreviewBudget.DEFAULT);
            scenes.beginFrame();
        }
        try {
            UiIcon preview = scenes == null ? null : scenes.icon("mixed-stone",
                    new PreviewSubject.Block(Blocks.STONE.defaultBlockState(), null), 32,
                    new PreviewMotion(0, 10_000), UiSceneStyle.icon(), sprite);
            for (int row = 0; row < 2; row++) {
                int rowY = y + row * 48;
                graphics.fill(x, rowY, bounds.right(), rowY + 44, theme.surfaces().panelFill());
                graphics.drawString(font, text(row == 0 ? "forward" : "reverse"), x + 2, rowY + 2, theme.textColor(true), false);
                int step = overlap ? 40 : 60;
                for (int i = 0; i < items.size(); i++) renderMixed(graphics, font, items.get(i), x + i * step, rowY + 14, row == 1);
                if (preview != null) renderMixed(graphics, font, preview, x + items.size() * step, rowY + 12, row == 1);
            }
        } finally { if (scenes != null) scenes.endFrame(); }
        graphics.drawString(font, state(), x, y + 100, theme.textColor(true), false);
        if (foreground) UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND, () -> {
            UiRect panel = new UiRect(x + 8, y + 20, Math.clamp(bounds.width() - 8, 0, 180), 56);
            graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), theme.surfaces().panelFill());
            graphics.drawString(font, text("foreground"), panel.x() + 4, panel.y() + 6, theme.textColor(true), false);
        });
        controls.renderTooltip(graphics, font, pointer);
    }
    private void renderMixed(GuiGraphics graphics, Font font, UiIcon icon, int x, int y, boolean reverse) {
        Runnable label = () -> graphics.drawString(font, text("sample"), x + (overlap ? 8 : 40), y + 4, theme.textColor(true), false);
        if (reverse) label.run(); else sprite.render(graphics, x, y);
        icon.render(graphics, x + (overlap ? 4 : 20), y);
        if (reverse) sprite.render(graphics, x, y); else label.run();
    }
    private static UiIcon glintItem() {
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return new UiIcon.Item(stack);
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
                        new UiLayoutNode(UiLayoutNode.Kind.ROW, new UiRect(bounds.x(), bounds.y() + 132, bounds.width(), 12),
                                state(), 0, 0, true, true, List.of()),
                        new UiLayoutNode(UiLayoutNode.Kind.FRAME, bounds, text("actions"), 0, 0, true, true,
                                controls.controls().stream().flatMap(control -> control.layoutNodes().stream()).toList()))));
    }
    private UiRect contentBounds() { return new UiRect(bounds.x(), bounds.y() + 32, bounds.width(), 96); }
    private Component state() { return Component.translatable(KitShowcaseContent.UI + "showcase.layers.state", foreground, overlap, previewFactory != null); }
    void invalidateResources() { close(); }
    /** 仅释放本展示创建的场景，线程池归注入工厂的宿主所有。 */
    @Override public void close() { if (scenes != null) { scenes.close(); scenes = null; } }
    private static Component text(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase.layers." + key); }
}
