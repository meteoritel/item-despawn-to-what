package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphRenderer.drawBorder;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine.Kind;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine.Tone;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * 条件节点、动作参数与规则摘要共用的悬停说明面板：按语义行排版，自绘底色与描边。
 *
 * <p><b>排版</b>：标签列取所有字段行标签的最大宽度并以固定间距让出取值列，使同一面板里的取值左对齐；
 * 缩进按层级换算成像素，条目行补圆点，分区标题上方多留一段间距。折行只发生在取值列或整行文本上，
 * 折行后的续行与取值列对齐，标签不重复。面板宽度取排版后最宽的一行，受宿主可视区域与
 * {@link #setMaxWidth(int)} 约束；定位优先落在指针右下，越界时翻到左上。</p>
 *
 * <p><b>取色</b>：本类只按 {@link Tone} 从宿主调色板取色，条件与动作不各自挑颜色；
 * 标题与分区标题额外加粗，用来和取值行区分层次。</p>
 *
 * <p><b>行数</b>：面板高度受宿主可视区域约束，超出时从末尾按语义行整行裁掉并以查看详情提示收尾；
 * 间距行不占行数预算，单独出现在首尾时会被丢弃。</p>
 */
public final class UiDescriptionTooltip {
    private static final int TOOLTIP_MIN_WIDTH = 80;
    private static final int TOOLTIP_MAX_WIDTH = 250;
    private static final int TOOLTIP_PADDING = 4;
    private static final int TOOLTIP_MARGIN = 4;
    private static final int TOOLTIP_LINE_GAP = 1;
    // 每层缩进的像素宽度
    private static final int TOOLTIP_INDENT = 6;
    // 标签列与取值列之间的间距
    private static final int TOOLTIP_LABEL_GAP = 6;
    // 标签列宽度上限：超过后标签内部截断，避免把取值列挤到面板外
    private static final int TOOLTIP_LABEL_MAX = 60;
    // 取值列至少保留的像素宽度
    private static final int TOOLTIP_MIN_VALUE_WIDTH = 40;
    // 分区标题上方的额外间距
    private static final int TOOLTIP_SECTION_GAP = 3;
    // 间距行高度
    private static final int TOOLTIP_SPACER_HEIGHT = 4;
    // 条目行圆点；默认字体回落到统一字体，任何语言下都有字形
    private static final String TOOLTIP_BULLET = "· ";
    // 与鼠标的间距：优先画在指针右下，越界时翻到左上
    private static final int TOOLTIP_MOUSE_OFFSET = 12;
    private static final int TOOLTIP_LAYER = UiRenderLayers.FOREGROUND * 2 + UiRenderLayers.ICON;

    private final UiHostTheme theme;
    private final Font font;
    private int maxWidth = TOOLTIP_MAX_WIDTH;
    private int maxLines = Integer.MAX_VALUE;
    private Component overflowHint = Component.literal("…");

    /**
     * 创建使用宿主浮层配色的说明面板。
     *
     * @param theme 宿主主题，面板底色、描边与语义色都从这里取
     * @param font 折行与绘制字体
     */
    public UiDescriptionTooltip(UiHostTheme theme, Font font) {
        this.theme = theme;
        this.font = font;
    }

    /**
     * 调整面板的最大宽度，供参数较多的说明使用。
     *
     * @param width 期望的最大宽度，低于最小宽度时按最小宽度处理
     * @return 当前面板，便于链式调用
     */
    public UiDescriptionTooltip setMaxWidth(int width) {
        this.maxWidth = Math.max(TOOLTIP_MIN_WIDTH, width);
        return this;
    }

    /**
     * 限制折行后的正文行数，超出时以宿主给定的查看详情提示收尾。
     *
     * @param count 包含收尾提示的最大行数，至少为两行
     * @param hint 超出行数时的本地化提示
     */
    public void setMaxLines(int count, Component hint) {
        maxLines = Math.max(2, count);
        overflowHint = hint;
    }

    /**
     * 在宿主可视区域内绘制提示面板。
     *
     * @param graphics 绘制上下文
     * @param bounds 宿主可视区域，面板越界时按此区域翻转并钳制
     * @param lines 提示内容行，空文本行与首尾间距行会被丢弃
     * @param mouseX 鼠标屏幕 X 坐标
     * @param mouseY 鼠标屏幕 Y 坐标
     */
    public void render(GuiGraphics graphics, UiRect bounds, List<DescriptionLine> lines, int mouseX, int mouseY) {
        if (lines.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int textWidth = textWidthLimit(bounds);
        int visibleLines = Math.clamp(
                (bounds.height() - TOOLTIP_MARGIN * 2 - TOOLTIP_PADDING * 2 + TOOLTIP_LINE_GAP)
                        / (font.lineHeight + TOOLTIP_LINE_GAP), 1, maxLines);
        List<DescriptionLine> content = normalize(lines);
        List<Row> rows = layout(content, textWidth);
        if (textRowCount(rows) > visibleLines) {
            rows = truncate(content, textWidth, visibleLines);
        }
        if (rows.isEmpty()) {
            return;
        }
        // 分区间距可能让总高略微超出可视区域，按行从末尾继续收敛
        int maxHeight = bounds.height() - TOOLTIP_MARGIN * 2;
        while (rows.size() > 1 && panelHeight(rows) > maxHeight) {
            rows.removeLast();
        }
        int contentWidth = 0;
        for (Row row : rows) {
            contentWidth = Math.max(contentWidth, row.contentWidth());
        }
        int minWidth = Math.min(TOOLTIP_MIN_WIDTH, bounds.width() - TOOLTIP_MARGIN * 2);
        int widthLimit = Math.min(maxWidth, bounds.width() - TOOLTIP_MARGIN * 2);
        int width = Math.clamp(contentWidth + TOOLTIP_PADDING * 2, minWidth, widthLimit);
        int height = panelHeight(rows);
        int x = resolveX(bounds, width, mouseX);
        int y = resolveY(bounds, height, mouseY);
        int labelColor = theme.palette().canvasPickerTextMuted();
        List<Row> frame = rows;
        UiRenderLayers.draw(graphics, TOOLTIP_LAYER, () -> {
            graphics.fill(x, y, x + width, y + height, theme.palette().canvasPickerFill());
            drawBorder(graphics, new UiRect(x, y, width, height), theme.palette().canvasPickerBorder());
            int textY = y + TOOLTIP_PADDING;
            for (int index = 0; index < frame.size(); index++) {
                Row row = frame.get(index);
                textY += row.topGap();
                if (row.label() != null) {
                    graphics.drawString(font, row.label(), x + row.labelX(), textY, labelColor, false);
                }
                if (row.text() != null) {
                    graphics.drawString(font, row.text(), x + row.textX(), textY, row.color(), false);
                }
                textY += row.height();
                if (index < frame.size() - 1) {
                    textY += TOOLTIP_LINE_GAP;
                }
            }
        });
    }

    // 折行宽度上限：由面板可用宽度决定换行位置，避免折行后再次超出屏幕
    private int textWidthLimit(UiRect bounds) {
        int available = Math.min(maxWidth, bounds.width() - TOOLTIP_MARGIN * 2);
        return Math.max(1, available - TOOLTIP_PADDING * 2);
    }

    // 去掉空文本行与多余的间距行：间距行只保留一个，且不出现在首尾
    private static List<DescriptionLine> normalize(List<DescriptionLine> lines) {
        List<DescriptionLine> result = new ArrayList<>();
        for (DescriptionLine line : lines) {
            if (line.kind() == Kind.SPACER) {
                if (result.isEmpty() || result.getLast().kind() == Kind.SPACER) {
                    continue;
                }
                result.add(line);
                continue;
            }
            if (line.text().getString().isBlank()) {
                continue;
            }
            result.add(line);
        }
        while (!result.isEmpty() && result.getLast().kind() == Kind.SPACER) {
            result.removeLast();
        }
        return result;
    }

    // 超出预算时按像素行截断并追加查看详情提示；间距行不占预算，也不留在末尾
    private List<Row> truncate(List<DescriptionLine> content, int textWidth, int visibleLines) {
        List<Row> hint = layout(List.of(DescriptionLine.note(overflowHint, 0)), textWidth);
        int hintRows = Math.min(textRowCount(hint), visibleLines);
        int budget = Math.max(1, visibleLines - hintRows);
        List<Row> rows = layout(content, textWidth);
        int kept = 0;
        int used = 0;
        for (Row row : rows) {
            if (used >= budget) {
                break;
            }
            if (!row.spacer()) {
                used++;
            }
            kept++;
        }
        List<Row> result = new ArrayList<>(rows.subList(0, kept));
        while (!result.isEmpty() && result.getLast().spacer()) {
            result.removeLast();
        }
        result.addAll(hint.subList(0, hintRows));
        return result;
    }

    // 标签列宽：取字段行标签的最大宽度，并给取值列保留足够宽度
    private int labelColumn(List<DescriptionLine> lines, int textWidth) {
        int column = 0;
        for (DescriptionLine line : lines) {
            if (line.kind() == Kind.FIELD && line.label() != null) {
                column = Math.max(column, font.width(line.label()));
            }
        }
        column = Math.min(column, TOOLTIP_LABEL_MAX);
        return Math.clamp(column, 0, Math.max(0, textWidth - TOOLTIP_MIN_VALUE_WIDTH));
    }

    // 按语义行排成像素行；标签列宽由整块面板统一决定
    private List<Row> layout(List<DescriptionLine> lines, int textWidth) {
        int labelColumn = labelColumn(lines, textWidth);
        List<Row> rows = new ArrayList<>();
        int labelColor = theme.palette().canvasPickerTextMuted();
        boolean first = true;
        for (DescriptionLine line : lines) {
            if (line.kind() == Kind.SPACER) {
                rows.add(new Row(null, 0, 0, null, 0, 0, 0, TOOLTIP_SPACER_HEIGHT, 0, true));
                first = false;
                continue;
            }
            int left = TOOLTIP_PADDING + line.indent() * TOOLTIP_INDENT;
            int color = colorOf(line.tone());
            Component text = styled(line);
            int topGap = line.kind() == Kind.SECTION && !first ? TOOLTIP_SECTION_GAP : 0;
            if (line.kind() == Kind.FIELD) {
                int valueX = left + labelColumn + TOOLTIP_LABEL_GAP;
                int valueWidth = Math.max(TOOLTIP_MIN_VALUE_WIDTH,
                        textWidth - (left - TOOLTIP_PADDING) - labelColumn - TOOLTIP_LABEL_GAP);
                List<FormattedCharSequence> values = font.split(text, valueWidth);
                FormattedCharSequence label = line.label() == null ? null
                        : firstRow(font.split(line.label(), Math.max(1, labelColumn)));
                int labelWidth = label == null ? 0 : font.width(label);
                for (int index = 0; index < values.size(); index++) {
                    rows.add(new Row(index == 0 ? label : null, index == 0 ? left : 0, labelWidth,
                            values.get(index), valueX, font.width(values.get(index)), color, font.lineHeight,
                            index == 0 ? topGap : 0, false));
                }
            } else if (line.kind() == Kind.ENTRY) {
                Component bullet = Component.literal(TOOLTIP_BULLET).withStyle(style -> style.withColor(labelColor));
                Component entry = Component.empty().append(bullet).append(text);
                appendWrapped(rows, entry, left, textWidth, color, topGap);
            } else {
                appendWrapped(rows, text, left, textWidth, color, topGap);
            }
            first = false;
        }
        return rows;
    }

    // 整行折行：续行与行首左对齐
    private void appendWrapped(List<Row> rows, Component text, int left, int textWidth, int color, int topGap) {
        int wrapWidth = Math.max(TOOLTIP_MIN_VALUE_WIDTH, textWidth - (left - TOOLTIP_PADDING));
        List<FormattedCharSequence> sequences = font.split(text, wrapWidth);
        for (int index = 0; index < sequences.size(); index++) {
            rows.add(new Row(null, 0, 0, sequences.get(index), left, font.width(sequences.get(index)), color,
                    font.lineHeight, index == 0 ? topGap : 0, false));
        }
    }

    // 标题与分区标题加粗；取值不加粗，避免整块面板过重
    private static Component styled(DescriptionLine line) {
        if (line.tone() == Tone.TITLE || line.tone() == Tone.SECTION) {
            return line.text().copy().withStyle(ChatFormatting.BOLD);
        }
        return line.text();
    }

    private int colorOf(Tone tone) {
        UiHostPalette palette = theme.palette();
        return switch (tone) {
            case TITLE, SECTION -> palette.tooltipTitle();
            case VALUE -> palette.canvasPickerText();
            case METRIC -> palette.tooltipMetric();
            case REFERENCE -> palette.tooltipReference();
            case NOTE -> palette.canvasPickerTextMuted();
            case DANGER -> palette.canvasTextDanger();
            case ALL -> palette.canvasAllText();
            case ANY -> palette.canvasAnyText();
            case NOT -> palette.canvasNotText();
        };
    }

    private static @Nullable FormattedCharSequence firstRow(List<FormattedCharSequence> sequences) {
        return sequences.isEmpty() ? null : sequences.getFirst();
    }

    // 正文行数：间距行不计入预算
    private static int textRowCount(List<Row> rows) {
        int count = 0;
        for (Row row : rows) {
            if (!row.spacer()) {
                count++;
            }
        }
        return count;
    }

    private static int panelHeight(List<Row> rows) {
        int height = TOOLTIP_PADDING * 2;
        for (Row row : rows) {
            height += row.height() + row.topGap();
        }
        return height + Math.max(0, rows.size() - 1) * TOOLTIP_LINE_GAP;
    }

    private static int resolveX(UiRect bounds, int width, int mouseX) {
        int left = mouseX + TOOLTIP_MOUSE_OFFSET;
        if (left + width > bounds.right() - TOOLTIP_MARGIN) {
            left = mouseX - TOOLTIP_MOUSE_OFFSET - width;
        }
        return Math.clamp(left, bounds.x() + TOOLTIP_MARGIN,
                Math.max(bounds.x() + TOOLTIP_MARGIN, bounds.right() - TOOLTIP_MARGIN - width));
    }

    private static int resolveY(UiRect bounds, int height, int mouseY) {
        int top = mouseY + TOOLTIP_MOUSE_OFFSET;
        if (top + height > bounds.bottom() - TOOLTIP_MARGIN) {
            top = mouseY - TOOLTIP_MOUSE_OFFSET - height;
        }
        return Math.clamp(top, bounds.y() + TOOLTIP_MARGIN,
                Math.max(bounds.y() + TOOLTIP_MARGIN, bounds.bottom() - TOOLTIP_MARGIN - height));
    }

    /**
     * 排好版的一行：标签与正文都已折行，偏移相对面板内容区。
     *
     * @param label 标签序列；没有标签时为 {@code null}
     * @param labelX 标签相对面板左边的偏移
     * @param labelWidth 标签像素宽度
     * @param text 正文序列；间距行为 {@code null}
     * @param textX 正文相对面板左边的偏移
     * @param textWidth 正文像素宽度
     * @param color 正文颜色
     * @param height 行高
     * @param topGap 行上方额外间距
     * @param spacer 是否为间距行
     */
    private record Row(@Nullable FormattedCharSequence label, int labelX, int labelWidth,
                       @Nullable FormattedCharSequence text, int textX, int textWidth,
                       int color, int height, int topGap, boolean spacer) {

        // 本行对面板宽度的贡献：标签末端与正文末端取大者
        private int contentWidth() {
            int textEnd = textX + textWidth;
            if (label == null) {
                return textEnd - TOOLTIP_PADDING;
            }
            return Math.max(labelX + labelWidth, textEnd) - TOOLTIP_PADDING;
        }
    }
}
