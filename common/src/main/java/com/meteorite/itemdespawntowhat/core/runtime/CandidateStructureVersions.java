package com.meteorite.itemdespawntowhat.core.runtime;

import com.google.gson.JsonElement;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选结构摘要表：为每条规则计算候选结构摘要（轮询游标判定「结构是否变化」的依据），并按规则实例缓存。
 * 缓存理由：摘要要对全部候选做一次 JSON 编码再取内容摘要，代价只与规则内容有关，而规则对象不可变——
 * 同一规则实例在两次转化之间候选结构不会变化，结构未变时无须重算。
 * 失效条件（由持有者显式触发）：缓存键是规则实例身份，规则索引整体重建（replaceRules / publishRules /
 * applySavedIndex）或停服时 ConversionRuntime 调用 clear()；缓存不跨规则代次，也不跨注册表视图复用。
 * 先建后换：prepare() 在构建期把摘要算到独立映射，install() 发布时才一次性换入，构建失败不影响旧缓存。
 * 摘要口径：硬约束是不得使用对象哈希——record 的 hashCode 会逐层展开组件，而枚举组件用的是 Object 身份哈希
 * （值不受内容约束，随 JVM、哈希模式与分配状态变化），重启后可能被判为新结构而让游标归零。
 */
final class CandidateStructureVersions {

    private static final Logger LOGGER = LogManager.getLogger();
    // 内容摘要算法：必须由所有 JVM 提供
    private static final String DIGEST_ALGORITHM = "SHA-256";
    // 摘要取前 8 字节拼成 64 位版本号，碰撞概率可忽略
    private static final int DIGEST_BYTES = 8;

    private final BuiltinTypeRegistries types;
    // 规则实例 -> 候选结构摘要：按身份比较，避开 record 的深层 equals 与其枚举组件的身份哈希
    private final Map<Rule, Integer> byRule = new IdentityHashMap<>();

    CandidateStructureVersions(BuiltinTypeRegistries types) {
        this.types = types;
    }

    // 候选结构摘要：同一规则实例只编码一次；编码失败返回 0，调用方据此重置轮询游标
    int versionOf(Rule rule, HolderLookup.Provider registries) {
        Integer cached = byRule.get(rule);
        if (cached != null) {
            return cached;
        }
        int version = encodeVersion(rule, registries);
        byRule.put(rule, version);
        return version;
    }

    // 预计算候选结构摘要到独立映射：构建期不写缓存，旧索引的摘要表在换入前保持可读
    Map<Rule, Integer> prepare(List<Rule> rules, HolderLookup.Provider registries) {
        Map<Rule, Integer> prepared = new IdentityHashMap<>(Math.max(4, rules.size() * 2));
        for (Rule rule : rules) {
            prepared.put(rule, encodeVersion(rule, registries));
        }
        return prepared;
    }

    // 一次性换入预计算表：发布阶段只替换容器内容，不再编码或读取规则
    void install(Map<Rule, Integer> prepared) {
        byRule.clear();
        byRule.putAll(prepared);
    }

    // 清空缓存：调用点即失效条件（规则索引重建、停服）
    void clear() {
        byRule.clear();
    }

    // 编码规则的候选结构（id、安全生成/起点填充标记、效果顺序与全部参数）为稳定 JSON 文本后取内容摘要
    private int encodeVersion(Rule rule, HolderLookup.Provider registries) {
        Codec<List<OutcomeCandidate>> codec = OutcomeCandidate.codec(RuleCodecs.effectCodec(types.effectTypes())).listOf();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        JsonElement json = codec.encodeStart(ops, rule.effectiveOutcomes())
                .resultOrPartial(reason -> LOGGER.warn("候选结构摘要编码失败，轮询游标将重置：规则={} 原因={}", rule.id(), reason))
                .orElse(null);
        if (json == null) {
            return 0;
        }
        return digest(json.toString());
    }

    // 内容摘要：同一文本必然同值、文本变化必然变值，与任何对象身份无关
    private static int digest(String text) {
        try {
            byte[] digest = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(text.getBytes(StandardCharsets.UTF_8));
            int version = 0;
            for (int index = 0; index < DIGEST_BYTES; index++) {
                version = (version << 8) | (digest[index] & 0xFF);
            }
            return version;
        } catch (NoSuchAlgorithmException impossible) {
            // 所有 JVM 都必须提供 SHA-256；真缺失时退回字符串内容哈希，仍与对象身份无关
            return text.hashCode();
        }
    }
}
