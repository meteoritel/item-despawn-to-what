package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewState;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewStructure;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewSubject;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/** 宿主按资源管理器读取 assets 中的结构；资源包覆盖与场景使用同一世代。 */
public final class StructurePreviewResources {
    private static final int CAPACITY = 256;
    private static final long NBT_LIMIT = 16L * 1024 * 1024;
    private static final Map<ResourceLocation, CompletableFuture<Result>> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static final Result PREPARING = new Result(null, PreviewState.PREPARING, null);
    private StructurePreviewResources() { }

    /** 读取结果保持资源标识与具体原因，失败不会被当成空结构。 */
    public record Result(@Nullable PreviewSubject.Structure subject, PreviewState state, @Nullable String reason) { }

    /** 枚举本模组 structures 子目录，保留相对路径作为逻辑 id。 */
    public static List<ResourceLocation> list() {
        return Minecraft.getInstance().getResourceManager().listResources("structures",
                id -> id.getNamespace().equals(Constants.MOD_ID) && id.getPath().endsWith(".nbt"))
                .keySet().stream().map(id -> ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                        id.getPath().substring("structures/".length(), id.getPath().length() - 4)))
                .sorted().toList();
    }

    static Result get(ResourceLocation id, ExecutorService executor) {
        CompletableFuture<Result> future = CACHE.get(id);
        if (future == null) {
            if (CACHE.size() >= CAPACITY) {
                var entry = CACHE.entrySet().iterator();
                boolean removed = false;
                while (entry.hasNext()) {
                    if (entry.next().getValue().isDone()) { entry.remove(); removed = true; break; }
                }
                if (!removed) return PREPARING;
            }
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            future = CompletableFuture.supplyAsync(() -> read(id, resources), executor);
            CACHE.put(id, future);
        }
        return future.getNow(PREPARING);
    }

    private static Result read(ResourceLocation id, ResourceManager resources) {
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
        var resource = resources.getResource(path);
        if (resource.isEmpty()) return failure(id, PreviewState.MISSING_SUBJECT, new IOException("结构资源缺失: " + path));
        try (var input = resource.get().open()) {
            var root = NbtIo.readCompressed(input, NbtAccounter.create(NBT_LIMIT));
            return new Result(new PreviewSubject.Structure(PreviewStructure.decode(root)), PreviewState.READY, null);
        } catch (IOException | RuntimeException exception) {
            return failure(id, PreviewState.FAILED, exception);
        }
    }

    private static Result failure(ResourceLocation id, PreviewState state, Exception exception) {
        String reason = id + ": " + exception.getMessage();
        Constants.LOG.warn("Structure preview {}", reason, exception);
        return new Result(null, state, reason);
    }

    static void clear() { CACHE.values().forEach(future -> future.cancel(true)); CACHE.clear(); }
}
