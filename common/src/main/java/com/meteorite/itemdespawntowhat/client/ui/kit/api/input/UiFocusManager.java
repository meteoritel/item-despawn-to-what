package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 按视觉顺序管理一个作用域的唯一逻辑焦点，键盘提示与业务选中分别表达。
 */
public final class UiFocusManager {
    /**
     * 宿主通过通知接入旁白与滚动到焦点。
     */
    public interface Listener {
        /**
         * 焦点已切换，原生控件适配亦已同步。
         */
        void focusChanged(@Nullable UiFocusTarget previous, @Nullable UiFocusTarget current);

        /** 按声明顺序执行效果，便于组合滚动、播报与宿主键政策。 */
        static Listener compose(Listener... effects) {
            List<Listener> ordered = List.of(effects);
            return (previous, current) -> ordered.forEach(effect -> effect.focusChanged(previous, current));
        }

        /** 焦点名称交给宿主播报；平台 narrator 的选择由 sink 负责。 */
        static Listener narrate(Consumer<Component> sink) {
            return (previous, current) -> {
                Component name = current == null ? null : current.accessibleName();
                if (name != null && !name.getString().isBlank()) sink.accept(name);
            };
        }
    }

    private final UiInputScope scope;
    private Index index = new Index();
    private Index pending = new Index();
    private final Map<UiFocusTarget, UiFocusTarget> lastWithin = new IdentityHashMap<>();
    private @Nullable UiFocusTarget withinGroup;
    private boolean registeredChild;

    private static final class Index {
        final List<UiFocusTarget> leaves = new ArrayList<>();
        final Map<UiFocusTarget, List<UiFocusTarget>> members = new IdentityHashMap<>();
        final Set<UiFocusTarget> path = Collections.newSetFromMap(new IdentityHashMap<>());

        List<UiFocusTarget> collect(UiFocusTarget target) {
            Objects.requireNonNull(target);
            if (path.contains(target))
                throw new IllegalArgumentException("Cyclic focusTargets: " + target.getClass().getName());
            List<UiFocusTarget> known = members.get(target);
            if (known != null) return known;
            List<UiFocusTarget> children = List.copyOf(target.focusTargets());
            if (children.size() == 1 && children.getFirst() == target) {
                leaves.add(target);
                List<UiFocusTarget> result = List.of(target);
                members.put(target, result);
                return result;
            }
            path.add(target);
            List<UiFocusTarget> result = new ArrayList<>();
            Set<UiFocusTarget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            try {
                for (UiFocusTarget child : children)
                    for (UiFocusTarget leaf : collect(child)) if (seen.add(leaf)) result.add(leaf);
            } finally {
                path.remove(target);
            }
            List<UiFocusTarget> resolved = List.copyOf(result);
            members.put(target, resolved);
            return resolved;
        }

        boolean contains(UiFocusTarget group, UiFocusTarget leaf) {
            List<UiFocusTarget> children = members.get(group);
            return children != null && children.stream().anyMatch(target -> target == leaf);
        }

        int leafIndex(@Nullable UiFocusTarget target) {
            for (int i = 0; i < leaves.size(); i++) if (leaves.get(i) == target) return i;
            return -1;
        }
    }
    private @Nullable UiFocusTarget focused;
    private @Nullable UiFocusTarget anchor;
    private @Nullable Listener listener;
    private boolean updating;
    private boolean suspended = true;
    private boolean keyboardMode;
    private boolean enterActivates = true;
    private boolean spaceActivates = true;

    UiFocusManager(UiInputScope scope) {
        this.scope = scope;
    }

    UiInputScope scope() {
        return scope;
    }

    @Nullable UiFocusTarget current() {
        return suspended ? null : focused;
    }

    boolean keyboardMode() {
        return keyboardMode;
    }

    boolean containsFocus(@Nullable UiFocusTarget target) {
        return !suspended && focused != null && target != null
                && (focused == target || anchor != null && index.contains(target, anchor));
    }

    /**
     * 显式记录输入方式，只影响额外焦点提示。
     */
    public void setKeyboardMode(boolean keyboardMode) {
        this.keyboardMode = keyboardMode;
    }

    /**
     * 开始替换视觉顺序；未重新登记的目标在结束更新时卸载。
     *
     * @throws IllegalStateException 上一次登记尚未结束
     */
    public void beginUpdate() {
        if (updating) throw new IllegalStateException("UiFocusManager.beginUpdate: update already started");
        pending = new Index();
        updating = true;
    }

