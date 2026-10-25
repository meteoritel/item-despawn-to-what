package com.meteorite.itemdespawntowhat.core.state;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本服启用状态：按存档保存的规则启用覆盖，按规则 id 记录布尔值，缺席表示继承规则内容里的默认启用。
 * 状态不写规则文件、不占覆盖层条目，随存档保存、复制与备份移动；只记录覆盖值本身，不保存默认值副本，
 * 因此「恢复默认」只需删除记录。已找不到对应规则的记录保留下来并在加载时忽略，由命令查看与清理。
 */
public final class RuleEnableState extends SavedData implements RuleEnableResolver {

    // 数据存储标识（挂在主世界的数据存储上，随存档保存）
    private static final String DATA_ID = Constants.MOD_ID + "_rule_enable_state";
    // NBT 键：覆盖列表与单条记录的字段
    private static final String KEY_OVERRIDES = "overrides";
    private static final String KEY_ID = "id";
    private static final String KEY_ENABLED = "enabled";

    // 规则 id → 本服覆盖值；LinkedHashMap 保持写入顺序，便于命令与调试输出稳定
    private final Map<ResourceLocation, Boolean> overrides = new LinkedHashMap<>();

    /**
     * 取当前存档的本服启用状态表。
     * 状态挂在主世界的数据存储上；此处只借用对应的数据存储实例，不关闭传进来的世界。
     *
     * @param level 任意已加载维度
     * @return 当前存档的状态实例
     */
    @SuppressWarnings("resource")
    public static RuleEnableState get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RuleEnableState::new, RuleEnableState::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_ID);
    }

    @Override
    public @Nullable Boolean overrideFor(ResourceLocation id) {
        return overrides.get(id);
    }

    /**
     * 计算生效启用值：本服覆盖优先，缺席时继承默认启用。
     *
     * @param id 规则标识
     * @param defaultEnabled 规则内容声明的默认启用值
     * @return 生效启用值
     */
    public boolean effective(ResourceLocation id, boolean defaultEnabled) {
        Boolean override = overrides.get(id);
        return override == null ? defaultEnabled : override;
    }

    /**
     * 写入本服覆盖；与现有记录相同时不标脏。
     *
     * @param id 规则标识
     * @param enabled 本服启用值
     * @return 是否实际改变了状态
     */
    public boolean put(ResourceLocation id, boolean enabled) {
        Boolean previous = overrides.put(id, enabled);
        if (previous != null && previous == enabled) {
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * 删除本服覆盖，使该规则恢复默认启用。
     *
     * @param id 规则标识
     * @return 是否删除了记录
     */
    public boolean clear(ResourceLocation id) {
        if (overrides.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * 列出全部持有本服覆盖的规则 id。
     *
     * @return 规则 id 的只读副本
     */
    public Set<ResourceLocation> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(overrides.keySet()));
    }

    /**
     * 全部本服覆盖的只读快照，供快照装配与命令输出使用。
     *
     * @return 规则 id 到覆盖值的只读映射
     */
    public Map<ResourceLocation, Boolean> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
    }

    /**
     * 找出已没有对应规则的覆盖记录：这些记录保留在存档里，加载时被忽略。
     *
     * @param existingIds 当前现存规则 id 集合
     * @return 无主记录（按 id 排序）
     */
    public List<ResourceLocation> orphans(Collection<ResourceLocation> existingIds) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation id : overrides.keySet()) {
            if (!existingIds.contains(id)) {
                result.add(id);
            }
        }
        result.sort(Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(result);
    }

    /**
     * 清理无主的覆盖记录。
     *
     * @param existingIds 当前现存规则 id 集合
     * @return 被清理的记录数
     */
    public int clearOrphans(Collection<ResourceLocation> existingIds) {
        int removed = 0;
        var iterator = overrides.keySet().iterator();
        while (iterator.hasNext()) {
            if (!existingIds.contains(iterator.next())) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Boolean> entry : overrides.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString(KEY_ID, entry.getKey().toString());
            item.putBoolean(KEY_ENABLED, entry.getValue());
            list.add(item);
        }
        tag.put(KEY_OVERRIDES, list);
        return tag;
    }

    // 读取存档：逐条恢复覆盖，非法 id 与缺字段的记录跳过，不阻断其余记录
    static RuleEnableState load(CompoundTag tag, HolderLookup.Provider registries) {
        RuleEnableState state = new RuleEnableState();
        ListTag list = tag.getList(KEY_OVERRIDES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag item = list.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(item.getString(KEY_ID));
            if (id != null && item.contains(KEY_ENABLED)) {
                state.overrides.put(id, item.getBoolean(KEY_ENABLED));
            }
        }
        return state;
    }
}
