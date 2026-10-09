package com.meteorite.itemdespawntowhat.core.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayDeque;
import net.minecraft.world.item.Item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;

/**
 * 催化剂固定成本的运行期预留表（D31）：同维度维护「已预留未支付」的催化剂实体 UUID → 件数，
 * 使多个结算任务不会重复使用同一批催化剂；组开始时把该组预留转为已支付（真实 shrink + 空栈 discard）。
 * 访问约束：只在服务端线程访问，不做同步；按维度分表，跨维度互不影响。
 * 生命周期：纯运行期结构、不落盘 —— 重启后预留自然消失，中断时已释放的预留不会在重启后产生任何件数扣减或返还。
 */
final class CatalystReservations {

    // 预留项：某维度内某个催化剂实体被某任务占用的件数
    record Hold(UUID itemId, int count, Item expectedItem) {}

    // 支付三态：已支付 / 本刻预算不足需下刻重试 / 可用量不足（含实体已不存在或被扣减）
    enum PayOutcome { PAID, RETRY, SHORT }

    record PayResult(PayOutcome outcome, int paid) {
        static PayResult paid(int paid) { return new PayResult(PayOutcome.PAID, paid); }
        static PayResult retry() { return new PayResult(PayOutcome.RETRY, 0); }
        static PayResult shortfall() { return new PayResult(PayOutcome.SHORT, 0); }
    }

    // 某任务持有的预留：维度 + 由近及远的预留项（顺序即支付顺序）
    private record OwnerHolds(ResourceKey<Level> dimension, List<Hold> holds,
                              ArrayDeque<List<Hold>> groups, PaymentProgress progress) {}

    private static final class PaymentProgress { private int charged; }

    private final Map<ResourceKey<Level>, Map<UUID, Integer>> reservedByItem = new HashMap<>();
    private final Map<String, OwnerHolds> byOwner = new LinkedHashMap<>();

    // 某实体当前可用的未预留件数：实际件数减去已被任何任务预留的部分
    int available(ServerLevel level, ItemEntity item) {
        return Math.max(0, item.getItem().getCount() - reservedCount(level.dimension(), item.getUUID()));
    }

    // 该维度当前被任何任务预留的件数合计（只读，供观测与断言）
    int dimensionReserved(ResourceKey<Level> dimension) {
        Map<UUID, Integer> table = reservedByItem.get(dimension);
        if (table == null) {
            return 0;
        }
        int total = 0;
        for (int count : table.values()) {
            total += count;
        }
        return total;
    }

    // 某任务仍被预留的件数（只读，供观测与断言）
    int ownerReserved(String owner) {
        OwnerHolds ownerHolds = byOwner.get(owner);
        if (ownerHolds == null) {
            return 0;
        }
        int total = 0;
        for (Hold hold : ownerHolds.holds()) {
            total += hold.count();
        }
        return total;
    }

    // 合并逐轮支付清单中的实体数量，并整体预留：
    // 任一件数不足则整体不预留（绝不半预留），返回 null 表示可用量不足，由调用方跳过该候选
    List<Hold> tryReserve(String owner, ServerLevel level, List<List<Hold>> groups) {
        if (groups.isEmpty()) {
            return List.of();
        }
        // 同一任务已有预留：直接拒绝，绝不叠加第二次预留（调用方收到 null 后跳过该候选）
        if (byOwner.containsKey(owner)) {
            return null;
        }
        Map<UUID, Hold> combined = new LinkedHashMap<>();
        for (List<Hold> group : groups) for (Hold hold : group) {
            combined.merge(hold.itemId(), hold, (left, right) ->
                    new Hold(left.itemId(), left.count() + right.count(), left.expectedItem()));
        }
        List<Hold> picks = new ArrayList<>(combined.values());
        for (Hold pick : picks) {
            ItemEntity item = resolve(level, pick.itemId());
            if (item == null || item.getItem().getItem() != pick.expectedItem()
                    || available(level, item) < pick.count()) return null;
        }
        reserve(owner, level.dimension(), picks, groups);
        return picks;
    }

