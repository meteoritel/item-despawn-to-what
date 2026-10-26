package com.meteorite.itemdespawntowhat.core.network.transport;

import org.jetbrains.annotations.Nullable;

/**
 * 分片批次装配：服务端累加器与客户端缓冲共用的形状校验、归位与收齐判定。
 * 字节数只在校验入口计量一次，避免调用方绕过单片的限额。
 */
public final class RuleChunkAssembly {

    private RuleChunkAssembly() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 通过形状校验的分片及其 UTF-8 字节数。
     * @param payload 分片载荷
     * @param bytes 文本字节数
     */
    public record Chunk(RuleChunkPayload payload, int bytes) { }

    /**
     * 形状校验：标识、序号、总数与单片长度任一非法即拒绝。
     * @param payload 原始分片，可为 null
     * @return 校验通过的分片，非法时返回 null
     */
    public static @Nullable Chunk validate(@Nullable RuleChunkPayload payload) {
        if (payload == null) {
            return null;
        }
        String transferId = payload.transferId();
        int count = payload.count();
        int index = payload.index();
        if (transferId == null || transferId.isEmpty()
                || transferId.length() > RuleEditLimits.MAX_TRANSFER_ID_LENGTH
                || count > RuleEditLimits.MAX_CHUNK_COUNT
                || index < 0 || index >= count) {
            return null;
        }
        String chunk = payload.chunk();
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }
        int bytes = RuleEditLimits.encodedLength(chunk);
        return bytes <= RuleEditLimits.MAX_CHUNK_BYTES ? new Chunk(payload, bytes) : null;
    }

    /**
     * 一次传输的接收状态：按下标归位，重复下标保留首片，收齐后可取出拼接文本。
     */
    public static final class Batch {

        private final String sessionId;
        private final String correlationId;
        private final String transferId;
        private final int count;
        private final String[] chunks;
        private int received;
        private int bytes;
        private long lastTick;

        private Batch(Chunk first) {
            RuleChunkPayload payload = first.payload();
            this.sessionId = payload.sessionId();
            this.correlationId = payload.correlationId();
            this.transferId = payload.transferId();
            this.count = payload.count();
            this.chunks = new String[this.count];
        }

        /**
         * 用首片开启批次。
         * @param first 已校验的首片
         * @return 新批次
         */
        public static Batch start(Chunk first) {
            return new Batch(first);
        }

        /**
         * 判断分片是否属于同一逻辑传输：会话、请求标识、批次与总数全等。
         * @param next 已校验分片
         * @return 是否同一批次
         */
        public boolean matches(Chunk next) {
            RuleChunkPayload payload = next.payload();
            return count == payload.count()
                    && transferId.equals(payload.transferId())
                    && sessionId.equals(payload.sessionId())
                    && correlationId.equals(payload.correlationId());
        }

        /**
         * 刷新最近一次收到分片的活动刻。
         * @param tick 活动刻
         */
        public void touch(long tick) {
            this.lastTick = tick;
        }

        /**
         * 写入一片；已存在的下标按首片为准。
         * @param next 已校验分片
         */
        public void offer(Chunk next) {
            int index = next.payload().index();
            if (chunks[index] == null) {
                chunks[index] = next.payload().chunk();
                received++;
                bytes += next.bytes();
            }
        }

        /**
         * 是否已收齐全部下标。
         * @return 收齐返回 true
         */
        public boolean complete() {
            return received == count;
        }

        /**
         * 已收到的分片字节数之和。
         * @return UTF-8 字节数
         */
        public int bytes() {
            return bytes;
        }

        /**
         * 最近一次收到分片的活动刻。
         * @return 活动刻
         */
        public long lastTick() {
            return lastTick;
        }

        /**
         * 收起后按下标拼接完整文本；未收齐的分片位按空串计入。
         * @return 拼接文本
         */
        public String json() {
            StringBuilder builder = new StringBuilder();
            for (String part : chunks) {
                builder.append(part == null ? "" : part);
            }
            return builder.toString();
        }
    }
}