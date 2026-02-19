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