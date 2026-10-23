# 更新日志 Change log

## [2.0.0]（开发中）
### 环境与依赖
- **NeoForge 版本口径**：`gradle.properties` 的 `neoforge_version=21.1.218` 是构建依赖版本；`neoforge.mods.toml` 的 `[21.1.210,)` 是运行依赖的声明区间，下界为 21.1.210。声明区间不代表其中所有版本均经过运行验证。

### 新增
- **统一规则链路**：后端改为 `core/**` 单一规则链路——规则、有序效果与递归条件树经 DFU Codec 编解码，并按扁平 `type` 字段分发；规则来源分三层（内置数据包 / 世界数据包 / `config` 覆盖层），不同 id 叠加、同 id 按优先级覆盖合并。
- **服务端配置**：模组级配置为 `config/itemdespawntowhat/server.json`，覆盖检查周期与退避、每 tick 工作量与预算、覆盖层目录、调试开关、新产物保护与转化冷却等参数。
- **图形化规则编辑器**：`/idtw config edit` 在取得全局独占编辑会话与目标级编辑锁后打开，支持浏览 / 新建 / 复制 / 编辑 / 停用 / 删除 / 恢复规则、条件树画布页、按来源目录选 ID、草稿持久化与撤销重做。
- **触发与结算**：新增消失方式触发（`triggers` 的 `natural` / `fire` / `lava` / `cactus`）；命中后按固定源成本整组结算，可用 `outcomes` 配合 `combination`（`round_robin` / `priority`）声明候选结果。
- **运行时**：所有维度共享的公共 tick 预算调度器；催化剂门槛在运行投影中派生；实体生成统一为一种按 `variant`（`item` / `entity` / `experience`）分发的效果。
- **命令**：`/idtw config reload|validate|list|edit|edit-lock` 与 `/idtw rule list|show`。
- **产物汇总交付**：直接物品产物按候选结果、槽位与物品汇总装箱，同一槽位的多组产出合并交付并保留最晚的原定执行时刻，大批量产出不会逐组生成实体。

### 移除
- **旧配置链路退役**：按转化类型划分的旧配置模型与执行器、NeoForge 服务端 TOML 配置（`ModConfigValues` / `itemdespawntowhat-server.toml`）、旧网络同步与旧客户端编辑界面，由统一规则链路与规则编辑器取代。
- **编辑器快捷键退役**：移除客户端按键绑定与快捷键处理，编辑器只保留 `/idtw config edit` 指令入口。

### New Features
- **Unified rule pipeline**: the backend now runs a single `core/**` rule path — rules, ordered effects and a recursive condition tree encoded through DFU codecs with flat `type` dispatch. Rules come from three layers (builtin datapack / world datapack / `config` overlay) and merge by id with priority.
- **Server config**: module-level configuration lives in `config/itemdespawntowhat/server.json`, covering check interval and backoff, per-tick work and budgets, the overlay directory, debug switches, new-product protection and conversion cooldown.
- **Graphical rule editor**: `/idtw config edit` takes a global exclusive edit session and a per-target lock, then offers browse / create / copy / edit / disable / delete / restore, a condition-tree canvas page, catalog-based id picking, draft persistence and undo/redo.
- **Triggers and settlement**: new disappearance-kind triggers (`natural` / `fire` / `lava` / `cactus`); a hit settles a complete group at a fixed source cost, with `outcomes` plus `combination` (`round_robin` / `priority`) for candidate results.
- **Runtime**: a shared server tick budget scheduler across all dimensions; catalyst thresholds derived in a runtime projection; entity spawn unified into one effect dispatched by `variant` (`item` / `entity` / `experience`).
- **Commands**: `/idtw config reload|validate|list|edit|edit-lock` and `/idtw rule list|show`.
- **Batched product delivery**: direct item products are boxed by candidate, slot and item, so groups that share a slot deliver together at the latest scheduled time instead of spawning one entity per group.

