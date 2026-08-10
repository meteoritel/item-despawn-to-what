package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.network.ConfigEditLimits;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.platform.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户端配置保存分包工具。
 */
public final class SaveConfigChunker {
    private SaveConfigChunker() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 判断当前 JSON 是否必须拆包发送。
    public static boolean requiresChunking(String jsonData) {
        return ConfigEditLimits.encodedLength(jsonData) > ConfigEditLimits.MAX_DIRECT_PACKET_BYTES;
    }

    // 发送分包保存请求，返回实际发送的分片数量。
    public static int sendChunks(ConversionType configType, String jsonData) {
        validateConfigSize(jsonData);
        String transferId = UUID.randomUUID().toString();
        List<String> chunks = splitIntoChunks(jsonData);
        int chunkCount = chunks.size();

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            Services.PLATFORM.sendToServer(new SaveConfigChunkPayload(
                    configType,
                    transferId,
                    chunkIndex,
                    chunkCount,
                    chunks.get(chunkIndex)
            ));
        }

        return chunkCount;
    }

    // 小 JSON 仍然走单包快速通道。
    public static void sendSingle(ConversionType configType, String jsonData) {
        validateConfigSize(jsonData);
        Services.PLATFORM.sendToServer(new SaveConfigPayload(configType, jsonData));
    }

    private static List<String> splitIntoChunks(String jsonData) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int offset = 0; offset < jsonData.length(); ) {
            int codePoint = jsonData.codePointAt(offset);
            int codePointBytes = utf8Length(codePoint);
            int codePointChars = Character.charCount(codePoint);

            if (currentBytes > 0 && currentBytes + codePointBytes > ConfigEditLimits.MAX_CHUNK_BYTES) {
                chunks.add(currentChunk.toString());
                currentChunk.setLength(0);
                currentBytes = 0;
            }

            currentChunk.appendCodePoint(codePoint);
            currentBytes += codePointBytes;
            offset += codePointChars;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    private static void validateConfigSize(String jsonData) {
        if (!ConfigEditLimits.isConfigSizeValid(jsonData)) {
            throw new IllegalArgumentException("Config payload exceeds the maximum allowed size");
        }
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }
}
