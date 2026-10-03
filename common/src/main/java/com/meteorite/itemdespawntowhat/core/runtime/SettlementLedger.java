package com.meteorite.itemdespawntowhat.core.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器级结算账本：保存进行中与已完成的转化结算记录。
 * 存在主世界（服务端全局）的数据存储里：维度卸载或停服都不会丢掉记录本身，
 * 返回待返还数据的完整恢复由阶段 6 落地；写入仍只发生在服务端线程。
 */
public final class SettlementLedger extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger();
    // 数据存储 id（主世界）
    private static final String DATA_ID = "itemdespawntowhat_settlements";
    // 已完成记录的保留上限：先按 tick 过期，再按条数兜底
    private static final int MAX_RECORDS = 512;
    private static final long RETAIN_COMPLETED_TICKS = 6000L;

    private final Map<String, SettlementRecord> records = new LinkedHashMap<>();

    // 取主世界账本（同一 id 全局唯一，维度卸载不影响）
    // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
    @SuppressWarnings("resource")
    public static SettlementLedger get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementLedger::new, SettlementLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_ID);
    }

    // 记录入账（新建或更新同一 id）
    public void put(SettlementRecord record) {
        if (record == null || record.id().isEmpty()) {
            return;
        }
        records.put(record.id(), record);
        setDirty();
    }

    // 按 id 查询，未找到返回 null
    public SettlementRecord find(String id) {
        return records.get(id);
    }

    public int size() {
        return records.size();
    }

    // 统计进行中的记录数（未完成且有待返还数据），供调试观测
    public int unsettledCount() {
        int count = 0;
        for (SettlementRecord record : records.values()) {
            if (record.status() != SettlementRecord.Status.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    // 待恢复记录快照（按加入顺序）：阶段6 恢复只交付这些记录的 pendingDelivery，不重放旧效果队列
    public List<SettlementRecord> unsettled() {
        List<SettlementRecord> list = new ArrayList<>();
        for (SettlementRecord record : records.values()) {
            if (record.needsRecovery()) {
                list.add(record);
            }
        }
        return list;
    }

    // 指定维度的待恢复记录快照
    public List<SettlementRecord> unsettled(String dimension) {
        List<SettlementRecord> list = new ArrayList<>();
        for (SettlementRecord record : records.values()) {
            if (record.needsRecovery() && record.dimension().equals(dimension)) {
                list.add(record);
            }
        }
        return list;
    }

    // 待交付返还总量（调试观测）：与「已派发未回执」的 pendingUnits 无关
    public int pendingDeliveryTotal() {
        int total = 0;
        for (SettlementRecord record : records.values()) {
            total += record.pendingDelivery();
        }
        return total;
    }

    // 已派发未回执的计划量（调试观测）：不是待返还库存，恢复交付绝不使用
    public int pendingUnitsTotal() {
        int total = 0;
        for (SettlementRecord record : records.values()) {
            total += record.pendingUnits();
        }
        return total;
    }

    // 裁剪：已完成且返还交付完毕的旧记录先按 tick 过期，再按条数上限兜底
    public void prune(long gameTime) {
        boolean changed = records.values().removeIf(record ->
                record.status() == SettlementRecord.Status.COMPLETED
                        && record.pendingDelivery() <= 0
                        && record.finishedTick() >= 0L
                        && gameTime - record.finishedTick() > RETAIN_COMPLETED_TICKS);
        while (records.size() > MAX_RECORDS) {
            String victim = null;
            for (Map.Entry<String, SettlementRecord> entry : records.entrySet()) {
                if (entry.getValue().status() == SettlementRecord.Status.COMPLETED) {
                    victim = entry.getKey();
                    break;
                }
            }
            if (victim == null) {
                // 上限是硬保护：未结清/中断记录是阶段 6 的恢复依据，一条都不能裁，只告警并结束本次裁剪
                LOGGER.warn("结算账本达到上限 {} 且没有可裁剪的已完成记录，未结清记录全部保留", MAX_RECORDS);
                break;
            }
            records.remove(victim);
            changed = true;
        }
        if (changed) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("结算账本已裁剪：剩余 {} 条，未结清 {} 条", records.size(), unsettledCount());
            }
            setDirty();
        }
    }

    @Override
    public @NotNull CompoundTag save(
            @NotNull CompoundTag tag,
            @NotNull HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (SettlementRecord record : records.values()) {
            list.add(record.save(registries));
        }
        tag.put("records", list);
        return tag;
    }

    // 读档：单条记录损坏只跳过该条，不阻塞其它记录恢复
    static SettlementLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementLedger ledger = new SettlementLedger();
        ListTag list = tag.getList("records", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            try {
                SettlementRecord record = SettlementRecord.load(entry, registries);
                if (!record.id().isEmpty()) {
                    ledger.records.put(record.id(), record);
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("结算记录恢复失败，已跳过：index={}", index, failure);
            }
        }
        return ledger;
    }
}
