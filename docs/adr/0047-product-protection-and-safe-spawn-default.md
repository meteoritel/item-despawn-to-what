# ADR-0047：产物环境伤害保护覆盖全部产物实体，安全生成默认开启

- 状态：已实施。

## 背景

实体产物原先只有掉落物拿到新产物环境伤害保护：NeoForge 端 `EntityInvulnerabilityCheckEvent` 处理器按 `ItemEntity` 过滤，Fabric 端注入 `ItemEntity#hurt`，生成入口也只有 `EffectTargets.addConversionProductNow(ItemEntity)` 授予状态。结果是在岩浆里转化出的生物在生成当刻就被打死：岩浆每刻造成 4.0 伤害，鸡只有 4 点生命；账本仍记为已完成，源成本照常扣除。安全生成默认关闭时落点就是源物品所在的岩浆格，这条路径无法回避。

## 决策

1. 保护按伤害入口统一：两端都在原版 `Entity#isInvulnerableTo` 这一道门判定（NeoForge 的 `EntityInvulnerabilityCheckEvent` 就挂在这里），不再按实体类过滤。`ItemEntity#hurt` 与 `LivingEntity#hurt` 都以这道门开头，掉落物与生物因此共用一套判定。
2. 状态存放分两类：掉落物继续写平台持久化状态（要跨存档、跨合并，与返还物的永久保护共用同一份数据）；生物与其他实体只按实体 UUID 在内存记到期刻，2 秒的保护期不需要持久化，也不给这类实体写任何实体数据。
3. 授予入口收敛到 `DropStateStore.grantNewProduct(Entity)`：掉落物分支写临时保护与转化冷却，其它实体分支只记内存保护。生成侧新增 `EffectTargets.addEntityProductNow`，`SpawnEntityExecutor` 的实体写入统一走它。
4. `safe_spawn` 默认值改为 true：`OutcomeCandidate.DEFAULT_SAFE_SPAWN` 与编解码默认值同步，编辑器的勾选框默认勾选；没写这个字段的既有规则按开启生效。

## 考虑过的替代方案

- 在 `EffectTargets.addEntityNow` 上加保护：所有实体写入都带保护，会连返还交付与其它立即生成的实体一起纳入，语义超出「转化产物」。
- 生物也写持久化状态：2 秒的保护跨存档没有意义，却要给每个产物实体写一份实体数据，还多一条要维护的存档分支。
- 只把 `safe_spawn` 默认改 true 而不扩保护：安全搜索能避开岩浆，但关闭安全生成的规则仍然一生成即死，缺陷还在。

## 后果

岩浆、火、仙人掌旁转化出的生物与其它实体在保护期内不会被环境伤害销毁；落到危险地形的产物在保护结束后仍会受伤，与原版行为一致。生物的保护只活在同一进程内，实体卸载或服务器重启即失效。`safe_spawn` 改默认会让没写该字段的既有规则开始挑落点，产出位置从源物品所在格变为附近可站立的格子；要维持原点行为需要在规则里显式写 `false`。