package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 嵌套视口共享屏幕坐标，展示滚轮余量传播与纵向轨道捕获。 */
final class KitScrollExample implements UiInputTarget {
    private final UiStyleSource theme;
    private final UiScrollView outer = new UiScrollView();
    private final UiScrollView inner = new UiScrollView();
    private int rows = 20;

    KitScrollExample(UiStyleSource theme) {
        this.theme = theme;
        outer.setContentHeight(400);
        outer.setScrollbarVisible(true);
        inner.setContentHeight(240);
        inner.setScrollbarVisible(true);
    }

    void reset() {
        outer.setOffset(0);
        inner.setOffset(0);
    }

    void layout(UiRect rect) {
        outer.setViewport(rect.x(), rect.y(), rect.width(), rect.height());
        synchronize();
    }

    private void synchronize() {
        UiRect box = outer.viewport();
        // 外层内容随视口增高保持“至少一屏再加几行”，窗口变大时仍然可滚动。
        rows = Math.max(20, Math.ceilDiv(box.height(), 20) + 6);
        outer.setContentHeight(rows * 20);
        // 内层做成右侧画中画：与外层文字分开摆放，两块内容都完整可读。
        int height = Math.clamp(box.height() / 3, 48, 140);
        int width = Math.min(320, Math.max(120, box.width() / 3));
        int inset = Math.min(width, Math.max(0, box.width() - 8));
        inner.setViewport(box.right() - inset - 2, outer.toScreenY(40), inset, height);
    }

    void render(GuiGraphics graphics, Font font) {
        synchronize();
        UiRect box = outer.viewport();
        graphics.enableScissor(box.x(), box.y(), box.right(), box.bottom());
        try {
            theme.drawInset(graphics, box);
            for (int i = 0; i < rows; i++) {
                graphics.drawString(font, Component.translatable(KitShowcaseContent.UI + "list.item", i),
                        box.x() + 4, outer.toScreenY(i * 20), theme.surfaces().inputText(), false);
            }
            UiRect nested = inner.viewport();
            theme.drawInset(graphics, nested);
            graphics.renderOutline(nested.x() - 1, nested.y() - 1, nested.width() + 2, nested.height() + 2, theme.surfaces().panelBorder());
            inner.push(graphics);
            try {
                for (int i = 0; i < 20; i++) {
                    graphics.drawString(font, Component.translatable(KitShowcaseContent.UI + "list.item", i),
                            2, i * 12, theme.surfaces().inputText(), false);
                }
            } finally {
                inner.pop(graphics);
            }
            inner.renderScrollbar(graphics, theme);
            outer.renderScrollbar(graphics, theme);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (!outer.contains(x, y)) return null;
        synchronize();
        UiPointerHit hit = outer.hitTest(x, y);
        if (hit == null) hit = inner.hitTest(x, y);
        return hit == null ? UiPointerHit.leaf(this, x, y).preservingFocus() : hit.within(this);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!outer.contains(context.pointerX(), context.pointerY())) return false;
        synchronize();
        return outer.mousePressed(context) || inner.mousePressed(context);
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double x, double y) {
        if (!outer.contains(context.pointerX(), context.pointerY())) return false;
        synchronize();
        if (inner.contains(context.pointerX(), context.pointerY()) && inner.scrollByPixels(y * 24)) return true;
        return outer.scrollByPixels(y * 24);
    }

    List<UiLayoutNode> nodes() {
        synchronize();
        return List.of(node("outer", outer.viewport(), List.of(new UiLayoutNode(UiLayoutNode.Kind.ROW, outer.viewport(),
                Component.translatable(KitShowcaseContent.UI + "showcase.scroll.values", outer.offset(), inner.offset()),
                0, 0, true, true, List.of()),
                node("outer.scrollbar", outer.scrollbarTrack(), List.of()),
                node("inner", inner.viewport(), List.of(node("inner.scrollbar", inner.scrollbarTrack(), List.of()))))));
    }

    private UiLayoutNode node(String key, UiRect rect, List<UiLayoutNode> children) {
        return new UiLayoutNode(children.isEmpty() ? UiLayoutNode.Kind.CONTROL : UiLayoutNode.Kind.FRAME,
                rect, Component.translatable(KitShowcaseContent.UI + "showcase." + key), 0, 0, true, true, children);
    }
}