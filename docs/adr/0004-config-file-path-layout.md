# 配置文件路径统一为 <ns>/<path>.json 并迁移内置类型

## 背景
第三方转化类型的 id 形如 `<ns>:<path>`，需一个不冲突的配置文件路径。现有内置路径 `config/itemdespawntowhat/<fileName>.json` 与 id 的命名空间不对应，无法直接扩展到第三方。

## 决策
统一路径规则：类型 id `<ns>:<path>` 对应 `config/itemdespawntowhat/<ns>/<path>.json`。内置 5 类型（`itemdespawntowhat:<path>`）一次性迁移到 `itemdespawntowhat/` 子目录；首次加载若新路径缺失而旧路径 `config/itemdespawntowhat/<fileName>.json` 存在，则读取旧内容写入新路径并清理旧文件，实现向后兼容。

## 考虑过的替代方案
- **内置特例（零迁移）**：内置类型保持根目录，第三方类型放 `<ns>/` 子目录。零迁移、不扰现有用户，但路径规则对内置是特例，长期不统一。否决。
- **扁平命名空间 `<ns>.<path>.json`**：无嵌套、唯一，但内置文件名仍需迁移且与 id 直觉不符。否决。

## 后果
- 现有用户的内置配置文件位置变化（首次加载自动迁移，向后兼容读取旧路径）。
- `ConfigType.getFileName()` 语义由"直接文件名"改为"由 id 派生路径"。
