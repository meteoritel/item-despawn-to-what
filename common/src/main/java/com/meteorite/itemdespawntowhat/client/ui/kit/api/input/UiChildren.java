package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * 容器的实际有序成员：绘制方直接遍历本集合，命中按逆序，Tab 默认按声明顺序。
 * 只持有结构，不持有捕获；拖动与释放继续由作用域的唯一会话派发。
 */
public final class UiChildren<T extends UiInputTarget> extends AbstractList<T> implements UiInputTarget {
    private final List<T> members = new ArrayList<>();
    private final Map<T, Integer> tabOrder = new IdentityHashMap<>();
    private List<T> navigation;
    private long revision;

    /** 读取声明顺序中的成员。 */
    @Override public T get(int index) { return members.get(index); }
    /** 当前实际成员数量。 */
    @Override public int size() { return members.size(); }
    /** 插入成员并使焦点结构失效。 */
    @Override public void add(int index, T target) { members.add(index, target); navigation = null; revision++; modCount++; }
    /** 移除成员；交互生命周期仍由其所有者结清。 */
    @Override public T remove(int index) { T previous = members.remove(index); tabOrder.remove(previous); navigation = null; revision++; modCount++; return previous; }
    /** 替换成员，保持该位置的绘制与导航顺序。 */
    @Override public T set(int index, T target) { T previous = members.set(index, target); if (previous != target) { tabOrder.remove(previous); navigation = null; revision++; } return previous; }
    /** 导航优先级独立于遮挡；相同优先级保持声明顺序，默认优先级为零。 */
    public void setTabOrder(T target, int order) {
        if (tabOrder.getOrDefault(target, 0) == order) return;
        tabOrder.put(target, order);
        navigation = null;
        revision++;
    }
    private List<T> navigation() {
        if (tabOrder.isEmpty()) return members;
        if (navigation == null) {
            navigation = new ArrayList<>(members);
            navigation.sort(Comparator.comparingInt(target -> tabOrder.getOrDefault(target, 0)));
        }
        return navigation;
    }
    /** 结构及子树变化的版本；坐标移动不改变版本。 */
    @Override public long focusRevision() {
        long value = revision;
        for (T member : members) value = 31 * value + member.focusRevision();
        return value;
    }
    /** 按导航顺序递归贡献，资格由实际焦点目标决定。 */
    @Override public void addFocusTargets(Consumer<UiFocusTarget> out) { for (T member : navigation()) {
        if (!(member instanceof UiWidget widget) || widget.isVisible()) member.addFocusTargets(out);
    } }
    /** 会话由作用域结束，本集合只传播成员的本地退出通知。 */
    @Override public void scopeExited(UiInteractionSession.EndReason reason) {
        for (T member : members) member.scopeExited(reason);
    }
    /** 持焦路径直接沿实际成员判断，保持组内编辑的归属。 */
    @Override public boolean hasFocus() {
        for (T member : members) if (member.hasFocus()) return true;
        return false;
    }
    /** 顶层成员遮挡下层，即使它没有动作或提示。 */
    @Override public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = UiPointerHit.topmost(x, y, members);
        return hit == null ? null : hit.within(this);
    }
    /** 只派发给快照选中的直接成员，不重新查询几何。 */
    @Override public boolean mousePressed(UiInputContext context) {
        UiInputContext resolved = resolve(context);
        for (T member : members.reversed()) if (resolved.hits(member)) return member.mousePressed(resolved);
        return false;
    }
    /** 无捕获的配对释放只交给原命中的成员一次。 */
    @Override public boolean mouseReleased(UiInputContext context) {
        for (T member : members.reversed()) if (context.hits(member)) return member.mouseReleased(context);
        return false;
    }
    /** 子成员先处理滚轮，未消费时由外部视口继续处理。 */
    @Override public boolean mouseScrolled(UiInputContext context, double x, double y) {
        UiInputContext resolved = resolve(context);
        for (T member : members.reversed()) if (resolved.hits(member)) return member.mouseScrolled(resolved, x, y);
        return false;
    }
    /** 普通键只沿拥有逻辑焦点的子树派发。 */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        for (T member : members) if (member.hasFocus()) return member.keyPressed(key, scan, modifiers);
        return false;
    }
    /** 修饰态释放沿当前逻辑焦点路径派发。 */
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        for (T member : members) if (member.hasFocus()) return member.keyReleased(key, scan, modifiers);
        return false;
    }
    /** 字符与普通键使用同一焦点路径。 */
    @Override public boolean charTyped(char value, int modifiers) {
        for (T member : members) if (member.hasFocus()) return member.charTyped(value, modifiers);
        return false;
    }
    private UiInputContext resolve(UiInputContext context) {
        if (context.hit() != null) return context;
        UiPointerHit hit = hitTest(context.pointerX(), context.pointerY());
        return context.withHit(hit == null ? UiPointerHit.EMPTY : hit);
    }
}
