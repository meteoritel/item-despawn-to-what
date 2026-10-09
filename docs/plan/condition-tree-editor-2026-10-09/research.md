# 条件树编辑页面：规划调研资料

> 调研日期：2026-10-09。本文记录已核对的一手开源资料和算法参考；已确认范围、交互行为与验收以 [处理规划](plan.md) 为准。本文不是当前实现说明。未接入依赖、未移植代码、未运行浏览器交互或性能基准。

## 1. 项目约束与调研范围

已确认的目标是先处理编辑可靠性和信息展示，再重构条件树的交互与可视化层，并同步扩展 Kit API。现有 JSON 和条件运行语义保持；客户端不认识的第三方条件叶保留原内容、参数只读，其他节点仍能编辑。

[ADR-0018](../../adr/0018-condition-tree-contract.md) 定义四类节点 `all_of`、`any_of`、`inverted`、`leaf`，以及每表达式深度 16、节点 256、叶 128 的限额。[ADR-0025](../../adr/0025-reusable-client-ui-kit-boundary.md) 与 [Kit API](../../dev/internals/ui-kit-api.md) 要求 Kit 不依赖 `core`、`client.edit`、Gson 或 loader。规则含义、JSON 和权限由宿主适配。

本次比较对象是 Web 开源树交互的设计参考。React、DOM、CSS 和浏览器拖拽不能直接用于 Minecraft Java 原生 GUI；比较结论针对交互、状态组织与算法，不意味着引入其运行时。

## 2. 已核对的开源事实

### 2.1 React Arborist：完整的受控缩进树

