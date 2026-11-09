package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 稳定作用域的一份呈现描述；生命周期与遮罩政策仍由实际内容拥有。 */
public record UiLayer(UiInputScope scope, Painter painter, UiLayoutSource layout) {
    /** 在本项作用域解析指针后绘制，独立栈入口与清单共用此实现。 */
    public void render(GuiGraphics graphics, Font font, double x, double y, int depth) {
        UiPointer pointer = scope.pointer(x, y).resolve(scope.content());
        painter.render(graphics, font, pointer, depth);
    }

    /** 内容在清单给定的覆盖深度绘制，包络由内容自己的 UiOverlay 应用一次。 */
    @FunctionalInterface
    public interface Painter {
        /** 指针已按本项作用域解析；depth 从首个覆盖面起计零。 */
        void render(GuiGraphics graphics, Font font, UiPointer pointer, int depth);
    }
}
