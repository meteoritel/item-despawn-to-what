package com.meteorite.itemdespawntowhat.core.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 候选轮询游标：按「规则 id + 维度」共享并持久化，保证跨实体接续与重启后继续。
 * 候选结构版本（候选 id 与效果构成的哈希）变化时自动重置游标，避免旧位置指向新结构（阶段 4 验收③）。
 */
public final class RoundRobinCursors extends SavedData {

    // 数据存储 id（主世界）
    private static final String DATA_ID = "itemdespawntowhat_round_robin";

    private final Map<String, Cursor> cursors = new LinkedHashMap<>();

    // 游标：候选结构版本 + 下一个候选下标
    private record Cursor(int structureVersion, int nextIndex) {
    }

    // 取主世界游标表
    // 主世界由服务端管理，此处只借用数据存储，不能关闭世界实例。
    @SuppressWarnings("resource")
    public static RoundRobinCursors get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RoundRobinCursors::new, RoundRobinCursors::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_ID);
    }

    // 当前轮询位置；结构版本变化或首次访问时归零
    public int cursor(String key, int structureVersion, int size) {
        if (size <= 0) {
            return 0;
        }
        Cursor stored = cursors.get(key);
        if (stored == null || stored.structureVersion() != structureVersion) {
            return 0;
        }
        return Math.floorMod(stored.nextIndex(), size);
    }

    // 记录下一个候选下标；结构版本变化时以新版本重写
    public void moveTo(String key, int structureVersion, int size, int nextIndex) {
        if (size <= 0) {
            return;
        }
        cursors.put(key, new Cursor(structureVersion, Math.floorMod(nextIndex, size)));
        setDirty();
    }

    // 游标条目数（调试观测）
    public int size() {
        return cursors.size();
    }

    @Override
    public @NotNull CompoundTag save(
            @NotNull CompoundTag tag,
            @NotNull HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Cursor> entry : cursors.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("key", entry.getKey());
            item.putInt("version", entry.getValue().structureVersion());
            item.putInt("next", entry.getValue().nextIndex());
            list.add(item);
        }
        tag.put("cursors", list);
        return tag;
    }

    // 读档：字段缺失按 0 处理，游标损坏不影响其它规则
    static RoundRobinCursors load(CompoundTag tag, HolderLookup.Provider registries) {
        RoundRobinCursors table = new RoundRobinCursors();
        ListTag list = tag.getList("cursors", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag item = list.getCompound(index);
            String key = item.getString("key");
            if (!key.isEmpty()) {
                table.cursors.put(key, new Cursor(item.getInt("version"), item.getInt("next")));
            }
        }
        return table;
    }
}
