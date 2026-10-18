package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * 数值控件与精确文本的双向绑定；统一预览、提交、取消和程序回填的重入保护。
 * <p>V 可为单值或不可变区间，T 可为字符串或不可变文本列表。解析返回 null 表示暂不可用，
 * 原文本保留，数值停在最近一次有效位置；单位换算、空值策略与错误展示由宿主提供。
 * <p>数值写入器应只回填、不产生用户提交事件。提交型精确编辑也可使用自行发出事件的写入器，
 * 此时 committed 应为空操作，避免重复提交。宿主将滑条的生命周期转交本类，
 * 无净变化而未收到 committed 时调用 finishValueChange 收束文本快照。
 *
 * @param <V> 不可变数值快照类型
 * @param <T> 不可变文本快照类型
 */
public final class UiValueTextBinding<V, T> {

    private final Supplier<V> readValue;
    private final Predicate<V> writeValue;
    private final Supplier<T> readText;
    private final Consumer<T> writeText;
    private final Function<T, @Nullable V> parse;
    private final Function<V, T> format;
    private final Runnable committed;
    private boolean writingText;
    private boolean textPreviewing;
    private boolean valueChanging;
    private V textStartValue;
    private T textStartText;
    private T valueStartText;
    private T previousText;

    /**
     * 注入控件读写和文本转换；构造时只读取初始快照，不写控件或发出事件。
     *
     * @param readValue 读取当前数值
     * @param writeValue 精确回填数值，返回是否接受
     * @param readText 读取当前文本快照
     * @param writeText 回填文本，不应产生文本编辑历史
     * @param parse 解析文本，非法或未完成输入返回 null
     * @param format 把数值转换为显示文本
     * @param committed 一次用户提交的宿主通知
     */
    public UiValueTextBinding(Supplier<V> readValue, Predicate<V> writeValue,
                              Supplier<T> readText, Consumer<T> writeText,
                              Function<T, @Nullable V> parse, Function<V, T> format,
                              Runnable committed) {
        this.readValue = Objects.requireNonNull(readValue, "readValue");
        this.writeValue = Objects.requireNonNull(writeValue, "writeValue");
        this.readText = Objects.requireNonNull(readText, "readText");
        this.writeText = Objects.requireNonNull(writeText, "writeText");
        this.parse = Objects.requireNonNull(parse, "parse");
        this.format = Objects.requireNonNull(format, "format");
        this.committed = Objects.requireNonNull(committed, "committed");
        this.textStartValue = readValue.get();
        this.previousText = readText.get();
        this.textStartText = previousText;
        this.valueStartText = previousText;
    }

    /**
     * 程序回填文本并刷新文本基线；回填期间的文本预览回调被忽略。
     *
     * @param text 要显示的文本快照
     */
    public void setText(T text) {
        boolean previousWriting = writingText;
        writingText = true;
        try {
            writeText.accept(text);
            previousText = readText.get();
        } finally {
            writingText = previousWriting;
        }
    }

    /** 接受当前缓冲为新的回退基线；装载或宿主接受输入后调用，不发出提交事件。 */
    public void acceptText() {
        textPreviewing = false;
        valueChanging = false;
        previousText = readText.get();
    }

    /**
     * 预览当前文本；首次编辑保存原数值与原文本，后续非法输入保持最近有效数值。
     *
     * @return 文本能否解析且数值写入被接受；程序回填期间返回 true
     */
    public boolean previewText() {
        if (writingText) return true;
        if (!textPreviewing) {
            textStartValue = readValue.get();
            textStartText = previousText;
            textPreviewing = true;
        }
        T text = readText.get();
        V value = parse.apply(text);
        boolean accepted = value != null && writeValue.test(value);
        previousText = text;
        return accepted;
    }

    /**
     * 提交精确文本；成功后规范化显示并通知宿主一次，失败保留当前缓冲。
     *
     * @param text 要提交的文本
     * @return 解析与数值写入是否成功
     */
    public boolean commitText(T text) {
        V value = parse.apply(text);
        if (value == null || !writeValue.test(value)) return false;
        acceptText();
        setText(format.apply(readValue.get()));
        committed.run();
        return true;
    }

    /** 取消文本预览并恢复编辑前的数值与原始文本；没有预览时显示当前数值。 */
    public void cancelText() {
        if (textPreviewing) {
            V restoredValue = textStartValue;
            T restoredText = textStartText;
            acceptText();
            writeValue.test(restoredValue);
            setText(restoredText);
        } else {
            setText(format.apply(readValue.get()));
        }
    }

    /** 滑条开始操作时保存原始文本，结束先前的文本预览。 */
    public void beginValueChange() {
        acceptText();
        valueStartText = readText.get();
        valueChanging = true;
    }

    /** 滑条预览只同步显示，不通知宿主提交。 */
    public void previewValue() {
        setText(format.apply(readValue.get()));
    }

    /** 滑条提交时同步显示并通知宿主一次。 */
    public void commitValue() {
        acceptText();
        previewValue();
        committed.run();
    }

    /** 滑条已恢复数值后调用，恢复操作前的原始文本，不产生提交。 */
    public void cancelValue() {
        if (!valueChanging) return;
        T restoredText = valueStartText;
        acceptText();
        setText(restoredText);
    }

    /** 滑条操作结束而无净变化时恢复原始文本；已提交或取消时为空操作。 */
    public void finishValueChange() {
        cancelValue();
    }
}
