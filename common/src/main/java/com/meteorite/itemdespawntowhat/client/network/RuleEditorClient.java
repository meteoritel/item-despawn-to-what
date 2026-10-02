package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetPayload;
import com.meteorite.itemdespawntowhat.platform.Services;
import com.mojang.serialization.DataResult;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 客户端配置编辑门面（阶段④ 冻结契约，界面层只依赖本类的静态方法）。
 * 职责：
 * - 出站：经 {@code Services.PLATFORM.sendToServer} 发送请求快照与变更集，超长变更集在本类内自动分片；
 * - 入站：平台接收器只调用 {@code RuleEditPayloadRouter}，本类在类初始化时注册为消费者，并把回调切到客户端线程；
 * - 状态：缓存最近一次成功接收的快照，供界面随时读取。
 * 与旧链路 client.network 包完全独立，不读写旧链路的任何状态。
 */
public final class RuleEditorClient {

    // 最近一次成功接收的快照；未收到过时为 null
    private static volatile RuleSnapshot lastSnapshot;
    // 快照到达回调
    private static volatile Consumer<RuleSnapshot> snapshotListener;
    // 保存结果 / 冲突文本回调
    private static volatile Consumer<String> resultListener;

    static {
        // 入站分发注册：平台接收器不直接依赖本类，避免服务端加载 client 包
        RuleEditPayloadRouter.installSnapshotSink(RuleEditorClient::onSnapshotText);
        RuleEditPayloadRouter.installResultSink(RuleEditorClient::onResultText);
    }

    private RuleEditorClient() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 向服务端请求覆盖层快照；服务端会顺带打开（或续期）本玩家的编辑会话
    public static void requestSnapshot() {
        try {
            Services.PLATFORM.sendToServer(new RequestRuleSnapshotPayload());
        } catch (RuntimeException e) {
            deliverResult("请求规则快照失败: " + e.getMessage());
        }
    }

    // 提交变更集；超过直发上限时自动拆成多片发送
    public static void sendChangeSet(RuleEditChangeSet changeSet) {
        if (changeSet == null) {
            return;
        }
        String json;
        try {
            // 计划书 3.8：编码期异常（例如未注册类型）必须转成用户可读错误，不得让界面线程崩溃
            json = changeSet.serialize();
        } catch (RuntimeException e) {
            deliverResult("变更集编码失败: " + e.getMessage());
            return;
        }
        int bytes = RuleEditLimits.encodedLength(json);
        if (bytes > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
            deliverResult("变更集过大（" + bytes + " 字节，上限 " + RuleEditLimits.MAX_CHANGE_SET_BYTES + "），已取消提交");
            return;
        }
        try {
            if (bytes <= RuleEditLimits.MAX_DIRECT_PACKET_BYTES) {
                Services.PLATFORM.sendToServer(new SaveRuleChangeSetPayload(json));
                return;
            }
            sendChunked(json);
        } catch (RuntimeException e) {
            deliverResult("变更集发送失败: " + e.getMessage());
        }
    }

    // 最近一次成功接收的快照；尚未收到时为 null
    public static @Nullable RuleSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    // 注册快照到达回调；传 null 表示清除。回调已切换到客户端主线程
    public static void setSnapshotListener(Consumer<RuleSnapshot> listener) {
        snapshotListener = listener;
    }

    // 注册保存结果 / 冲突文本回调；传 null 表示清除。回调已切换到客户端主线程
    public static void setResultListener(Consumer<String> listener) {
        resultListener = listener;
    }

    // 分片发送：按 UTF-8 字节边界切分，保证不劈开码点
    private static void sendChunked(String json) {
        List<String> chunks = splitByUtf8Bytes(json, RuleEditLimits.MAX_CHUNK_BYTES);
        if (chunks.isEmpty() || chunks.size() > RuleEditLimits.MAX_CHUNK_COUNT) {
            deliverResult("变更集分片数超出上限，已取消提交");
            return;
        }
        String transferId = UUID.randomUUID().toString();
        for (int index = 0; index < chunks.size(); index++) {
            Services.PLATFORM.sendToServer(
                    new SaveRuleChangeSetChunkPayload(transferId, index, chunks.size(), chunks.get(index)));
        }
    }

    // 按 UTF-8 字节上限切分文本：逐码点累计，超限即断片，绝不切断代理对
    private static List<String> splitByUtf8Bytes(String text, int maxBytes) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        int bytes = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int size = utf8Length(codePoint);
            if (bytes + size > maxBytes && index > start) {
                chunks.add(text.substring(start, index));
                start = index;
                bytes = 0;
            }
            bytes += size;
            index += Character.charCount(codePoint);
        }
        if (start < text.length()) {
            chunks.add(text.substring(start));
        }
        return chunks;
    }

    // 单个码点的 UTF-8 编码长度
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

    // 收到快照文本：解析成功后缓存并回调，失败则转成结果文本提示
    private static void onSnapshotText(String text) {
        DataResult<RuleSnapshot> parsed = RuleSnapshot.parse(text);
        RuleSnapshot snapshot = parsed.result().orElse(null);
        if (snapshot == null) {
            deliverResult(parsed.error().map(error -> error.message()).orElse("规则快照解析失败"));
            return;
        }
        lastSnapshot = snapshot;
        runOnClientThread(() -> {
            Consumer<RuleSnapshot> listener = snapshotListener;
            if (listener != null) {
                listener.accept(snapshot);
            }
        });
    }

    // 收到结果文本：切到客户端线程后回调
    private static void onResultText(String text) {
        deliverResult(text);
    }

    // 把回调切到客户端主线程；客户端尚未就绪时直接回调，避免丢消息
    private static void runOnClientThread(Runnable action) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            action.run();
            return;
        }
        client.execute(action);
    }

    // 结果文本统一入口：无监听者时静默丢弃
    private static void deliverResult(String text) {
        if (resultListener == null) {
            return;
        }
        runOnClientThread(() -> {
            Consumer<String> listener = resultListener;
            if (listener != null) {
                listener.accept(text);
            }
        });
    }
}