### Removals
- **Legacy config path retired**: the per-conversion-type config model and executors, the NeoForge server TOML config (`ModConfigValues` / `itemdespawntowhat-server.toml`), the legacy network sync and the legacy client editor UI, replaced by the unified rule pipeline and the rule editor.
- **Editor keybinding retired**: the client key mapping and shortcut handling are gone; `/idtw config edit` is the only way to open the editor.

## [1.2.1]

### 变化
- GUI优化：完善文本框校验警告红框，优化配置列表的显示
- 进行1.21.1 fabric移植

## [1.2.0]

### 新增
- **转化为世界事件**：添加了箭雨、爆炸、闪电、天气调整等世界效果
- **转化为经验球**：现在可以将物品转化为经验球了
- **标签支持**：`itemId` 字段现在支持使用物品标签，催化剂物品现在支持使用标签
- **TOMl 全局配置**：新增服务端 TOML 配置文件，目前可调整以下参数：
  - 闪电每次之间的间隔 tick 数
  - 爆炸每次之间的间隔 tick 数
  - 箭矢每支之间的间隔 tick 数
  - 方块放置任务每圈之间的间隔 tick 数
- **GUI 新增**：为非法输入框添加了红框高亮提示，为按钮添加了提示信息，优化了GUI排版
- **超长 JSON 分包**：添加了发包保存 JSON 时超长字符串的分包策略

### 变化
- 移除了游戏主页面按钮入口，现在必须通过 `/conversion_config edit` 命令或快捷键（仅单人模式）打开 GUI。为了避免服务端未启动造成的各种问题。
- 现在把 `转化为实体` 修改为 `转化为生物` ，只支持继承自`Mob类`的生物
- 现在支持 `n * 起始物品 -> m * 结果` 的转化了。
- 方块放置现在支持切换放置形状（平面圆形、方形，立体球形、正方体）

### 修复
- 修复多人服务器上的一系列bug

### New Features
- **Convert to World effect**：Added world effects such as arrow rain, explosions, lightning, weather adjustments, etc.
- **Convert to Experience Orb**：Items can now be converted into experience orbs.
- **Tag Support**：`itemId` field now supports using item tags, and catalyst items now support tags as well.
- **TOML Global Configuration**：Added a server-side TOML configuration file. The following parameters can now be adjusted:
  - Tick interval between each lightning strike
  - Tick interval between each explosion
  - Tick interval between each arrow
  - Tick interval between each loop of block placement tasks
- **GUI Additions**：Added red border highlighting for invalid input fields, added tooltips to buttons, and optimized the GUI layout.
- **Oversized JSON Packet Splitting**：Added a packet splitting strategy for saving oversized JSON strings when sending packets.

### Changes
- Removed the button entry on the main game page.The GUI must now be opened via the `/conversion_config` edit command or a shortcut key (singleplayer only), to avoid various issues caused by the server not being started.
- Renamed `Convert to Entity` to `Convert to Mob`, now only supports mobs that inherit from the Mob class.
- Conversions of the form `n * starting item -> m * result` are now supported.
- Block placement now supports switching placement shapes (circles, squares; spheres, cubes).

### Fixes
- Fixed a series of bugs on multiplayer servers.

## [1.1.1]

### 新增
- 新增 **辅助物品（Catalyst Item）** 与 **浸润流体（Inner Fluid）** 两个配置字段，现在可以实现更多带条件的世界转化。
- 为 GUI 中的多个字段增加了自动补全功能，现在 **维度（Dimension）** 与 **标签（Tag）** 也支持自动补全。
- 将游戏内打开 GUI 的快捷键默认设置为 **未绑定**。由于该功能在游戏中使用频率较低，此调整可以避免误触或按键冲突。您仍然可以通过指令 `/conversion_config edit` 打开配置编辑 GUI。
- 优化了整体性能。

> 注意：维度和标签支持通过数据包进行扩展。在服务端启动之前，系统无法获取完整的数据用于自动补全。如果您需要使用完整的补全功能，请在进入世界后再进行编辑。编辑完成后，请不要忘记使用 `/conversion_config reload` 重新加载规则，否则新配置不会生效。

