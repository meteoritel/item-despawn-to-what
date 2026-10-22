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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产物容量探测：把一个候选的每轮产出折算成「当前还能开出多少组」，并封装邻近物品与实体的区域计数。
 * 需求来自各效果类型声明的 {@link CapacityDemand}：经验不占邻近产物容量，产物类型未知的产出（战利品表）
 * 按区域物品总量保守计入；仅含一次性效果的候选固定按 1 组处理。
 * 搜索盒以成功转化当刻固定的触发点为准，与效果执行器的判定范围一致，不随源实体后续位移漂移。
 * 需求表、引用匹配器与搜索盒都只由候选参数、固定触发点与半径决定，因此按候选缓存一次后复用：
 * 同一候选在本结算内的多次探测不再重建需求映射、标签键与搜索盒。
 * 每次世界查询按 {@link #QUERY_UNITS} 计划入预算；预算耗尽时返回
 * {@link SettlementGroupPlan#CAPACITY_PENDING}，由调用方跨刻续算。
 */
final class SettlementCapacityProbe {

    // 每次邻近计数查询计入的预算单位：一次实体遍历对齐「一次世界查询」的成本口径
    static final int QUERY_UNITS = 2;

    private final ConversionRuntime runtime;
    private final ServerLevel level;
    private final Vec3 triggerPosition;
    // 候选每轮一组的容量需求表（按候选 id 缓存）：需求只由效果参数决定，本结算内复用同一份
    private final Map<String, CapacityNeed> needs = new HashMap<>();
    // 搜索盒按半径缓存：中心是固定的触发点，同一半径的盒完全相同，避免每次查询重建
    private final Map<Integer, AABB> searchBoxes = new HashMap<>();

    SettlementCapacityProbe(ConversionRuntime runtime, ServerLevel level, Vec3 triggerPosition) {
        this.runtime = runtime;
        this.level = level;
        this.triggerPosition = triggerPosition;
    }

    // 单条物品需求：引用在汇总时已编译为可复用匹配器（标签键预建），逐实体判定不再重建 TagKey
    private record ItemNeed(TaggedItemMatch matcher, int count) {
    }

    // 单条实体需求：标签引用预建 TagKey，具体类型在汇总时解析一次；两者都为空表示引用当前无法解析，永不命中
    private record EntityNeed(@Nullable TagKey<EntityType<?>> tag, @Nullable EntityType<?> type, int count) {
    }

    // 候选的容量需求表：一次性效果不参与容量计算，只含一次性效果的候选固定按一组处理
    private record CapacityNeed(List<ItemNeed> items, List<EntityNeed> entities, int unspecifiedItems,
                                boolean oneShotOnly) {
    }

    // 候选每轮一组的产出需求：按候选缓存，首次探测时汇总并预编译，之后直接复用
    int groups(OutcomeCandidate selected, ServerTickBudget budget) {
        CapacityNeed need = needOf(selected);
        int groups = Integer.MAX_VALUE;
        NearbyProductLimits limits = runtime.nearbyProducts();
        if (limits.itemLimit() > 0) {
            for (ItemNeed demand : need.items()) {
                if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                budget.charge(QUERY_UNITS);
                int room = Math.max(0, limits.itemLimit() - countNearbyItems(demand, limits.itemLimit(), limits.itemRadius()));
                groups = Math.min(groups, room / demand.count());
            }
            // 产物类型未知：按区域全部物品的件数估算占用，room 为 0 时本候选开不出任何组
            if (need.unspecifiedItems() > 0) {
                if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                budget.charge(QUERY_UNITS);
                int room = Math.max(0, limits.itemLimit() - countNearbyAllItems(limits.itemLimit(), limits.itemRadius()));
                groups = Math.min(groups, room / need.unspecifiedItems());
            }
        }
        if (limits.entityLimit() > 0) {
            for (EntityNeed demand : need.entities()) {
                if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                budget.charge(QUERY_UNITS);
                int room = Math.max(0, limits.entityLimit() - countNearbyEntities(demand, limits.entityLimit(), limits.entityRadius()));
                groups = Math.min(groups, room / demand.count());
            }
        }
        return need.oneShotOnly() ? 1 : groups;
    }

    // 候选需求表：同一候选内重复声明同一引用时按件数合并，再为每条引用预编译匹配器/标签键
    private CapacityNeed needOf(OutcomeCandidate selected) {
        return needs.computeIfAbsent(selected.id(), id -> summarize(selected));
    }

    // 需求汇总：只读效果参数与注册表，不查世界，因此可以在首次探测时一次性完成
    private CapacityNeed summarize(OutcomeCandidate selected) {
        boolean hasOneShot = false;
        boolean hasRepeatable = false;
        Map<TaggedId, Integer> items = new LinkedHashMap<>();
        Map<TaggedId, Integer> entities = new LinkedHashMap<>();
        int unspecifiedItems = 0;
        for (Effect effect : selected.effects()) {
            EffectType<?> definition = runtime.effectDefinition(effect.type());
            if (definition == null) { continue; }
            if (definition.oneShot()) { hasOneShot = true; continue; }
            hasRepeatable = true;
            CapacityDemand demand = capacityDemandOf(definition, effect);
            for (var item : demand.items().entrySet()) {
                items.merge(item.getKey(), item.getValue(), SettlementCapacityProbe::saturatedAdd);
            }
            for (var entity : demand.entities().entrySet()) {
                entities.merge(entity.getKey(), entity.getValue(), SettlementCapacityProbe::saturatedAdd);
            }
            unspecifiedItems = saturatedAdd(unspecifiedItems, demand.unspecifiedItems());
        }
        List<ItemNeed> itemNeeds = new ArrayList<>(items.size());
        items.forEach((reference, count) -> itemNeeds.add(new ItemNeed(TaggedItemMatch.compile(reference), count)));
        List<EntityNeed> entityNeeds = new ArrayList<>(entities.size());
        entities.forEach((reference, count) -> entityNeeds.add(entityNeed(reference, count)));
        return new CapacityNeed(List.copyOf(itemNeeds), List.copyOf(entityNeeds), unspecifiedItems,
                hasOneShot && !hasRepeatable);
    }

    // 实体引用预编译：标签在汇总时建 TagKey，具体类型在汇总时解析一次；未注册的 id 解析为空，永不命中
    private static EntityNeed entityNeed(TaggedId reference, int count) {
        if (reference == null) {
            return new EntityNeed(null, null, count);
        }
        if (reference.tag()) {
            return new EntityNeed(TagKey.create(Registries.ENTITY_TYPE, reference.id()), null, count);
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(reference.id())) {
            return new EntityNeed(null, null, count);
        }
        return new EntityNeed(null, BuiltInRegistries.ENTITY_TYPE.get(reference.id()), count);
    }

    // 效果类型是参数化接口：这里已按 type 字段查表得到类型定义，交由该类型按实例参数声明容量需求
    @SuppressWarnings("unchecked")
    private static CapacityDemand capacityDemandOf(EffectType<?> definition, Effect effect) {
        return ((EffectType<Effect>) definition).capacityDemand(effect);
    }

    // 邻近物品总量：与实体产出掉落物同一判定（具体物品比注册表对象，标签比 TagKey），
    // 匹配器随候选需求表预编译一次后逐实体复用，不在查询谓词里重复创建 TagKey。
    // 计数口径与 countNearbyEntities 一致：谓词只筛选实体，收集条数由查询的 maxResults 限制，
    // 数量来自结果列表；物品按堆叠数逐条累加（空堆叠不命中，因此收满 limit 条时总量必然 >= limit，
    // 此时 room 已经归零，不会高估可开组数）。
    private int countNearbyItems(ItemNeed demand, int limit, int radius) {
        AABB box = searchBox(radius);
        List<ItemEntity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box,
                item -> item.isAlive() && !item.getItem().isEmpty() && demand.matcher().matches(item.getItem()), nearby, limit);
        int total = 0;
        for (ItemEntity item : nearby) {
            total = saturatedAdd(total, item.getItem().getCount());
        }
        return total;
    }

    // 邻近物品总量（不区分类别）：产物类型未知的产出（战利品表）只能按区域物品总量保守占用容量。
    // 收集条数上限取 limit，收满时总量必然 >= limit，room 归零，不会高估可开组数。
    private int countNearbyAllItems(int limit, int radius) {
        AABB box = searchBox(radius);
        List<ItemEntity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box,
                item -> item.isAlive() && !item.getItem().isEmpty(), nearby, limit);
        int total = 0;
        for (ItemEntity item : nearby) {
            total = saturatedAdd(total, item.getItem().getCount());
        }
        return total;
    }

    // 邻近实体总量：与 spawn_entity 执行器同一判定（支持具体类型与标签）；类型或标签键随需求表预编译，
    // 具体类型仍走按类型过滤的查询，标签走全实体查询加标签成员判定
    private int countNearbyEntities(EntityNeed demand, int limit, int radius) {
        if (demand.type() == null && demand.tag() == null) {
            return 0;
        }
        AABB box = searchBox(radius);
        if (demand.tag() == null) {
            List<Entity> nearby = new ArrayList<>();
            level.getEntities(demand.type(), box, Entity::isAlive, nearby, limit);
            return nearby.size();
        }
        TagKey<EntityType<?>> tag = demand.tag();
        List<Entity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(Entity.class), box,
                entity -> entity.isAlive() && entity.getType().is(tag), nearby, limit);
        return nearby.size();
    }

    // 搜索盒与效果执行器同一口径：以成功转化当刻固定的触发点所在方块为中心、边长 2*radius+1 格；
    // 中心与半径都在结算内固定，按半径缓存一次即可复用
    private AABB searchBox(int radius) {
        return searchBoxes.computeIfAbsent(radius, key -> EffectTargets.blockBox(BlockPos.containing(triggerPosition), key));
    }

    // 组内需求累加采用饱和值，不允许溢出后绕过阈值。
    static int saturatedAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
    }
}
