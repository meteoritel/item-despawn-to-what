package com.meteorite.itemdespawntowhat.client.ui.kit.api.text;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 原版文本模型的编辑协调：码点接纳、UTF-16 选择快照、局部历史与指针选择。
 * 文本由 Model 唯一持有；模型重建后 adapter 必须读取当前实例。
 */
public final class UiTextEditSession implements UiInputTarget {
    /** 原版模型的差异端口；选择和指针寻址继续使用原版实现。 */
    public interface Model {
        /** 光标的 UTF-16 索引。 */
        int cursor();
        /** 选择锚点的 UTF-16 索引。 */
        int anchor();
        /** 按模型所用的指针坐标空间寻址；捕获时允许视口外坐标。 */
        void seek(double pointerX, double pointerY);
        /** 恢复 UTF-16 选区，保留锚点到光标的方向。 */
        void select(int anchor, int cursor);

        /** 当前模型文本。 */
        String value();

        /** 写入完整原文，调用方由 session 提供静默接纳作用域。 */
        void write(String text);

        /** 拒绝用户输入后的恢复；不能预检的模型可保留其实际光标位置。 */
        default void restoreRejected(String text) {
            write(text);
        }
    }

    /** 光标与选择锚点按 UTF-16 位置保存，与原版文本控件的坐标一致。 */
    public record Snapshot(String text, int cursor, int anchor) {
        /** 文本非空，UTF-16 索引钳制到该快照原文范围内。 */
        public Snapshot {
            Objects.requireNonNull(text);
            cursor = Math.clamp(cursor, 0, text.length());
            anchor = Math.clamp(anchor, 0, text.length());
        }
    }

    private final Model model;
    private final UiTextAcceptance acceptance;
    private final UiTextHistory history = new UiTextHistory();
    private final UiTextSelection selection;

    /** 创建协调器；maximumCodePoints 是码点上限，历史使用统一默认容量。 */
    public UiTextEditSession(Model model, int maximumCodePoints) {
        this.model = model;
        acceptance = new UiTextAcceptance(maximumCodePoints);
        acceptance.reset(model.value());
        selection = new UiTextSelection(model);
    }

    /** 配置码点上限、过滤和通知，或供可预检的原版模型安装 accepts 过滤器。 */
    public UiTextAcceptance acceptance() {
        return acceptance;
    }

    /** 原版模型的文本变化通知入口；拒绝时整体恢复，接受时通知一次。 */
    public void changed() {
        acceptance.changed(model.value(), model::restoreRejected);
    }

    /** 围绕一次原版输入记录实际变化；纯选择或拒绝输入不记历史。 */
    public boolean edit(BooleanSupplier input) {
        endInteraction(UiInteractionSession.EndReason.RELEASE);
        Snapshot before = snapshot();
        boolean handled = input.getAsBoolean();
        history.record(before, snapshot());
        return handled;
    }

    /** 原文静默回填；不同文本清空局部历史，已有超限文本保留完整内容。 */
    public void backfill(String text) {
        endInteraction(UiInteractionSession.EndReason.UNMOUNTED);
        boolean changed = !text.equals(model.value());
        acceptance.silently(() -> model.write(text));
        acceptance.reset(model.value());
        if (changed) history.clear();
    }

    /** 读取当前文本、光标与选择方向，供换宽重建使用。 */
    public Snapshot snapshot() {
        return new Snapshot(model.value(), model.cursor(), model.anchor());
    }

    /** 在当前原版模型中静默恢复快照，保留局部历史与选择方向。 */
    public void restore(Snapshot snapshot) {
        acceptance.silently(() -> {
            model.write(snapshot.text());
            model.select(snapshot.anchor(), snapshot.cursor());
        });
        acceptance.reset(model.value());
    }

    /** 执行局部撤销或重做；恢复后只通知一次文本变化。 */
    public void applyHistory(UiHistoryShortcut shortcut) {
        endInteraction(UiInteractionSession.EndReason.RELEASE);
        Snapshot snapshot = shortcut == UiHistoryShortcut.UNDO ? history.undo() : history.redo();
        if (snapshot == null) return;
        restore(snapshot);
        acceptance.notifyChanged();
    }

    /** 为宿主的命中链附加内部选择器身份，指针捕获与绘制使用同一归属。 */
    public UiPointerHit includeSelectionOwner(UiPointerHit hit) {
        return hit.within(selection);
    }

    /** 使用当前几何和焦点资格开始指针选择；焦点候选由输入路由统一处理。 */
    public boolean beginSelection(UiInputContext context, UiRect bounds, boolean enabled) {
        selection.setBounds(bounds);
        selection.setEnabled(enabled);
        return selection.mousePressed(context);
    }

    /** 隐藏时结束指针选择。 */
    public void setVisible(boolean visible) {
        selection.setVisible(visible);
    }

    /** 是否正在拖动选择。 */
    public boolean isSelecting() {
        return selection.isSelecting();
    }

    /** 结束选择；取消恢复指针按下前的光标和锚点。 */
    public void endInteraction(UiInteractionSession.EndReason reason) {
        selection.endInteraction(reason);
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dx, double dy) {
        return selection.mouseDragged(context, dx, dy);
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        return selection.mouseReleased(context);
    }
}
