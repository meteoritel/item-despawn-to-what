---
title: 独立服务端连接冒烟测试
layer: 5
audience: 验证双端连接与独立服务端启动的开发者
authority:
  - tools/connection_smoke.py
  - tools/connection-smoke.init.gradle
---

# 独立服务端连接冒烟测试

## TL;DR

编译当前源码，通过真实客户端连接本机独立服务端，或单独检查服务端启动。默认依次检查 NeoForge、Fabric，连接模式每端连接两次，每次观察 30 秒。

## 适用对象

需要检查开发环境中服务端启动、Loader 握手与客户端进服的开发者。需要 Java 21、Python 3，以及可显示 Minecraft 窗口的 Windows 桌面；Gradle 使用项目配置的依赖和游戏资源。

## 执行

在项目根目录运行：

```powershell
# 双端连接：每种 Loader 连接一次，进服后观察 10 秒
python tools/connection_smoke.py --loader all --attempts 1 --seconds 10 --accept-eula

# 纯服务端：启动后独立运行 10 秒
python tools/connection_smoke.py --loader all --mode server --seconds 10 --accept-eula
```

`--accept-eula` 表示接受 Minecraft EULA，写入本次隔离目录的 `eula.txt`。服务端使用离线身份，只监听 `127.0.0.1`。客户端窗口会自动打开、连接和关闭，执行时保持窗口开启。

`--loader` 可选 `neoforge`、`fabric`、`all`；`--offline` 仅使用缓存的 Gradle 依赖；`--port` 默认 `25575`；`--timeout` 为启动及进服等待秒数，默认 `240`；`--prepare-timeout` 为 Gradle 准备超时秒数，默认 `900`。

## 判定与产物

服务端日志出现 `Done (...)!` 标记后判为完成启动。纯服务端模式继续观察指定秒数，期间进程持续运行且没有匹配到启动或 packet 错误。

连接模式要求服务端记录 `ConnectionTest joined the game`、客户端生成本次 Quick Play 进入记录，且观察期间双端持续运行、玩家保持连接、日志没有匹配到握手或 packet 编解码错误。关闭客户端后等待服务端记录断开；多次连接复用同一服务端。

结果位于 `build/connection-smoke/<loader>/<时间戳>/result.json`，同目录保留 `prepare.log`、`server-console.log`、各轮 `client-<序号>-console.log`、启动命令配置及隔离的 `server/`、`client/` 运行目录。脚本结束时关闭本轮游戏进程。所有 Loader 通过时退出码为 `0`，任意失败时为 `1`。

测试覆盖当前源码的开发运行环境中的独立服务端启动与本机连接。

## 相关文档

- [文档体系](../docs/README.md)
- [平台模块](../docs/modules/platform.md)
