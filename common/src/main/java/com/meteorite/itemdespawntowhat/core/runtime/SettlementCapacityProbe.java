package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.config.NearbyProductLimits;
import com.meteorite.itemdespawntowhat.core.model.CapacityDemand;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.type.TaggedItemMatch;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.EffectTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按行为产出原点测量初始邻近存量，再将空间与引用交集折算为候选的完整组预算。
 * 同域同类查询只执行一次，查询预算耗尽后保留游标续接；开组只扣本地理论额度。
 * 相交查询域共享预留，分离域独立，未知物品产出在自身区域按全部物品计入。
 */
final class SettlementCapacityProbe {
    static final int QUERY_UNITS = 2;

    private final ConversionRuntime runtime;
    private final ServerLevel level;
    private final EffectOrigins origins;
    private final Map<String, CapacityMeasurement> measurements = new LinkedHashMap<>();
    private final Map<String, List<BudgetDebit>> debits = new HashMap<>();
    private final Map<AreaKey, AABB> searchBoxes = new HashMap<>();
    private final Map<QueryKey, Integer> itemCounts = new HashMap<>();
    private final Map<QueryKey, Integer> entityCounts = new HashMap<>();

    SettlementCapacityProbe(ConversionRuntime runtime, ServerLevel level, EffectOrigins origins) {
        this.runtime = runtime;
        this.level = level;
        this.origins = origins;
    }

    private record AreaKey(BlockPos center, int radius) {}
    private record ProductKey(@Nullable TaggedId reference, BlockPos center) {}
    private record QueryKey(@Nullable TaggedId reference, AABB area) {}

    // reference/matcher 为空表示未知物品产出：查询该区域内的全部物品。
    private record ItemNeed(@Nullable TaggedId reference, @Nullable TaggedItemMatch matcher,
                            Set<Item> possible, AABB area, int count) {}
    private record EntityNeed(TaggedId reference, @Nullable TagKey<EntityType<?>> tag,
                              @Nullable EntityType<?> type, Set<EntityType<?>> possible, AABB area, int count) {}
    private record CapacityNeed(List<ItemNeed> items, List<EntityNeed> entities, boolean oneShotOnly) {}

    private static final class CapacityMeasurement {
        private final CapacityNeed need;
        private int itemCursor;
        private int entityCursor;
        private int groups = Integer.MAX_VALUE;
        private final int[] itemRooms;
        private final int[] entityRooms;
        private final int[] itemCosts;
        private final int[] entityCosts;

        private CapacityMeasurement(CapacityNeed need) {
            this.need = need;
            itemRooms = new int[need.items().size()];
            entityRooms = new int[need.entities().size()];
            itemCosts = need.items().stream().mapToInt(item -> itemCost(need, item)).toArray();
            entityCosts = need.entities().stream().mapToInt(entity -> entityCost(need, entity)).toArray();
            Arrays.fill(itemRooms, Integer.MAX_VALUE);
            Arrays.fill(entityRooms, Integer.MAX_VALUE);
        }
    }

    // 缓存的是初始世界存量；扣减只改变 measurement 的房间余量，不修改查询缓存。
    int groups(OutcomeCandidate selected, ServerTickBudget budget) {
        CapacityMeasurement measurement = measurements.computeIfAbsent(selected.id(),
                ignored -> new CapacityMeasurement(summarize(selected)));
        CapacityNeed need = measurement.need;
        NearbyProductLimits limits = runtime.nearbyProducts();
        if (limits.itemLimit() > 0) {
            while (measurement.itemCursor < need.items().size()) {
                ItemNeed demand = need.items().get(measurement.itemCursor);
                QueryKey key = new QueryKey(demand.reference(), demand.area());
                Integer count = itemCounts.get(key);
                if (count == null) {
                    if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                    budget.charge(QUERY_UNITS);
                    count = countNearbyItems(demand, limits.itemLimit());
                    itemCounts.put(key, count);
                }
                int room = Math.max(0, limits.itemLimit() - count);
                measurement.itemRooms[measurement.itemCursor] = room;
                measurement.groups = Math.min(measurement.groups, room / measurement.itemCosts[measurement.itemCursor]);
                measurement.itemCursor++;
            }
        }
        if (limits.entityLimit() > 0) {
            while (measurement.entityCursor < need.entities().size()) {
                EntityNeed demand = need.entities().get(measurement.entityCursor);
                QueryKey key = new QueryKey(demand.reference(), demand.area());
                Integer count = entityCounts.get(key);
                if (count == null) {
                    if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                    budget.charge(QUERY_UNITS);
                    count = countNearbyEntities(demand, limits.entityLimit());
                    entityCounts.put(key, count);
                }
                int room = Math.max(0, limits.entityLimit() - count);
                measurement.entityRooms[measurement.entityCursor] = room;
                measurement.groups = Math.min(measurement.groups, room / measurement.entityCosts[measurement.entityCursor]);
                measurement.entityCursor++;
            }
        }
        return need.oneShotOnly() ? 1 : measurement.groups;
    }

