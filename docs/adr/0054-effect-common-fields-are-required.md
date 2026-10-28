# ADR-0054：效果类型必须声明通用效果字段

- 状态：已实施。
- 相关决策：[ADR-0049](0049-single-parameter-declaration.md)（参数声明是校验与客户端投影的共同事实源）。

## 背景

所有效果实例都承载延迟、概率与整格触发点偏移。参数声明机制允许第三方 `EffectType` 提供空声明或只声明专属字段，而 `Effect` 提供延迟、概率和偏移 getter；两者不一致时，客户端表单与基于声明的校验无法完整表达该类型的效果契约。

## 决策

1. 每个已注册 `EffectType` 的 `params()` 必须包含 `CommonFields.DELAY_TICKS`、`CommonFields.CHANCE` 与 `CommonFields.OFFSET` 三个相同声明实例。
2. `BuiltinTypeRegistries` 在所有内置类型与 SPI provider 登记完成后、效果注册表冻结前执行校验；缺失时以类型 id 和字段名抛出装配错误。
3. `Effect` 显式提供三个 getter；`offset()` 返回 `EffectOffset`，零值由公共参数声明的缺省值承载。类型实现者通过 `CommonFields.EFFECT_PARAMS` 复用三个声明，Codec 通过同一声明构造。
4. 注册后的规则校验只使用参数声明校验已知类型；无注册表或未知类型入口仍对可访问的通用值执行基础检查，并报告未知类型。
5. `SimpleEffectType` 省略声明、传入 `null` 或空声明时使用 `CommonFields.EFFECT_PARAMS`；显式非空声明由实现者追加公共声明。类型的 Codec 由实现者负责，按相同字段名编解码延迟、概率和整格偏移，并与实例 getter 保持一致。

## 考虑过的替代方案

- **保留运行时兼容补验**：允许类型声明和效果接口继续不一致，客户端编辑器仍无法投影缺失字段。否决。
- **增加 `Effect` 默认 getter 或反射读取 Codec**：隐式补值会掩盖类型的字段绑定缺失；显式 getter 与公共声明共同表达实例契约。否决。

## 后果

- 第三方效果类型提供自身非空 `ParamList` 时需追加 `CommonFields.EFFECT_PARAMS`，否则服务端或客户端类型装配失败；`SimpleEffectType` 的便捷构造器默认使用公共声明。
- 注册完成的类型表可以依赖公共字段声明完整，表单投影与参数校验共享该声明。
