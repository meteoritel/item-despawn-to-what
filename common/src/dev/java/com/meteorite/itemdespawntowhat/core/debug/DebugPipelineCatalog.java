package com.meteorite.itemdespawntowhat.core.debug;

import java.util.List;
import java.util.Locale;

/** 命令阶段元数据来自共用序列目录，执行计划由同一展开器生成。 */
final class DebugPipelineCatalog {
    static final int DEFAULT_GAP_SECONDS = 5;
    static final int MIN_GAP_SECONDS = 3;
    static final int MAX_GAP_SECONDS = 30;
    enum Quantity { NONE, FIXED, CEILING }
    record Stage(String name, Quantity quantity, int defaultEntities) {}
    private DebugPipelineCatalog() {}
    static List<Stage> stages() {
        return DebugSequenceCatalog.entries().stream().filter(entry -> entry.get("priority").getAsString().equals("extended"))
                .map(entry -> new Stage(entry.get("id").getAsString(), Quantity.valueOf(entry.get("quantity").getAsString().toUpperCase(Locale.ROOT)),
                        entry.get("default_entities").getAsInt())).toList();
    }
    static Stage find(String name) {
        return stages().stream().filter(stage -> stage.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知测试阶段：" + name));
    }
}
