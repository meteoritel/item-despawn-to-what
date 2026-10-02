# ConversionType 由封闭枚举改为统一 ResourceLocation 注册表

> 历史记录：本文绑定的旧实现已退役；当前后端契约与前端占位决定以 [ADR-0017](0017-backend-cutover-and-budgeted-effects.md) 和 [当前架构](../dev/architecture.md) 为准。

## 背景
为支持第三方模组注册新的物品转化类型，`ConfigType` 不能继续作为封闭 enum 存在——enum 常量无法在运行时由第三方追加。上一轮重构虽已为 `ConfigType` 增加稳定 `ResourceLocation` id，但身份仍是枚举，id 未作为注册键使用。

## 决策
将 `ConfigType` 重构为以 `ResourceLocation` 为键的统一注册对象（持有 id + 反向引用其类型定义）；5 个内置类型（item→item / mob / block / xp_orb / world_effect）在启动时预注册为公开常量。`ConfigType.values()` 等遍历改为 `registry.all()`。第三方在自身初始化阶段调用 `registry.register(...)`。内置与第三方走同一条元数据路径，不分层。

## 考虑过的替代方案
- **两层（保留 enum + 独立第三方注册表 + 统一 Handle 接口）**：当下迁移量更小，内置类型零改动；但引入永久双层抽象，内置与第三方走不同元数据路径，长期维护成本更高。否决。

## 后果
- 内置类型的常量引用（各 config 构造器、EditScreen、2 个客户端注册表、`ConfigTypeSelectionScreen`）迁移为引用预注册常量/实例，机械改动。
- `ConfigService` 的 `reloadType` / `removeByInternalId` 中的 `values()` 遍历改为 `registry.all()`。
- 一旦第三方依赖此注册表形状，身份模型即被锁定，后续难反转。
