# 客户端 UI 代码审查索引

> 审查对象：`common/src/main/java/com/meteorite/itemdespawntowhat/client/**`，主范围 `client/ui/**`（`kit` / `theme` / `widget` / `screen` / `screen/form` / `prototype`），旁及 `client/edit`、`client/net`、`client/key` 中直接影响界面的部分。
> 审查方式：静态审查 + 脚本度量（依赖图、体量排序、字面量与引用计数、相似度比对），逐条结论回到源码定位。未编译、未运行游戏、未改动任何 Java 源码。
> 判定基线：[架构总览](../dev/architecture.md)、[客户端层](../dev/layers/client.md)、[UI kit 入口](../dev/internals/ui-kit/README.md)、[编辑器四页契约](../dev/internals/editor-pages-contract.md)、[编辑页卡片布局](../dev/internals/editor-card-layout.md)、[ADR-0022](../adr/0022-client-ui-kit-adoption.md)、[ADR-0025](../adr/0025-reusable-client-ui-kit-boundary.md)、[ADR-0028](../adr/0028-canvas-page-and-flat-skin.md)。

## 1. 产物索引

| 维度 | 产物 | 内容 |
| --- | --- | --- |
| 维度一：代码简洁性 | [01-conciseness.md](01-conciseness.md) | 重复逻辑、冗余与可合并代码、过长函数与嵌套、无用代码、魔法字符串/数字、参数过多与可提取公共方法 |
| 维度二：代码架构 | [02-architecture.md](02-architecture.md) | 分层与依赖方向、模块职责、状态管理、组件复用机制、耦合度、四个扩展点的改动清单、与现行文档不一致汇总 |
| 维度三：代码风格 | [03-style.md](03-style.md) | 命名规范、目录组织与文件结构、注释规范、样式组织方式、i18n 与团队约定一致性、可执行的收敛规则表 |
| 独立核验 | [verification.md](verification.md) | 对上述三维逐条回源核验（高优先级全量 + 中低抽样）、五要素完备性、相对链接、跨文件数字一致性、需修订清单 |

三维共记录 108 条问题（维度一 37、维度二 31、维度三 40），每条含**定位 / 问题 / 影响 / 建议 / 优先级**五要素。

## 2. 跨维度结论

三个维度各自独立取证，但指向五条共同主线。这些主线是本轮审查对「让 UI 架构更清晰、未来拓展更容易」最有价值的输出。

### 2.1 契约与实现漂移：文档说的方向与代码的实际依赖不一致

- 界面层直接依赖协议包 `core.network.protocol` 的 DTO，而承担隔离职责的工作区视图本身也输出这些类型（[A-01](02-architecture.md)、[H12](03-style.md)）。
- `client/edit` 反向依赖 `client/ui`（`EditSession` → `FormIssue`；树编解码实现 kit 接口），与「层内单向 net → edit → ui」相反（[A-02](02-architecture.md)）。
- `ui/screen` 与 `ui/screen/form` 双向依赖，并存在类级相互引用（[A-03](02-architecture.md)、[H8](03-style.md)）。
- 主题按文档应为「映射成样式对象后注入」，实际是全局静态调用；画布页与表单页各持一套主题（[H7](03-style.md)）。

**影响**：任何新增界面能力都可以各自解释「现状允许」，契约无法用于约束改动，扩展时只能跟随既有写法继续分叉。

### 2.2 扩展瓶颈集中在少数巨型类

`RuleEditorEditPages`、`RuleEditorScreen`、`FormControl`、`RuleEditorP4Panels`、`UiConditionGraphEditor`、`UiConditionTreeSummary` 等文件同时承担构建、布局、渲染、输入与业务装配。[02-architecture.md §7](02-architecture.md) 已把「新增字段控件 / 新增编辑页 / 新增条目类型预览 / 新增条件或效果类型」四个典型扩展的改动清单量化到具体类。

**影响**：常用扩展会命中 2000 行级文件的散弹式修改，是当前扩展成本的主要来源。

### 2.3 重复实现与闲置基础设施并存

- 同一换行排布算法在多个屏幕/表单类中各手写一份，而 kit 内已有的布局类在生产代码中零引用（[H1](01-conciseness.md)、[H6](01-conciseness.md)）。
- 同一层的两个滑块核心、两个区间条大段同构（[H2](01-conciseness.md)、[H3](01-conciseness.md)）。
- 两套条件树控件并存，结构操作语义各写一遍（[A-08](02-architecture.md)）。
- 同包内两套数值解析与两套数值格式化（[H8](01-conciseness.md)、[H9](01-conciseness.md)）。

