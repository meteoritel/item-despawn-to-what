# 更新日志 Change log

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

## [1.1.0]
### 新增 
- **优化 GUI**：改善了上一版本较为简陋的图形界面。在注册名 (物品、方块、实体ID)输入框中输入内容时，将自动显示建议下拉列表，方便快速选择。
![1.1.0-3.png](docs/changelog/v1.1.0/1.1.0-3.png)
- **配置管理 GUI**：现在可以在图形界面内直接**新增、删除或编辑配置**，无需手动修改配置文件，对新手更加友好。
![1.1.0-2.png](docs/changelog/v1.1.0/1.1.0-2.png)
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