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
 * 返回待返还数据的完整恢复由 ConversionRuntime 落地；写入仍只发生在服务端线程。
 * 可变性契约：记录是可变对象，两张索引持有同一个引用，账本不在写入时做快照；
 * 更改变量字段的方法在 {@link SettlementRecord} 内私有，写入统一经 {@link SettlementRecord.Writer}：
 * 变更与「提交标脏」由该句柄统一发起，调用方无法只改字段不落账；结算任务在步内累积、步末一次提交。
 * SavedData 只在仍为脏时落盘，故提交后内存账目与后续落盘值一致；提交只标脏不即时刷盘。
 */
public final class SettlementLedger extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger();
    // 数据存储 id（主世界）
    private static final String DATA_ID = "itemdespawntowhat_settlements";
    // 已完成记录的保留上限：先按 tick 过期，再按条数兜底
    private static final int MAX_RECORDS = 512;
    private static final long RETAIN_COMPLETED_TICKS = 6000L;
    // 条数上限告警的降频刻：上限持续被占满时每个保留窗口只提示一次，避免每 tick 刷日志
    private long lastCapacityWarnTick = Long.MIN_VALUE;
    // 写入观测（仅调试）：字段变更次数、提交次数、提交的记录份数与被步内合并的变更次数。
    // 变更次数是一次提交标脏的「未合并口径」，与提交次数对照即可核对步内累积是否生效；不参与业务判定。
    private long mutations;
    private long commits;
    private long committedRecords;
    private long deferredMutations;

    // 主索引：持有与 scanIndex 相同的可变记录引用，不做写入时拷贝；迭代顺序即加入与持久化顺序。
    // 两张索引只能通过 index / unindex 成对变更，禁止在其它位置单独增删其中一张。
    private final Map<String, SettlementRecord> records = new LinkedHashMap<>();
    // 恢复游标索引（同样持有可变记录引用）；不改变账本的加入顺序或持久化顺序。
    private final java.util.NavigableMap<String, SettlementRecord> scanIndex = new java.util.TreeMap<>();

    /**
     * 取主世界账本：同一 id 全局唯一，维度卸载不影响记录本身。
     * 实例由 Minecraft 管理并负责生命周期，调用方不得关闭。
     *
     * @param level 任意维度，内部统一转到主世界存取
     * @return 全局共享的账本实例
     */
    @SuppressWarnings("resource")
    public static SettlementLedger get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementLedger::new, SettlementLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_ID);
    }

    // 提交一条记录（新建或更新同一 id）：两张索引写入同一引用并标脏；只由 SettlementRecord.Writer 调用，
    // 保证字段变更与索引、标脏同时发生
    void submit(SettlementRecord record) {
        if (record == null || record.id().isEmpty()) {
            return;
        }
        index(record);
        commits++;
        // 一次提交只携带一条记录；独立计数保留「提交了多少份记录」的口径，批量提交多条时两者才会分叉
        committedRecords++;
        setDirty();
    }

    // 域变更计数：step 内累积时由 Writer 以 deferred=true 调用，只改计数，不影响落账与标脏
    void noteMutation(boolean deferred) {
        mutations++;
        if (deferred) {
            deferredMutations++;
        }
    }

    // 两张索引的唯一写入入口：records 决定加入与持久化顺序，scanIndex 提供恢复游标；调用方不得单独写其中一张
    private void index(SettlementRecord record) {
        records.put(record.id(), record);
        scanIndex.put(record.id(), record);
    }

    // 两张索引的唯一摘除入口：返回是否真的摘除，调用方据此决定是否需要标脏
    private boolean unindex(String id) {
        boolean removed = records.remove(id) != null;
        removed |= scanIndex.remove(id) != null;
        return removed;
    }

    // 撤销一条登记（扣减失败补偿专用）：把记录从两张索引成对摘除并标脏，
    // 避免重启后的恢复任务把已经写回源实体的库存再交付一次；未登记该 id 时为空操作。
    // 只由 SettlementRecord.Writer#discard 调用，调用方不直接增删索引
    void unregister(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (unindex(id)) {
            setDirty();
        }
    }

    /**
     * 按 id 查询记录。
     *
     * @param id 记录 id
     * @return 对应记录；不存在时返回 null
     */
    public SettlementRecord find(String id) {
        return records.get(id);
    }

    /**
     * @return 账本当前持有的记录数（含已终结但仍在保留窗口内的记录）
     */
    public int size() {
        return records.size();
    }

    /**
     * 字段变更总次数（仅调试观测）：与提交次数对照可看出步内累积把多少次变更合并成一次落账。
     *
     * @return 未合并的变更次数累计值
     */
    public long mutationCount() {
        return mutations;
    }

    /**
     * 落账提交总次数（仅调试观测）。
     *
     * @return 提交次数累计值，一次提交对应一次标脏
     */
    public long commitCount() {
        return commits;
    }

    /**
     * 提交涉及的记录份数（仅调试观测）；当前一次提交只携带一条记录。
     *
     * @return 已提交记录份数累计值
     */
    public long committedRecordCount() {
        return committedRecords;
    }

    /**
     * 被步内累积合并、未当场落账的变更次数（仅调试观测）。
     *
     * @return 延迟提交的变更次数累计值
     */
    public long deferredMutationCount() {
        return deferredMutations;
    }

    SettlementRecord nextForRecovery(String afterId) {
        var entry = afterId == null ? scanIndex.firstEntry() : scanIndex.higherEntry(afterId);
        return entry == null ? null : entry.getValue();
    }

    /**
     * 统计未结清记录数（未完成且有待返还数据），供调试观测。
     *
     * @return 未结清记录数
     */
    public int unsettledCount() {
        int count = 0;
        for (SettlementRecord record : records.values()) {
            if (record.status() != SettlementRecord.Status.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    /**
     * 待恢复记录快照（按加入顺序）：恢复任务只交付这些记录的 pendingDelivery，不重放既有效果队列。
     *
     * @return 需要恢复交付的记录列表
     */
    public List<SettlementRecord> unsettled() {
        List<SettlementRecord> list = new ArrayList<>();
        for (SettlementRecord record : records.values()) {
            if (record.needsRecovery()) {
                list.add(record);
            }
        }
        return list;
    }

    /**
     * 指定维度的待恢复记录快照。
     *
     * @param dimension 维度 id
     * @return 该维度下需要恢复交付的记录列表
     */
    public List<SettlementRecord> unsettled(String dimension) {
        List<SettlementRecord> list = new ArrayList<>();
        for (SettlementRecord record : records.values()) {
            if (record.needsRecovery() && record.dimension().equals(dimension)) {
                list.add(record);
            }
        }
        return list;
    }

    /**
     * 待交付返还总量（仅调试观测）：是还没交付出去的库存，与「已派发未回执」的 pendingUnits 无关。
     *
     * @return 全部记录 pendingDelivery 之和
     */
    public int pendingDeliveryTotal() {
        int total = 0;
        for (SettlementRecord record : records.values()) {
            total += record.pendingDelivery();
        }
        return total;
    }

    /**
     * 已派发未回执的计划量（仅调试观测）：不是待返还库存，恢复交付绝不使用。
     *
     * @return 全部记录 pendingUnits 之和
     */
    public long pendingUnitsTotal() {
        long total = 0;
        for (SettlementRecord record : records.values()) {
            total = Math.addExact(total, record.pendingUnits());
        }
        return total;
    }

    /**
     * 裁剪账本：已终结且无可交付内容的记录先清理，已完成记录再按 tick 过期，最后按条数上限兜底。
     * 有待交付量的记录一律保留，它们是恢复依据；取消/失败后没有待交付量说明已无恢复价值。
     *
     * @param gameTime 当前游戏刻，用于判定已完成记录是否已过保留窗口
     */
    public void prune(long gameTime) {
        boolean changed = false;
        // 先判定再成对摘除：记录可变，判定必须发生在摘除之前，快照遍历避免边遍历边改主索引
        for (SettlementRecord record : List.copyOf(records.values())) {
            if (trimmable(record, gameTime)) {
                changed |= unindex(record.id());
            }
        }
        while (records.size() > MAX_RECORDS) {
            String victim = null;
            for (Map.Entry<String, SettlementRecord> entry : records.entrySet()) {
                SettlementRecord candidate = entry.getValue();
                if (candidate.pendingDelivery() > 0 || candidate.finishedTick() < 0L) {
                    continue;
                }
                SettlementRecord.Status status = candidate.status();
                if (status == SettlementRecord.Status.COMPLETED) {
                    // 已完成记录优先：即使在保留窗口内，硬上限下也允许提前裁掉
                    victim = entry.getKey();
                    break;
                }
                if (victim == null && (status == SettlementRecord.Status.INTERRUPTED
                        || status == SettlementRecord.Status.FAILED)) {
                    victim = entry.getKey();
                }
            }
            if (victim == null) {
                // 上限是硬保护：待交付记录是恢复依据，一条都不能裁；告警按保留窗口降频
                if (lastCapacityWarnTick == Long.MIN_VALUE
                        || gameTime - lastCapacityWarnTick >= RETAIN_COMPLETED_TICKS) {
                    LOGGER.warn("结算账本达到上限 {} 且没有可裁剪的已终结记录，未结清记录全部保留", MAX_RECORDS);
                    lastCapacityWarnTick = gameTime;
                }
                break;
            }
            unindex(victim);
            changed = true;
        }
        if (changed) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("结算账本已裁剪：剩余 {} 条，未结清 {} 条", records.size(), unsettledCount());
            }
            setDirty();
        }
    }

    // 单条记录是否已无保留价值：已完成记录按保留窗口过期，取消/失败且无待交付的记录直接清理
    private boolean trimmable(SettlementRecord record, long gameTime) {
        if (record.pendingDelivery() > 0 || record.finishedTick() < 0L) {
            return false;
        }
        return switch (record.status()) {
            case COMPLETED -> gameTime - record.finishedTick() > RETAIN_COMPLETED_TICKS;
            case INTERRUPTED, FAILED -> true;
            default -> false;
        };
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
                    ledger.index(record);
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("结算记录恢复失败，已跳过：index={}", index, failure);
            }
        }
        return ledger;
    }
}
