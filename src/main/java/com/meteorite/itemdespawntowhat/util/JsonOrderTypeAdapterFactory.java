package com.meteorite.itemdespawntowhat.util;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JsonOrderTypeAdapterFactory implements TypeAdapterFactory {

    private final Map<Class<?>, List<FieldInfo>> cache = new ConcurrentHashMap<>();

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> rawType = type.getRawType();

        // 跳过基本类型、枚举、数组、JDK 类等
        if (rawType.isPrimitive() || rawType.isEnum() || rawType.isArray() ||
                (rawType.getPackage() != null && rawType.getPackage().getName().startsWith("java."))) {
            return null;
        }

        List<FieldInfo> fieldInfos = getFieldInfos(rawType);
        if (fieldInfos.isEmpty()) {
            return null;
        }

        // 获取默认适配器，用于反序列化
        TypeAdapter<T> defaultAdapter = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }

                out.beginObject();
                for (FieldInfo info : fieldInfos) {
                    try {
                        Object fieldValue = info.field.get(value);
                        // 遵循 Gson 的 null 输出配置
                        if (fieldValue == null && !gson.serializeNulls()) {
                            continue;
                        }
                        out.name(info.jsonName);
                        if (fieldValue == null) {
                            out.nullValue();
                        } else {
                            info.write(gson, out, fieldValue);
                        }
                    } catch (IllegalAccessException e) {
                        throw new JsonIOException(e);
                    }
                }
                out.endObject();
            }

            @Override
            public T read(JsonReader in) throws IOException {
                // 完全使用默认反序列化，不参与排序
                return defaultAdapter.read(in);
            }
        };
    }

    private List<FieldInfo> getFieldInfos(Class<?> clazz) {
        return cache.computeIfAbsent(clazz, c -> {
            List<Field> fields = collectFields(c);
            // 若没有任何字段使用 @JsonOrder，则跳过排序（返回空列表）
            if (fields.stream().noneMatch(f -> f.isAnnotationPresent(JsonOrder.class))) {
                return Collections.emptyList();
            }
            sortFields(fields);
            List<FieldInfo> infos = new ArrayList<>(fields.size());
            for (Field f : fields) {
                f.setAccessible(true);
                String jsonName = getJsonName(f);
                Type fieldType = f.getGenericType();
                infos.add(new FieldInfo(jsonName, f, fieldType));
            }
            return infos;
        });
    }

    private List<Field> collectFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isStatic(mod) && !Modifier.isTransient(mod)) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private void sortFields(List<Field> fields) {
        Map<Field, Integer> declarationIndex = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            declarationIndex.put(fields.get(i), i);
        }
        fields.sort((f1, f2) -> {
            int order1 = Optional.ofNullable(f1.getAnnotation(JsonOrder.class))
                    .map(JsonOrder::value).orElse(Integer.MAX_VALUE);
            int order2 = Optional.ofNullable(f2.getAnnotation(JsonOrder.class))
                    .map(JsonOrder::value).orElse(Integer.MAX_VALUE);
            if (order1 != order2) {
                return Integer.compare(order1, order2);
            }
            return Integer.compare(declarationIndex.get(f1), declarationIndex.get(f2));
        });
    }

    private String getJsonName(Field f) {
        SerializedName sn = f.getAnnotation(SerializedName.class);
        return sn != null ? sn.value() : f.getName();
    }

    @SuppressWarnings("unchecked")
    record FieldInfo(String jsonName, Field field, Type fieldType) {

        void write(Gson gson, JsonWriter out, Object value){
                try {
                    TypeAdapter<Object> adapter = (TypeAdapter<Object>) gson.getAdapter(TypeToken.get(fieldType));
                    adapter.write(out, value);
                } catch (Exception e) {
                    JsonElement element = gson.toJsonTree(value);
                    gson.toJson(element, out);
                }
        }
    }
}
