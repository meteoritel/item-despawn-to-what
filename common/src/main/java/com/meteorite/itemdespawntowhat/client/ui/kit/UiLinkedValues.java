package com.meteorite.itemdespawntowhat.client.ui.kit;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;

/**
 * 两个数值之间的联动绑定：驱动值变化时把跟随值改写为目标值，跟随值被手动改动后停止自动跟随。
 *
 * <p>本类只跟踪状态与调度写入，不判断「该不该联动」：读写由 {@link Accessor} 提供，目标值与触发条件由宿主给出。
 * 宿主在输入回调里按变化来源调用 {@link #driverChanged()} 或 {@link #followerChanged()}，
 * 本类据此区分联动写入与手动改动。</p>
 *
 * <p>同步规则：驱动值变化、未处于手动状态且满足触发条件时，把跟随值写成 {@code target(驱动值)}。
 * 手动状态：跟随值收到一次非联动写入即进入，此后驱动值变化不再改写跟随值；
 * 只有宿主调用 {@link #resetManual()}（如切换编辑对象）才恢复自动跟随。</p>
 */
public final class UiLinkedValues {

    private final Accessor driver;
    private final Accessor follower;
    private final DoubleUnaryOperator target;
    // 触发条件：驱动值不满足时不联动；null 表示恒联动
    private @Nullable Predicate<Double> condition;
    // 跟随值是否已被手动改过：置位后驱动值变化不再改写跟随值
    private boolean manual;
    // 本绑定正在写跟随值：用于把自身的写入与宿主的真实输入区分开
    private boolean applying;

    /**
     * 构造一组联动绑定。
     *
     * @param driver 驱动值访问器
     * @param follower 跟随值访问器
     * @param target 由驱动值算出的跟随值目标
     */
    public UiLinkedValues(Accessor driver, Accessor follower, DoubleUnaryOperator target) {
        this(driver, follower, target, false);
    }

    /**
     * 构造一组联动绑定，并带回上次记录的手动状态（宿主重建控件后沿用）。
     *
     * @param driver 驱动值访问器
     * @param follower 跟随值访问器
     * @param target 由驱动值算出的跟随值目标
     * @param manual 是否已被手动改过
     */
    public UiLinkedValues(Accessor driver, Accessor follower, DoubleUnaryOperator target, boolean manual) {
        this.driver = Objects.requireNonNull(driver, "driver");
        this.follower = Objects.requireNonNull(follower, "follower");
        this.target = Objects.requireNonNull(target, "target");
        this.manual = manual;
    }

    /**
     * 设置联动触发条件：驱动值不满足条件时不做同步；默认恒联动。
     *
     * @param condition 以驱动值为入参的判断
     * @return 本实例，便于链式配置
     */
    public UiLinkedValues followWhen(Predicate<Double> condition) {
        this.condition = condition;
        return this;
    }

    /** 跟随值是否已被手动改过（此时不再自动跟随）。 */
    public boolean manual() {
        return manual;
    }

    /** 清除手动状态，恢复自动跟随。 */
    public void resetManual() {
        manual = false;
    }

    /**
     * 驱动值已变化：未手动且满足触发条件时，把跟随值同步为目标值。
     *
     * @return 是否写入了跟随值
     */
    public boolean driverChanged() {
        if (manual) {
            return false;
        }
        double driven = driver.read();
        if (condition != null && !condition.test(driven)) {
            return false;
        }
        return write(target.applyAsDouble(driven));
    }

    /** 跟随值被外部改动：非本绑定写入期间的变化记为手动改动，此后不再自动跟随。 */
    public void followerChanged() {
        if (!applying) {
            manual = true;
        }
    }

    /**
     * 忽略手动状态与触发条件，强制同步一次；供宿主初始化或显式重置使用。
     *
     * @return 是否写入了跟随值
     */
    public boolean sync() {
        return write(target.applyAsDouble(driver.read()));
    }

    // 写入跟随值；写入期间宿主可能回调 followerChanged，用 applying 标记避免误判为手动改动
    private boolean write(double value) {
        if (Double.compare(follower.read(), value) == 0) {
            return false;
        }
        applying = true;
        try {
            follower.write(value);
        } finally {
            applying = false;
        }
        return true;
    }

    /** 一侧数值的访问器：read 取当前值，write 落值；落到草稿、控件还是外部状态由宿主决定。 */
    public interface Accessor {

        /** 读取当前值。 */
        double read();

        /** 写入新值。 */
        void write(double value);
    }

    /**
     * 只读访问器：用于只被读取的驱动值，写入被忽略。
     *
     * @param reader 取值回调
     * @return 只读访问器
     */
    public static Accessor readOnly(DoubleSupplier reader) {
        Objects.requireNonNull(reader, "reader");
        return new Accessor() {
            @Override
            public double read() {
                return reader.getAsDouble();
            }

            @Override
            public void write(double value) {
                // 只读访问器不接收写入：驱动值只由宿主控件产生
            }
        };
    }

    /**
     * 由读写回调组装访问器。
     *
     * @param reader 取值回调
     * @param writer 落值回调
     * @return 读写访问器
     */
    public static Accessor of(DoubleSupplier reader, DoubleConsumer writer) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(writer, "writer");
        return new Accessor() {
            @Override
            public double read() {
                return reader.getAsDouble();
            }

            @Override
            public void write(double value) {
                writer.accept(value);
            }
        };
    }
}
