package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControlGroup;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
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

/**
 * 同条信息的混合绘制与前景重叠空间，供控件调整和前端布局快照使用。
 */
final class KitLayersExample implements UiWidget, UiLayoutSource, AutoCloseable {
    private static final int ROW_HEIGHT = 44;
    private static final int ROW_GAP = 6;
    private static final int ITEM_STEP = 84;
    private static final int OVERLAP_STEP = 40;
    private final UiControlGroup controls = new UiControlGroup();
    private final UiStyleSource theme;
    private final Font font;
    private final @Nullable Function<PreviewBudget, PreviewScenes> previewFactory;
    private PreviewScenes scenes;
    private final UiIcon sprite = new UiIcon.Sprite(ResourceLocation.withDefaultNamespace("textures/block/stone.png"), 0, 0, 16, 16, 16, 16);
    private final List<UiIcon> items = List.of(new UiIcon.Item(new ItemStack(Items.STONE)),
            new UiIcon.Item(new ItemStack(Items.DIAMOND)), new UiIcon.Item(new ItemStack(Items.GLASS)), glintItem());
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect actionsRect = new UiRect(0, 0, 0, 0);
    private UiRect contentRect = new UiRect(0, 0, 0, 0);
    private UiRect stateRect = new UiRect(0, 0, 0, 0);
    private int rowCount = 2;
    private boolean foreground = true;
    private boolean overlap;

    KitLayersExample(UiStyleSource theme, Font font, @Nullable Function<PreviewBudget, PreviewScenes> previewFactory) {
        this.theme = theme;
        this.font = font;
        this.previewFactory = previewFactory;
        controls.setTheme(theme);
        controls.beginUpdate();
        controls.obtain(0).configure(font, text("foreground"), null, List.of(text("tooltip")), () -> foreground = !foreground);
        controls.obtain(1).configure(font, text("overlap"), null, List.of(text("tooltip")), () -> overlap = !overlap);
        controls.endUpdate();
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    /**
     * 动作行、两行混合绘制内容与状态行自上而下排列，内容行不再与状态行重叠。
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int capHeight = font.lineHeight + 2;
        actionsRect = new UiRect(x, y, width, capHeight + WorkbenchLayout.CONTROL_HEIGHT);
        int controlWidth = Math.clamp((width - WorkbenchLayout.GAP) / 2, 60, 140);
        for (int i = 0; i < controls.size(); i++) {
            controls.controls().get(i).setBounds(x + i * (controlWidth + WorkbenchLayout.GAP), y + capHeight, controlWidth, WorkbenchLayout.CONTROL_HEIGHT);
        }
        int contentTop = actionsRect.bottom() + WorkbenchLayout.GAP;
        int stateTop = Math.max(contentTop, bounds.bottom() - font.lineHeight);
        stateRect = new UiRect(x, stateTop, width, font.lineHeight);
        int rowsBottom = Math.max(contentTop, stateTop - WorkbenchLayout.GROUP_GAP);
        contentRect = new UiRect(x, contentTop, width, rowsBottom - contentTop);
        // 用成对的正向/反向样例铺满内容区，行数随窗口高度变化，不在底部留空。
        rowCount = Math.max(2, (contentRect.height() + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    @Override
    public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        graphics.drawString(font, text("actions"), actionsRect.x(), actionsRect.y(), theme.surfaces().secondaryText(), false);
        controls.render(graphics, font, pointer);
        int x = bounds.x();
        int y = contentRect.y();
        if (previewFactory != null) {
            if (scenes == null) scenes = previewFactory.apply(PreviewBudget.DEFAULT);
        }
        Runnable painter = () -> {
            UiIcon preview = scenes == null ? null : scenes.icon("mixed-stone",
                    new PreviewSubject.Block(Blocks.STONE.defaultBlockState(), null), 32,
                    new PreviewMotion(0, 10_000), UiSceneStyle.icon(), sprite);
            for (int row = 0; row < rowCount; row++) {
                boolean reverse = row % 2 == 1;
                int rowY = y + row * (ROW_HEIGHT + ROW_GAP);
                graphics.fill(x, rowY, bounds.right(), rowY + ROW_HEIGHT, theme.surfaces().panelFill());
                if (row < 2) {
                    graphics.drawString(font, text(reverse ? "reverse" : "forward"), x + 4, rowY + 4, theme.textColor(true), false);
                }
                int step = overlap ? OVERLAP_STEP : ITEM_STEP;
                int itemY = rowY + 18;
                for (int i = 0; i < items.size(); i++)
                    renderMixed(graphics, font, items.get(i), x + i * step, itemY, reverse);
                if (preview != null) renderMixed(graphics, font, preview, x + items.size() * step, itemY - 2, reverse);
            }
        };
        if (scenes == null) painter.run();
        else scenes.renderFrame(painter);
        graphics.drawString(font, state(), stateRect.x(), stateRect.y(), theme.textColor(true), false);
        if (foreground) UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND, () -> {
            UiRect panel = new UiRect(x + 8, y + 8, Math.clamp(bounds.width() - 16, 0, 180), 56);
            graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), theme.surfaces().panelFill());
            graphics.drawString(font, text("foreground"), panel.x() + 4, panel.y() + 6, theme.textColor(true), false);
        });
        controls.renderTooltip(graphics, font, pointer);
    }

    private void renderMixed(GuiGraphics graphics, Font font, UiIcon icon, int x, int y, boolean reverse) {
        Runnable label = () -> graphics.drawString(font, text("sample"), x + (overlap ? 8 : 40), y + 4, theme.textColor(true), false);
        if (reverse) label.run();
        else sprite.render(graphics, x, y);
        icon.render(graphics, x + (overlap ? 4 : 20), y);
        if (reverse) sprite.render(graphics, x, y);
        else label.run();
    }

    private static UiIcon glintItem() {
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return new UiIcon.Item(stack);
    }

    @Override
    public void addFocusTargets(Consumer<UiFocusTarget> out) {
        controls.addFocusTargets(out);
    }

    @Override
    public long focusRevision() {
        return controls.focusRevision();
    }

    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = controls.hitTest(x, y);
        if (hit != null) return hit.within(this);
        return contentRect.contains(x, y) ? UiPointerHit.leaf(this, x, y).preservingFocus()
                .withTextTooltip(List.of(text("tooltip")), contentRect) : null;
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        return controls.mousePressed(context);
    }

    @Override
    public List<UiLayoutNode> layoutNodes() {
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.FRAME, bounds, text("title"), 0, 0, true, true,
                List.of(new UiLayoutNode(UiLayoutNode.Kind.ROW, contentRect, text("content"), 0, 0, true, true, List.of()),
                        new UiLayoutNode(UiLayoutNode.Kind.ROW, stateRect, state(), 0, 0, true, true, List.of()),
                        new UiLayoutNode(UiLayoutNode.Kind.FRAME, actionsRect, text("actions"), 0, 0, true, true,
                                controls.controls().stream().flatMap(control -> control.layoutNodes().stream()).toList()))));
    }

    private Component state() {
        return Component.translatable(KitShowcaseContent.UI + "showcase.layers.state", foreground, overlap, previewFactory != null);
    }

    void invalidateResources() {
        close();
    }

    /**
     * 仅释放本展示创建的场景，线程池归注入工厂的宿主所有。
     */
    @Override
    public void close() {
        if (scenes != null) {
            scenes.close();
            scenes = null;
        }
    }

    private static Component text(String key) {
        return Component.translatable(KitShowcaseContent.UI + "showcase.layers." + key);
    }
}