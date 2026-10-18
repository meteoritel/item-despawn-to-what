package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Arrays;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * JSON 语法着色：把美化后的 JSON 文本按词法分类，供只读代码视图按类别取色绘制。
 *
 * <p>分类是固定规则，全模块共用一套：<b>键</b>、<b>字符串</b>、<b>数字</b>、<b>true / false / null</b>
 * 与<b>标点</b>各一类，颜色由主题调色板的 {@code code*} 项给出，因此来源视图弹窗、条件树 JSON 视图
 * 与损坏规则详情看到的是同一套配色，新增只读 JSON 入口也应复用本类而不另起一套规则。</p>
 *
 * <p>本类只做文本分类与按类别绘制，不持有颜色常量，也不依赖主题之外的模组代码。</p>
 */
public final class UiJsonText {

    /** 标点与结构字符：花括号、方括号、冒号、逗号与空白。 */
    public static final byte PUNCTUATION = 0;
    /** 对象键：后随冒号的字符串。 */
    public static final byte KEY = 1;
    /** 字符串值。 */
    public static final byte STRING = 2;
    /** 数字值。 */
    public static final byte NUMBER = 3;
    /** {@code true}、{@code false} 与 {@code null}。 */
    public static final byte LITERAL = 4;
    /** 类别总数，颜色表按此长度分配。 */
    public static final int KIND_COUNT = 5;

    // 数字字面量允许出现的字符
    private static final String NUMBER_CHARS = "+-.eE0123456789";

    // 工具类，禁止实例化
    private UiJsonText() {
    }

    /**
     * 按 JSON 词法给每个字符标记类别。
     *
     * @param text 美化后的 JSON 文本
     * @return 与文本等长的类别表，下标即字符下标；空白与结构字符为 {@link #PUNCTUATION}
     */
    public static byte[] classify(String text) {
        byte[] kinds = new byte[text.length()];
        if (text.isEmpty()) {
            return kinds;
        }
        int length = text.length();
        int index = 0;
        while (index < length) {
            char current = text.charAt(index);
            if (current == '"') {
                int end = stringEnd(text, index);
                // 字符串后面跳过空白紧跟冒号时是对键，否则是字符串值
                int next = end;
                while (next < length && Character.isWhitespace(text.charAt(next))) {
                    next++;
                }
                byte kind = next < length && text.charAt(next) == ':' ? KEY : STRING;
                Arrays.fill(kinds, index, end, kind);
                index = end;
            } else if (current == '-' || (current >= '0' && current <= '9')) {
                int end = index + 1;
                while (end < length && NUMBER_CHARS.indexOf(text.charAt(end)) >= 0) {
                    end++;
                }
                Arrays.fill(kinds, index, end, NUMBER);
                index = end;
            } else if (Character.isLetter(current)) {
                int end = index + 1;
                while (end < length && Character.isLetter(text.charAt(end))) {
                    end++;
                }
                // true / false / null 单独成色，其余裸词按标点处理
                Arrays.fill(kinds, index, end, isLiteral(text, index, end) ? LITERAL : PUNCTUATION);
                index = end;
            } else {
                index++;
            }
        }
        return kinds;
    }

    /**
     * 绘制文本中的一段：相邻同类字符合并成一次绘制，按类别取色。
     *
     * @param graphics   绘制上下文
     * @param font       渲染字体
     * @param text       与类别表对应的文本
     * @param kinds      {@link #classify} 的结果
     * @param kindColors 按类别下标取色的颜色表
     * @param x          起始横坐标
     * @param y          文本顶部纵坐标
     * @param begin      段落起点（含）
     * @param end        段落终点（不含）
     */
    public static void paint(GuiGraphics graphics, Font font, String text, byte[] kinds, int[] kindColors,
                             int x, int y, int begin, int end) {
        int from = Math.clamp(begin, 0, text.length());
        int to = Math.clamp(end, from, text.length());
        int cursor = x;
        int index = from;
        while (index < to) {
            byte kind = index < kinds.length ? kinds[index] : PUNCTUATION;
            int runEnd = index + 1;
            while (runEnd < to && runEnd < kinds.length && kinds[runEnd] == kind) {
                runEnd++;
            }
            // 结尾落在类别表之外的字符按标点带过，避免漏画
            runEnd = Math.min(runEnd, to);
            String run = text.substring(index, runEnd);
            graphics.drawString(font, run, cursor, y, colorOf(kindColors, kind), false);
            cursor += font.width(run);
            index = runEnd;
        }
    }

    // 类别对应的颜色，越界时回落到标点色
    private static int colorOf(int[] kindColors, byte kind) {
        return kind >= 0 && kind < kindColors.length ? kindColors[kind] : kindColors[PUNCTUATION];
    }

    // 字符串字面量的结束下标（含右引号）；未闭合时取文本末尾
    private static int stringEnd(String text, int start) {
        int index = start + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\') {
                // 转义字符连带其下一个字符一起跳过
                index += 2;
                continue;
            }
            if (current == '"') {
                return index + 1;
            }
            index++;
        }
        return text.length();
    }

    // 判断 [begin, end) 是否为 true / false / null
    private static boolean isLiteral(String text, int begin, int end) {
        String word = text.substring(begin, end);
        return "true".equals(word) || "false".equals(word) || "null".equals(word);
    }
}
