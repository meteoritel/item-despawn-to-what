package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 滑块的会话与值操作编排；模型负责快照、恢复及预览值的合法化。
 */
final class SliderInteraction<V> {
    private final UiInteractionSession.Target owner;
    private final UiValueInteraction<V> values;
    private final Supplier<V> snapshot;
    private final Consumer<V> restore;
    private final Supplier<UiValueInteraction.@Nullable Listener<V>> listener;
    private @Nullable UiInteractionSession capture;
    private double anchorPointer;
    private double anchorValue;
    private boolean fine;

    SliderInteraction(UiInteractionSession.Target owner, Predicate<V> valid, BiPredicate<V, V> equivalent,
                      Supplier<V> snapshot, Consumer<V> restore,
                      Supplier<UiValueInteraction.@Nullable Listener<V>> listener) {
        this.owner = owner;
        values = new UiValueInteraction<>(valid, equivalent);
        this.snapshot = snapshot;
        this.restore = restore;
        this.listener = listener;
    }

    boolean isActive() {
        return values.isActive();
    }

    V startValue() {
        return values.startValue();
    }

    boolean isCaptured() {
        return capture != null && capture.owns(owner);
    }

    boolean isDragging() {
        return matches(UiInteractionSession.Source.POINTER, 0);
    }

    boolean beginOperation() {
        return values.begin(snapshot.get(), listener.get());
    }

    boolean beginPointer(UiInputContext context) {
        return begin(context.scope().interaction(), UiInteractionSession.Source.POINTER, context.button());
    }

    void beginKey(UiFocusState focus, int key) {
        begin(Objects.requireNonNull(focus.scope(), "Focused input requires a mounted scope").interaction(),
                UiInteractionSession.Source.KEY, key);
    }

    private boolean begin(UiInteractionSession session, UiInteractionSession.Source source, int input) {
        // 换键会先结束旧操作；旧回调摘除关联后再保存新会话。
        session.begin(owner, source, input);
        capture = session;
        return !values.isActive() && beginOperation();
    }

    void preview(V value) {
        values.preview(value);
    }

    boolean releasePointer(int button) {
        return release(UiInteractionSession.Source.POINTER, button);
    }

    boolean releaseKey(int key) {
        return release(UiInteractionSession.Source.KEY, key);
    }

    private boolean release(UiInteractionSession.Source source, int input) {
        if (!matches(source, input)) return false;
        end(UiInteractionSession.EndReason.RELEASE);
        return true;
    }

    private boolean matches(UiInteractionSession.Source source, int input) {
        return capture != null && capture.owns(owner) && capture.matches(source, input);
    }

    boolean cancel() {
        if (!isActive() && !isCaptured()) return false;
        end(UiInteractionSession.EndReason.CANCEL);
        return true;
    }

    void finishBeforeDiscreteChange() {
        if (isActive()) end(UiInteractionSession.EndReason.RELEASE);
    }

    void end(UiInteractionSession.EndReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (capture != null && capture.end(owner, reason)) return;
        finish(reason);
    }

    void finish(UiInteractionSession.EndReason reason) {
        // 监听器可卸载控件或开始新操作，旧会话关联必须先清掉。
        capture = null;
        if (!values.isActive()) return;
        if (reason.cancels()) {
            restore.accept(values.startValue());
            values.cancel();
        } else {
            values.commit();
        }
    }

    void anchor(double pointer, double value, boolean fine) {
        anchorPointer = pointer;
        anchorValue = value;
        this.fine = fine;
    }

    boolean reanchor(double pointer, double value, boolean nextFine) {
        if (fine == nextFine) return false;
        anchor(pointer, value, nextFine);
        return true;
    }

    boolean isFine() {
        return fine;
    }

    double dragValue(double pointer, double valuePerPixel) {
        return anchorValue + (pointer - anchorPointer) * valuePerPixel;
    }
}