    int remainingGroups(OutcomeCandidate selected) {
        CapacityMeasurement measurement = measurements.get(selected.id());
        int groups = measurement.groups;
        for (int i = 0; i < measurement.itemRooms.length; i++) {
            if (measurement.itemRooms[i] != Integer.MAX_VALUE) {
                groups = Math.min(groups, measurement.itemRooms[i] / measurement.itemCosts[i]);
            }
        }
        for (int i = 0; i < measurement.entityRooms.length; i++) {
            if (measurement.entityRooms[i] != Integer.MAX_VALUE) {
                groups = Math.min(groups, measurement.entityRooms[i] / measurement.entityCosts[i]);
            }
        }
        return measurement.need.oneShotOnly() ? 1 : groups;
    }

    // 同一个已支付组向所有空间与引用相交的额度扣账；概率落空不归还理论额度。
    void reserve(OutcomeCandidate selected) {
        for (BudgetDebit debit : debits.computeIfAbsent(selected.id(), ignored -> debitsFor(selected))) {
            for (int i = 0; i < debit.items().length; i++) {
                debit.target().itemRooms[i] = subtractRoom(debit.target().itemRooms[i], debit.items()[i]);
            }
            for (int i = 0; i < debit.entities().length; i++) {
                debit.target().entityRooms[i] = subtractRoom(debit.target().entityRooms[i], debit.entities()[i]);
            }
        }
    }

    private record BudgetDebit(CapacityMeasurement target, int[] items, int[] entities) {}

    // 初始测量覆盖全部候选后才开组，扣减表因此包含本次结算的全部空间域。
    private List<BudgetDebit> debitsFor(OutcomeCandidate selected) {
        CapacityNeed source = measurements.get(selected.id()).need;
        List<BudgetDebit> result = new ArrayList<>();
        for (CapacityMeasurement target : measurements.values()) {
            int[] items = target.need.items().stream().mapToInt(item -> itemCost(source, item)).toArray();
            int[] entities = target.need.entities().stream().mapToInt(entity -> entityCost(source, entity)).toArray();
            result.add(new BudgetDebit(target, items, entities));
        }
        return List.copyOf(result);
    }

    private static int itemCost(CapacityNeed source, ItemNeed target) {
        int count = 0;
        for (ItemNeed item : source.items()) {
            if (item == target || item.area().intersects(target.area())
                    && (item.matcher() == null || target.matcher() == null
                    || !Collections.disjoint(item.possible(), target.possible()))) {
                count = saturatedAdd(count, item.count());
            }
        }
        return count;
    }

    private static int entityCost(CapacityNeed source, EntityNeed target) {
        int count = 0;
        for (EntityNeed entity : source.entities()) {
            if (entity == target || entity.area().intersects(target.area())
                    && !Collections.disjoint(entity.possible(), target.possible())) {
                count = saturatedAdd(count, entity.count());
            }
        }
        return count;
    }

    private static int subtractRoom(int room, int count) {
        return room == Integer.MAX_VALUE ? room : Math.max(0, room - count);
    }

