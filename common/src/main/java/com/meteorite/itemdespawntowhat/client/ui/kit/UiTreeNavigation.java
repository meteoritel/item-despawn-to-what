package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 可见树行的纯导航算法；激活与编辑语义由宿主决定。
 * <p>rows 与 state 属于同一结构版本；折叠变化后宿主重新派生可见行，再处理下一次导航。</p>
 * <pre>{@code
 * var intent = UiTreeNavigation.fromKey(keyCode);
 * if (intent != null) state = UiTreeNavigation.navigate(projection.visible(state), state, intent, pageRows);
 * }</pre>
 */
public final class UiTreeNavigation {
    /** 视图导航意图。 */
    public enum Intent { UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN }
    private UiTreeNavigation() {}
    /** 将标准树导航键转换为意图，其余键返回 null。 */
    public static @Nullable Intent fromKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_UP -> Intent.UP; case GLFW.GLFW_KEY_DOWN -> Intent.DOWN;
            case GLFW.GLFW_KEY_LEFT -> Intent.LEFT; case GLFW.GLFW_KEY_RIGHT -> Intent.RIGHT;
            case GLFW.GLFW_KEY_HOME -> Intent.HOME; case GLFW.GLFW_KEY_END -> Intent.END;
            case GLFW.GLFW_KEY_PAGE_UP -> Intent.PAGE_UP; case GLFW.GLFW_KEY_PAGE_DOWN -> Intent.PAGE_DOWN;
            default -> null;
        };
    }
    /** 返回新状态；空树不改选择，页行数至少为 1。 */
    public static <N> UiTreeViewState navigate(List<UiTreeProjection.Entry<N>> rows, UiTreeViewState state,
                                               Intent intent, int pageRows) {
        if (rows.isEmpty()) return state;
        int current = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).path().equals(state.selected())) { current = i; break; }
        if (current < 0) return state.select((intent == Intent.END || intent == Intent.UP || intent == Intent.PAGE_UP
                ? rows.getLast() : rows.getFirst()).path());
        UiTreeProjection.Entry<N> row = rows.get(current);
        int next = switch (intent) {
            case UP -> current - 1; case DOWN -> current + 1;
            case HOME -> 0; case END -> rows.size() - 1;
            case PAGE_UP -> current - Math.max(1, pageRows); case PAGE_DOWN -> current + Math.max(1, pageRows);
            case LEFT, RIGHT -> current;
        };
        if (intent == Intent.LEFT) {
            if (row.childCount() > 0 && !state.collapsed().contains(row.path())) return state.fold(row.path(), true);
            if (row.parent() != null) return state.select(row.parent());
        } else if (intent == Intent.RIGHT && row.childCount() > 0) {
            if (state.collapsed().contains(row.path())) return state.fold(row.path(), false);
            if (current + 1 < rows.size() && row.path().equals(rows.get(current + 1).parent())) next++;
        }
        return state.select(rows.get(Math.clamp(next, 0, rows.size() - 1)).path());
    }
}
