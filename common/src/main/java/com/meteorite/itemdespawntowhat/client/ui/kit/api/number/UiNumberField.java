package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.UiTextValue;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * 标量轨道与精确文本的组合控件；两个子控件各自保有焦点和输入状态。
 * 合法域、显示窗口与解析由调用方装配，字段空值和提交业务通过回调注入。
 */
public final class UiNumberField implements UiWidget {
    /** 单行编辑端口；原版编辑模型与主题由宿主实现。 */
    public interface Editor extends UiWidget, UiTextValue {
        /** 设置单行 Enter 提交通知。 */
        Editor setOnCommit(Consumer<String> listener);

        /** 设置 Esc 取消通知。 */
        Editor setOnCancel(Runnable listener);

        /** 设置文本会话结束通知。 */
        Editor setOnInteractionEnd(@Nullable Consumer<UiInteractionSession.EndReason> listener);
    }

    /** 轨道和编辑框之间的间距，以及极窄布局中轨道的最小宽度。 */
    public record Metrics(int gap, int minimumSliderWidth) {
    }

    private final UiScalarSlider slider;
    private final Editor editor;
    private final UiValueTextBinding<Double, String> binding;
    private final Function<String, @Nullable Double> parse;
    private final Runnable beforeTextCommit;
    private final @Nullable Double emptyPreview;
    private final @Nullable Runnable emptyCommitted;
    private final Metrics metrics;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private int inputWidth;

    /**
     * 注入实际子控件及转换；emptyPreview 为空时空串保持解析失败，emptyCommitted 为空时无缺席提交通道。
     * beforeTextCommit 只记录显式提交意图，committed 负责一次成功数值操作的宿主通知。
     */
    public UiNumberField(UiScalarSlider slider, Editor editor, Function<String, @Nullable Double> parse,
                          Function<Double, String> format, Runnable committed, Runnable beforeTextCommit,
                          @Nullable Double emptyPreview, @Nullable Runnable emptyCommitted, Metrics metrics) {
        this.slider = slider;
        this.editor = editor;
        this.parse = parse;
        this.beforeTextCommit = beforeTextCommit;
        this.emptyPreview = emptyPreview;
        this.emptyCommitted = emptyCommitted;
        this.metrics = metrics;
        binding = new UiValueTextBinding<>(slider::value, slider::setValue, editor::value, editor::setValue,
                parse, format, committed);
        slider.setInteractionListener(new UiValueInteraction.Listener<>() {
            @Override
            public void began(Double startValue) {
                binding.beginValueChange();
            }

            @Override
            public void previewed(Double startValue, Double currentValue) {
                binding.previewValue();
                slider.setError(false);
            }

            @Override
            public void committed(Double startValue, Double currentValue) {
                slider.setError(false);
                binding.commitValue();
            }

            @Override
            public void cancelled(Double startValue, Double restoredValue) {
                binding.cancelValue();
                refreshTextError();
            }

            @Override
            public void finished() {
                binding.finishValueChange();
            }
        });
        editor.setOnCommit(this::commitText);
        editor.setOnCancel(() -> {
            binding.cancelText();
            refreshTextError();
        });
        editor.setOnValueChanged(this::previewText);
        editor.setOnInteractionEnd(this::finishText);
    }

    /** 设置编辑框的期望宽度；实际布局为轨道保留最小宽度。 */
    public void setInputWidth(int width) {
        inputWidth = Math.max(0, width);
    }

    /** 接受当前文本为新的业务回填基线，不产生提交。 */
    public void acceptText() {
        binding.acceptText();
    }

    /** 静默回填文本并保留原文。 */
    public void setText(String text) {
        binding.setText(text);
    }

    /** 显式提交精确文本；空串缺席和解析失败分别处理，失败保留输入原文。 */
    public boolean commitText(String text) {
        beforeTextCommit.run();
        boolean accepted;
        if (text.isBlank() && emptyCommitted != null) {
            binding.setText(text);
            commitEmpty(emptyCommitted);
            accepted = true;
        } else {
            accepted = binding.commitText(text);
        }
        slider.setError(!accepted);
        return accepted;
    }

    private void previewText() {
        if (!editor.isFocused()) return;
        boolean accepted = binding.previewText();
        if (!accepted && editor.value().isBlank() && emptyPreview != null) {
            slider.setValue(emptyPreview);
            slider.setError(false);
        } else {
            slider.setError(!accepted);
        }
    }

    private void finishText(UiInteractionSession.EndReason reason) {
        if (reason.cancels()) binding.cancelText();
        else if (editor.value().isBlank() && emptyCommitted != null) commitEmpty(emptyCommitted);
        else binding.finishText();
        refreshTextError();
    }

    private void commitEmpty(Runnable committed) {
        binding.rollbackTextPreview();
        committed.run();
        binding.acceptText();
    }

    private void refreshTextError() {
        slider.setError(!editor.value().isBlank() && parse.apply(editor.value()) == null);
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int minimumSlider = Math.min(bounds.width(), metrics.minimumSliderWidth());
        int gap = Math.min(metrics.gap(), bounds.width() - minimumSlider);
        int sliderWidth = Math.max(minimumSlider, bounds.width() - inputWidth - gap);
        int editorWidth = Math.max(0, bounds.width() - sliderWidth - gap);
        slider.setBounds(x, y, sliderWidth, bounds.height());
        editor.setBounds(x + sliderWidth + gap, y, editorWidth, bounds.height());
    }

    /** {@inheritDoc} */
    @Override
    public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        slider.render(graphics, font, pointer);
        editor.render(graphics, font, pointer);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        return slider.mousePressed(context) || editor.mousePressed(context);
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dx, double dy) {
        return editor.mouseDragged(context, dx, dy) || slider.mouseDragged(context, dx, dy);
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        boolean handled = slider.mouseReleased(context);
        return editor.mouseReleased(context) || handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return slider.isFocused() ? slider.keyPressed(keyCode, scanCode, modifiers)
                : editor.isFocused() && editor.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return slider.isFocused() && slider.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return editor.isFocused() && editor.charTyped(codePoint, modifiers);
    }

    /** 以视觉顺序追加两个真实 Tab 目标，组合本身不持有额外焦点。 */
    public void addFocusTargets(List<UiFocusTarget> out) {
        out.add(slider);
        out.add(editor);
    }

    /** 分别设置精确编辑与轨道资格，供固定域保留禁用轨道。 */
    public void setEnabled(boolean editable, boolean sliderEnabled) {
        slider.setEnabled(sliderEnabled);
        editor.setEditable(editable);
    }

    /** 按统一原因结清子控件交互；无净变化也由监听器收束绑定快照。 */
    public void endInteraction(UiInteractionSession.EndReason reason) {
        slider.endInteraction(reason);
        editor.endInteraction(reason);
    }
    /** 使用当前布局解析实际命中，保留容器归属与叶子身份。 */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = UiPointerHit.first(x, y, editor, slider);
        return hit == null ? null : hit.within(this);
    }

}
