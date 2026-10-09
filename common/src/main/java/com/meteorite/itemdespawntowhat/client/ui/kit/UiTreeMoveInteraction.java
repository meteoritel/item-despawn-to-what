package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import java.util.List;
import java.util.Collection;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** 树移动的一次捕获：预览不写绑定，正常释放提交一次，其它结束原因取消。 */
public final class UiTreeMoveInteraction<N> implements UiInputCapture.Target {
    private final UiTreeEditor<N> editor;
    private final UiInputCapture capture = new UiInputCapture();
    private final Consumer<UiTreeEditor.Outcome> ended;
    private @Nullable N before;
    private List<UiTreePath> sources = List.of();
    private @Nullable UiTreeEditor.MovePreview<N> preview;

    public UiTreeMoveInteraction(UiTreeEditor<N> editor, Consumer<UiTreeEditor.Outcome> ended) {
        this.editor = Objects.requireNonNull(editor);
        this.ended = Objects.requireNonNull(ended);
    }

    public boolean begin(UiTreePath source) { return begin(List.of(source)); }

    public boolean begin(Collection<UiTreePath> sources) {
        if (isActive() || !editor.beginMove(sources)) return false;
        this.before = editor.root();
        this.sources = UiTreeEditor.normalize(sources);
        preview = null;
        capture.begin(this);
        return true;
    }

    public boolean isActive() { return capture.isCapturedBy(this); }
    public @Nullable UiTreeEditor.MovePreview<N> preview() { return preview; }

    public @Nullable UiTreeEditor.MovePreview<N> preview(UiTreePath target, UiTreeEditor.DropPosition position) {
        if (!isActive() || sources.isEmpty()) return null;
        if (preview == null || !preview.target().equals(target) || preview.position() != position) {
            preview = editor.previewMove(before, sources, target, position);
        }
        return preview;
    }

    public void clearPreview() { preview = null; }
    public boolean end(UiInputCapture.EndReason reason) { return capture.end(reason); }

    @Override
    public void captureEnded(UiInputCapture.EndReason reason) {
        UiTreeEditor.MovePreview<N> candidate = preview;
        before = null;
        sources = List.of();
        preview = null;
        UiTreeEditor.Outcome result = reason == UiInputCapture.EndReason.RELEASE && candidate != null
                ? editor.commitMove(candidate) : new UiTreeEditor.Outcome(false, null);
        ended.accept(result);
    }
}
