# ItemDespawnToWhat

让即将消失的掉落物按规则变成别的东西。

模组运行在 Minecraft 1.21.1 上，同时提供 Fabric 与 NeoForge 两个版本。掉落物自然消失、被火烧毁、被岩浆吞掉或被仙人掌扎掉时，规则决定它是否转化、在什么条件下转化，以及转化出什么：物品、生物、方块、经验、天气或爆炸。

规则可以在游戏内编辑器里直接修改（进入世界后输入 `/idtw config edit`），也可以写成 JSON 放在 `config` 覆盖层或数据包里。

## 文档

| 你的目标 | 入口 |
|---|---|
| 安装模组、写第一条规则、查命令与配置 | [玩家指南](docs/wiki/Home.md)（[中文](docs/wiki/zh-Home.md) · [English](docs/wiki/en-Home.md)） |
| 首次接触代码，判断改动落在哪一层 | [文档体系](docs/README.md)、[架构总览](docs/architecture/overview.md) |
| 新增条件或效果、修改运行时 | [模块文档索引](docs/modules/README.md)、[关键链路](docs/architecture/key-flows.md) |
| 写规则、对接规则文件格式 | [规则 JSON 契约](docs/api/rule-json.md) |
| 调整服务端参数、排查运行问题 | [运行配置](docs/ops/configuration.md)、[故障排查](docs/ops/troubleshooting.md) |
| 查术语与设计取舍 | [术语表](GLOSSARY.md)、[ADR 索引](docs/adr/README.md) |

## 安装

1. 为 Minecraft 1.21.1 安装 Fabric Loader 或 NeoForge。
2. 从 Releases 下载对应加载器的 jar，放进 `mods/`。
3. Fabric 端同时安装 Fabric API。

需要 Java 21。

## 从源码构建

需要 JDK 21：

```powershell
./gradlew build
```

Windows 下可用 `gradlew.bat build`。构建产物位于 `fabric/build/libs/` 与 `neoforge/build/libs/`，发布用 jar 是不带 `-sources`、`-javadoc` 后缀的那个。

## 许可证

[MIT](LICENSE.txt)
