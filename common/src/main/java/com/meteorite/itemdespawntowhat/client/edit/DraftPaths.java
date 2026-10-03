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
 */
public final class DraftPaths {

    // 工具类
    private DraftPaths() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 路径段：字段名与可选数组下标（{@code -1} 表示该段没有下标）。
     */
    public record Segment(String name, int index) {

        // 该段是否带数组下标
        public boolean hasIndex() {
            return index >= 0;
        }
    }

    // 解析路径：字段名 + 可选一个数组下标；空路径得到空列表
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

    // 把路径段重新渲染成字符串（重排后重算路径用）
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

    // 拼接子字段路径
    public static String child(@Nullable String path, String field) {
        return path == null || path.isEmpty() ? field : path + "." + field;
    }

    // 拼接数组元素路径（listPath 为空时只留下标）
    public static String indexPath(@Nullable String listPath, int index) {
        String base = listPath == null ? "" : listPath;
        return base + "[" + index + "]";
    }

    // 按路径读取，路径不存在返回 null
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

    // 按路径读取
    public static @Nullable JsonElement readAt(@Nullable JsonElement root, @Nullable String path) {
        return read(root, parse(path));
    }

    // 单步下降：先取字段名，再取数组下标
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

    // 按路径写入，必要时创建中间容器
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

    // 按路径删除
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
