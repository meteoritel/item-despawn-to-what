package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.FieldNumbers;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 条件摘要的通用格式化：把规则 JSON 里的资源引用、数值区间、枚举值与布尔开关渲染成玩家可读的短文本。
 *
 * <p><b>职责</b>：只做「JSON 取值 → Component」的纯格式化，不读写草稿、不判断条件语义；
 * 条件类型专属的组装交给 {@link BuiltinConditionSummaries}。取值缺失或无法识别时一律返回 {@code null}，
 * 由调用方决定兜底文案，避免把畸形数据误报成有效参数。
 *
 * <p><b>长度</b>：摘要显示在画布叶卡片的内容行，可用宽度有限（卡片宽度上限见
 * {@code com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout}），
 * 因此列表类文本按 {@link #MAX_ENTRIES} 截断并追加「共 n 项」，长度可控且不隐瞒总数。
 *
 * <p><b>显示名</b>：资源引用走 {@link RuleDisplayLabels}（注册表 + 语言键）；
 * 枚举与布尔短语要求语言键存在才输出，缺键时回落 {@code null} 让调用方兜底。
 */
public final class SummaryFormats {

    /** 摘要语言键前缀，与 {@code NaturalSummary} 共用同一命名空间。 */
    public static final String KEY_PREFIX = TypeLabels.UI_PREFIX + "summary.";

    /** 列表类摘要最多展示的条目数；超出部分以「共 n 项」提示。 */
    public static final int MAX_ENTRIES = 3;

    // 列表连接与截断模板键
    private static final String SEPARATOR_KEY = KEY_PREFIX + "separator";
    private static final String MORE_KEY = KEY_PREFIX + "cond.more";

    // 数值区间模板键：两端 / 只有下界 / 只有上界
    private static final String RANGE_KEY = KEY_PREFIX + "cond.range";
    private static final String RANGE_MIN_KEY = KEY_PREFIX + "cond.range.min";
    private static final String RANGE_MAX_KEY = KEY_PREFIX + "cond.range.max";

    // 布尔短语键后缀
    private static final String TRUE_SUFFIX = ".true";
    private static final String FALSE_SUFFIX = ".false";

    // 世界刻与 24 小时制的换算基数：0 刻为 06:00
    private static final int TICKS_PER_DAY = 24000;
    private static final int MINUTES_PER_DAY = 1440;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int DAY_START_MINUTE = 360;

    // 工具类
    private SummaryFormats() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 用摘要分隔符连接多段文本，不做长度截断。
     *
     * @param parts 已本地化的文本片段，按显示顺序给出
     * @return 连接后的文本；空列表返回空文本
     */
    public static Component join(List<Component> parts) {
        Component result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result = result.copy().append(Component.translatable(SEPARATOR_KEY));
            }
            result = result.copy().append(parts.get(i));
        }
        return result;
    }

    /**
     * 连接多段文本，超过上限时只展示前若干项并追加「共 n 项」。
     *
     * @param parts      已本地化的文本片段，按显示顺序给出
     * @param maxEntries 最多展示的条目数；不超过该数量时等价于 {@link #join(List)}
     * @return 连接后的文本；空列表返回空文本
     */
    public static Component joinCapped(List<Component> parts, int maxEntries) {
        if (maxEntries <= 0 || parts.size() <= maxEntries) {
            return join(parts);
        }
        List<Component> visible = new ArrayList<>(parts.subList(0, maxEntries));
        visible.add(Component.translatable(MORE_KEY, parts.size()));
        return join(visible);
    }

    /**
     * 把标签引用列表渲染成显示名列表：命中注册表用本地化名称，否则回落到去掉默认命名空间的短名。
     *
     * @param list 形如 {@code ["minecraft:water", "#minecraft:logs"]} 的列表值，可为 {@code null}
     * @param type 目录类型，决定显示名的查询方式
     * @return 连接后的引用文本；列表为空或不是列表时返回 {@code null}
     */
    public static @Nullable Component refs(@Nullable RuleValue list, RuleCatalogType type) {
        if (list == null || !list.isList()) {
            return null;
        }
        List<Component> parts = new ArrayList<>();
        for (String raw : list.textItems()) {
            parts.add(ref(raw, type));
        }
        return parts.isEmpty() ? null : joinCapped(parts, MAX_ENTRIES);
    }

    /**
     * 把单个引用渲染成显示名：优先注册表名称，标签与未知名回落短名（标签保留 # 前缀）。
     *
     * @param raw  引用文本，形如 {@code minecraft:stone} 或 {@code #minecraft:logs}
     * @param type 目录类型，决定显示名的查询方式
     * @return 引用文本；空引用返回空文本，绝不返回 {@code null}
     */
    public static Component ref(@Nullable String raw, RuleCatalogType type) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return Component.empty();
        }
        TaggedId tagged = TaggedId.parse(value).result().orElse(null);
        if (tagged == null) {
            return Component.literal(value);
        }
        if (!tagged.tag()) {
            Component label = RuleDisplayLabels.catalogLabel(type, tagged.id());
            if (label != null) {
                return label;
            }
        }
        return Component.literal(shortName(tagged));
    }

    /**
     * 把数值区间渲染成短文本：两端为 {@code 下界~上界}，只有一端时用「≥ / ≤」表示。
     *
     * @param min 下界文本，可为 {@code null} 表示不限制
     * @param max 上界文本，可为 {@code null} 表示不限制
     * @return 区间文本；两端都为空时返回 {@code null}
     */
    public static @Nullable Component range(@Nullable String min, @Nullable String max) {
        String low = trimToNull(min);
        String high = trimToNull(max);
        if (low == null && high == null) {
            return null;
        }
        if (low == null) {
            return Component.translatable(RANGE_MAX_KEY, high);
        }
        if (high == null) {
            return Component.translatable(RANGE_MIN_KEY, low);
        }
        return Component.translatable(RANGE_KEY, low, high);
    }

    /**
     * 渲染枚举取值的本地化文本：只有语言键存在才输出，保证缺键时不显示内部枚举名。
     *
     * @param value     枚举取值，形如 {@code rain}
     * @param keyPrefix 语言键前缀，最终读取 {@code <keyPrefix>.<value>}
     * @return 本地化文本；取值为空或语言键不存在时返回 {@code null}
     */
    public static @Nullable Component enumText(@Nullable String value, String keyPrefix) {
        String key = trimToNull(value);
        if (key == null) {
            return null;
        }
        String full = keyPrefix + "." + key;
        return Language.getInstance().has(full) ? Component.translatable(full) : null;
    }

    /**
     * 渲染布尔开关的短语：读取 {@code <keyPrefix>.true} 或 {@code <keyPrefix>.false}。
     *
     * @param value     开关取值
     * @param keyPrefix 语言键前缀
     * @return 短语文本；语言键不存在时返回 {@code null}
     */
    public static @Nullable Component flag(boolean value, String keyPrefix) {
        String key = keyPrefix + (value ? TRUE_SUFFIX : FALSE_SUFFIX);
        return Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    /**
     * 渲染数值文本：JSON 数值按最短往返表示，字符串数值按原文保留。
     *
     * @param value 数值或文本标量，可为 {@code null}
     * @return 文本；取值缺失或不是标量时返回 {@code null}
     */
    public static @Nullable String numberText(@Nullable RuleValue value) {
        if (value == null || value.isNull() || !value.isScalar()) {
            return null;
        }
        if (value.isNumber()) {
            return FieldNumbers.text(value.asDecimal(0.0D), -1);
        }
        return trimToNull(value.asText(""));
    }

    /**
     * 把世界刻换算成 24 小时制时刻文本，0 刻对应 06:00。
     *
     * @param ticks 世界刻，可为任意整数，超出一天时按周期取模
     * @return 形如 {@code 06:00} 的时刻文本
     */
    public static String clockText(int ticks) {
        int minute = Math.floorMod(ticks, TICKS_PER_DAY) * MINUTES_PER_DAY / TICKS_PER_DAY + DAY_START_MINUTE;
        minute = Math.floorMod(minute, MINUTES_PER_DAY);
        return String.format(Locale.ROOT, "%02d:%02d", minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR);
    }

    // 显示名缺失时的短名：去掉默认命名空间，标签保留 # 前缀
    private static String shortName(TaggedId tagged) {
        ResourceLocation id = tagged.id();
        String path = ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())
                ? id.getPath()
                : id.getNamespace() + ":" + id.getPath();
        return tagged.tag() ? "#" + path : path;
    }

    // 去空白；空串返回 null
    private static @Nullable String trimToNull(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
