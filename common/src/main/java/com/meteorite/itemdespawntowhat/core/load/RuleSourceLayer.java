package com.meteorite.itemdespawntowhat.core.load;

/**
 * 规则来源层。
 * 按优先级从低到高排列：合并时同 id 由后者覆盖前者，即 覆盖层 &gt; 世界数据包 &gt; 内置数据包。
 */
public enum RuleSourceLayer {

    // mod 内置数据包：jar 内 data/<ns>/idtw/rules/**，只读，作为默认值与示例
    BUILTIN("内置数据包", 0),
    // 世界数据包：存档 datapacks/*/data/<ns>/idtw/rules/**，只读，由原版机制同步
    WORLD("世界数据包", 1),
    // config 覆盖层：config/itemdespawntowhat/rules/**，可写，GUI 的写入目标
    OVERLAY("config 覆盖层", 2);

    private final String displayName;
    private final int priority;

    RuleSourceLayer(String displayName, int priority) {
        this.displayName = displayName;
        this.priority = priority;
    }

    // 优先级数值：越大越优先（合并时后处理）
    public int priority() {
        return priority;
    }

    // 面向命令与日志输出的中文层名
    public String displayName() {
        return displayName;
    }

    // 该层是否允许被 GUI / 命令写入（仅覆盖层可写）
    public boolean writable() {
        return this == OVERLAY;
    }
}
