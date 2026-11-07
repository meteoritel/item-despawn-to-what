package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/** JSON 遍历只解释位置段；容器的创建和替换由实际编辑操作决定。 */
final class DraftPaths {
    private DraftPaths() { }

    static @Nullable JsonElement readAt(@Nullable JsonElement root, FieldPath path) {
        JsonElement current = root;
        for (FieldPath.Segment segment : path.segments()) {
            current = readStep(current, segment);
            if (current == null) return null;
        }
        return current;
    }

    static @Nullable JsonElement readStep(@Nullable JsonElement current, FieldPath.Segment segment) {
        return switch (segment) {
            case FieldPath.Key(var name) -> current instanceof JsonObject object ? object.get(name) : null;
            case FieldPath.Index(var index) -> current instanceof JsonArray array && index < array.size() ? array.get(index) : null;
        };
    }

    static boolean canWrite(JsonObject root, FieldPath path) {
        if (path.isRoot()) return false;
        JsonElement parent = readAt(root, path.parent());
        return switch (path.segments().getLast()) {
            case FieldPath.Key ignored -> parent instanceof JsonObject;
            case FieldPath.Index(var index) -> parent instanceof JsonArray array && index < array.size();
        };
    }

    static boolean write(JsonObject root, FieldPath path, JsonElement value) {
        if (path.isRoot()) return false;
        JsonElement parent = readAt(root, path.parent());
        return switch (path.segments().getLast()) {
            case FieldPath.Key(var name) -> {
                if (!(parent instanceof JsonObject object)) yield false;
                object.add(name, value);
                yield true;
            }
            case FieldPath.Index(var index) -> {
                if (!(parent instanceof JsonArray array) || index >= array.size()) yield false;
                array.set(index, value);
                yield true;
            }
        };
    }

    static boolean remove(JsonObject root, FieldPath path) {
        if (path.isRoot()) return false;
        JsonElement parent = readAt(root, path.parent());
        return switch (path.segments().getLast()) {
            case FieldPath.Key(var name) -> parent instanceof JsonObject object && object.remove(name) != null;
            case FieldPath.Index(var index) -> {
                if (!(parent instanceof JsonArray array) || index >= array.size()) yield false;
                array.remove(index);
                yield true;
            }
        };
    }
}
