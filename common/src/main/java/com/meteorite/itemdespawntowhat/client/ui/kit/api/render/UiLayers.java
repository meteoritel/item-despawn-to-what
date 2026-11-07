package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 同一呈现清单派生活动作用域、绘制层序和布局快照，持续交互仍归作用域。 */
public final class UiLayers implements UiLayoutSource {
    private UiLayer base;
    private List<UiLayer> overlays = List.of();

    /** 每个呈现清单都有一个基础内容域。 */
    public UiLayers(UiLayer base) { this.base = base; }

    /** 页面切换时替换基础内容描述。 */
    public void setBase(UiLayer base) { this.base = base; }

    /** 覆盖面按底到顶登记；拓扑不变时复用已有清单。 */
    public void setOverlays(List<UiLayer> overlays) {
        if (!this.overlays.equals(overlays)) this.overlays = List.copyOf(overlays);
    }

    /** 所有覆盖面独占输入，基础域在没有覆盖面时活动。 */
    public UiInputScope activeScope() {
        return overlays.isEmpty() ? base.scope() : overlays.getLast().scope();
    }

    /** 每项使用自身作用域解析指针，下层 hover 和 tooltip 由作用域门禁关闭。 */
    public void render(GuiGraphics graphics, Font font, double x, double y) {
        base.render(graphics, font, x, y, 0);
        for (int depth = 0; depth < overlays.size(); depth++) {
            overlays.get(depth).render(graphics, font, x, y, depth);
        }
    }

    /** 只汇集实际呈现项，顺序与绘制一致。 */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>(base.layout().layoutNodes());
        for (UiLayer layer : overlays) nodes.addAll(layer.layout().layoutNodes());
        return List.copyOf(nodes);
    }
}
