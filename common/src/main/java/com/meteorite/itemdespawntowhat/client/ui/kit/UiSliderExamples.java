package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * kit 数值控件的无规则语义调用示例（规格 §7）。
 *
 * <p>三个示例只演示 kit 自身的装配与事件顺序，不涉及任何业务字段、不读取存档、配置或世界时间：
 * 宿主把示例里的标签换成自己的本地化 key、把回调接到自己的状态上即可。</p>
 *
 * <p><b>事件顺序</b>与 {@link UiInputRouter} 一致：顶层 modal 作用域 → 该作用域捕获目标 → 聚焦控件 → 容器导航，
 * 每个事件只消费一次（返回 {@code true} 表示已消费，宿主不再向下传递）。指针释放必须转发给捕获目标，
 * 即使指针已移出控件矩形；键盘用 {@code keyReleased} 把按住的方向键重复合并成一次提交。</p>
 *
 * <p><b>生命周期</b>：控件被隐藏/禁用/卸载、焦点范围切换、宿主窗口关闭时，必须调用统一结束入口
 * （{@code endInteraction(EndReason.HIDDEN/DISABLED)}、{@code unmount()}、{@code onFocusScopeChanged()}、
 * {@code onHostClosed()}），捕获才会释放、预览值才会按原因提交或回退。</p>
 */
public final class UiSliderExamples {

    // 示例工具类，禁止实例化
    private UiSliderExamples() {
    }

    // 示例一：普通小数滑块（精确回填、Shift 细调、方向键、Esc 取消）
    // 宿主典型接线（modifiers 由宿主从 GLFW 位掩码或按键快照取得）：
    //   slider.mousePressed(UiInputContext.pointer(x, y, 0, modifiers));
    //   slider.mouseDragged(UiInputContext.pointer(x, y, 0, modifiers));
    //   slider.mouseReleased(UiInputContext.pointer(x, y, 0, modifiers));
    //   slider.keyPressed(GLFW.GLFW_KEY_LEFT, 0, modifiers);   // Shift 按下即切细调档
    //   slider.keyReleased(GLFW.GLFW_KEY_LEFT, 0, modifiers);  // 抬起合并为一次提交
    //   slider.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);         // 回退到本次交互前的值
    public static UiScalarSlider scalarSlider(Component label, UiRect bounds,
            UiValueInteraction.Listener listener) {
        UiNumberPolicy policy = UiNumberPolicy.of(0.0D, 1.0D, 0.05D, 0.01D).withFineDragFactor(0.25D);
        UiScalarSlider slider = new UiScalarSlider(policy, UiSliderWindow.of(0.0D, 1.0D));
        slider.setStyle(UiSliderStyle.pixelDefaults());
        slider.setPainter(PixelSliderPainter.INSTANCE);
        slider.setLabel(label);
        slider.setPageStep(0.1D);
        slider.setInteractionListener(listener);
        slider.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        // 精确回填：不吸附、不回调、不产生历史；只有用户操作才走 preview/preview 合并后的 commit
        slider.setValue(0.37D);
        return slider;
    }

    // 示例二：双端区间滑块（可选端点、端点选择、精确编辑单端）
    //   slider.setEndPresent(UiRangeEnd.HIGH, false);   // 只编辑低端：缺席端不参与 min<=max 约束
    //   slider.selectEnd(UiRangeEnd.LOW);               // 键盘/精确入口都作用于当前端
    //   slider.setSelectedValue(12.0D, false);          // 精确编辑当前端 = 一次完整操作
    // 拖到对端会停在同值且不交换字段身份：低端仍是低端，便于宿主区分身份。
    public static UiRangeSlider rangeSlider(Component label, UiRect bounds, UiRangeSlider.Listener listener) {
        UiNumberPolicy policy = UiNumberPolicy.integer(0, 24000, 1000);
        UiRangeSlider slider = new UiRangeSlider(policy, UiSliderWindow.of(0.0D, 24000.0D),
                UiRangeValue.of(0.0D, 24000.0D));
        slider.setThumbWidth(3);
        slider.setPageStep(6000.0D);
        slider.setLabel(label);
        slider.setListener(listener);
        slider.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        slider.selectEnd(UiRangeEnd.LOW);
        return slider;
    }

    // 示例三：周期区间（宿主注入周期、刻度与标记）
    // 宿主按自己的时间轴把刻度换算成 tick 后传入，kit 不读取世界时间、不假定 level；
    // from > to 表示跨周期，同刻为单点，二者都由宿主按需设置。
    public static UiCyclicRange cyclicRange(Component label, UiRect bounds, double period,
            List<Double> hostTicks, UiCyclicRange.Listener listener) {
        UiCyclicRange range = new UiCyclicRange(period);
        range.setThumbWidth(3);
        range.setTickStep(period / 24.0D);
        List<UiCyclicRange.Tick> ticks = new ArrayList<>();
        for (double tick : hostTicks) {
            ticks.add(new UiCyclicRange.Tick(tick, null));
        }
        range.setTicks(ticks);
        range.setMarkers(List.of(new UiCyclicRange.Marker(0.0D, UiSliderStyle.pixelDefaults().focusColor())));
        range.setLabel(label);
        range.setListener(listener);
        range.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        range.setRange(period * 0.75D, period * 0.25D);
        range.setSelectedTick(period * 0.25D);
        return range;
    }
}
