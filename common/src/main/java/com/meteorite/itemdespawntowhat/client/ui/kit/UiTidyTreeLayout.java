package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通用 tidy 树布局：固定层高，水平方向按子树宽度装箱（自底向上累加，父节点在自己的子树带内居中）。
 *
 * <p>口径：{@code x} 为节点左边界，{@code y} 为所在层顶部；层高由 {@code levelPitch} 给出，
 * 因此 {@link #contentHeight()} 为「层数 × 层高」。同层相邻子树之间至少保留 {@code siblingGap} 像素。
 * 布局是全量重排：{@link #layout(Object, Source)} 先清空上一次结果，256 节点量级足够。</p>
 *
 * <p>节点使用身份比较（{@link IdentityHashMap}），结构相等的不同节点对象不会互相覆盖；
 * 未参与本次布局的对象，{@link #x(Object)} / {@link #y(Object)} / {@link #widthOf(Object)}
 * 一律返回 0。{@code Source} 给出的结构若含环，重复访问的节点按已记账宽度处理并只摆放一次，
 * 不会无限递归。</p>
 */
public final class UiTidyTreeLayout<N> {

    /** 子节点来源：调用方给出子列表与节点自身宽度。 */
    public interface Source<N> {
        List<N> children(N node);

        int width(N node);
    }

    private final int levelPitch;
    private final int siblingGap;
    private final Map<N, Integer> xs = new IdentityHashMap<>();
    private final Map<N, Integer> ys = new IdentityHashMap<>();
    private final Map<N, Integer> widths = new IdentityHashMap<>();
    private final Map<N, Integer> subtreeWidths = new IdentityHashMap<>();
    private final Set<N> placed = Collections.newSetFromMap(new IdentityHashMap<>());
    private int contentWidth;
    private int contentHeight;
    private int maxDepth;

    public UiTidyTreeLayout(int levelPitch, int siblingGap) {
        if (levelPitch <= 0) throw new IllegalArgumentException("Non-positive level pitch");
        if (siblingGap < 0) throw new IllegalArgumentException("Negative sibling gap");
        this.levelPitch = levelPitch;
        this.siblingGap = siblingGap;
    }

    /** 全量重排：{@code root} 或 {@code source} 为 {@code null} 时只清空结果。 */
    public void layout(N root, Source<N> source) {
        clear();
        if (root == null || source == null) return;
        contentWidth = subtreeWidth(root, source);
        place(root, source, 0, 0, contentWidth);
        contentHeight = (maxDepth + 1) * levelPitch;
    }

    public int x(N node) { return value(xs, node); }

    public int y(N node) { return value(ys, node); }

    public int widthOf(N node) { return value(widths, node); }

    /** 根子树的总宽度：所有节点左边界与右边界都落在 [0, contentWidth) 内。 */
    public int contentWidth() { return contentWidth; }

    /** 层数 × 层高；节点自身高度不由本类决定。 */
    public int contentHeight() { return contentHeight; }

    public void clear() {
        xs.clear();
        ys.clear();
        widths.clear();
        subtreeWidths.clear();
        placed.clear();
        contentWidth = 0;
        contentHeight = 0;
        maxDepth = 0;
    }

    // ---------- 内部 ----------

    // 先记账自身宽度再递归：结构含环时回边直接读到已记账值，递归必然终止。
    private int subtreeWidth(N node, Source<N> source) {
        Integer cached = subtreeWidths.get(node);
        if (cached != null) return cached;
        int own = sourceWidth(node, source);
        subtreeWidths.put(node, own);
        int total = Math.max(own, childrenWidth(source.children(node)));
        subtreeWidths.put(node, total);
        return total;
    }

    private void place(N node, Source<N> source, int depth, int left, int band) {
        if (!placed.add(node)) return;
        int own = sourceWidth(node, source);
        xs.put(node, left + (band - own) / 2);
        ys.put(node, depth * levelPitch);
        maxDepth = Math.max(maxDepth, depth);
        List<N> children = source.children(node);
        int childrenWidth = childrenWidth(children);
        if (childrenWidth <= 0) return;
        int cursor = left + (band - childrenWidth) / 2;
        for (N child : children) {
            if (child == null) continue;
            int childBand = subtreeWidthOf(child);
            place(child, source, depth + 1, cursor, childBand);
            cursor += childBand + siblingGap;
        }
    }

    // 节点自身宽度只向 Source 取一次：两趟布局（量宽 + 摆放）共用同一份记账。
    private int sourceWidth(N node, Source<N> source) {
        Integer cached = widths.get(node);
        if (cached != null) return cached;
        int own = Math.max(0, source.width(node));
        widths.put(node, own);
        return own;
    }

    private int childrenWidth(List<N> children) {
        if (children == null || children.isEmpty()) return 0;
        int total = 0;
        int count = 0;
        for (N child : children) {
            if (child == null) continue;
            total += subtreeWidthOf(child);
            count++;
        }
        return count == 0 ? 0 : total + siblingGap * (count - 1);
    }

    private int subtreeWidthOf(N node) {
        Integer cached = subtreeWidths.get(node);
        return cached == null ? 0 : cached;
    }

    private static <K> int value(Map<K, Integer> map, K node) {
        Integer found = node == null ? null : map.get(node);
        return found == null ? 0 : found;
    }
}