    /**
     * 同步收集并替换视觉顺序；收集失败时保留已提交列表并释放登记状态。
     * 回调中已执行的宿主副作用与焦点视图挂载由其各自所有者管理。
     *
     * @throws IllegalStateException 已有未结束的登记
     */
    public void updateTargets(Consumer<UiFocusManager> register) {
        beginUpdate();
        boolean collected = false;
        try {
            register.accept(this);
            collected = true;
        } finally {
            if (!collected) abortUpdate();
        }
        endUpdate();
    }

    /**
     * 登记目标，同时将包装控件与核心的焦点视图接入本作用域。
     */
    public void add(UiFocusTarget target) {
        if (updating) {
            pending.collect(target);
            return;
        }
        beginUpdate();
        pending.leaves.addAll(index.leaves);
        pending.members.putAll(index.members);
        try {
            pending.collect(target);
        } catch (RuntimeException failure) {
            abortUpdate();
            throw failure;
        }
        endUpdate();
    }

    /**
     * 完成登记并取消已卸载目标的未完成交互；通知发生在登记状态退出后。
     *
     * @throws IllegalStateException 尚未开始登记
     */
    public void endUpdate() {
        if (!updating) throw new IllegalStateException("UiFocusManager.endUpdate: update not started");
        index = pending;
        pending = new Index();
        updating = false;
        // 先挂组，再挂叶子：共享视图最终指向实际 Tab 目标。
        for (UiFocusTarget target : index.members.keySet()) target.focusState().attach(this, target);
        for (UiFocusTarget target : index.leaves) target.focusState().attach(this, target);
        if (focused != null && withinGroup != null) focused.focusState().attach(this, focused);
        lastWithin.entrySet().removeIf(entry -> !index.contains(entry.getKey(), entry.getValue()));
        refresh();
    }

    void requireUpdateComplete() {
        if (!updating) return;
        abortUpdate();
        throw new IllegalStateException("UiFocusManager.refreshFocus: update not completed");
    }

    private void abortUpdate() {
        pending = new Index();
        updating = false;
    }

    /**
     * 卸载全部焦点目标。
     */
    public void clear() {
        invalidate(UiInteractionSession.EndReason.UNMOUNTED);
        index = new Index();
        pending = new Index();
        updating = false;
        lastWithin.clear();
    }

    /**
     * 登记数量。
     */
    public int size() {
        return index.leaves.size();
    }

    /**
     * 只读视觉顺序。
     */
    public List<UiFocusTarget> targets() {
        return List.copyOf(index.leaves);
    }

    /**
     * 活动作用域内的当前目标。
     */
    public @Nullable UiFocusTarget focused() {
        refresh();
        return current();
    }

    /**
     * 当前目标在视觉序列中的位置，无焦点时为 -1。
     */
    public int focusedIndex() {
        UiFocusTarget target = focused();
        return target == null ? -1 : index.leafIndex(anchor == null ? target : anchor);
    }

    /**
     * 进入已登记组的内部模式，按请求组选择当前、最近或首个合格 Tab 锚点。
     */
    public boolean focusWithin(UiFocusTarget group, UiFocusTarget child) {
        List<UiFocusTarget> members = index.members.get(group);
        if (members == null || !group.canFocus() || !child.canFocus()) return false;
        if (index.members.containsKey(child) && !index.contains(group, child)) return false;
        UiFocusTarget stop = index.contains(group, child) ? child
                : anchor != null && index.contains(group, anchor) && anchor.canFocus() ? anchor
                : lastWithin.get(group);
        if (stop == null || !index.contains(group, stop) || !stop.canFocus())
            stop = members.stream().filter(UiFocusTarget::canFocus).findFirst().orElse(null);
        if (stop == null) return false;
        setFocusedInternal(child, stop, group, index.members.containsKey(child));
        return true;
    }

    /**
     * 请求已登记叶子或组；共享视图的包装对象解析为实际 Tab 目标。
     */
    public boolean focusOn(@Nullable UiFocusTarget target) {
        if (target == null) return clearFocus();
        if (!target.canFocus()) return false;
        UiFocusTarget requested = target;
        if (index.leafIndex(target) < 0) {
            target = index.leaves.stream().filter(leaf -> leaf.focusState() == requested.focusState())
                    .findFirst().orElse(null);
            if (target == null) {
                List<UiFocusTarget> members = index.members.get(requested);
                if (members != null) target = members.stream().filter(UiFocusTarget::canFocus).findFirst().orElse(null);
            }
        }
        if (target == null || !target.canFocus()) return false;
        setFocusedInternal(target, target, null, false);
        return true;
    }