**影响**：新增控件或修正交互时要判断「改哪一份」，且两份实现的行为已经出现漂移。

### 2.4 同一事实存在多个来源

草稿/会话访问前奏在页面类中重复出现（[H4](01-conciseness.md)）；目录建议绕过只读工作区视图直取全局单例（[A-10](02-architecture.md)）；客户端复制了服务端的结构校验子集（[A-09](02-architecture.md)）；消耗类效果标识在多个类各定义一份（[A-11](02-architecture.md)）；模组命名空间与本地化键前缀各有多个常量来源（[H3](03-style.md)、[H5](03-style.md)）。

**影响**：改动一处不足以生效，容易产生「改了一半」的不一致状态。

### 2.5 文档与注释失修，且已进入正式标识

界面注释中的文档指针全部失效；计划阶段码（P3/P4/P6）与任务码残留，其中一处已进入公开类名；注释存在「原实现 / 旧版 / 不再」式沿革叙述（[H1](03-style.md)、[H2](03-style.md)、[H9](03-style.md)、[A-30](02-architecture.md)）。

**影响**：维护者按注释找不到依据；阶段码进入类名后成为对外契约的一部分，迁移成本被动抬高。

## 3. 整改顺序建议

| 阶段 | 内容 | 依据 |
| --- | --- | --- |
| P0 · 先裁决（决定后续方向） | 裁定「界面层可否直接用协议只读 DTO」「`edit → ui` 与 `screen ↔ screen/form` 的方向」；决定 kit 中零引用类的去留；修复失效文档指针与阶段码 | [02 §8](02-architecture.md)、[H6](01-conciseness.md)、[H1](03-style.md)、[H2](03-style.md) |
| P1 · 结构性收敛（收益最大） | 拆分四个编辑页巨型类；条件树双实现收敛；抽取换行布局与滑块/区间条基类；统一会话与工作区访问入口；收敛命名空间与 i18n 前缀常量 | [02 §7](02-architecture.md)、[A-08](02-architecture.md)、[H1](01-conciseness.md)、[H2](01-conciseness.md)、[H3](01-conciseness.md)、[H4](01-conciseness.md)、[A-10](02-architecture.md)、[H3](03-style.md)、[H5](03-style.md) |
| P2 · 规范落地 | 按维度三的收敛规则表统一命名、目录、可见性、注释与样式；为 Gson 与协议 DTO 补成文约定；统一异常消息语言 | [03 §七](03-style.md)、[H7](03-style.md)、[H10](03-style.md)、[H12](03-style.md) |

## 4. 需要决策的问题（不宜由审查单方面裁定）

1. 「传输载荷类型」是否包含 `core.network.protocol` 的只读 DTO——决定 A-01 是改代码还是改文档。
2. `client/edit → client/ui` 的收敛方式：`FormIssue` 下沉，还是把 kit 明确登记为可被 edit 依赖的公共边界。
3. `ui/screen` 与 `ui/screen/form` 是否承认同层子包双向，还是抽中立子包承载共享类型。
4. kit 中零引用类（浮动面板、线性布局、导航历史、装配示例）是接线使用、删除，还是确认为对外公开 API。
5. 两套条件树控件是否合并为同一控件的两种渲染模式。
6. 主题是否改为宿主注入（与 [client.md](../dev/layers/client.md) 的表述对齐），以及迁移范围。
7. 界面层 Gson 用法是否下沉到 `client/edit`。
8. 审查类文档是否同样适用「符号定位不写行号」的项目约定。

## 5. 核验与限制

- 三维结论已由独立核验逐条回源：[verification.md](verification.md) 覆盖全部高优先级条目（34 条）与近半数中低优先级（45 条）；核验发现的问题已全部修订，复核后 79 条均为核实，跨文件共有事实 6 组无残留矛盾。
- **已知限制**：全部结论来自静态审查（源码文本、依赖与度量），未编译、未运行游戏，不覆盖反射、资源注册与构建期生成；渲染与交互时序的正确性、视觉规格、i18n 译文质量、运行时性能不在本轮范围。
- **计数口径**：文件数按 `.java` 计数；行数按原始行数（文件末尾换行不计）；引用计数区分「import 条数」与「字符串出现次数」。
- 本轮只产出审查文档，未修改任何 Java 源码与 `docs/dev`、`docs/adr` 文档。
