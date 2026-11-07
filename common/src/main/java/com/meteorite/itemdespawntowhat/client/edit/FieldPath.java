package com.meteorite.itemdespawntowhat.client.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * JSON 字段的不可变位置；键和数组下标分别表达，重排后的身份由编辑历史负责映射。
 * 文本形式只在问题输入、草稿持久化和通用树展示出口使用。
 */
public final class FieldPath {
    /** 整个值的根位置；不隐含任何业务字段。 */
    public static final FieldPath ROOT = new FieldPath(List.of());

    sealed interface Segment permits Key, Index { }
    record Key(String name) implements Segment { }
    record Index(int value) implements Segment { }

    private final List<Segment> segments;

    private FieldPath(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    /** 从单个字段键构造位置，不把键名解释为路径表达式。 */
    public static FieldPath key(String name) {
        return ROOT.child(name);
    }

    /**
     * 接纳点与方括号语法；键中的点、括号和反斜杠使用反斜杠转义。
     * 空文本表示根，非法语法抛出 IllegalArgumentException。
     * 整段输入必须被消耗，避免把畸变地址改写成另一个合法位置。
     */
    public static FieldPath parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return ROOT;
        List<Segment> parsed = new ArrayList<>();
        int cursor = 0;
        boolean expectKey = text.charAt(0) != '[';
        while (cursor < text.length()) {
            if (expectKey) {
                StringBuilder key = new StringBuilder();
                while (cursor < text.length()) {
                    char character = text.charAt(cursor);
                    if (character == '.' || character == '[' || character == ']') break;
                    cursor++;
                    if (character == '\\') {
                        if (cursor == text.length()) throw invalid(text);
                        character = text.charAt(cursor++);
                        if (".[]\\".indexOf(character) < 0) throw invalid(text);
                    }
                    key.append(character);
                }
                if (key.isEmpty()) throw invalid(text);
                parsed.add(new Key(key.toString()));
                expectKey = false;
            }
            if (cursor == text.length()) break;
            char next = text.charAt(cursor++);
            if (next == '.') {
                if (cursor == text.length()) throw invalid(text);
                expectKey = true;
            } else if (next == '[') {
                int begin = cursor;
                while (cursor < text.length() && text.charAt(cursor) >= '0' && text.charAt(cursor) <= '9') cursor++;
                if (cursor == begin || cursor == text.length() || text.charAt(cursor) != ']') throw invalid(text);
                try {
                    parsed.add(new Index(Integer.parseInt(text.substring(begin, cursor))));
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("字段路径下标越界：" + text, error);
                }
                cursor++;
                if (cursor < text.length() && text.charAt(cursor) != '.' && text.charAt(cursor) != '[') throw invalid(text);
            } else {
                throw invalid(text);
            }
        }
        return new FieldPath(parsed);
    }

    private static IllegalArgumentException invalid(String text) {
        return new IllegalArgumentException("字段路径语法无效：" + text);
    }

    /** 在当前位置追加一个原始字段键。 */
    public FieldPath child(String name) {
        if (Objects.requireNonNull(name, "name").isEmpty()) throw new IllegalArgumentException("字段键不能为空");
        return appendSegment(new Key(name));
    }

    /** 追加非负数组下标；是否存在该元素由读取或写入者判断。 */
    public FieldPath index(int index) {
        if (index < 0) throw new IllegalArgumentException("字段路径下标不能为负数：" + index);
        return appendSegment(new Index(index));
    }

    private FieldPath appendSegment(Segment segment) {
        List<Segment> result = new ArrayList<>(segments);
        result.add(segment);
        return new FieldPath(result);
    }

    /** 组合一个相对位置；根后缀保持当前位置。 */
    public FieldPath append(FieldPath suffix) {
        if (suffix.isRoot()) return this;
        List<Segment> result = new ArrayList<>(segments);
        result.addAll(suffix.segments);
        return new FieldPath(result);
    }

    /** 是否为根位置。 */
    public boolean isRoot() { return segments.isEmpty(); }

    /** 键和下标分别计为一个位置段。 */
    public int depth() { return segments.size(); }

    /** 父位置；根的父位置仍为根。 */
    public FieldPath parent() { return prefix(Math.max(0, depth() - 1)); }

    /** 取指定段深度的前缀，供结构位置适配使用。 */
    public FieldPath prefix(int depth) {
        return depth == 0 ? ROOT : new FieldPath(segments.subList(0, depth));
    }

    /** 当前路径是否与作用域相同或位于其下；按完整键和下标比较。 */
    public boolean isWithin(FieldPath scope) {
        return depth() >= scope.depth() && segments.subList(0, scope.depth()).equals(scope.segments);
    }

    /** 返回相对作用域的后缀；作用域不包含当前位置时抛出 IllegalArgumentException。 */
    public FieldPath relativeTo(FieldPath scope) {
        if (!isWithin(scope)) throw new IllegalArgumentException(this + " 不属于 " + scope);
        return new FieldPath(segments.subList(scope.depth(), depth()));
    }

    /** 把已匹配的作用域替换为新位置，保留键和下标后缀。 */
    public FieldPath replacePrefix(FieldPath from, FieldPath to) {
        return to.append(relativeTo(from));
    }

    /** 顶层字段键；根或以数组下标开始的位置返回 null。 */
    public @Nullable String firstKey() {
        return !isRoot() && segments.getFirst() instanceof Key(var name) ? name : null;
    }

    /** 最后一个字段键；根或数组元素位置返回 null。 */
    public @Nullable String lastKey() {
        return !isRoot() && segments.getLast() instanceof Key(var name) ? name : null;
    }

    /** 最后一个数组下标；当前位置不是数组元素时返回 -1。 */
    public int lastIndex() {
        return !isRoot() && segments.getLast() instanceof Index(var value) ? value : -1;
    }

    /** 后代相对本位置的直接字段键；相同位置或非字段后继返回 null。 */
    public @Nullable String childName(FieldPath descendant) {
        return descendant.depth() > depth() && descendant.isWithin(this)
                && descendant.segments.get(depth()) instanceof Key(var name) ? name : null;
    }

    List<Segment> segments() { return segments; }

    /** 按完整位置段判断相等。 */
    @Override public boolean equals(Object other) {
        return this == other || other instanceof FieldPath path && segments.equals(path.segments);
    }

    /** 与位置段相等语义一致，供输入与历史索引使用。 */
    @Override public int hashCode() { return segments.hashCode(); }

    /** 输出现行草稿与问题定位使用的点和方括号表示。 */
    @Override public String toString() {
        StringBuilder text = new StringBuilder();
        for (Segment segment : segments) {
            if (segment instanceof Key(var name)) {
                if (!text.isEmpty()) text.append('.');
                for (char character : name.toCharArray()) {
                    if (".[]\\".indexOf(character) >= 0) text.append('\\');
                    text.append(character);
                }
            } else if (segment instanceof Index(var value)) {
                text.append('[').append(value).append(']');
            }
        }
        return text.toString();
    }
}
