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
import org.jetbrains.annotations.Nullable;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;

/**
 * 催化剂固定成本的运行期预留表：同维度维护「已预留未支付」的催化剂实体 UUID → 件数，
 * 使多个结算任务不会重复使用同一批催化剂；组开始时把该组预留转为已支付（真实 shrink + 空栈 discard）。
 * 访问约束：只在服务端线程访问，不做同步；按维度分表，跨维度互不影响。
 * 生命周期：纯运行期结构、不落盘 —— 重启后预留自然消失，中断时已释放的预留不会在重启后产生任何件数扣减或返还。
 */
final class CatalystReservations {

    // 预留项：某维度内某个催化剂实体被某任务占用的件数
    record Hold(UUID itemId, int count, Item expectedItem) {}

    // 支付结果：已支付 / 本刻预算不足需下刻重试 / 催化剂可用量不足 / 门槛不足 / 流体不足
    enum PayOutcome { PAID, RETRY, CATALYST_SHORT, THRESHOLD_SHORT, FLUID_SHORT }

    // 支付结果：结果 + 实际支付件数（只有 PAID 可能非零）
    record PayResult(PayOutcome outcome, int paid) {
        static PayResult paid(int paid) { return new PayResult(PayOutcome.PAID, paid); }
        static PayResult retry() { return new PayResult(PayOutcome.RETRY, 0); }
        static PayResult blocked(PayOutcome outcome) { return new PayResult(outcome, 0); }
    }

    /**
     * 开组前输入复核：返回 null 表示门槛与流体都可支付，返回具体原因表示本组不能开。
     * 用原因而非布尔值，让结算能区分「催化剂不足」与「门槛或流体在开组当刻不满足」。
     */
    @FunctionalInterface
    interface PaymentInputs {
        @Nullable PayOutcome commit();
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

    // 某实体可供该任务使用的件数：现存件数减去其它任务已预留的部分。
    // 计划期上界与开组前门槛复核共用这一口径，避免本任务自己的预留把可用量越算越小
    int availableExcept(ServerLevel level, ItemEntity item, String owner) {
        return Math.max(0, item.getItem().getCount()
                - reservedCount(level.dimension(), item.getUUID())
                + ownReserved(owner, item.getUUID()));
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

    /**
     * 支付一组催化剂：先按 UUID 重读实体与件数，任一件取不到就整组不扣；
     * 预算耗尽返回 RETRY（同样是整组不扣，下刻重试）；输入复核在真实扣减之前进行，
     * 不通过时返回对应原因，调用方可据此决定弃组重扫还是截断整批。
     * 只有全部校验通过才真实 shrink 并回写预留表。
     *
     * @param owner  结算记录 id
     * @param level  源所在维度
     * @param perGroup 本组应支付的催化剂总件数
     * @param budget 当前步骤的世界查询预算
     * @param inputs 门槛与流体的开组前复核
     * @return 支付结果与原因
     */
    PayResult payGroup(String owner, ServerLevel level, int perGroup, ServerTickBudget budget,
                       PaymentInputs inputs) {
        if (perGroup <= 0) {
            return commitOrPaid(inputs, 0);
        }
        OwnerHolds ownerHolds = byOwner.get(owner);
        if (ownerHolds == null || ownerHolds.holds().isEmpty()) {
            return PayResult.blocked(PayOutcome.CATALYST_SHORT);
        }
        // 第一遍：只算出本组要从哪些实体各取多少件，不改动任何状态
        List<Hold> plan = ownerHolds.groups().peekFirst();
        if (plan == null || plan.stream().mapToInt(Hold::count).sum() != perGroup) {
            // 预留总量都不够：记账已损坏，按催化剂不足处理并要求调用方释放
            return PayResult.blocked(PayOutcome.CATALYST_SHORT);
        }
        // 逐实体计费跨 tick 保留进度，避免多物品支付计划反复从头消耗预算。
        while (ownerHolds.progress().charged < plan.size()) {
            if (budget.exhausted()) return PayResult.retry();
            budget.charge(1);
            ownerHolds.progress().charged++;
        }
        // 真正支付前按 UUID 重读全部实体与件数，任何一件不足都整组不扣。
        // 位置不参与校验：催化剂被推动或被其它实体挤开不影响预留与支付，只有实体消失或件数不足才算不足。
        List<ItemEntity> resolved = new ArrayList<>(plan.size());
        for (Hold take : plan) {
            ItemEntity item = resolve(level, take.itemId());
            if (item == null || !item.isAlive()
                    || item.getItem().getItem() != take.expectedItem()
                    || item.getItem().getCount() < reservedCount(level.dimension(), take.itemId())) {
                return PayResult.blocked(PayOutcome.CATALYST_SHORT);
            }
            resolved.add(item);
        }
        PayOutcome blocked = inputs.commit();
        if (blocked != null) {
            return PayResult.blocked(blocked);
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

    // 无需催化剂时同样要复核门槛与流体：把输入复核结果折叠进零支付路径
    private static PayResult commitOrPaid(PaymentInputs inputs, int paid) {
        PayOutcome blocked = inputs.commit();
        return blocked == null ? PayResult.paid(paid) : PayResult.blocked(blocked);
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

    // 该任务对某实体的未支付预留件数
    private int ownReserved(String owner, UUID itemId) {
        OwnerHolds ownerHolds = byOwner.get(owner);
        if (ownerHolds == null) {
            return 0;
        }
        int total = 0;
        for (Hold hold : ownerHolds.holds()) {
            if (hold.itemId().equals(itemId)) {
                total += hold.count();
            }
        }
        return total;
    }

    private static ItemEntity resolve(ServerLevel level, UUID itemId) {
        return level.getEntity(itemId) instanceof ItemEntity item ? item : null;
    }
}
