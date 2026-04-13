package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 客户端保存分包工具：负责把超长 JSON 拆成安全大小的分片并按序发送。
public final class SaveConfigChunker {
    private static final int MAX_DIRECT_PACKET_LENGTH = 32766;
    private static final int CHUNK_LENGTH = 30000;

    private SaveConfigChunker() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 判断当前 JSON 是否必须拆包发送。
    public static boolean requiresChunking(String jsonData) {
        return encodedLength(jsonData) > MAX_DIRECT_PACKET_LENGTH;
    }

    // 发送分包保存请求，返回实际发送的分片数量。
    public static int sendChunks(ConfigType configType, String jsonData) {
        String transferId = UUID.randomUUID().toString();
        List<String> chunks = splitIntoChunks(jsonData);
        int chunkCount = chunks.size();

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            PacketDistributor.sendToServer(new SaveConfigChunkPayload(
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
    public static void sendSingle(ConfigType configType, String jsonData) {
        PacketDistributor.sendToServer(new SaveConfigPayload(configType, jsonData));
    }

    private static List<String> splitIntoChunks(String jsonData) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int offset = 0; offset < jsonData.length(); ) {
            int codePoint = jsonData.codePointAt(offset);
            int codePointBytes = utf8Length(codePoint);
            int codePointChars = Character.charCount(codePoint);

            if (currentBytes > 0 && currentBytes + codePointBytes > CHUNK_LENGTH) {
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

    private static int encodedLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
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
