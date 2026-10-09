package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.EffectTargets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** 按完整轮次分配催化剂；重叠标签共享真实库存，图构建和增广搜索按 tick 预算续跑。 */
public final class CatalystAllocation {
    private static final int SOURCE = 0;
    private final CatalystCost cost;
    private final List<TaggedId> references;
    private final List<ItemEntity> items;
    private final int[] available;
    private final List<ItemStack> stacks;
    private final int sink;
    private final List<List<Edge>> graph = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();
    private final Edge[] previous;
    private final int[] parents;
    private List<Map<Integer, Integer>> best = List.of();
    private int low;
    private int high;
    private int attempt;
    private int referenceCursor;
    private int itemCursor;
    private int sinkCursor;
    private int flow;
    private int searchNode = -1;
    private int edgeCursor;
    private boolean searching;
    private boolean building = true;

    CatalystAllocation(CatalystCost cost, List<ItemEntity> items, int[] available, int maximumGroups) {
        this.cost = cost;
        this.references = cost.items().stream().distinct().toList();
        this.items = List.copyOf(items);
        stacks = items.stream().map(item -> item.getItem().copy()).toList();
        this.available = available.clone();
        sink = 1 + references.size() + items.size();
        previous = new Edge[sink + 1];
        parents = new int[sink + 1];
        high = Math.min(maximumGroups, Integer.MAX_VALUE / Math.max(1, cost.totalCount()));
        resetAttempt();
    }

    boolean advance(ServerTickBudget budget) {
        while (low < high) {
            if (budget.exhausted()) return false;
            budget.charge(1);
            step();
        }
        return true;
    }

    int groups() {
        return low;
    }

    // 效果式消耗按单轮同步执行，复用相同的共享库存分配，避免重叠标签的贪心误判。
    public static Map<ItemEntity, Integer> payment(CatalystCost cost, List<ItemEntity> items, int rounds) {
        int[] available = items.stream().mapToInt(item -> item.getItem().getCount()).toArray();
        CatalystAllocation allocation = new CatalystAllocation(cost, items, available, rounds);
        while (allocation.low < allocation.high) allocation.step();
        if (allocation.groups() < rounds) return Map.of();
        Map<ItemEntity, Integer> payment = new LinkedHashMap<>();
        for (var reference : allocation.best) reference.forEach((index, amount) ->
                payment.merge(items.get(index), amount, Integer::sum));
        return payment;
    }

    // 分配成功的总量拆成逐轮支付计划；同一实体命中多个引用时在同一轮合并扣除。
    List<List<CatalystReservations.Hold>> paymentGroups(int count) {
        List<Map<Integer, Integer>> remaining = best.stream()
                .map(values -> (Map<Integer, Integer>) new LinkedHashMap<>(values)).toList();
        List<List<CatalystReservations.Hold>> result = new ArrayList<>();
        for (int group = 0; group < count; group++) {
            Map<Integer, Integer> payment = new LinkedHashMap<>();
            for (int reference = 0; reference < references.size(); reference++) {
                int needed = cost.countFor(references.get(reference));
                for (var pick : remaining.get(reference).entrySet()) {
                    int take = Math.min(needed, pick.getValue());
                    if (take > 0) payment.merge(pick.getKey(), take, Integer::sum);
                    pick.setValue(pick.getValue() - take);
                    needed -= take;
                    if (needed == 0) break;
                }
            }
            List<CatalystReservations.Hold> holds = new ArrayList<>();
            payment.forEach((index, amount) -> holds.add(new CatalystReservations.Hold(
                    items.get(index).getUUID(), amount, stacks.get(index).getItem())));
            result.add(List.copyOf(holds));
        }
        return result;
    }

    private void resetAttempt() {
        attempt = low + (high - low + 1) / 2;
        graph.clear();
        for (int node = 0; node <= sink; node++) graph.add(new ArrayList<>());
        links.clear();
        referenceCursor = 0;
        itemCursor = 0;
        sinkCursor = 0;
        flow = 0;
        searching = false;
        building = true;
    }

    private void step() {
        if (building) {
            if (referenceCursor < references.size()) {
                TaggedId reference = references.get(referenceCursor);
                int node = 1 + referenceCursor;
                int demand = cost.countFor(reference) * attempt;
                if (itemCursor == 0) edge(SOURCE, node, demand);
                if (itemCursor < items.size()) {
                    if (EffectTargets.matchesAny(List.of(reference), stacks.get(itemCursor))) {
                        Edge match = edge(node, 1 + references.size() + itemCursor, demand);
                        links.add(new Link(referenceCursor, itemCursor, match));
                    }
                    itemCursor++;
                } else {
                    referenceCursor++;
                    itemCursor = 0;
                }
                return;
            }
            if (sinkCursor < items.size()) {
                edge(1 + references.size() + sinkCursor, sink, available[sinkCursor]);
                sinkCursor++;
                return;
            }
            building = false;
        }
        if (!searching) {
            Arrays.fill(previous, null);
            Arrays.fill(parents, -1);
            queue.clear();
            queue.add(SOURCE);
            parents[SOURCE] = SOURCE;
            searchNode = -1;
            searching = true;
        }
        if (searchNode < 0) {
            if (queue.isEmpty()) {
                finishAttempt(false);
                return;
            }
            searchNode = queue.removeFirst();
            edgeCursor = 0;
        }
        if (edgeCursor >= graph.get(searchNode).size()) {
            searchNode = -1;
            return;
        }
        Edge candidate = graph.get(searchNode).get(edgeCursor++);
        if (candidate.remaining <= 0 || parents[candidate.to] >= 0) return;
        previous[candidate.to] = candidate;
        parents[candidate.to] = searchNode;
        if (candidate.to != sink) {
            queue.add(candidate.to);
            return;
        }
        int amount = Integer.MAX_VALUE;
        for (int node = sink; node != SOURCE; node = parents[node]) amount = Math.min(amount, previous[node].remaining);
        for (int node = sink; node != SOURCE; node = parents[node]) {
            Edge chosen = previous[node];
            chosen.remaining -= amount;
            graph.get(chosen.to).get(chosen.reverse).remaining += amount;
        }
        flow += amount;
        searching = false;
        if (flow == cost.totalCount() * attempt) finishAttempt(true);
    }

    private void finishAttempt(boolean complete) {
        if (complete) {
            low = attempt;
            List<Map<Integer, Integer>> allocation = new ArrayList<>();
            for (int reference = 0; reference < references.size(); reference++) allocation.add(new LinkedHashMap<>());
            for (Link link : links) {
                int assigned = link.edge.initial - link.edge.remaining;
                if (assigned > 0) allocation.get(link.reference).put(link.item, assigned);
            }
            best = allocation;
        } else {
            high = attempt - 1;
        }
        if (low < high) resetAttempt();
    }

    private Edge edge(int from, int to, int capacity) {
        Edge forward = new Edge(to, graph.get(to).size(), capacity);
        Edge reverse = new Edge(from, graph.get(from).size(), 0);
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
        return forward;
    }

    private record Link(int reference, int item, Edge edge) { }

    private static final class Edge {
        private final int to;
        private final int reverse;
        private final int initial;
        private int remaining;

        private Edge(int to, int reverse, int capacity) {
            this.to = to;
            this.reverse = reverse;
            this.initial = capacity;
            remaining = capacity;
        }
    }
}
