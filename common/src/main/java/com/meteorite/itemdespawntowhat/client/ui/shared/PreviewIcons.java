package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiPreviewCache;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewMotion;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewScenes;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewState;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewSubject;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.UiSceneStyle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 图标宿主入口：解释注册名和展示参数，场景缓存、状态与绘制统一交给 kit.preview。 */
@SuppressWarnings("resource") // scenes() 返回由 PreviewResources 生命周期管理的共享所有者。
public final class PreviewIcons {
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "icon-preview"); thread.setDaemon(true); return thread;
    });
    private static final UiPreviewCache<Key, UiIcon> ICONS = new UiPreviewCache<>(256);
    private static final PreviewMotion STATIC = new PreviewMotion(45, 0);
    private static final PreviewMotion TURNTABLE = new PreviewMotion(0, 10_000);
    private static final PreviewMotion STRUCTURE = new PreviewMotion(0, 20_000);
    private static @Nullable PreviewScenes.Geometry geometry;
    private static @Nullable PreviewScenes scenes;
    private static Object generation;
    private static boolean rotating = true;
    private static final long RETAIN_MILLIS = 15_000;
    private static final int RETAIN_SCENES = 64;
    private static final int RETAIN_INSTANCES = 64;
    private static long retainedUntil;
    private record Key(String id, PreviewSubject subject, int size, PreviewMotion motion) { }
    private PreviewIcons() { }

    /** 平台客户端初始化注入真实模型与流体的层分发。 */
    public static void install(PreviewScenes.Geometry renderer) { geometry = renderer; }

    private static PreviewScenes scenes() {
        expireRetained();
        Object current = PreviewResources.generation();
        if (scenes == null || generation != current) {
            clear();
            if (geometry == null) throw new IllegalStateException("预览平台适配尚未安装");
            scenes = new PreviewScenes(WORKERS, Util::getMillis, geometry,
                    (id, failure) -> Constants.LOG.warn("Preview {}", id, failure), true);
            generation = current;
        }
        return scenes;
    }

    /** 每个宿主屏幕在绘制第一枚图标前调用一次。 */
    public static void beginFrame() {
        PreviewScenes owner = scenes();
        retainedUntil = 0;
        owner.beginFrame();
    }

    /** 可见图标绘制完毕后，用剩余的共享预算推进预取。 */
    public static void endFrame() { if (scenes != null) scenes.endFrame(); }

    /** 关屏保留近期就绪内容，重复关闭通知沿用同一到期时间。 */
    public static void retainRecent() {
        if (scenes == null || retainedUntil != 0) return;
        scenes.retainRecent(RETAIN_SCENES, RETAIN_INSTANCES);
        ICONS.clear();
        StructurePreviewResources.clear();
        retainedUntil = Util.getMillis() + RETAIN_MILLIS;
    }

    private static void expireRetained() {
        if (retainedUntil != 0 && Util.getMillis() >= retainedUntil) clear();
    }

    /** 已创建场景在世界或模型资源世代改变时立即释放。 */
    public static void observeGeneration(Object current) {
        expireRetained();
        if (scenes != null && generation != current) clear();
    }

    /** 显式释放当前世代；已有图标在下次绘制时取得新场景所有者。 */
    public static void clear() {
        if (scenes != null) { scenes.close(); scenes = null; }
        ICONS.clear();
        StructurePreviewResources.clear();
        retainedUntil = 0;
    }

    /** 任意完整主体的图标入口；同内容与相位复用场景。 */
    public static UiIcon icon(String id, PreviewSubject subject, int size, PreviewMotion motion) {
        Key key = new Key(id, subject, size, motion);
        return ICONS.getOrLoad(key, ignored -> {
            UiIcon fallback = CatalogPreviewIcons.unavailableMarker(size, size);
            UiIcon preparing = CatalogPreviewIcons.preparingMarker(size, size);
            return new UiIcon.Rendered(size, size, (graphics, box) -> {
                PreviewMotion effective = rotating ? motion : new PreviewMotion(motion.startYawDegrees(), 0);
                scenes().icon(id, subject, size, effective, UiSceneStyle.icon(), preparing, fallback)
                        .render(graphics, box.x(), box.y());
            }, () -> scenes().prefetch(id, subject));
        });
    }

    /** 保留物品数量与组件，内部仍使用原版 GUI 物品绘制。 */
    public static UiIcon item(ItemStack stack) {
        return icon(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), new PreviewSubject.Item(stack), 16, STATIC);
    }

    /** 放置态方块与可选方块实体载荷，与实体共用转台周期和相位。 */
    public static UiIcon block(BlockState state, @Nullable CompoundTag data) {
        return icon(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), new PreviewSubject.Block(state, data), 18, TURNTABLE);
    }

    /** 目录默认方块态。 */
    public static UiIcon block(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        var block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        return block == null ? CatalogPreviewIcons.unavailableMarker(18, 18) : block(block.defaultBlockState(), null);
    }

    /** 默认流体态经过真实液体 tessellator。 */
    public static @Nullable UiIcon fluid(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        var fluid = key == null ? null : BuiltInRegistries.FLUID.getOptional(key).orElse(null);
        return fluid == null || fluid.defaultFluidState().isEmpty() ? null
                : icon(id, new PreviewSubject.Fluid(fluid.defaultFluidState()), 18, STATIC);
    }

    private static PreviewSubject.Entity entitySubject(String id, int age) {
        CompoundTag data = new CompoundTag();
        data.putString("id", id); data.putInt("Age", age);
        return new PreviewSubject.Entity(data);
    }

    /** 规则已有 age 展示参数进入实体内容身份。 */
    public static UiIcon entity(String id, int age) { return icon(id, entitySubject(id, age), 18, TURNTABLE); }

    /** 实体完整载荷入口，可展示有效物品、展示实体和其他可见数据。 */
    public static UiIcon entity(CompoundTag data) { return icon(data.getString("id"), new PreviewSubject.Entity(data), 18, TURNTABLE); }

    /** 统一查询实体终态，准备中不提示失败。 */
    public static boolean entityUnavailable(String id, int age) {
        return scenes().request(id, entitySubject(id, age)).state().unavailable();
    }

    /** 统一查询默认方块终态。 */
    public static boolean blockUnavailable(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        var block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        return block == null || scenes().request(id, new PreviewSubject.Block(block.defaultBlockState(), null)).state().unavailable();
    }

    /** 结构资源异步读取后通过同一场景入口生成转台图标。 */
    public static UiIcon structure(ResourceLocation id, int size) {
        UiIcon preparing = CatalogPreviewIcons.preparingMarker(size, size);
        UiIcon fallback = CatalogPreviewIcons.unavailableMarker(size, size);
        return new UiIcon.Rendered(size, size, (graphics, box) -> {
            scenes();
            var resource = StructurePreviewResources.get(id, WORKERS);
            if (resource.subject() != null) icon(id.toString(), resource.subject(), size, STRUCTURE).render(graphics, box.x(), box.y());
            else if (resource.state() == PreviewState.PREPARING) preparing.render(graphics, box.x(), box.y());
            else if (resource.state().unavailable()) fallback.render(graphics, box.x(), box.y());
        }, () -> {
            PreviewScenes owner = scenes();
            var resource = StructurePreviewResources.get(id, WORKERS);
            if (resource.subject() != null) owner.prefetch(id.toString(), resource.subject());
        });
    }

    /** 开发示例与提示查询结构资源／场景的统一状态。 */
    public static PreviewState structureState(ResourceLocation id) {
        scenes();
        var resource = StructurePreviewResources.get(id, WORKERS);
        return resource.subject() == null ? resource.state() : scenes().request(id.toString(), resource.subject()).state();
    }

    /** 开发示例展示失败原因。 */
    public static @Nullable String structureReason(ResourceLocation id) {
        scenes();
        var resource = StructurePreviewResources.get(id, WORKERS);
        return resource.subject() == null ? resource.reason() : scenes().request(id.toString(), resource.subject()).reason();
    }

    /** 宿主本地化语义状态。 */
    public static Component status(PreviewState state) {
        return Component.translatable("gui.itemdespawntowhat.edit.preview.state." + state.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** 开发示例读取预算账本。 */
    public static long[] memoryBytes() { return scenes().memoryBytes(); }

    /** 切换宿主的统一转台开关，已有 wrapper 当帧生效。 */
    public static void setRotating(boolean value) { rotating = value; }
}
