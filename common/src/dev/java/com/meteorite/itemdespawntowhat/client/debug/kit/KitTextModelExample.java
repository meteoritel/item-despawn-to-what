package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiShellWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidgetShell;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.UiTextEditSession;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 独立 Model 演示：原版模型持有文本，session 持有接纳、选择与局部历史。 */
final class KitTextModelExample implements UiShellWidget {
    private final UiWidgetShell shell = new UiWidgetShell(this, this::endSelection);
    private final TrackedEditBox box;
    private final UiTextEditSession session;

    KitTextModelExample(Font font) {
        box = new TrackedEditBox(font);
        box.setMaxLength(Integer.MAX_VALUE);
        session = new UiTextEditSession(new UiTextEditSession.Model() {
            @Override public String value() { return box.getValue(); }
            @Override public void write(String value) { box.setValue(value); }
            @Override public int cursor() { return box.getCursorPosition(); }
            @Override public int anchor() { return box.anchor; }
            @Override public void seek(double x, double y) { box.onClick(x, y); }
            @Override public void select(int anchor, int cursor) {
                box.setCursorPosition(cursor);
                box.setHighlightPos(anchor);
            }
        }, 64);
        box.setFilter(session.acceptance()::accepts);
        box.setResponder(value -> session.changed());
        session.backfill(Component.translatable(KitShowcaseContent.UI + "showcase.models.text").getString());
    }

    private void endSelection(UiInteractionSession.EndReason reason) { session.endInteraction(reason); }
    /** {@inheritDoc} */
    @Override public UiWidgetShell shell() { return shell; }
    /** 原版换宽后恢复方向与光标；整个过程保留局部历史。 */
    @Override public void setBounds(int x, int y, int width, int height) {
        shell.setBounds(x, y, width, height);
        box.setX(x);
        box.setY(y);
        box.setHeight(height);
        if (box.getWidth() != Math.max(1, width)) {
            UiTextEditSession.Snapshot saved = session.snapshot();
            box.setWidth(Math.max(1, width));
            session.restore(saved);
        }
    }
    /** {@inheritDoc} */
    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        box.renderWidget(graphics, (int) pointer.x(), (int) pointer.y(), 0);
    }
    /** {@inheritDoc} */
    @Override public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = UiShellWidget.super.hitTest(x, y);
        return hit == null ? null : session.includeSelectionOwner(hit);
    }
    /** {@inheritDoc} */
    @Override public boolean mousePressed(UiInputContext context) {
        return session.beginSelection(context, bounds(), canFocus());
    }
    /** {@inheritDoc} */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        UiHistoryShortcut history = UiHistoryShortcut.fromKey(key, modifiers);
        if (history != null) { session.applyHistory(history); return true; }
        return session.edit(() -> box.keyPressed(key, scan, modifiers));
    }
    /** {@inheritDoc} */
    @Override public boolean charTyped(char value, int modifiers) {
        return session.edit(() -> box.charTyped(value, modifiers));
    }
    /** 文本由字符与导航键编辑，不把 Enter/Space 当作按钮激活。 */
    @Override public boolean activate() { return false; }
    /** {@inheritDoc} */
    @Override public void focusChanged(boolean focused) { box.setFocused(focused); }
    /** {@inheritDoc} */
    @Override public Component accessibleName() { return Component.translatable(KitShowcaseContent.UI + "showcase.models.text"); }

    private static final class TrackedEditBox extends EditBox {
        private int anchor;
        private TrackedEditBox(Font font) { super(font, 0, 0, 1, 20, Component.empty()); }
        @Override public void setHighlightPos(int position) {
            super.setHighlightPos(position);
            anchor = Math.clamp(position, 0, getValue().length());
        }
    }
}
