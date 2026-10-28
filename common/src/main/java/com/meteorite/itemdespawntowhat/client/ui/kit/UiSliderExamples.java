package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * kit 数值控件的无规则语义调用示例。
 *
 * <p>三个示例只演示 kit 自身的装配与事件顺序，不涉及任何业务字段、不读取存档、配置或世界时间：
 * 宿主把示例里的标签换成自己的本地化 key、把回调接到自己的状态上即可。</p>
 *
 * <p>宿主为内容创建稳定的 {@link UiInputScope}，将实际焦点目标登记到该作用域，
 * 再通过 {@link UiInputRouter#select(UiInputScope)} 选择活动作用域。指针事件携带同一个作用域，
 * 拖动、释放与键盘事件全部交给路由器；路由器负责会话匹配、Tab 完成、Esc 取消和作用域切换收尾。</p>
 * <pre>{@code
 * UiInputScope scope = new UiInputScope(slider);
 * scope.setFocusTargets(() -> {
 *     scope.focus().beginUpdate();
 *     scope.focus().add(slider);
 *     scope.focus().endUpdate();
 * });
 * scope.refreshFocus();
 * UiInputRouter input = new UiInputRouter();
 * input.select(scope);
 * input.mousePressed(UiInputContext.pointer(scope, x, y, 0, modifiers));
 * input.mouseDragged(UiInputContext.pointer(scope, x, y, 0, modifiers), dx, dy);
 * input.mouseReleased(UiInputContext.pointer(scope, x, y, 0, modifiers));
 * input.keyPressed(GLFW.GLFW_KEY_LEFT, 0, modifiers);
 * input.keyReleased(GLFW.GLFW_KEY_LEFT, 0, modifiers);
 * input.close();
 * }</pre>
 * <p>控件隐藏、禁用由控件入口取消；内容重建时刷新焦点登记，并对卸载子树调用
 * {@code endInteraction(EndReason.UNMOUNTED)}。关屏调用 {@link UiInputRouter#close()}，
 * 作用域切换只调用 {@code select}，无需遍历控件模拟释放。</p>
 */
public final class UiSliderExamples {

    // 示例工具类，禁止实例化
    private UiSliderExamples() {
    }

    /**
     * 示例一：普通小数滑块（精确回填、Shift 细调、方向键、Esc 取消）。
     *
     * @param label    控件标签
     * @param bounds   控件矩形
     * @param listener 交互结果回调
     * @return 装配好的滑块
     */
    public static UiScalarSlider scalarSlider(Component label, UiRect bounds,
                                              UiValueInteraction.Listener listener) {
        UiNumberPolicy policy = UiNumberPolicy.of(0.0D, 1.0D, 0.05D, 0.01D).withFineDragFactor(0.25D);
        UiScalarSlider slider = new UiScalarSlider(policy, UiSliderWindow.of(0.0D, 1.0D));
        // 无主题宿主可直接用 kit 自带的像素外观（样式与画法成对）
        UiSliderSkin skin = UiSliderSkin.pixelDefaults();
        slider.setStyle(skin.style());
        slider.setPainter(skin.painter());
        slider.setLabel(label);
        slider.setPageStep(0.1D);
        slider.setInteractionListener(listener);
        slider.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        // 精确回填：不吸附、不回调、不产生历史；只有用户操作才走 preview/preview 合并后的 commit
        slider.setValue(0.37D);
        return slider;
    }

    /**
     * 示例二：双端区间滑块（可选端点、端点选择、精确编辑单端）。
     * slider.setEndPresent(UiRangeEnd.HIGH, false); // 只编辑低端：缺席端不参与 min&lt;=max 约束
     * slider.selectEnd(UiRangeEnd.LOW); // 键盘/精确入口都作用于当前端
     * slider.setSelectedValue(12.0D, false); // 精确编辑当前端 = 一次完整操作
     * 拖到对端会停在同值且不交换字段身份：低端仍是低端，便于宿主区分身份。
     *
     * @param label    控件标签
     * @param bounds   控件矩形
     * @param listener 区间结果回调
     * @return 装配好的区间滑块
     */
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

    /**
     * 示例三：周期区间（宿主注入周期、刻度与标记）。
     * 宿主按自己的时间轴把刻度换算成 tick 后传入，kit 不读取世界时间、不假定 level；
     * from > to 表示跨周期，同刻为单点，二者都由宿主按需设置。
     *
     * @param label     控件标签
     * @param bounds    控件矩形
     * @param period    周期长度
     * @param hostTicks 宿主换算后的刻度值
     * @param listener  区间结果回调
     * @return 装配好的周期区间控件
     */
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
