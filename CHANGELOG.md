# 更新日志 Change log

## [2.0.0]

### 破坏性更新

**2.0.0 是一次破坏性更新。** 模组经过大规模重写，1.x 的规则配置和服务端配置已不兼容，升级后需要重新配置。

### 升级与重新配置

1. **升级前备份旧配置**，作为重新编写规则时的参考。旧文件无法通过复制或改名继续使用。
2. 进入世界或加入服务器，在聊天栏执行 **`/idtw editor open`** 打开规则编辑 GUI，也可使用 `/idtw editor`。
3. 在 GUI 中选择「新建规则」，参照旧配置重新设置输入物品、转化时机、条件和结果。
4. 点击界面底部的「应用更改」，等待保存成功。规则会自动重载并生效。

单人存档房主可直接打开编辑器；多人服务器需要 OP 权限（等级 ≥2），操作玩家的客户端也需安装本模组。

### 文件存储位置

以下为默认位置。`config/` 相对于单人游戏目录或服务器根目录；多人游戏中的规则保存在服务器端。

| 内容 | 位置 | 用途 |
| --- | --- | --- |
| 自定义规则 | `config/itemdespawntowhat/rules/` | 存放规则 JSON，也是 GUI 保存规则的位置；可按子目录整理 |
| 服务端运行配置 | `config/itemdespawntowhat/server.json` | 存放性能与运行参数，首次启动世界或服务器时自动生成 |
| 世界数据包规则 | `<世界存档>/datapacks/<数据包>/data/<命名空间>/idtw/rules/` | 通过数据包提供规则 |

规则目录可通过 `server.json` 中的 `overlay_directory` 调整为 `config/<overlay_directory>/rules/`；`server.json` 自身位置固定。

手动修改规则 JSON 后，执行 `/idtw rule validate` 检查，再执行 **`/idtw rule reload`** 应用。修改 `server.json` 后，重启服务器；单人游戏退出并重新进入世界。

使用说明：[首次使用](https://github.com/meteoritel/item-despawn-to-what/wiki/zh-01-getting-started) · [命令参考](https://github.com/meteoritel/item-despawn-to-what/wiki/zh-08-commands) · [服务端配置](https://github.com/meteoritel/item-despawn-to-what/wiki/zh-07-server-config)。

### Breaking Update

**2.0.0 is a breaking update.** The mod has been extensively rewritten. Rule files and server settings from 1.x are incompatible and must be recreated.

### Upgrading and Recreating Your Configuration

1. **Back up your old configuration before upgrading** so you can refer to it when rebuilding your rules. Copying or renaming old files will not make them compatible.
2. Enter a world or join your server, then run **`/idtw editor open`** in chat to open the rule editor GUI. `/idtw editor` also works.
3. Select **New rule** and recreate your input items, conversion timing, conditions, and results using the old configuration as a reference.
4. Click **Apply changes** at the bottom and wait for a successful save. Rules reload and take effect automatically.

Single-player world owners can open the editor directly. On multiplayer servers, you need OP permission level 2 or higher, and the player using the editor must also have the mod installed on their client.

### File Locations

These are the default locations. `config/` is relative to your single-player game directory or server root directory. Multiplayer rules are stored on the server.

| Content | Location | Purpose |
| --- | --- | --- |
| Custom rules | `config/itemdespawntowhat/rules/` | Rule JSON files, including rules saved through the GUI; subfolders are allowed |
| Server settings | `config/itemdespawntowhat/server.json` | Performance and runtime settings; generated when you first start a world or server |
| World datapack rules | `<world save>/datapacks/<pack>/data/<namespace>/idtw/rules/` | Rules supplied through a datapack |

The `overlay_directory` setting in `server.json` changes the rule directory to `config/<overlay_directory>/rules/`. The location of `server.json` stays fixed.

After editing rule JSON files by hand, run `/idtw rule validate` to check them, then **`/idtw rule reload`** to apply them. After editing `server.json`, restart the server or leave and reopen your single-player world.

Guides: [Getting started](https://github.com/meteoritel/item-despawn-to-what/wiki/en-01-getting-started) · [Command reference](https://github.com/meteoritel/item-despawn-to-what/wiki/en-08-commands) · [Server settings](https://github.com/meteoritel/item-despawn-to-what/wiki/en-07-server-config).

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