[官方仓库 README](https://github.com/jameskerr/react-arborist) 提供折叠、键盘导航、虚拟化、自定义行与拖拽预览、多选接口，以及受控/非受控两种模式。受控数据使用 `data`；创建、改名、移动、删除由宿主处理回调。`onMove` 携带 `dragIds`、`parentId`、`index`，`onDelete` 接受多个 ID；`disableDrop` 可根据目标父节点、被拖节点与位置阻止落点。

值得单独保留的细节是同父重排：README 明确 `onMove.index` 指删除前的可见插槽，移除前方被拖节点后需要调整插入索引，并提供 `adjustMoveIndex`。只处理单节点上移/下移，无法替代这个批量移动契约。显示层可分别替换行、拖影和插入线；节点接口区分选中与聚焦。[受控移动与索引说明](https://github.com/jameskerr/react-arborist#control-the-tree-data)、[公开接口](https://github.com/jameskerr/react-arborist#api-reference)。

维护适配判断：它适合参考文件树式行交互与紧凑公开接口；其网页布局、React 状态和拖拽 backend 需要在 Java 中重新实现。官方资料列出虚拟化能力，但不能据此得出本项目的帧率或分配量结论。仓库有独立 README、接口说明与许可证，本文未据星数推断维护质量。[官方仓库](https://github.com/jameskerr/react-arborist)。

### 2.2 Headless Tree：数据、交互状态与渲染分离

官方将 Headless Tree 定位为 React Complex Tree 的后继；核心可通过 `createTree` 独立于 React 使用。树读取通过 `getItem(id)` 与 `getChildren(id)` 接入任意载荷，渲染使用扁平节点列表和层级值；多选、拖拽、快捷键等按 feature 装配，而非要求一个固定页面。[官方首页](https://headless-tree.lukasbach.com/)、[入门与数据装配](https://headless-tree.lukasbach.com/getstarted/)。

状态文档明确：选中、展开、拖拽候选位置属于树的显示/交互状态；树数据和落下后的变更属于外部数据源。可全部受控，也可分别控制状态子集。外部数据变更后需要在数据源已更新时重建投影，避免用旧数据刷新视图。[状态指南](https://headless-tree.lukasbach.com/guides/state/)、[外部变更回填](https://headless-tree.lukasbach.com/recipe/external-state-updates/)。

拖拽目标区分「成为某节点的子节点」和「插入指定父节点的子序列」。有序目标区分视觉 `childIndex` 与删除后 `insertionIndex`；行间插入线使用独立位置/层级信息。`canDrag`、`canDrop` 是可配置门禁，非法落点不会触发 `onDrop`，并有禁止视觉反馈；可以设置独立拖拽手柄。[拖拽目标契约](https://headless-tree.lukasbach.com/dnd/overview/)、[限制与手柄](https://headless-tree.lukasbach.com/dnd/customizability/)。

多选支持 Ctrl/Shift 点选，与拖拽组合可移动多个节点；键盘拖拽独立于普通聚焦导航，拥有开始、移动、完成、取消过程。快捷键文档包含是否允许输入框聚焦时触发的选项。[多选](https://headless-tree.lukasbach.com/features/selection/)、[键盘拖拽](https://headless-tree.lukasbach.com/features/kdnd/)、[快捷键](https://headless-tree.lukasbach.com/guides/hotkeys/)。

性能方面，核心输出扁平列表，虚拟化由外部装配，并不是组件内置特性。官方演示使用另外的虚拟列表；本次未复现其规模声明。[虚拟化指南](https://headless-tree.lukasbach.com/recipe/virtualization/)。维护适配判断：它适合借鉴 Kit 的通用交互核心与宿主分工，但无需把异步数据加载、所有插件或 Web 可访问性属性整体移植。官方入门页仍标记 Beta，本项目不据此依赖其接口稳定性。[入门](https://headless-tree.lukasbach.com/getstarted/)。

### 2.3 dnd-kit 官方树示例：扁平投影与拖动预览

已核对的是官方仓库 **`master` 分支中的历史 React 树示例**。`utilities.ts` 将树投影为含 ID、父 ID、深度、同级索引的序列；拖动水平偏移计算候选深度，并用邻接行给出的上下界限制深度，随后推导父节点；也提供从序列重建树、查找和删除函数。[官方示例算法](https://github.com/clauderic/dnd-kit/blob/master/stories/3%20-%20Examples/Tree/utilities.ts)。

`SortableTree.tsx` 根据折叠状态与当前被拖子树过滤展示序列，拖动过程中只更新 active/over/水平偏移和候选位置；结束时应用树变更，取消则重置拖拽状态。示例有指针和键盘 sensor、拖影、删除和折叠，代码中的 active ID 为单个节点；它不是已经完成多选批量移动、条件叶表单与业务校验的编辑器。[官方树示例](https://github.com/clauderic/dnd-kit/blob/master/stories/3%20-%20Examples/Tree/SortableTree.tsx)。

必须区分版本口径：目前仓库 `main` 的 README 描述抽象核心、DOM 层及多个框架适配，不能把 `master` 示例中的 `@dnd-kit/core`/`@dnd-kit/sortable` 当成当前 `main` 的统一 API。调研借鉴的是可读的树投影算法；若未来复制或改写源码，应固定所选提交并核对其许可证。[当前主分支](https://github.com/clauderic/dnd-kit)。

维护适配判断：示例粒度最适合实现原生 GUI 的候选落点与拖影；它的父节点算法不知道条件节点容量，也没有项目所需的运行语义与第三方叶保留契约，不能照搬。示例遍历、重建和绘制全部行，不是本项目已验证的优化实现。[算法源码](https://github.com/clauderic/dnd-kit/blob/master/stories/3%20-%20Examples/Tree/utilities.ts)、[视图源码](https://github.com/clauderic/dnd-kit/blob/master/stories/3%20-%20Examples/Tree/SortableTree.tsx)。

### 2.4 React Query Builder：条件编辑与即时诊断补充参考

该项目比文件树更接近条件编辑：支持嵌套规则组、AND/OR、组级取反、规则/组复制和独立参数控件；`query` 与 `onQueryChange` 支持受控数据。它的规则组格式与本项目四种节点格式不同，仅适合参考表单和反馈呈现。[组件接口](https://react-querybuilder.js.org/docs/components/querybuilder)。

校验器可以返回按规则/组 ID 索引的结果与原因；官方示例将空组保留在编辑页面并展示错误，避免以校验失败为理由丢失编辑态。拖拽可以通过 `canDrop` 做门禁，并区分移动、复制、组成新组。[校验与空组反馈](https://react-querybuilder.js.org/docs/utils/validation)、[拖拽契约](https://react-querybuilder.js.org/docs/dnd)。

维护适配判断：可参考参数编辑、组标题和诊断；通用查询语言转换、SQL/数据库功能与本任务无关，无需引入。组级 `not` 不能替代本项目独立的 `inverted` 节点，也不能用它的默认删除/解组行为改变本项目业务语义。[官方仓库](https://github.com/react-querybuilder/react-querybuilder)、[组件接口](https://react-querybuilder.js.org/docs/components/querybuilder)。

## 3. 简洁比较

下表是根据上述资料对本项目的适配判断；能力事实分别由各节来源支持。

| 维度 | React Arborist | Headless Tree | dnd-kit 历史树示例 | React Query Builder |
| --- | --- | --- | --- | --- |
| 最适合借鉴 | 行界面、公开操作回调 | Kit 核心和宿主分工 | 候选深度、父节点、拖影算法 | 条件表单和即时诊断 |
| 缩进与折叠 | 完整组件 | 由扁平投影自定义渲染 | 示例已实现 | 嵌套规则组呈现 |
| 多选批量移动 | 支持多 ID 回调 | selection 与 drag feature 组合 | 单 active ID，需补充 | 本次未确认通用多选能力 |
| 跨层落点限制 | `disableDrop` | `canDrop` 与显式目标 | 邻接深度限制，需业务门禁 | `canDrop` 与组规则 |
| 受控回填 | 宿主处理修改回调 | 数据外置，交互状态可控 | 示例内部状态 | query/change 回调 |
| 键盘 | 导航与选中 API | 导航、多选、键盘拖拽 | 键盘 sensor | 本次未确认树式键盘移动 |
| 性能参考 | 内置虚拟化 | 外部虚拟化可装配 | 扁平遍历/重建 | 本次未做性能评估 |
| 原生 Java 接入 | 重写交互 | 重写核心与视图 | 重写算法 | 重写局部表单与反馈 |

## 4. 参考组合与实现候选

已确认的页面与行为见 [处理规划](plan.md)：大屏叠加编辑页、缩进树与参数面板、当前条件的只读 JSON 切换、Kit 通用操作与宿主编解码、空组和空 NOT 草稿、未知叶保留、非法落点拒绝及一次操作一次配置撤销。以下为实现机制参考，不代表选定具体依赖或已落地接口。

1. **组合参考**：借鉴 Headless Tree 的数据、交互状态与渲染分离，Arborist 的受控操作回调，dnd-kit 历史示例的扁平行投影和候选深度，以及 Query Builder 的空组提示与节点诊断。具体算法与源码移植尚未选定。
2. **节点身份候选**：以编辑期稳定 ID 管理展开、聚焦和选中状态，让状态跟随节点移动；编辑 ID 不进入现有规则 JSON。公开类型与身份维护方式应结合真实调用设计。
3. **落点算法候选**：目标显式包含父节点、插入位置和意图；明确索引是删除前插槽还是删除后位置。预览与最终命令使用相同的合法性判断，避免落点看似可用却在提交时改变行为。
4. **批量算法候选**：先消除祖先与后代的重复选择，再按原树顺序处理；计算变更后完整树的深度与节点数量，不只计算拖动根。父子共同选中的细节和同父重排索引应通过人工场景核对。
5. **诊断实现候选**：完整树分析与可见行布局分别计算，折叠节点可汇总后代问题；叶参数表单只修改本叶参数，未知叶载荷不经内置类型重新编码。
6. **性能候选**：现有每表达式最多 256 节点，可先按数据变化重建结构投影，滚动时只绘制视口行；拖动位置更新只计算候选与反馈，不在每帧编解码 JSON。是否引入复杂增量索引或虚拟化应依据原生 GUI 验证结果，不直接套用 Web 的十万节点目标。

## 5. 许可与来源记录

已读取四个项目的正式许可证，均为 MIT。它们的许可要求在软件副本或实质部分保留版权和许可声明；若移植/翻译具体实现，应核对选定提交与涉及文件并记录来源。仅借鉴界面思想与直接复制源码的交付处理不同，本文不对具体移植作法律结论。[Arborist LICENSE](https://raw.githubusercontent.com/jameskerr/react-arborist/main/LICENSE)、[Headless Tree LICENSE](https://raw.githubusercontent.com/lukasbach/headless-tree/main/LICENSE)、[dnd-kit master LICENSE](https://raw.githubusercontent.com/clauderic/dnd-kit/master/LICENSE)、[Query Builder LICENSE](https://raw.githubusercontent.com/react-querybuilder/react-querybuilder/main/LICENSE.md)。

本项目维护副本的来源与差异记录遵循 [ADR-0025](../../adr/0025-reusable-client-ui-kit-boundary.md) 和 [NOTICE-kit.md](../../../NOTICE-kit.md)。此调研没有复制源码，因此未更改 NOTICE。

## 6. 已知限制与待验证点

- 本次核对 README、官方指南、示例源码及许可证，未安装这些库或执行网页示例；截图/交互观感与原生 GUI 用户体验未验证。
- 链接指向检索时的主分支/文档，后续可能更新；正式移植须固定提交。dnd-kit 的历史示例与现行主分支已明确区分。
- 各库未自带本项目四态求值、单子项 NOT、服务器授权或草稿保存规则；这些规则继续来自既有业务层，不由 UI 方案重新定义。
- 已确认的实时预览限于结构、摘要、JSON、诊断与拖拽落点；不包含世界上下文下的求值结果。
- 人工验收范围以 [处理规划](plan.md) 为准。实现算法的补充核对样例包括同父向下重排无偏移、跨父多选移动保序、父子共同选中不重复处理；具体操作步骤在实施阶段依据实际界面整理。
