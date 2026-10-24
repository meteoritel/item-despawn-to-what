package com.meteorite.itemdespawntowhat.core.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * 规则 JSON 解码的规模预算与唯一入口。
 * 规则 JSON 文本与条件树的嵌套形态属于对外契约（字段名统一见 {@link RuleFields}），
 * 因此预算常量与校验入口放在契约层，供模型层、加载层、编辑协议与配置层共用同一份口径：
 * 文本解析先做嵌套深度预检，条件树在进入递归解码之前做深度与节点数校验，
 * 避免超深或超规模输入从解析期、解码期击穿调用栈而留下未捕获错误。
 * 预算校验自身全部用显式栈迭代，不占用 Java 调用栈，因此校验器不可能被超深输入击穿。
 */
public final class RuleDecodeBudget {

    // 规则 JSON 文本允许的最大嵌套深度：最深合法写法（outcomes → effects → conditions →
    // 16 层 all_of / any_of 条件树，每层占 2 层 JSON）约 40 层，再为类型专属参数留出余量
    public static final int MAX_NESTING_DEPTH = 64;

    // 条件树最大深度，根节点深度为 1；与解码后的结构校验同口径
    public static final int MAX_CONDITION_DEPTH = 16;

    // 条件树最大节点数，组合节点与条件叶都计入；与解码后的结构校验同口径
    public static final int MAX_CONDITION_NODES = 256;

    // 工具类不创建实例。
    private RuleDecodeBudget() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 有界解析结果：element 与 failureReason 恰有一个非空。
     * @param element 解析出的 JSON 元素；解析失败时为 null
     * @param failureReason 解析失败的可读原因；解析成功时为 null
     */
    public record ParseResult(@Nullable JsonElement element, @Nullable String failureReason) {

        /**
         * 是否解析成功。
         * @return 解析出 JSON 元素时返回 true
         */
        public boolean ok() {
            return element != null;
        }
    }

    /**
     * 有界解析规则 JSON 文本：嵌套深度超限或文本非法时返回失败结果，任何解析期失败都不抛出异常。
     * @param text 待解析文本；null 视为空文本
     * @return 解析结果；失败原因可能为 null（底层解析器未给出消息）
     */
    public static ParseResult parse(@Nullable String text) {
        return parseEnvelope(text, 0);
    }

    /**
     * 有界解析带包装层的文档（快照、变更集、来源视图等）：包装结构会在规则体之外附加固定层数，
     * 直接套用 {@link #MAX_NESTING_DEPTH} 会把接近上限的合法规则体误判为超限，
     * 因此包装层与规则体分别预算，两者相加才是整篇文本的允许深度。
     *
     * @param text          待解析文本；null 视为空文本
     * @param envelopeDepth 包装结构在规则体之外附加的嵌套层数；负数按 0 处理
     * @return 解析结果；失败原因可能为 null（底层解析器未给出消息）
     */
    public static ParseResult parseEnvelope(@Nullable String text, int envelopeDepth) {
        int allowed = MAX_NESTING_DEPTH + Math.max(0, envelopeDepth);
        if (text == null) {
            return new ParseResult(null, "文本为空");
        }
        int depth = nestingDepth(text);
        if (depth > allowed) {
            // 预检发生在解析器之前：超深文本不进 Gson 的递归下降
            return new ParseResult(null, "JSON 嵌套深度超限: " + depth + " > " + allowed);
        }
        try {
            return new ParseResult(JsonParser.parseString(text), null);
        } catch (RuntimeException invalid) {
            return new ParseResult(null, invalid.getMessage());
        } catch (StackOverflowError overflow) {
            // Error 不是 RuntimeException 的子类；深度预检之后仍出现的栈溢出按解析失败降级，不向上抛出
            return new ParseResult(null, "JSON 解析栈溢出");
        }
    }

