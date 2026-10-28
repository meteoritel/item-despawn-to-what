package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiJsonText;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 只读代码视图：带行号栏与语法色的 JSON 正文，供来源视图弹窗、条件树 JSON 视图与损坏规则详情共用。
 *
 * <p>一个视图由若干 {@link Section} 组成，每段可选一段标题（层名与数据包）与副标题（文件路径），
 * 正文按 {@link UiJsonText} 的分类着色，配色取自主题调色板的 {@code code*} 项；三处入口因此共用
 * 同一套「键 / 字符串 / 数字 / 字面量 / 标点」规则，新增只读 JSON 入口也应复用本控件。</p>
 *
 * <p>行号按代码块重新计数；超宽的行按可用宽度折行，续行不再重复行号，与常见 IDE 的软换行一致。
 * 视图不承载编辑：只响应滚轮与方向键滚动、获得焦点时绘制焦点轮廓。</p>
 */
public final class UiCodeView implements UiWidget, UiFocusTarget {

    /**
     * 一个代码块。
     *
     * @param title  标题，为空时不占行；用于层名与数据包
     * @param detail 副标题，为空时不占行；用于文件路径
     * @param body   正文，按行绘制 JSON 语法色
     */
    public record Section(@Nullable Component title, @Nullable Component detail, String body) {

        public Section {
            body = body == null ? "" : body;
        }
    }

    // 左右内边距
    private static final int PADDING_X = 3;
    // 上下内边距
    private static final int PADDING_Y = 2;
    // 内容超出可视高度时的滚动条宽度
    private static final int SCROLLBAR_WIDTH = 3;
    // 行号栏与正文之间的间距
    private static final int GUTTER_GAP = 4;
    // 一次滚轮的滚动行数
    private static final int WHEEL_ROWS = 3;

    // 行的绘制角色
    private enum RowKind {
        // 代码块标题
        TITLE,
        // 代码块副标题
        DETAIL,
        // 正文行
        CODE,
        // 代码块之间的空行
        GAP
    }

    // 一行绘制单元：标题与正文共用 offBegin / offEnd，正文行另带行号
    private static final class Row {
        private final RowKind kind;
        private final int section;
        private final int begin;
        private final int end;
        // 逻辑行号；折行的续行为 0，表示不再画行号
        private int lineNumber;

        private Row(RowKind kind, int section, int begin, int end) {
            this.kind = kind;
            this.section = section;
            this.begin = begin;
            this.end = end;
        }
    }

    private final Font font;
    // 注入的宿主主题
    private final UiHostTheme theme;
    // 按 JsonText 类别取色的颜色表
    private final int[] kindColors;
    // 代码块及其正文的字符类别
    private List<Section> sections = List.of();
    private List<byte[]> kinds = List.of();
    // 已布局的行与对应的正文宽度
    private List<Row> rows = List.of();
    private int rowsWidth = -1;
    // 空正文提示
    private @Nullable Component emptyHint;
    // 无障碍名称
    private @Nullable Component accessibleName;
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 垂直滚动偏移（像素）
    private int scrollOffset;
    private boolean lineNumbers = true;
    private boolean visible = true;
    private final UiFocusState focusState = new UiFocusState();

    /**
     * 创建只读代码视图。
     *
     * @param theme 宿主主题
     * @param font  渲染字体
     */
    public UiCodeView(UiHostTheme theme, Font font) {
        this.theme = UiHostThemes.require(theme);
        this.font = font;
        this.kindColors = new int[UiJsonText.KIND_COUNT];
        kindColors[UiJsonText.PUNCTUATION] = theme.palette().codePunctuation();
        kindColors[UiJsonText.KEY] = theme.palette().codeKey();
        kindColors[UiJsonText.STRING] = theme.palette().codeString();
        kindColors[UiJsonText.NUMBER] = theme.palette().codeNumber();
        kindColors[UiJsonText.LITERAL] = theme.palette().codeLiteral();
    }

    // ---- 配置 ----

    /**
     * 替换全部代码块：重新分类正文并把滚动位置退回顶部。
     */
    public UiCodeView setSections(List<Section> sections) {
        this.sections = List.copyOf(sections);
        List<byte[]> next = new ArrayList<>(this.sections.size());
        for (Section section : this.sections) {
            next.add(UiJsonText.classify(section.body()));
        }
        this.kinds = List.copyOf(next);
        this.rows = List.of();
        this.rowsWidth = -1;
        scrollToTop();
        return this;
    }

    public UiCodeView setEmptyHint(@Nullable Component emptyHint) {
        this.emptyHint = emptyHint;
        return this;
    }

