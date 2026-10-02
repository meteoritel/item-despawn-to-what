# 服务端追踪集驱动的转化入口与内存状态

> 历史记录：本文绑定的旧实现已退役；当前后端契约与前端占位决定以 [ADR-0017](0017-backend-cutover-and-budgeted-effects.md) 和 [当前架构](../dev/architecture.md) 为准。

核心玩法的转化检查入口从“两平台分叉（Fabric 在 `ItemEntity.tick` 注入 mixin、NeoForge 每 20 tick 全量扫描所有掉落物）+ 状态持久化进实体 NBT”改为：服务端按维度维护一个只含被追踪掉落物的集合，每 20 tick 只遍历该集合（O(T) 而非 O(N)）；追踪状态（计时器 / 选中规则 / 锁）存服务端内存 Map，不持久化。

## Considered Options

- **自身 tick（Fabric 现状 mixin）**：搭 vanilla 遍历便车、零额外扫描，但对每个掉落物每 tick 触发（含未追踪、含客户端），未追踪物品每检查周期被摸 20 次；mixin 作用于类、客户端也付开销。
- **世界集合全量扫描（NeoForge 现状）**：每 20 tick O(N) 扫描，未追踪物品摸 1 次，但仍重扫全部去找被追踪的。
- **显式追踪集（采用）**：O(T)，未追踪物品零触摸，去掉 Fabric mixin，客户端零开销。

物品很多时，自身 tick 的 20 倍触摸 + 客户端开销压过其“零扫描”优点；世界集合胜自身 tick 但仍重扫未追踪物品；追踪集在所有规模下不劣于另两者。状态不持久化：转换锁本就瞬态、不应进存档，计时器跨重启归零可接受（物品重载后重新评估），合并 / restore 用 UUID 键自然处理，比当前显式 merge 状态拷贝更简单。

## Consequences

- 需在物品消失（despawn / 合并 / 拾取 / 区块卸载）时从追踪集移除以避免泄漏：遍历时 lazy 检查 `isAlive()` + 物品 discard/remove 钩子。
- 跨重启不保留转化进度（计时器归零）。
- 不再需要 Fabric `ItemEntityMixin` 的状态字段与 merge/restore 拷贝逻辑。
- 客户端反馈所需的追踪状态需另行同步（见后续决策）。