### 修复
- 修复了在多个维度同时进行转化时无法正常执行的 Bug。
- 修复数字文本框无法输入负数的 Bug。
- 修复实体上限不正确的 Bug

### Added
- Added two new configuration fields: **Catalyst Item** and **Inner Fluid**, enabling conditional world conversions.
- Added auto-completion for more fields in the GUI. **Dimensions** and **Tags** are now also supported in the auto-complete system.
- The in-game keybinding for opening the GUI is now **unbound by default**. Since this feature is rarely used during gameplay, this change helps prevent accidental triggers or key conflicts. You can still open the configuration editor GUI using the command `/conversion_config edit`.
- Improved overall performance.

> Note: Dimensions and tags can be extended via **datapacks**. Before the server starts, the game cannot retrieve the full set of data required for auto-completion. If you want to use the complete auto-completion feature, please edit the configuration after entering a world. After editing, remember to run `/conversion_config reload` to apply the new rules.

### Fixed
- Fixed the bug where conversions failed to run correctly when multiple dimensions were being processed simultaneously.
- Fixed the bug where negative numbers couldn't be entered in the numeric text box.
- Fixed the bug where the entity upper limit was incorrect.

## [1.1.0]
### 新增 
- **优化 GUI**：改善了上一版本较为简陋的图形界面。在注册名 (物品、方块、实体ID)输入框中输入内容时，将自动显示建议下拉列表，方便快速选择。
- **配置管理 GUI**：现在可以在图形界面内直接**新增、删除或编辑配置**，无需手动修改配置文件，对新手更加友好。
- **支持六面方块标签 (Block Tags)**：所有六面方块现在可以使用标签。
- **新增“物品对应方块 (Item Corresponding Block)”选项**：
  - 在 `item_to_block.json` 配置中启用后，当源物品为方块物品时，将自动使用其对应方块。
  - 无需手动填写目标方块的注册名，提高使用便捷性。
- **命令更新**：
  - `/conversion_config reload`：重新加载所有配置文件。
  - `/conversion_config edit`：打开配置编辑 GUI。

### New Features

- **Improved GUI**: The previously simplistic graphical interface has been enhanced. When entering text in the "Registry Name" input field, a suggestion dropdown will now appear for quick selection.
- **Existing Configuration Management GUI**: Configurations can now be **created, deleted, and edited directly in the GUI**, eliminating the need to manually edit configuration files.
- **Support for Six-Sided Block Tags**: All six-faced blocks now support block tags.
- **New `block_of_item` Option**:
  - When enabled in `item_to_block.json`, if the source item is a block item, its corresponding block will be automatically used.
  - No need to manually enter the target block's registry name, improving usability.
- **Command Updates**:
  - `/conversion_config reload` – Reload all configuration files.
  - `/conversion_config edit` – Open the configuration editor GUI.

## [1.0.4] 
### 新增 New Features
- 新增了可视化编辑配置文件的GUI，可以在游戏开始页面右上角找到入口
- 在单人模式下，可以使用**insert**键打开编辑GUI，多人模式下只有op玩家可以使用指令 `/editConversionConfigs`
打开GUI进行编辑（此功能测试中，有bug还请反馈）
- 游戏内使用GUI编辑后，使用指令 `/reloadConversionConfigs` 重新加载新规则


- Added a GUI for visually editing configuration files. You can find the entry point in the top-right corner of the game’s main menu.
- In single-player mode, the editor GUI can be opened using the **Insert** key. In multiplayer mode, only OP players can open the editor GUI using the `/editConversionConfigs` command.  
  (This feature is still under testing; please report any bugs you encounter.)
- After editing configurations in-game via the GUI, use the `/reloadConversionConfigs` command to reload the new rules.

### 下一步计划 Next Plans
- 添加GUI内文本框输入预览功能
- itemId 条目支持列表输入


- Add input preview functionality for text fields within the GUI.
- Support list-based input for `itemId` entries.

