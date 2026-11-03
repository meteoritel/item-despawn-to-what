package com.meteorite.itemdespawntowhat.client.ui.kit.api.preview;

/**
 * 单个场景所有者的资源预算。字节限制涵盖顶点与索引；每帧次数同时限制实例准备和网格上传，
 * 纳秒为渲染线程准备的软时间上限，已开始的单次操作会执行完毕。
 */
public record PreviewBudget(long singleSceneBytes, long gpuBytes, long pendingBytes,
                            int requests, int uploadsPerFrame, long uploadNanos) {
    /** 保持默认图标场景的容量与逐帧调度。 */
    public static final PreviewBudget DEFAULT = new PreviewBudget(8L * 1024 * 1024,
            32L * 1024 * 1024, 32L * 1024 * 1024, 256, 2, 1_500_000);

    /** 每项为正，且单场景能够完成 CPU 预留和 GPU 驻留。 */
    public PreviewBudget {
        if (singleSceneBytes <= 0 || gpuBytes < singleSceneBytes || pendingBytes < singleSceneBytes
                || requests <= 0 || uploadsPerFrame <= 0 || uploadNanos <= 0)
            throw new IllegalArgumentException("预览预算须为正且能够容纳单场景");
    }
}
