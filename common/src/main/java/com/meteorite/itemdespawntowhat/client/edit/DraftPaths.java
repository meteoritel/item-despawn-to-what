package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 草稿 JSON 的路径工具：路径语法、按路径读写删、以及把路径重新渲染成字符串。
 * <p>路径以 {@code .} 分隔字段名，数组下标写在字段名后的方括号中，例如
 * {@code effects[2].chance}、{@code outcomes[0].effects[1]}、{@code conditions.terms[0].condition}。
 * <p>本类不持有草稿状态、不做模型校验：读写时未识别字段与第三方扩展字段一律原样保留。
 * 路径是「当前 JSON 形状」的表达，重排后必须由调用方重算，不能靠路径下标跟踪对象身份。
 * <p>本类集中提供草稿路径的解析、读写删与构造（{@link #child} / {@link #indexPath}），
 * {@link #render} 只把调用方提供的路径段渲染成字符串，不更新下标或跟踪对象身份。构造方法不读写草稿，
 * 其签名不含 Gson 类型，因此界面层可直接调用；读写方法带 Gson 类型，只在 {@code client.edit} 内使用。
 */
public final class DraftPaths {

    // 工具类
    private DraftPaths() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 路径段：字段名与可选数组下标。
     *
     * @param name  字段名；{@code [i]} 段的字段名为空串
     * @param index 数组下标；{@code -1} 表示该段没有下标
     */
    public record Segment(String name, int index) {

        /**
         * 该段是否带数组下标。
         *
         * @return 下标不小于 0 时为 {@code true}
         */
        public boolean hasIndex() {
            return index >= 0;
        }
    }

    /**
     * 解析路径字符串为路径段列表。
     * <p>空路径与 {@code null} 得到空列表；连续点与首尾点产生的空段被跳过；
     * 下标不是整数时该段按无下标处理（静默降级，不抛异常）；{@code ]} 之后的残留字符被丢弃。
     *
     * @param path 草稿路径，可为 {@code null}
     * @return 路径段列表；空路径为空列表
     */
    public static List<Segment> parse(@Nullable String path) {
        List<Segment> segments = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return segments;
        }
        for (String part : path.split("\\.")) {
            if (part.isEmpty()) {
                continue;
            }
            int open = part.indexOf('[');
            if (open < 0) {
                segments.add(new Segment(part, -1));
                continue;
            }
            int close = part.indexOf(']', open);
            String name = part.substring(0, open);
            String digits = close < 0 ? part.substring(open + 1) : part.substring(open + 1, close);
            int index;
            try {
                index = Integer.parseInt(digits.trim());
            } catch (NumberFormatException ignored) {
                index = -1;
            }
            segments.add(new Segment(name, index));
        }
        return segments;
    }

    /**
     * 把路径段重新渲染成路径字符串；空列表得到空串。
     * <p>数组重排后由调用方按当前结构更新路径段，本方法不负责重算下标。
     *
     * @param segments 路径段列表
     * @return 路径字符串
     */
    public static String render(List<Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(segment.name());
            if (segment.hasIndex()) {
                builder.append('[').append(segment.index()).append(']');
            }
        }
        return builder.toString();
    }

    /**
     * 拼接子字段路径：基路径为空或 {@code null} 时只返回字段名。
     *
     * @param path  基路径，可为 {@code null}
     * @param field 子字段名
     * @return 子字段路径
     */
    public static String child(@Nullable String path, String field) {
        return path == null || path.isEmpty() ? field : path + "." + field;
    }

    /**
     * 拼接数组元素路径：列表路径为空或 {@code null} 时只返回下标段。
     *
     * @param listPath 数组所在路径，可为 {@code null}
     * @param index    元素下标
     * @return 元素路径
     */
    public static String indexPath(@Nullable String listPath, int index) {
        String base = listPath == null ? "" : listPath;
        return base + "[" + index + "]";
    }

    /**
     * 按路径读取元素：任一步类型不符或越界即返回 {@code null}。
     *
     * @param root     根元素，可为 {@code null}
     * @param segments 路径段列表
     * @return 命中的元素；路径不存在为 {@code null}
     */
    public static @Nullable JsonElement read(@Nullable JsonElement root, List<Segment> segments) {
        JsonElement current = root;
        for (Segment segment : segments) {
            current = readStep(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 按路径字符串读取元素。
     *
     * @param root 根元素，可为 {@code null}
     * @param path 草稿路径，可为 {@code null}
     * @return 命中的元素；路径不存在为 {@code null}
     */
    public static @Nullable JsonElement readAt(@Nullable JsonElement root, @Nullable String path) {
        return read(root, parse(path));
    }

    /**
     * 单步下降：先按字段名进入对象，再按下标进入数组。
     *
     * @param current 当前元素，可为 {@code null}
     * @param segment 路径段
     * @return 下降后的元素；类型不符或越界为 {@code null}
     */
    public static @Nullable JsonElement readStep(@Nullable JsonElement current, Segment segment) {
        if (current == null) {
            return null;
        }
        if (!segment.name().isEmpty()) {
            if (!current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(segment.name());
            if (current == null) {
                return null;
            }
        }
        if (segment.index() >= 0) {
            if (!current.isJsonArray()) {
                return null;
            }
            JsonArray array = current.getAsJsonArray();
            if (segment.index() >= array.size()) {
                return null;
            }
            current = array.get(segment.index());
        }
        return current;
    }

    /**
     * 按路径写入，必要时创建中间容器。
     * <p>中间对象缺失时新建；中间数组按需用空对象补洞；带下标的段遇到非数组元素会整体替换成新数组。
     *
     * @param root     草稿根对象
     * @param segments 路径段列表
     * @param value    写入值
     * @return 是否写入成功；路径段为空时返回 {@code false}
     */
    public static boolean write(JsonObject root, List<Segment> segments, JsonElement value) {
        if (segments.isEmpty()) {
            return false;
        }
        JsonObject object = root;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            boolean last = i == segments.size() - 1;
            if (segment.index() < 0) {
                if (last) {
                    object.add(segment.name(), value);
                    return true;
                }
                JsonElement member = object.get(segment.name());
                JsonObject next = member != null && member.isJsonObject() ? member.getAsJsonObject() : new JsonObject();
                object.add(segment.name(), next);
                object = next;
                continue;
            }
            JsonElement member = object.get(segment.name());
            JsonArray array = member instanceof JsonArray existing ? existing : new JsonArray();
            object.add(segment.name(), array);
            while (array.size() <= segment.index()) {
                array.add(new JsonObject());
            }
            if (last) {
                array.set(segment.index(), value);
                return true;
            }
            JsonElement slot = array.get(segment.index());
            if (!slot.isJsonObject()) {
                slot = new JsonObject();
                array.set(segment.index(), slot);
            }
            object = slot.getAsJsonObject();
        }
        return false;
    }

    /**
     * 按路径删除；按下标删除会压缩数组，后续元素下标整体前移。
     *
     * @param root     草稿根对象
     * @param segments 路径段列表
     * @return 是否删除成功；路径段为空或路径不存在时返回 {@code false}
     */
    public static boolean remove(JsonObject root, List<Segment> segments) {
        if (segments.isEmpty()) {
            return false;
        }
        JsonElement parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            parent = readStep(parent, segments.get(i));
            if (parent == null) {
                return false;
            }
        }
        if (!parent.isJsonObject()) {
            return false;
        }
        Segment last = segments.getLast();
        if (last.index() < 0) {
            return parent.getAsJsonObject().remove(last.name()) != null;
        }
        JsonElement member = parent.getAsJsonObject().get(last.name());
        if (!(member instanceof JsonArray array) || last.index() >= array.size()) {
            return false;
        }
        array.remove(last.index());
        return true;
    }
}
