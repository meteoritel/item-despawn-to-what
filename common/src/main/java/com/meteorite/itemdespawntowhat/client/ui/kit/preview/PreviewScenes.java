package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/** 有界的场景所有者；宿主每帧推进一次，所有公开方法由渲染线程调用。 */
@SuppressWarnings("resource") // 场景与交接缓冲由本所有者跨帧统一释放。
public final class PreviewScenes implements AutoCloseable {
    static final long SCENE_LIMIT = 8L * 1024 * 1024;
    private static final long TOTAL_LIMIT = 32L * 1024 * 1024;
    private static final int REQUEST_LIMIT = 256;
    private static final int UPLOADS_PER_FRAME = 2;
    private static final long UPLOAD_NANOS = 1_500_000;
    private final ExecutorService executor;
    private final LongSupplier clock;
    private final Geometry geometry;
    private final BiConsumer<String, Throwable> diagnostic;
    private final Map<PreviewSubject, PreviewScene> scenes = new LinkedHashMap<>(32, 0.75F, true);
    private final ArrayDeque<Completion> completions = new ArrayDeque<>();
    private long frame;
    private long gpuBytes;
    private long pendingBytes;
    private boolean closed;
    private final boolean cull;

    /** 平台几何入口，所有参数均为原版类型；实现负责选择真实模型渲染层。 */
    public interface Geometry {
        /** 装配平台需要的冻结实体渲染状态，不推进游戏 tick。 */
        void prepareEntity(@NotNull Entity entity);
        /** 按平台模型层提交普通方块，buffers 按层合并整个场景。 */
        void block(@NotNull BlockState state, @NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks, @NotNull PoseStack pose,
                   @NotNull Function<RenderType, VertexConsumer> buffers, @NotNull RandomSource random);
        /** 提交流体段内顶点，调用方负责补区段原点。 */
        void fluid(@NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks,
                   @NotNull Function<RenderType, VertexConsumer> buffers,
                   @NotNull BlockState state, @NotNull FluidState fluid);
    }

    private record Completion(PreviewScene scene, @Nullable SceneMeshes.Prepared mesh,
                              @Nullable RuntimeException failure) { }

    /** 注入宿主线程池、时钟、平台适配与诊断；关闭场景不会关闭宿主线程池。 */
    public PreviewScenes(ExecutorService executor, LongSupplier clock, Geometry geometry,
                         BiConsumer<String, Throwable> diagnostic, boolean externalCulling) {
        this.executor = executor; this.clock = clock; this.geometry = geometry;
        this.diagnostic = diagnostic; this.cull = externalCulling;
    }

    /** 内容相同的请求共享句柄；同一帧使用的驻留场景不被 LRU 淘汰。 */
    public PreviewScene request(String id, PreviewSubject subject) {
        if (closed) throw new IllegalStateException("场景所有者已关闭");
        PreviewScene scene = scenes.get(subject);
        if (scene == null) {
            if (scenes.size() >= REQUEST_LIMIT && !evictOne()) return new PreviewScene(id, subject);
            scene = new PreviewScene(id, subject);
            scenes.put(subject, scene);
            if (subject instanceof PreviewSubject.Item item)
                scene.state = item.stack().isEmpty() ? PreviewState.EMPTY : PreviewState.READY;
            if (subject instanceof PreviewSubject.Entity actor) {
                ResourceLocation type = ResourceLocation.tryParse(actor.data().getString("id"));
                if (type == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(type))
                    fail(scene, PreviewState.MISSING_SUBJECT, new IllegalArgumentException("实体类型缺失"));
            }
        }
        scene.usedFrame = frame;
        return scene;
    }

    /** 把场景接入二维 kit；不可用标记由宿主提供，PREPARING 保留空槽。 */
    public UiIcon icon(String id, PreviewSubject subject, int size, PreviewMotion motion,
                       UiSceneStyle style, UiIcon fallback) {
        return new UiIcon.Rendered(size, size, (graphics, box) -> {
            PreviewScene scene = request(id, subject);
            if (scene.state == PreviewState.READY) {
                if (subject instanceof PreviewSubject.Item item) {
                    new UiIcon.Item(item.stack()).render(graphics, box.x() + (box.width() - 16) / 2, box.y() + (box.height() - 16) / 2);
                } else {
                    try {
                        UiSceneView.render(graphics, box, scene, new UiSceneCamera(motion.yaw(clock.getAsLong()),
                                (float) Math.toRadians(style.pitchDegrees())), style);
                    } catch (RuntimeException exception) { fail(scene, PreviewState.FAILED, exception); }
                }
            }
            if (scene.state.unavailable()) fallback.render(graphics, box.x(), box.y());
        });
    }