    // 同引用只有在同原点时才汇总，空间信息在候选汇总期间始终保留。
    private CapacityNeed summarize(OutcomeCandidate selected) {
        boolean hasOneShot = false;
        boolean hasRepeatable = false;
        Map<ProductKey, Integer> items = new LinkedHashMap<>();
        Map<ProductKey, Integer> entities = new LinkedHashMap<>();
        for (Effect effect : selected.effects()) {
            EffectType<?> definition = runtime.effectDefinition(effect.type());
            if (definition == null) { continue; }
            if (definition.oneShot()) { hasOneShot = true; continue; }
            hasRepeatable = true;
            BlockPos center = BlockPos.containing(origins.position(effect));
            CapacityDemand demand = capacityDemandOf(definition, effect);
            demand.items().forEach((reference, count) -> {
                if (count > 0) { items.merge(new ProductKey(reference, center), count, SettlementCapacityProbe::saturatedAdd); }
            });
            demand.entities().forEach((reference, count) -> {
                if (count > 0) { entities.merge(new ProductKey(reference, center), count, SettlementCapacityProbe::saturatedAdd); }
            });
            if (demand.unspecifiedItems() > 0) {
                items.merge(new ProductKey(null, center), demand.unspecifiedItems(), SettlementCapacityProbe::saturatedAdd);
            }
        }
        NearbyProductLimits limits = runtime.nearbyProducts();
        List<ItemNeed> itemNeeds = new ArrayList<>(items.size());
        items.forEach((key, count) -> {
            TaggedId reference = key.reference();
            itemNeeds.add(new ItemNeed(reference, reference == null ? null : TaggedItemMatch.compile(reference),
                    reference == null ? Set.of() : possible(reference, BuiltInRegistries.ITEM, Registries.ITEM),
                    searchBox(key.center(), limits.itemRadius()), count));
        });
        List<EntityNeed> entityNeeds = new ArrayList<>(entities.size());
        entities.forEach((key, count) -> entityNeeds.add(entityNeed(Objects.requireNonNull(key.reference()), count,
                searchBox(key.center(), limits.entityRadius()))));
        return new CapacityNeed(List.copyOf(itemNeeds), List.copyOf(entityNeeds), hasOneShot && !hasRepeatable);
    }

    private static EntityNeed entityNeed(TaggedId reference, int count, AABB area) {
        if (reference.tag()) {
            return new EntityNeed(reference, TagKey.create(Registries.ENTITY_TYPE, reference.id()), null,
                    possible(reference, BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE), area, count);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.containsKey(reference.id())
                ? BuiltInRegistries.ENTITY_TYPE.get(reference.id()) : null;
        return new EntityNeed(reference, null, type, type == null ? Set.of() : Set.of(type), area, count);
    }

    private static <T> Set<T> possible(TaggedId reference, Registry<T> registry,
                                      ResourceKey<? extends Registry<T>> key) {
        if (reference.tag()) {
            return registry.getTag(TagKey.create(key, reference.id()))
                    .map(tag -> tag.stream().map(Holder::value).collect(Collectors.toUnmodifiableSet()))
                    .orElse(Set.of());
        }
        return registry.containsKey(reference.id()) ? Set.of(registry.get(reference.id())) : Set.of();
    }

    @SuppressWarnings("unchecked")
    private static CapacityDemand capacityDemandOf(EffectType<?> definition, Effect effect) {
        return ((EffectType<Effect>) definition).capacityDemand(effect);
    }

    private int countNearbyItems(ItemNeed demand, int limit) {
        List<ItemEntity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), demand.area(),
                item -> item.isAlive() && !item.getItem().isEmpty()
                        && (demand.matcher() == null || demand.matcher().matches(item.getItem())), nearby, limit);
        int total = 0;
        for (ItemEntity item : nearby) { total = saturatedAdd(total, item.getItem().getCount()); }
        return total;
    }

    private int countNearbyEntities(EntityNeed demand, int limit) {
        if (demand.type() == null && demand.tag() == null) { return 0; }
        List<Entity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(Entity.class), demand.area(),
                entity -> entity.isAlive() && (demand.tag() == null
                        ? entity.getType() == demand.type() : entity.getType().is(demand.tag())), nearby, limit);
        return nearby.size();
    }

    private AABB searchBox(BlockPos center, int radius) {
        return searchBoxes.computeIfAbsent(new AreaKey(center, radius),
                key -> EffectTargets.blockBox(key.center(), key.radius()));
    }

    static int saturatedAdd(int left, int right) {
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }
}