    public UiCodeView setLineNumbers(boolean lineNumbers) {
        this.lineNumbers = lineNumbers;
        this.rows = List.of();
        this.rowsWidth = -1;
        return this;
    }

    public UiCodeView setAccessibleName(@Nullable Component accessibleName) {
        this.accessibleName = accessibleName;
        return this;
    }

    public UiCodeView setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * 全部正文按行拼接，供整段复制使用。
     */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (Section section : sections) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(section.body());
        }
        return text.toString();
    }

    // ---- 布局 ----

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        clampScroll();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    // 行号栏宽度：按最长的行号宽度加间距
    private int gutterWidth() {
        if (!lineNumbers || sections.isEmpty()) {
            return 0;
        }
        int longest = 0;
        for (Section section : sections) {
            longest = Math.max(longest, lineCount(section.body()));
        }
        return font.width(String.valueOf(Math.max(1, longest))) + GUTTER_GAP;
    }

    // 正文的行数：空正文记 0 行
    private static int lineCount(String body) {
        if (body.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int index = 0; index < body.length(); index++) {
            if (body.charAt(index) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    // 正文宽度变化时重算布局
    private void ensureRows(int codeWidth) {
        if (codeWidth == rowsWidth) {
            return;
        }
        this.rowsWidth = codeWidth;
        this.rows = buildRows(Math.max(1, codeWidth));
        clampScroll();
    }

    private List<Row> buildRows(int codeWidth) {
        List<Row> result = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            if (index > 0) {
                result.add(new Row(RowKind.GAP, index, 0, 0));
            }
            if (section.title() != null) {
                wrap(result, RowKind.TITLE, index, section.title().getString(), codeWidth);
            }
            if (section.detail() != null) {
                wrap(result, RowKind.DETAIL, index, section.detail().getString(), codeWidth);
            }
            appendBody(result, index, section.body(), codeWidth);
        }
        return result;
    }

    // 标题类文本按宽度折行
    private void wrap(List<Row> result, RowKind kind, int section, String text, int width) {
        if (text.isEmpty()) {
            result.add(new Row(kind, section, 0, 0));
            return;
        }
        int before = result.size();
        font.getSplitter().splitLines(text, width, Style.EMPTY, true,
                (style, begin, end) -> result.add(new Row(kind, section, begin, end)));
        if (result.size() == before) {
            result.add(new Row(kind, section, 0, text.length()));
        }
    }

    // 正文逐逻辑行折行；只有逻辑行的首行带行号
    private void appendBody(List<Row> result, int section, String body, int width) {
        if (body.isEmpty()) {
            return;
        }
        int lineNumber = 1;
        int start = 0;
        while (true) {
            int next = body.indexOf('\n', start);
            int end = next < 0 ? body.length() : next;
            int offset = start;
            int before = result.size();
            if (end == start) {
                result.add(new Row(RowKind.CODE, section, start, start));
            } else {
                String line = body.substring(start, end);
                font.getSplitter().splitLines(line, width, Style.EMPTY, true,
                        (style, begin, bend) -> result.add(new Row(RowKind.CODE, section, offset + begin, offset + bend)));
            }
            if (result.size() > before) {
                result.get(before).lineNumber = lineNumber;
            }
            lineNumber++;
            if (next < 0) {
                return;
            }
            start = next + 1;
        }
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        theme.drawInset(graphics, bounds);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1,
                theme.palette().slotInner());
        int innerX = bounds.x() + PADDING_X;
        int innerY = bounds.y() + PADDING_Y;
        int innerWidth = innerWidth();
        int innerHeight = innerHeight();
        int gutter = gutterWidth();
        int codeX = innerX + gutter;
        int rowHeight = font.lineHeight;
        ensureRows(Math.max(1, innerWidth - gutter));
        graphics.enableScissor(innerX, innerY, innerX + innerWidth + SCROLLBAR_WIDTH, innerY + innerHeight);
        try {
            if (gutter > 0) {
                int lineX = innerX + gutter - GUTTER_GAP + 1;
                graphics.fill(lineX, innerY, lineX + 1, innerY + innerHeight, theme.palette().codeGutterLine());
            }
            if (rows.isEmpty()) {
                if (emptyHint != null) {
                    graphics.drawString(renderFont, emptyHint, innerX, innerY, theme.palette().codeGutterText(), false);
                }
            } else {
                renderRows(graphics, renderFont, innerX, codeX, innerY, innerWidth, rowHeight);
            }
        } finally {
            graphics.disableScissor();
        }
        renderScrollbar(graphics, innerY, innerHeight);
        if (isFocusVisible()) {
            theme.drawFocusOutline(graphics, bounds);
        }
    }

    // 只绘制可视区内的行
    private void renderRows(GuiGraphics graphics, Font renderFont, int innerX, int codeX, int innerY,
                            int innerWidth, int rowHeight) {
        int first = Math.max(0, scrollOffset / rowHeight);
        int last = Math.min(rows.size(), first + innerHeight() / rowHeight + 2);
        int gutter = gutterWidth();
        for (int index = first; index < last; index++) {
            Row row = rows.get(index);
            int y = innerY + index * rowHeight - scrollOffset;
            Section section = sections.get(row.section);
            switch (row.kind) {
                case TITLE, DETAIL -> {
                    graphics.fill(innerX, y, innerX + innerWidth, y + rowHeight, theme.palette().codeSectionFill());
                    Component line = row.kind == RowKind.TITLE ? section.title() : section.detail();
                    if (line == null) {
                        continue;
                    }
                    int color = row.kind == RowKind.TITLE
                            ? theme.palette().codeSectionText() : theme.palette().codePathText();
                    String text = line.getString();
                    graphics.drawString(renderFont, text.substring(row.begin, row.end), codeX, y, color, false);
                }
                case CODE -> {
                    if (gutter > 0 && row.lineNumber > 0) {
                        String label = String.valueOf(row.lineNumber);
                        graphics.drawString(renderFont, label, codeX - GUTTER_GAP - font.width(label), y,
                                theme.palette().codeGutterText(), false);
                    }
                    UiJsonText.paint(graphics, renderFont, section.body(), kinds.get(row.section), kindColors,
                            codeX, y, row.begin, row.end);
                }
                case GAP -> {
                }
            }
        }
    }

    // 内容超出可视高度时在右侧绘制滚动条
    private void renderScrollbar(GuiGraphics graphics, int innerY, int innerHeight) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int x = bounds.right() - SCROLLBAR_WIDTH - 1;
        theme.drawScrollTrack(graphics, new UiRect(x, innerY, SCROLLBAR_WIDTH, innerHeight));
        int total = totalHeight();
        int thumbHeight = Math.max(8, innerHeight * innerHeight / Math.max(1, total));
        int thumbY = innerY + (innerHeight - thumbHeight) * scrollOffset / maxScroll;
        theme.drawScrollThumb(graphics, new UiRect(x, thumbY, SCROLLBAR_WIDTH, thumbHeight));
    }

    // ---- 输入 ----

    @Override
    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        context.scope().pointerHit(this);
        return true;
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        this.scrollOffset = Math.clamp(
                scrollOffset - (int) Math.round(scrollY * font.lineHeight * WHEEL_ROWS), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !isFocused()) {
            return false;
        }
        int maxScroll = maxScroll();
        int step = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> -font.lineHeight;
            case GLFW.GLFW_KEY_DOWN -> font.lineHeight;
            case GLFW.GLFW_KEY_PAGE_UP -> -innerHeight();
            case GLFW.GLFW_KEY_PAGE_DOWN -> innerHeight();
            case GLFW.GLFW_KEY_HOME -> -maxScroll;
            case GLFW.GLFW_KEY_END -> maxScroll;
            default -> 0;
        };
        if (step == 0) {
            return false;
        }
        this.scrollOffset = Math.clamp(scrollOffset + step, 0, maxScroll);
        return true;
    }

    // ---- 焦点 ----

    @Override
    public UiFocusState focusState() {
        return focusState;
    }

    @Override
    public boolean canFocus() {
        return visible;
    }


    @Override
    public boolean isFocused() {
        return focusState.isFocused();
    }

    @Override
    public boolean activate() {
        return false;
    }

    @Override
    public @Nullable Component accessibleName() {
        return accessibleName;
    }

    // ---- 滚动 ----

    // 回到顶部
    public void scrollToTop() {
        this.scrollOffset = 0;
    }

    // 文本可用宽度（右侧预留滚动条）
    private int innerWidth() {
        return Math.max(8, bounds.width() - PADDING_X * 2 - SCROLLBAR_WIDTH);
    }

    // 文本可视高度
    private int innerHeight() {
        return Math.max(1, bounds.height() - PADDING_Y * 2);
    }

    // 全部行的总高度
    private int totalHeight() {
        return rows.size() * font.lineHeight;
    }

    private int maxScroll() {
        return Math.max(0, totalHeight() - innerHeight());
    }

    private void clampScroll() {
        this.scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }
}
