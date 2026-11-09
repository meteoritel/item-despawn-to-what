package com.meteorite.itemdespawntowhat.client.ui.kit.api.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private final PreviewBudget budget;
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
    private int framePreparations;
    private long framePreparationNanos;
    private final boolean cull;

    /** 平台几何入口，所有参数均为原版类型；实现负责选择真实模型渲染层。 */
    public interface Geometry {
        /** 渲染线程装配冻结实体状态；不推进游戏 tick，不与同一实体的绘制并发。 */
        void prepareEntity(@NotNull Entity entity);
        /**
         * 在宿主 executor 工作线程提交普通方块；不同场景可以并发调用同一 Adapter。
         * blocks 是本任务的只读快照，pose、buffers、random 由任务独占；不访问活动世界或修改共享渲染状态。
         * 重载或 close 后正在运行的任务仍可完成，所有者负责释放晚到结果；不得在此上传或释放 GPU 缓冲。
         */
        void block(@NotNull BlockState state, @NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks, @NotNull PoseStack pose,
                   @NotNull Function<RenderType, VertexConsumer> buffers, @NotNull RandomSource random);
        /**
         * 与 block 相同的 executor、并发和只读契约；buffers 为本任务独占的 CPU 顶点输出。
         * 输出段内坐标，由调用方补区段原点；renderer 的实例准备及 GPU 上传仍归渲染线程。
         */
        void fluid(@NotNull BlockPos pos, @NotNull BlockAndTintGetter blocks,
                   @NotNull Function<RenderType, VertexConsumer> buffers,
                   @NotNull BlockState state, @NotNull FluidState fluid);
    }

    private record Completion(PreviewScene scene, @Nullable SceneMeshes.Prepared mesh,
                              @Nullable RuntimeException failure) { }

    /** 注入宿主线程池、时钟、平台适配与诊断；关闭场景不会关闭宿主线程池。 */
    public PreviewScenes(ExecutorService executor, LongSupplier clock, Geometry geometry,
                         BiConsumer<String, Throwable> diagnostic, boolean externalCulling) {
        this(executor, clock, geometry, diagnostic, externalCulling, PreviewBudget.DEFAULT);
    }

    /** 注入完整资源预算；executor 仍由宿主持有，动画时钟以毫秒为单位。 */
    public PreviewScenes(ExecutorService executor, LongSupplier clock, Geometry geometry,
                         BiConsumer<String, Throwable> diagnostic, boolean externalCulling, PreviewBudget budget) {
        this.budget = budget;
        this.executor = executor; this.clock = clock; this.geometry = geometry;
        this.diagnostic = diagnostic; this.cull = externalCulling;
    }

    /** 内容相同的已接收请求共享句柄；容量拒绝保留终态，新请求在容量恢复后可被接收。
     * 同一帧使用的驻留场景不被 LRU 淘汰。 */
    public PreviewScene request(String id, PreviewSubject subject) {
        PreviewScene scene = getOrCreate(id, subject);
        scene.usedFrame = frame;
        return scene;
    }

    /** 登记邻近内容，优先级低于实际绘制；过期预取不会继续消耗准备预算。 */
    public void prefetch(String id, PreviewSubject subject) {
        getOrCreate(id, subject).prefetchedFrame = frame;
    }

    private PreviewScene getOrCreate(String id, PreviewSubject subject) {
        if (closed) throw new IllegalStateException("场景所有者已关闭");
        PreviewScene scene = scenes.get(subject);
        if (scene == null) {
            if (scenes.size() >= budget.requests() && !evictOne()) {
                PreviewScene rejected = new PreviewScene(id, subject);
                rejected.state = PreviewState.CAPACITY_REJECTED;
                rejected.reason = id + ": request capacity " + budget.requests() + " is occupied";
                rejected.released = true;
                return rejected;
            }
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
        return scene;
    }

    /** 把场景接入二维 kit；未指定准备占位时保留空槽。 */
    public UiIcon icon(String id, PreviewSubject subject, int size, PreviewMotion motion,
                       UiSceneStyle style, UiIcon fallback) {
        return icon(id, subject, size, motion, style, null, fallback);
    }

    /** 实际绘制优先使用本帧准备预算；PREPARING 与不可用状态分别由宿主绘制。 */
    public UiIcon icon(String id, PreviewSubject subject, int size, PreviewMotion motion,
                       UiSceneStyle style, @Nullable UiIcon preparing, UiIcon fallback) {
        return new UiIcon.Themed(size, size, (graphics, box, theme) -> {
            PreviewScene scene = request(id, subject);
            if (canPrepare(scene)) { graphics.flush(); prepare(scene); }
            if (scene.state == PreviewState.READY) {
                if (subject instanceof PreviewSubject.Item item) {
                    new UiIcon.Item(item.stack()).renderScaled(graphics, box, theme);
                } else {
                    try {
                        UiSceneView.render(graphics, box, scene, new UiSceneCamera(motion.yaw(clock.getAsLong()),
                                (float) Math.toRadians(style.pitchDegrees())), style);
                    } catch (RuntimeException exception) { fail(scene, PreviewState.FAILED, exception); }
                }
            }
            if (scene.state == PreviewState.PREPARING && preparing != null) preparing.renderScaled(graphics, box, theme);
            if (scene.state.unavailable()) fallback.renderScaled(graphics, box, theme);
        }, () -> prefetch(id, subject));
    }

    /**
     * 在渲染线程执行一个真实 GUI 帧；底页、覆盖层和重复图标共用该帧预算。
     * 每个所有者每帧调用一次且不嵌套；内容绘制抛出异常仍执行帧末交接，异常继续传给宿主。
     * 场景 request/prefetch 可以在布局阶段登记，实际绘制放在本作用域中。
     */
    public void renderFrame(Runnable painter) {
        beginFrame();
        try { painter.run(); }
        finally { endFrame(); }
    }

    /** 开始一帧并交接任务；绘制与帧末预取共用实例准备、上传次数和软时间预算。 */
    private void beginFrame() {
        if (closed) return;
        frame++;
        receive();
        framePreparations = 0;
        framePreparationNanos = 0;
    }

    /** 可见内容绘制后用剩余预算预热邻近项；过期请求退出队列，已运行任务完成后释放结果。 */
    private void endFrame() {
        if (closed) return;
        for (PreviewScene scene : new ArrayList<>(scenes.values()).reversed()) {
            if (scene.state != PreviewState.PREPARING) continue;
            if (scene.usedFrame == frame || scene.prefetchedFrame == frame) prepare(scene);
            else { scenes.remove(scene.subject); release(scene); }
        }
    }

    private boolean hasPreparationBudget() {
        return framePreparations < budget.uploadsPerFrame() && framePreparationNanos < budget.uploadNanos();
    }

    private boolean canPrepare(PreviewScene scene) {
        return !scene.released && scene.state == PreviewState.PREPARING && hasPreparationBudget()
                && !(scene.instancesReady && scene.started && scene.prepared == null);
    }

    private void prepare(PreviewScene scene) {
        if (!canPrepare(scene)) return;
        long start = System.nanoTime();
        try {
            PreviewStructure structure = structure(scene.subject);
            if (!scene.instancesReady) {
                if (SceneInstances.needsLevel(scene.subject, structure) && Minecraft.getInstance().level == null) {
                    fail(scene, PreviewState.NO_LEVEL, new IllegalStateException("当前没有客户端世界"));
                    return;
                }
                scene.instances.prepare(scene.subject, structure, geometry);
                scene.bounds = scene.instances.measure();
                scene.instancesReady = true;
                framePreparations++;
                if (structure == null) {
                    scene.state = scene.bounds == null ? PreviewState.EMPTY : PreviewState.READY;
                    return;
                }
            }
            if (scene.prepared != null) {
                if (!hasPreparationBudget()) return;
                long bytes = scene.prepared.bytes;
                if (!makeRoom(bytes, scene)) return;
                try (SceneMeshes.Prepared prepared = scene.prepared) {
                    for (var entry : prepared.layers.entrySet())
                        if (entry.getValue().mesh != null) scene.layers.add(new SceneMeshes.Layer(entry.getKey(), entry.getValue()));
                    scene.bounds = SceneInstances.union(scene.bounds, prepared.bounds);
                    scene.bytes = bytes; gpuBytes += bytes;
                    scene.state = scene.bounds == null ? PreviewState.EMPTY : PreviewState.READY;
                } finally {
                    scene.prepared = null;
                    synchronized (completions) { pendingBytes -= scene.pendingBytes; scene.pendingBytes = 0; }
                }
                framePreparations++;
            } else if (!scene.started && structure != null) start(scene, structure);
        } catch (RuntimeException exception) {
            fail(scene, exception instanceof SceneMeshes.TooLarge ? PreviewState.TOO_LARGE : PreviewState.FAILED, exception);
        } finally {
            framePreparationNanos += System.nanoTime() - start;
        }
    }

    private void start(PreviewScene scene, PreviewStructure structure) {
        var level = Minecraft.getInstance().level;
        var biome = level == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME).get(Biomes.PLAINS);
        synchronized (completions) {
            if (pendingBytes > budget.pendingBytes() - budget.singleSceneBytes()) return;
            pendingBytes += budget.singleSceneBytes();
            scene.pendingBytes = budget.singleSceneBytes();
        }
        scene.started = true;
        scene.task = executor.submit(() -> {
            SceneMeshes.Prepared mesh = null;
            RuntimeException failure = null;
            try { mesh = SceneMeshes.compile(new SceneBlocks(structure, biome), geometry, cull, budget.singleSceneBytes()); }
            catch (RuntimeException exception) { failure = exception; }
            synchronized (completions) {
                if (closed || scene.released) {
                    if (mesh != null) mesh.close();
                    if (!closed) pendingBytes -= scene.pendingBytes;
                    scene.pendingBytes = 0;
                } else {
                    pendingBytes -= scene.pendingBytes;
                    scene.pendingBytes = mesh == null ? 0 : mesh.bytes;
                    pendingBytes += scene.pendingBytes;
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
                    if (completed.mesh() != null) completed.mesh().close();
                    pendingBytes -= scene.pendingBytes; scene.pendingBytes = 0;
                } else if (completed.failure() != null) {
                    if (!(completed.failure() instanceof CancellationException))
                        fail(scene, completed.failure() instanceof SceneMeshes.TooLarge ? PreviewState.TOO_LARGE : PreviewState.FAILED, completed.failure());
                } else scene.prepared = completed.mesh();
            }
        }
    }

    private boolean makeRoom(long bytes, PreviewScene protectedScene) {
        while (gpuBytes > budget.gpuBytes() - bytes) if (!evictOne(protectedScene)) return false;
        return true;
    }
    private boolean evictOne() { return evictOne(null); }
    private boolean evictOne(@Nullable PreviewScene protectedScene) {
        Iterator<PreviewScene> iterator = scenes.values().iterator();
        while (iterator.hasNext()) {
            PreviewScene scene = iterator.next();
            if (scene == protectedScene || scene.usedFrame >= frame - 1 || scene.prefetchedFrame == frame
                    || scene.task != null && !scene.task.isDone()) continue;
            iterator.remove(); release(scene); return true;
        }
        return false;
    }
    private void release(PreviewScene scene) {
        gpuBytes -= scene.bytes;
        synchronized (completions) {
            scene.released = true;
            if (scene.prepared != null || scene.task == null) {
                pendingBytes -= scene.pendingBytes;
                scene.pendingBytes = 0;
            }
        }
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

    /** 关屏只保留最近实际展示的 READY 场景；分别限制场景与 renderer 实例数量。 */
    public void retainRecent(int maximumScenes, int maximumInstances) {
        receive();
        int retained = 0;
        int instances = 0;
        for (PreviewScene scene : new ArrayList<>(scenes.values()).reversed()) {
            int count = scene.instances.blocks.size() + scene.instances.entities.size();
            if (scene.state == PreviewState.READY && scene.usedFrame >= frame - 1
                    && retained < maximumScenes && instances + count <= maximumInstances) {
                retained++; instances += count;
            } else { scenes.remove(scene.subject); release(scene); }
        }
    }

    /** 取消旧世代任务并释放 CPU/GPU 持有，晚到结果在交接处自行释放。 */
    @Override public void close() {
        synchronized (completions) {
            closed = true;
            scenes.values().forEach(scene -> {
                if (scene.task != null) scene.task.cancel(true);
                release(scene);
            });
            scenes.clear();
            for (Completion completion : completions)
                if (completion.mesh() != null) completion.mesh().close();
            completions.clear();
            pendingBytes = 0;
        }
    }
}