    /**
     * 清除当前焦点，完成该目标尚未结清的有效编辑。
     */
    public boolean clearFocus() {
        if (focused == null) return false;
        setFocusedInternal(null);
        return true;
    }

    /**
     * 选择首个合格目标。
     */
    public boolean focusFirst() {
        for (UiFocusTarget target : index.leaves) if (target.canFocus()) return focusOn(target);
        return false;
    }

    /**
     * Tab 在合格目标间按视觉顺序环绕。
     */
    public boolean moveFocus(int delta) {
        refresh();
        List<UiFocusTarget> available = index.leaves.stream().filter(UiFocusTarget::canFocus).toList();
        if (available.isEmpty()) return false;
        UiFocusTarget stop = anchor == null ? focused : anchor;
        int currentIndex = -1;
        for (int i = 0; i < available.size(); i++) if (available.get(i) == stop) { currentIndex = i; break; }
        int next = currentIndex < 0 ? (delta < 0 ? available.size() - 1 : 0) : Math.floorMod(currentIndex + delta, available.size());
        return focusOn(available.get(next));
    }

    /**
     * 激活当前焦点，不改变业务选中状态的归属。
     */
    public boolean activateFocused() {
        UiFocusTarget target = focused();
        return target != null && target.activate();
    }

    /**
     * 处理 Tab 和内容未消费的 Enter/Space。
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) return moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1);
        if (enterActivates && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER))
            return activateFocused();
        UiFocusTarget target = focused();
        return spaceActivates && keyCode == GLFW.GLFW_KEY_SPACE && target != null && !target.acceptsTextInput() && activateFocused();
    }

    /**
     * 宿主的 Enter 激活政策。
     */
    public void setEnterActivates(boolean value) {
        enterActivates = value;
    }

    /**
     * 宿主的 Space 激活政策。
     */
    public void setSpaceActivates(boolean value) {
        spaceActivates = value;
    }

    /**
     * 接入旁白和滚动通知。
     */
    public void setListener(@Nullable Listener value) {
        listener = value;
    }

    void suspend() {
        UiFocusTarget previous = current();
        suspended = true;
        if (previous != null) previous.focusChanged(false);
    }

    void resume() {
        refresh();
        suspended = false;
        if (focused == null) focusFirst();
        else {
            focused.focusChanged(true);
            if (listener != null) listener.focusChanged(null, focused);
        }
    }

    private void refresh() {
        if (focused == null) return;
        boolean mounted = anchor != null && index.leafIndex(anchor) >= 0 && anchor.canFocus();
        if (withinGroup != null) mounted &= index.contains(withinGroup, anchor)
                && withinGroup.canFocus() && (!registeredChild || index.contains(withinGroup, focused));
        if (!mounted || !focused.canFocus()) invalidate(UiInteractionSession.EndReason.UNMOUNTED);
    }

    private void invalidate(UiInteractionSession.EndReason reason) {
        if (focused == null) return;
        endFocusedInteraction(reason);
        setFocusedInternal(null);
    }

    private void endFocusedInteraction(UiInteractionSession.EndReason reason) {
        UiInteractionSession.Target target = scope.interaction().target();
        if (target instanceof UiFocusTarget focusTarget && focused != null && focusTarget.focusState() == focused.focusState()) {
            scope.interaction().end(reason);
        }
    }

    private void setFocusedInternal(@Nullable UiFocusTarget next) {
        setFocusedInternal(next, next, null, false);
    }

    private void setFocusedInternal(@Nullable UiFocusTarget next, @Nullable UiFocusTarget nextAnchor,
                                    @Nullable UiFocusTarget nextGroup, boolean nextRegisteredChild) {
        UiFocusTarget previous = focused;
        if (previous != next) endFocusedInteraction(UiInteractionSession.EndReason.FOCUS_TRANSFER);
        focused = next;
        anchor = nextAnchor;
        withinGroup = nextGroup;
        registeredChild = nextRegisteredChild;
        if (next != null) next.focusState().attach(this, next);
        if (nextAnchor != null) for (UiFocusTarget group : index.members.keySet())
            if (index.contains(group, nextAnchor)) lastWithin.put(group, nextAnchor);
        // 通知前提交完整归属，滚动 adapter 可立即查询当前组与锚点。
        if (!suspended && previous != next) {
            if (previous != null) previous.focusChanged(false);
            if (next != null) next.focusChanged(true);
            if (listener != null) listener.focusChanged(previous, next);
        }
    }
}