    // 支付一组催化剂：先按 UUID 重读实体与件数，任一件取不到就整组不扣；
    // 预算耗尽返回 RETRY（同样是整组不扣，下刻重试）；只有全部校验通过才真实 shrink 并回写预留表
    PayResult payGroup(String owner, ServerLevel level, int perGroup, ServerTickBudget budget) {
        if (perGroup <= 0) {
            return PayResult.paid(0);
        }
        OwnerHolds ownerHolds = byOwner.get(owner);
        if (ownerHolds == null || ownerHolds.holds().isEmpty()) {
            return PayResult.shortfall();
        }
        // 第一遍：只算出本组要从哪些实体各取多少件，不改动任何状态
        List<Hold> plan = ownerHolds.groups().peekFirst();
        if (plan == null || plan.stream().mapToInt(Hold::count).sum() != perGroup) {
            // 预留总量都不够：记账已损坏，按不足处理并要求调用方释放
            return PayResult.shortfall();
        }
        // 逐实体计费跨 tick 保留进度，避免多物品支付计划反复从头消耗预算。
        while (ownerHolds.progress().charged < plan.size()) {
            if (budget.exhausted()) return PayResult.retry();
            budget.charge(1);
            ownerHolds.progress().charged++;
        }
        // 真正支付前按 UUID 重读全部实体与件数，任何一件不足都整组不扣。
        List<ItemEntity> resolved = new ArrayList<>(plan.size());
        for (Hold take : plan) {
            ItemEntity item = resolve(level, take.itemId());
            if (item == null || item.getItem().getItem() != take.expectedItem()
                    || item.getItem().getCount() < reservedCount(level.dimension(), take.itemId())) {
                return PayResult.shortfall();
            }
            resolved.add(item);
        }
        // 全部校验通过后真实扣减；空栈实体按原版语义 discard
        for (int i = 0; i < plan.size(); i++) {
            ItemStack stack = resolved.get(i).getItem();
            stack.shrink(plan.get(i).count());
            if (stack.isEmpty()) {
                resolved.get(i).discard();
            }
        }
        consume(owner, ownerHolds.dimension(), plan);
        ownerHolds.groups().removeFirst();
        ownerHolds.progress().charged = 0;
        return PayResult.paid(perGroup);
    }

    // 释放某任务的全部未支付预留（幂等；未知 owner 直接返回，不产生任何世界写入）
    void releaseOwner(String owner) {
        OwnerHolds ownerHolds = byOwner.remove(owner);
        if (ownerHolds == null) {
            return;
        }
        Map<UUID, Integer> table = reservedByItem.get(ownerHolds.dimension());
        if (table == null) {
            return;
        }
        for (Hold hold : ownerHolds.holds()) {
            int left = table.getOrDefault(hold.itemId(), 0) - hold.count();
            if (left > 0) {
                table.put(hold.itemId(), left);
            } else {
                table.remove(hold.itemId());
            }
        }
        if (table.isEmpty()) {
            reservedByItem.remove(ownerHolds.dimension());
        }
    }

    // 停服兜底：清空全部维度与任务的全部预留（预留只在运行期存在，清空即可，无需世界写入）
    void clear() {
        reservedByItem.clear();
        byOwner.clear();
    }

    // 建立预留：按维度累计件数，并登记到任务名下（顺序保留，支付时仍按由近及远）
    private void reserve(String owner, ResourceKey<Level> dimension, List<Hold> picks, List<List<Hold>> groups) {
        Map<UUID, Integer> table = reservedByItem.computeIfAbsent(dimension, key -> new HashMap<>());
        for (Hold pick : picks) {
            table.merge(pick.itemId(), pick.count(), Integer::sum);
        }
        byOwner.put(owner, new OwnerHolds(dimension, new ArrayList<>(picks), new ArrayDeque<>(groups), new PaymentProgress()));
    }

    // 支付成功后回写：从任务预留与维度表中同额扣减
    private void consume(String owner, ResourceKey<Level> dimension, List<Hold> paid) {
        OwnerHolds ownerHolds = byOwner.get(owner);
        List<Hold> holds = ownerHolds == null ? null : ownerHolds.holds();
        for (Hold take : paid) {
            if (holds != null) {
                for (int i = 0; i < holds.size(); i++) {
                    if (!holds.get(i).itemId().equals(take.itemId())) {
                        continue;
                    }
                    int left = holds.get(i).count() - take.count();
                    if (left > 0) {
                        holds.set(i, new Hold(take.itemId(), left, take.expectedItem()));
                    } else {
                        holds.remove(i);
                    }
                    break;
                }
            }
            Map<UUID, Integer> table = reservedByItem.get(dimension);
            if (table == null) {
                continue;
            }
            int left = table.getOrDefault(take.itemId(), 0) - take.count();
            if (left > 0) {
                table.put(take.itemId(), left);
            } else {
                table.remove(take.itemId());
            }
        }
        if (holds != null && holds.isEmpty()) {
            byOwner.remove(owner);
        }
    }

    private int reservedCount(ResourceKey<Level> dimension, UUID itemId) {
        Map<UUID, Integer> table = reservedByItem.get(dimension);
        return table == null ? 0 : table.getOrDefault(itemId, 0);
    }

    private static ItemEntity resolve(ServerLevel level, UUID itemId) {
        return level.getEntity(itemId) instanceof ItemEntity item ? item : null;
    }
}
