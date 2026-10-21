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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产物容量探测：把一个候选的每轮产出折算成「当前还能开出多少组」，并封装邻近物品与实体的区域计数。
 * 需求来自各效果类型声明的 {@link CapacityDemand}：经验不占邻近产物容量，产物类型未知的产出（战利品表）
 * 按区域物品总量保守计入；仅含一次性效果的候选固定按 1 组处理。
 * 搜索盒以成功转化当刻固定的触发点为准，与效果执行器的判定范围一致，不随源实体后续位移漂移。
 * 每次世界查询按 {@link #QUERY_UNITS} 计划入预算；预算耗尽时返回
 * {@link SettlementGroupPlan#CAPACITY_PENDING}，由调用方跨刻续算。
 */
final class SettlementCapacityProbe {

    // 每次邻近计数查询计入的预算单位：一次实体遍历对齐「一次世界查询」的成本口径
    static final int QUERY_UNITS = 2;

    private final ConversionRuntime runtime;
    private final ServerLevel level;
    private final Vec3 triggerPosition;

    SettlementCapacityProbe(ConversionRuntime runtime, ServerLevel level, Vec3 triggerPosition) {
        this.runtime = runtime;
        this.level = level;
        this.triggerPosition = triggerPosition;
    }

    // 候选每轮一组的产出需求：逐效果查类型定义并聚合，一次性效果不参与容量计算
    int groups(OutcomeCandidate selected, ServerTickBudget budget) {
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
        int groups = Integer.MAX_VALUE;
        NearbyProductLimits limits = runtime.nearbyProducts();
        if (limits.itemLimit() > 0) {
            for (var demand : items.entrySet()) {
                if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                budget.charge(QUERY_UNITS);
                int room = Math.max(0, limits.itemLimit() - countNearbyItems(demand.getKey(), limits.itemLimit(), limits.itemRadius()));
                groups = Math.min(groups, room / demand.getValue());
            }
            // 产物类型未知：按区域全部物品的件数估算占用，room 为 0 时本候选开不出任何组
            if (unspecifiedItems > 0) {
                if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                budget.charge(QUERY_UNITS);
                int room = Math.max(0, limits.itemLimit() - countNearbyAllItems(limits.itemLimit(), limits.itemRadius()));
                groups = Math.min(groups, room / unspecifiedItems);
            }
        }
        if (limits.entityLimit() > 0) {
            for (var demand : entities.entrySet()) {
                if (budget.exhausted()) { return SettlementGroupPlan.CAPACITY_PENDING; }
                budget.charge(QUERY_UNITS);
                int room = Math.max(0, limits.entityLimit() - countNearbyEntities(demand.getKey(), limits.entityLimit(), limits.entityRadius()));
                groups = Math.min(groups, room / demand.getValue());
            }
        }
        return hasOneShot && !hasRepeatable ? 1 : groups;
    }

    // 效果类型是参数化接口：这里已按 type 字段查表得到类型定义，交由该类型按实例参数声明容量需求
    @SuppressWarnings("unchecked")
    private static CapacityDemand capacityDemandOf(EffectType<?> definition, Effect effect) {
        return ((EffectType<Effect>) definition).capacityDemand(effect);
    }

    // 邻近物品总量：与实体产出掉落物同一判定（具体物品比注册表对象，标签比 TagKey），
    // 匹配器按引用编译一次后逐实体复用，不在查询谓词里重复创建 TagKey。
    // 计数口径与 countNearbyEntities 一致：谓词只筛选实体，收集条数由查询的 maxResults 限制，
    // 数量来自结果列表；物品按堆叠数逐条累加（空堆叠不命中，因此收满 limit 条时总量必然 >= limit，
    // 此时 room 已经归零，不会高估可开组数）。
    private int countNearbyItems(TaggedId reference, int limit, int radius) {
        AABB box = searchBox(radius);
        TaggedItemMatch target = TaggedItemMatch.compile(reference);
        List<ItemEntity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box,
                item -> item.isAlive() && !item.getItem().isEmpty() && target.matches(item.getItem()), nearby, limit);
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

    // 邻近实体总量：与 spawn_entity 执行器同一判定（支持具体类型与标签）
    private int countNearbyEntities(TaggedId reference, int limit, int radius) {
        AABB box = searchBox(radius);
        if (reference == null) {
            return 0;
        }
        if (!reference.tag()) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(reference.id())) {
                return 0;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(reference.id());
            List<Entity> nearby = new ArrayList<>();
            level.getEntities(type, box, Entity::isAlive, nearby, limit);
            return nearby.size();
        }
        List<Entity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(Entity.class), box,
                entity -> entity.isAlive() && matchesEntityType(reference, entity), nearby, limit);
        return nearby.size();
    }

    // 搜索盒与效果执行器同一口径：以固定触发点所在方块为中心、边长 2*radius+1 格
    private AABB searchBox(int radius) {
        return EffectTargets.blockBox(BlockPos.containing(triggerPosition), radius);
    }

    private static boolean matchesEntityType(TaggedId reference, Entity entity) {
        if (reference == null) {
            return false;
        }
        if (reference.tag()) {
            return entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, reference.id()));
        }
        return BuiltInRegistries.ENTITY_TYPE.containsKey(reference.id())
                && entity.getType() == BuiltInRegistries.ENTITY_TYPE.get(reference.id());
    }

    // 组内需求累加采用饱和值，不允许溢出后绕过阈值。
    static int saturatedAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
    }
}
