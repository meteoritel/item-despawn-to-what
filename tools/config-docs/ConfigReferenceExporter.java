package com.meteorite.itemdespawntowhat.core.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** 构建期配置事实导出器；只读取真实声明与 Codec，工具类不进入模组发布产物。 */
public final class ConfigReferenceExporter {
    private ConfigReferenceExporter() { }

    /**
     * 校验声明与运行 Codec 的键集、默认等价配置，并导出双语参考表需要的事实。
     * @param args 唯一参数为输出 JSON 路径
     * @throws IOException 创建输出目录或写入文件失败时
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("需要输出 JSON 路径");
        }
        verifyKeys(ServerConfig.MAP_CODEC, ServerConfig.FIELDS);
        verifyKeys(NearbyProductLimits.MAP_CODEC, NearbyProductLimits.FIELDS);
        JsonObject defaults = ConfigField.defaults(ServerConfig.FIELDS);
        ServerConfig decoded = ServerConfig.CODEC.parse(JsonOps.INSTANCE, defaults).getOrThrow();
        if (!ServerConfig.DEFAULT.equals(decoded)) {
            throw new IllegalStateException("完整默认参考配置与运行 DEFAULT 不一致");
        }
        JsonObject result = new JsonObject();
        JsonArray fields = new JsonArray();
        ServerConfig.FIELDS.forEach(field -> fields.add(field.reference()));
        result.add("fields", fields);
        result.add("defaults", defaults);
        result.add("written_defaults", ServerConfig.CODEC.encodeStart(JsonOps.INSTANCE, ServerConfig.DEFAULT).getOrThrow());
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(result) + "\n",
                StandardCharsets.UTF_8);
        System.out.println("配置声明与 Codec 核对通过，已导出: " + output);
    }

    private static void verifyKeys(MapCodec<?> codec, List<ConfigField<?>> fields) {
        var expected = fields.stream().map(ConfigField::name).collect(Collectors.toSet());
        var actual = codec.keys(JsonOps.INSTANCE).map(value -> value.getAsString()).collect(Collectors.toSet());
        if (expected.size() != fields.size() || !actual.equals(expected)) {
            throw new IllegalStateException("配置声明与 Codec 键集不一致: " + actual + " / " + expected);
        }
        for (ConfigField<?> field : fields) {
            if (field.fallback() != null && !expected.contains(field.fallback())) {
                throw new IllegalStateException("缺省回退字段不存在: " + field.name());
            }
        }
    }
}
