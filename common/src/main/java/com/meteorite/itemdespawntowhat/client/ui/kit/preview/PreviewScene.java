package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.Nullable;

/** 场景句柄；状态与包围可查询，资源释放由创建它的 PreviewScenes 统一管理。 */
public final class PreviewScene {
    final String id;
    final PreviewSubject subject;
    final List<SceneMeshes.Layer> layers = new ArrayList<>();
    final SceneInstances instances = new SceneInstances();
    PreviewState state = PreviewState.PREPARING;
    @Nullable String reason;
    @Nullable UiModelBounds bounds;
    @Nullable Future<?> task;
    @Nullable SceneMeshes.Prepared prepared;
    boolean started;
    boolean instancesReady;
    long usedFrame;
    long bytes;

    PreviewScene(String id, PreviewSubject subject) { this.id = id; this.subject = subject; }
    /** 当前准备或绘制状态。 */
    public PreviewState state() { return state; }
    /** 模型空间的真实可视包围，准备中或空场景时为空。 */
    public @Nullable UiModelBounds bounds() { return bounds; }
    /** 终态诊断包含主体 id，供宿主日志或开发界面显示。 */
    public @Nullable String reason() { return reason; }

    void release() {
        if (task != null) task.cancel(true);
        if (prepared != null) { prepared.close(); prepared = null; }
        layers.forEach(SceneMeshes.Layer::close); layers.clear();
        instances.blocks.clear(); instances.entities.clear();
        bounds = null; bytes = 0; state = PreviewState.PREPARING;
    }
}
