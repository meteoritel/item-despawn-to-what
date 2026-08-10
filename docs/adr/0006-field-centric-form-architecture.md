# 客户端编辑表单统一为字段中心 FormField + FormRenderer 架构

## 背景
ADR-0003 确立"声明式 schema + 手写逃逸"的混合脚手架，已落地：3 个简单类型走 `ConfigFieldSchema`，2 个复杂类型手写 `ConfigFormSection`。但两条路径在横切关注点（校验 / 焦点 / 建议 / 条件可见性）上各自维护一套机制：

- 9 个通用字段由 `BaseConfigEditScreen`（716 行）直接管理；
- 手写 section 通过 `ConfigFormContext` 反向委托回 screen 注册校验 / 建议 / 焦点；
- schema section（`SchemaConfigFormSection`）自带 visibility 跟踪 + `rebuildConditional`；
- `result_id` 被特例化（`showsResultId` 标志、构造器注入校验 / 建议，未统一进字段描述）。

后果：新增任何特性（逐字段校验提示、新控件类型、新交互）须在多处同步修改；第三方扩展面对两套不一致的原语。且 schema 仅支持 EditBox、无 enum 选择器、无联动清空，致使 `item_to_block` / `item_to_world_effect` 被迫手写。

## 决策
引入字段中心统一抽象，两条路径共享同一组原语：

1. **`FormField<T>`**：统一字段原语，持有 key / 标签 / 输入控件（`FormFieldInput`，封装 EditBox / CycleButton / 复合组件）/ getter+setter / 校验器（返回错误消息而非布尔）/ 建议提供器 / 可见性（可依赖其他字段值）/ 隐藏即清空标志（条件联动）。
2. **`FormDefinition<T>`**：一组 `FormField` 的有序集合；`result_id` 降级为普通字段，取消特例。
3. **`FormRenderer`**：统一负责布局、焦点遍历（含复合组件内部焦点）、逐字段校验提示、建议浮层、条件可见性重建。`BaseConfigEditScreen` 降为瘦壳，仅持有 `FormDefinition` + `FormRenderer` + 会话 / 列表 / 按钮。
4. **两条路径合一**：`ConfigFieldSchema` 自动生成 `FormDefinition`（新增 ENUM 控件类型 + 联动清空）；手写类型用同一套 `FormField` 原语构建 `FormDefinition`（复合组件实现 `FormFieldInput`）。
5. **`item_to_block` 迁至 schema**（enum + 联动清空已可表达其"启用物品方块"切换）；`item_to_world_effect` 保持手写，但产出 `FormDefinition`。

## 考虑过的替代方案
- **区段中心精炼（保留 `FormSection` 为单元，抽取作用于 section 的 `FormRenderer`）**：迁移量较小，但 schema 与手写仍是两种构造方式，构造期重复留存，新特性仍可能要两处同步。否决。
- **双路径仅清理（保留 schema / 手写独立，只抽共享渲染逻辑）**：改动最小，但未消除根本重复，扩展性增益有限，与"方便未来增加特性"目标不符。否决。

## 后果
- 第三方编辑表单 SPI 形状锁定：实现新类型 = 提供一组 `FormField`（声明式 schema 或手写）+ 注册到 `ClientConversionTypeDefinition`。一旦第三方依赖此形状即难反转。
- 现有 `ConfigFormSection` / `ConfigFormContext` / `SchemaConfigFormSection` 重构或退役；9 个通用字段与 `result_id` 迁移为 `FormField`；`BaseConfigEditScreen` 显著瘦身。
- 逐字段校验提示、enum 选择器、联动清空等特性在 `FormRenderer` / `FormField` 一处实现即覆盖全部类型（含未来新类型）。
- presenter / tooltip 迁入 `ClientConversionTypeDefinition`（与 `formSectionFactory` 同模式），`ClientConversionTypeRegistry` 回归纯注册中心。
- 迁移量较大，需分阶段进行并保持每阶段可编译。