    /** 开始一帧，交接已完成任务，限制实例准备与上传次数和软时间预算。 */
    public void beginFrame() {
        if (closed) return;
        frame++;
        receive();
        long start = System.nanoTime();
        int uploads = 0;
        for (PreviewScene scene : List.copyOf(scenes.values())) {
            if (scene.state != PreviewState.PREPARING || uploads >= UPLOADS_PER_FRAME
                    || System.nanoTime() - start >= UPLOAD_NANOS) continue;
            try {
                PreviewStructure structure = structure(scene.subject);
                if (!scene.instancesReady) {
                    if (SceneInstances.needsLevel(scene.subject, structure) && Minecraft.getInstance().level == null) {
                        fail(scene, PreviewState.NO_LEVEL, new IllegalStateException("当前没有客户端世界"));
                        continue;
                    }
                    scene.instances.prepare(scene.subject, structure, geometry);
                    scene.bounds = scene.instances.measure();
                    scene.instancesReady = true;
                    uploads++;
                    if (structure == null) {
                        scene.state = scene.bounds == null ? PreviewState.EMPTY : PreviewState.READY;
                        continue;
                    }
                }
                if (scene.prepared != null) {
                    long bytes = scene.prepared.bytes;
                    if (!makeRoom(bytes, scene)) continue;
                    try (SceneMeshes.Prepared prepared = scene.prepared) {
                        for (var entry : prepared.layers.entrySet())
                            if (entry.getValue().mesh != null) scene.layers.add(new SceneMeshes.Layer(entry.getKey(), entry.getValue()));
                        scene.bounds = SceneInstances.union(scene.bounds, prepared.bounds);
                        scene.bytes = bytes; gpuBytes += bytes;
                        scene.state = scene.bounds == null ? PreviewState.EMPTY : PreviewState.READY;
                    } finally {
                        scene.prepared = null;
                        synchronized (completions) { pendingBytes -= bytes; }
                    }
                    uploads++;
                } else if (!scene.started && structure != null) start(scene, structure);
            } catch (RuntimeException exception) {
                fail(scene, exception instanceof SceneMeshes.TooLarge ? PreviewState.TOO_LARGE : PreviewState.FAILED, exception);
            }
        }
    }

    private void start(PreviewScene scene, PreviewStructure structure) {
        var level = Minecraft.getInstance().level;
        var biome = level == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME).get(Biomes.PLAINS);
        synchronized (completions) {
            if (pendingBytes + SCENE_LIMIT > TOTAL_LIMIT) return;
            pendingBytes += SCENE_LIMIT;
        }
        scene.started = true;
        scene.task = executor.submit(() -> {
            SceneMeshes.Prepared mesh = null;
            RuntimeException failure = null;
            try { mesh = SceneMeshes.compile(new SceneBlocks(structure, biome), geometry, cull); }
            catch (RuntimeException exception) { failure = exception; }
            synchronized (completions) {
                if (closed || Thread.currentThread().isInterrupted()) {
                    if (mesh != null) mesh.close();
                    if (!closed) pendingBytes -= SCENE_LIMIT;
                } else {
                    pendingBytes -= SCENE_LIMIT - (mesh == null ? 0 : mesh.bytes);
                    completions.add(new Completion(scene, mesh, failure));
                }
            }
        });
    }

    private void receive() {
        synchronized (completions) {
            Completion completed;
            while ((completed = completions.poll()) != null) {
                PreviewScene scene = completed.scene();
                if (scenes.get(scene.subject) != scene) {
                    if (completed.mesh() != null) { pendingBytes -= completed.mesh().bytes; completed.mesh().close(); }
                } else if (completed.failure() != null) {
                    if (!(completed.failure() instanceof CancellationException))
                        fail(scene, completed.failure() instanceof SceneMeshes.TooLarge ? PreviewState.TOO_LARGE : PreviewState.FAILED, completed.failure());
                } else scene.prepared = completed.mesh();
            }
        }
    }

    private boolean makeRoom(long bytes, PreviewScene protectedScene) {
        while (gpuBytes + bytes > TOTAL_LIMIT) if (!evictOne(protectedScene)) return false;
        return true;
    }
    private boolean evictOne() { return evictOne(null); }
    private boolean evictOne(@Nullable PreviewScene protectedScene) {
        Iterator<PreviewScene> iterator = scenes.values().iterator();
        while (iterator.hasNext()) {
            PreviewScene scene = iterator.next();
            if (scene == protectedScene || scene.usedFrame >= frame - 1 || scene.task != null && !scene.task.isDone()) continue;
            iterator.remove(); release(scene); return true;
        }
        return false;
    }
    private void release(PreviewScene scene) {
        gpuBytes -= scene.bytes;
        synchronized (completions) { if (scene.prepared != null) pendingBytes -= scene.prepared.bytes; }
        scene.release();
    }
    private void fail(PreviewScene scene, PreviewState state, RuntimeException exception) {
        release(scene); scene.state = state; scene.reason = scene.id + ": " + exception.getMessage();
        diagnostic.accept(scene.reason, exception);
    }

    private static @Nullable PreviewStructure structure(PreviewSubject subject) {
        return switch (subject) {
            case PreviewSubject.Structure structure -> structure.value();
            case PreviewSubject.Block block -> new PreviewStructure(new BlockPos(1, 1, 1),
                    List.of(block.state()), List.of(new PreviewStructure.Cell(BlockPos.ZERO, 0, block.blockEntityData())), List.of());
            case PreviewSubject.Fluid(var state) -> new PreviewStructure(new BlockPos(1, 1, 1),
                    List.of(state.createLegacyBlock()), List.of(new PreviewStructure.Cell(BlockPos.ZERO, 0, null)), List.of());
            default -> null;
        };
    }

    /** 当前 GPU 与待上传实际字节，待上传值包含尚未完成任务的预留。 */
    public long[] memoryBytes() { synchronized (completions) { return new long[]{gpuBytes, pendingBytes}; } }

    /** 取消旧世代任务并释放 CPU/GPU 持有，晚到结果在交接处自行释放。 */
    @Override public void close() {
        synchronized (completions) {
            closed = true;
            scenes.values().forEach(this::release); scenes.clear();
            for (Completion completion : completions)
                if (completion.mesh() != null) { pendingBytes -= completion.mesh().bytes; completion.mesh().close(); }
            completions.clear();
            pendingBytes = 0;
        }
    }
}