    /**
     * 校验规则 JSON 内全部 conditions 子树的深度与节点数预算。
     * 必须在进入递归解码之前调用：超限的子树写成 ERROR 级结构化议题并返回 false，由调用方整条拒载。
     * @param ruleBody 规则 JSON 对象
     * @param issues 本批问题收集器
     * @return 全部条件子树都在预算内时返回 true
     */
    public static boolean checkConditionBudgets(JsonObject ruleBody, IssueCollector issues) {
        boolean withinBudget = true;
        Deque<Pending> pending = new ArrayDeque<>();
        for (Map.Entry<String, JsonElement> member : ruleBody.entrySet()) {
            if (RuleFields.CONDITIONS.equals(member.getKey())) {
                withinBudget &= checkConditionTree(member.getValue(), member.getKey(), issues);
            } else {
                pending.push(new Pending(member.getValue(), member.getKey()));
            }
        }
        while (!pending.isEmpty()) {
            Pending current = pending.pop();
            JsonElement element = current.element();
            if (element.isJsonObject()) {
                for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
                    String fieldPath = current.fieldPath() + "." + member.getKey();
                    if (RuleFields.CONDITIONS.equals(member.getKey())) {
                        // 条件子树整体交给条件树校验，不再当作普通 JSON 向下展开
                        withinBudget &= checkConditionTree(member.getValue(), fieldPath, issues);
                    } else {
                        pending.push(new Pending(member.getValue(), fieldPath));
                    }
                }
            } else if (element.isJsonArray()) {
                int index = 0;
                for (JsonElement item : element.getAsJsonArray()) {
                    pending.push(new Pending(item, current.fieldPath() + "[" + index + "]"));
                    index++;
                }
            }
        }
        return withinBudget;
    }

    // 条件树预算：显式栈遍历，超限即写成 ERROR 议题并返回 false；
    // 形状非法（非对象、缺 op、op 非字符串）留给解码器给出精确报错，这里不做预算判定
    private static boolean checkConditionTree(JsonElement root, String fieldPath, IssueCollector issues) {
        if (!root.isJsonObject()) {
            return true;
        }
        boolean withinBudget = true;
        int nodes = 0;
        int maxDepth = 0;
        Deque<Level> pending = new ArrayDeque<>();
        pending.push(new Level(root, 1));
        while (!pending.isEmpty()) {
            Level level = pending.pop();
            JsonObject node = level.element().getAsJsonObject();
            String op = opOf(node);
            if (op == null) {
                continue;
            }
            nodes++;
            maxDepth = Math.max(maxDepth, level.depth());
            switch (op) {
                case RuleFields.OP_ALL_OF, RuleFields.OP_ANY_OF -> pushTerms(node, level.depth() + 1, pending);
                case RuleFields.OP_INVERTED -> {
                    JsonElement term = node.get(RuleFields.TERM);
                    if (term != null && term.isJsonObject()) {
                        pending.push(new Level(term, level.depth() + 1));
                    }
                }
                // 条件叶与未知 op 都没有更深的条件节点
                default -> { }
            }
        }
        if (maxDepth > MAX_CONDITION_DEPTH) {
            issues.error("条件树深度超出上限: " + maxDepth + " > " + MAX_CONDITION_DEPTH, null, fieldPath);
            withinBudget = false;
        }
        if (nodes > MAX_CONDITION_NODES) {
            issues.error("条件树节点数超出上限: " + nodes + " > " + MAX_CONDITION_NODES, null, fieldPath);
            withinBudget = false;
        }
        return withinBudget;
    }

    // 组合节点的子节点：terms 必须是数组，元素按下一层深度入栈
    private static void pushTerms(JsonObject node, int depth, Deque<Level> pending) {
        JsonElement terms = node.get(RuleFields.TERMS);
        if (terms == null || !terms.isJsonArray()) {
            return;
        }
        for (JsonElement term : terms.getAsJsonArray()) {
            if (term.isJsonObject()) {
                pending.push(new Level(term, depth));
            }
        }
    }

    // 节点 op：缺失或不是字符串时返回 null
    private static @Nullable String opOf(JsonObject node) {
        JsonElement raw = node.get(RuleFields.OP);
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
            return null;
        }
        return raw.getAsString();
    }

    // 迭代统计文本的最大嵌套深度：跳过字符串字面量，避免字符串内的括号干扰计数
    private static int nestingDepth(String text) {
        int depth = 0;
        int max = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                depth++;
                if (depth > max) {
                    max = depth;
                }
            } else if ((current == '}' || current == ']') && depth > 0) {
                depth--;
            }
        }
        return max;
    }

    // 待展开的 JSON 元素及其字段路径
    private record Pending(JsonElement element, String fieldPath) {
    }

    // 待判定的条件节点及其深度（根节点深度为 1）
    private record Level(JsonElement element, int depth) {
    }
}
