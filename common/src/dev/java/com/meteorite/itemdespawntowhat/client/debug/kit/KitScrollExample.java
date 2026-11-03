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
        inner.setViewport(box.x() + 12, outer.toScreenY(24), Math.max(0, box.width() - 32), 60);
    }

    void render(GuiGraphics graphics, Font font) {
        synchronize();
        UiRect box = outer.viewport();
        graphics.enableScissor(box.x(), box.y(), box.right(), box.bottom());
        try {
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0xCC181818);
            for (int i = 0; i < 20; i++) {
                graphics.drawString(font, Component.translatable(KitShowcaseContent.UI + "list.item", i),
                        box.x() + 2, outer.toScreenY(i * 20), 0xFFFFFFFF, false);
            }
            UiRect nested = inner.viewport();
            graphics.fill(nested.x(), nested.y(), nested.right(), nested.bottom(), 0xFF303030);
            inner.push(graphics);
            try {
                for (int i = 0; i < 20; i++) {
                    graphics.drawString(font, Component.translatable(KitShowcaseContent.UI + "list.item", i),
                            2, i * 12, 0xFF99CCFF, false);
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
