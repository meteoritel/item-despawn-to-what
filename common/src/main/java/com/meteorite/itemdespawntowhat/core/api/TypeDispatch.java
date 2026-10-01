package com.meteorite.itemdespawntowhat.core.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 扁平式类型分发编解码器。
 * 与 DFU 自带的 dispatch 不同：类型专属字段与通用字段处在**同一个 JSON 对象**中，不产生嵌套的 value 字段。
 * 适用场景：规则/效果/条件叶这类"type 字段 + 各自字段"的异构对象列表。
 * 说明：解码结果按 type 字段查表后强制转型为 A，安全性由"注册类型自身的参数对象即实现 A"这一约定保证。
 */
public final class TypeDispatch {

    private static final MapCodec<String> TYPE_FIELD = Codec.STRING.fieldOf(RuleFields.TYPE);

    private TypeDispatch() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 构造扁平分发 MapCodec；A 为分发结果的公共超类型（如 Effect / Condition）
    public static <A> MapCodec<A> flat(
            TypeRegistry<? extends TypeDefinition<?>> registry,
            Function<A, ResourceLocation> typeIdGetter
    ) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                return TYPE_FIELD.decode(ops, input)
                        .flatMap(rawId -> lookup(registry, rawId))
                        .flatMap(definition -> {
                            @SuppressWarnings("unchecked")
                            MapCodec<A> codec = (MapCodec<A>) definition.codec();
                            // 参数 codec 的错误默认不含字段名（DFU 的 intRange/doubleRange 只报范围），
                            // 这里统一前缀类型 id，便于定位到"哪个类型的哪个参数"
                            return codec.decode(ops, input)
                                    .mapError(message -> "类型 " + definition.id() + " 参数错误: " + message);
                        });
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                ResourceLocation id = typeIdGetter.apply(input);
                RecordBuilder<T> builder = prefix.add(RuleFields.TYPE, ops.createString(id.toString()));
                TypeDefinition<?> definition = registry.getOrNull(id);
                if (definition == null) {
                    // 未注册类型属于编程错误：静默半写会产出无法回读的 JSON，直接暴露
                    throw new IllegalStateException("编码失败：类型未注册 " + id + "，已注册: " + registry.ids());
                }
                @SuppressWarnings("unchecked")
                MapCodec<A> codec = (MapCodec<A>) definition.codec();
                return codec.encode(input, ops, builder);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return TYPE_FIELD.keys(ops);
            }
        };
    }

    // 按 type 字段取值查表，失败时给出可读原因
    private static DataResult<? extends TypeDefinition<?>> lookup(
            TypeRegistry<? extends TypeDefinition<?>> registry,
            String rawId
    ) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return DataResult.error(() -> "非法的类型标识: " + rawId);
        }
        return registry.find(id)
                .<DataResult<? extends TypeDefinition<?>>>map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "未注册的类型: " + id + "，已注册: " + registry.ids()));
    }
}
