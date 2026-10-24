package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSpinner;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons.Mark;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 目录选择页（宿主侧通用控件）：物品、标签、流体等所有注册表目录共用同一套页面样板。
 *
 * <p>页面自上而下两段：搜索框（必要时带范围轮询按钮）、可滚动的条目列表；底部一行由宿主弹窗的按钮行托管
 * （见 {@link UiFooterHost}），从左到右是只画上一页/下一页标记的翻页按钮与页码、状态文字与「清空已选」链接，最右才是弹窗自己的按钮。
 * 条目按「图标 + 名称」逐行排布并按页宽分列，名称超宽时悬停滚动；已选条目整行高亮并在行尾打勾，
 * 键盘方向键移动光标、空格切换选择、Enter 激活、PageUp/PageDown 翻页。</p>
 *
 * <p>悬停提示只给条目本身的信息：显示名与注册名各占一行（宿主附加说明另起一行），按提示宽度换行后再
 * 交给原版定位——原版的单个 Component 重载不处理 `\n`，因此必须用拆行后的列表重载。</p>
 *
 * <p>面板不知道任何目录协议类型：条目、图标与全部本地化文本由宿主提供。搜索与翻页只发出意图回调
 * （宿主据此走既有目录请求链路），快速切换时由宿主丢弃过时响应。</p>
 *
 * <p>条目策略由宿主通过 {@link EntryPolicy} 注入：宿主在分页前按 {@link EntryPolicy#hidden(String)} 排除
 * 技术性条目与空/流动流体，并按 {@link EntryPolicy#scope()} 取主分类或标签；页面只负责范围按钮的轮询与回写，
 * 因此页码、总数与可见条目始终一致。条目源通过 {@link #setEntrySource(Supplier)} 注入，
 * 宿主返回同一缓存实例时不会重建列表。</p>
 */
public final class UiCatalogGrid implements UiWidget, UiFocusTarget, UiFooterHost {

    /**
     * 交互意图回调：页面只上报意图、不直接改宿主数据，默认实现全部留空，宿主按需覆写。
     * 搜索接受文本、翻页、选择集变化、Enter 或双击条目时分别回调。
     */
    @SuppressWarnings("unused")
    public interface Listener {

        // 搜索意图（每次接受文本变化时发出；过滤缓存由宿主处理）
        default void onSearch(String filter) {
        }

        // 翻页意图：-1 上一页 / +1 下一页
        default void onPageChange(int delta) {
        }

        // 选择变化
        default void onSelectionChanged(List<String> selection) {
        }

        // 激活条目（Enter 或双击）
        default void onActivate(String id) {
        }
    }

    /** 条目范围：只列主分类条目、只列标签或两者同列；范围按钮在此三值间轮询并回写宿主策略。 */
    public enum Scope {
        ITEM, TAG, BOTH
    }

    /**
     * 条目策略：默认不展示哪些条目，以及工具栏上「主分类 / 标签 / 一起」的当前范围。
     * <p>宿主在分页前用 {@link #hidden(String)} 过滤并按 {@link #scope()} 取主分类或标签，
     * 范围状态由页面按钮回写，双方看到同一份事实。</p>
     */
    public interface EntryPolicy {

        // 该 id 是否属于默认不展示的内容（技术性条目、空与流动流体…）
        boolean hidden(String id);

        Scope scope();

        // 工具栏范围按钮回写
        void setScope(Scope scope);

        // 是否出现范围按钮：只有同时存在主分类与标签的目录页才需要
        default boolean scoped() {
            return false;
        }
    }

    /**
     * 一条目录条目：{@code id} 用于选择集与激活回调，{@code icon} 为空时回落文字槽，
     * {@code subLabel} 只供宿主搜索匹配、页面不渲染。
     */
    public record Entry(String id, Component label, @Nullable Component subLabel, @Nullable UiIcon icon) {
    }

    /**
     * 目录页的宿主本地化文本：搜索/空/加载/错误/选择状态、翻页按钮的标记贴图、
     * 范围按钮的短标签与对应悬停文案、清空链接；{@code withEmpty} 返回仅替换空态文案的副本。
     */
    public record Texts(Component searchHint, Component empty, Component loading, Component error,
                        Component prevPage, Component nextPage, Component selection, Component scopeItem,
                        Component scopeTag, Component scopeBoth, Component scopeItemLabel,
                        Component scopeTagLabel, Component scopeBothLabel, Component clear) {

        // 只替换空结果文案
        public Texts withEmpty(Component next) {
            return new Texts(searchHint, next, loading, error, prevPage, nextPage, selection, scopeItem,
                    scopeTag, scopeBoth, scopeItemLabel, scopeTagLabel, scopeBothLabel, clear);
        }
    }

    // 计划规定的分页上限
    public static final int PAGE_SIZE = 200;

    // 工具栏（搜索框 + 显示全部开关）
    private static final int TOOLBAR_HEIGHT = 16;
    // 底部状态下标与翻页按钮
    private static final int FOOTER_HEIGHT = 16;
    // 列表行高：与原版物品图标同高，图标正好放得下
    private static final int ROW_HEIGHT = 16;
    // 一列至少这么宽才拆下一列；越窄名称越难读
    private static final int MIN_COLUMN_WIDTH = 132;
    private static final int MAX_COLUMNS = 3;
    // 图标槽与图标、文字之间的间距
    private static final int ICON_BOX = 16;
    private static final int ICON_GAP = 5;
    private static final int PADDING_X = 3;
    // 行尾标记（不可用 / 已选勾）的保留宽度
    private static final int MARK_WIDTH = 10;
    // 列表内凹面板的边框与滚动条
    private static final int LIST_BORDER = 2;
    private static final int SCROLLBAR_WIDTH = 4;
    // 翻页按钮固定宽度、页码与清空链接的左右内边距、页码栏最小宽度
    private static final int PAGER_PADDING = 8;
    private static final int PAGER_SYMBOL_WIDTH = 14;
    private static final int PAGE_LABEL_MIN_WIDTH = 30;
    // 工具栏范围按钮：一个按钮轮询「只主分类 → 只标签 → 一起」，按钮上写当前范围的短标签、完整文案进悬停说明
    private static final int SCOPE_SIDE_PADDING = 4;
    // 滚动条：视觉只有 4 像素，可抓取范围外扩；滑块最矮 8 像素
    private static final int SCROLLBAR_GRAB_MARGIN = 3;
    private static final int MIN_SCROLL_THUMB = 8;
    // 悬停提示的换行宽度：超过就折行，避免一条横穿整屏
    private static final int TOOLTIP_MAX_WIDTH = 240;
    // 状态块之间的分隔符
    private static final String STATUS_SEPARATOR = "  ·  ";
    // 翻页按钮之间的间距
    private static final int GAP = 2;
    // 滚轮一格的滚动行数：页面最多 200 条，一格一行太慢
    private static final int WHEEL_ROWS = 3;

    private final Font font;
    private final boolean multiSelect;
    private final Texts texts;
    private final Listener listener;
    private final UiTextInput search;
    private final List<Entry> entries = new ArrayList<>();
    // 条目集合代次：状态文案的「空结果」分支与列表内容都只随集合替换变化
    private int entriesVersion;
    private final List<String> selection = new ArrayList<>();
    // 行标签文本缓存：条目对象由宿主目录缓存持有，命中时不再把 Component 逐帧取成 String
    private final Map<Entry, String> rowLabels = new IdentityHashMap<>();
    private @Nullable Object rowLabelsLanguage;

    private @Nullable Supplier<@Nullable List<Entry>> entrySource;
    private @Nullable List<Entry> sourceCache;
    private @Nullable EntryPolicy policy;
    private @Nullable Runnable onRetry;
    private Function<String, Component> tooltipSuffix = ignored -> null;
    private Function<String, @Nullable Component> unavailableReason = ignored -> null;

    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect toolbarRect = new UiRect(0, 0, 0, 0);
    private UiRect searchRect = new UiRect(0, 0, 0, 0);
    private UiRect scopeRect = new UiRect(0, 0, 0, 0);
    private UiRect listRect = new UiRect(0, 0, 0, 0);
    private UiRect cellsRect = new UiRect(0, 0, 0, 0);
    private UiRect scrollbarRect = new UiRect(0, 0, 0, 0);
    private UiRect footerRect = new UiRect(0, 0, 0, 0);
    private UiRect statusRect = new UiRect(0, 0, 0, 0);
    private UiRect prevRect = new UiRect(0, 0, 0, 0);
    private UiRect nextRect = new UiRect(0, 0, 0, 0);
    private UiRect pageRect = new UiRect(0, 0, 0, 0);
    private UiRect clearRect = new UiRect(0, 0, 0, 0);

    // 弹窗按钮行让出的空间：底部高度与右侧宽度（0 表示自己占满整行）
    private int footerReservedHeight;
    private int footerReservedRight;

    // 上一次布局的输入签名：输入全等时不再重建底栏/列表矩形，也不重测文本宽度
    private boolean layoutApplied;
    private int layoutX;
    private int layoutY;
    private int layoutWidth;
    private int layoutHeight;
    private int layoutFooterRight;
    private int layoutFooterHeight;
    private int layoutPage;
    private int layoutPageCount;
    private boolean layoutScoped;
    private @Nullable Object layoutPolicy;
    private @Nullable Object layoutLanguage;
    // 页码文本：页码与总页数才决定内容，缓存后布局与绘制都不再每帧构造 Component；
    // 初值对应 page=0、pageCount=0，与 setPage 从未被调用时的旧页码文本一致。
    private String pageNumber = "1";

    // 状态文案缓存：只有真实依赖（错误/加载/空结果/页码标签/附加说明/已选数/语言）变化才重建
    private @Nullable Component statusTextCache;
    private @Nullable Component statusTextError;
    private boolean statusTextLoading;
    private boolean statusTextExplicitLoading;
    private int statusTextEntriesVersion = -1;
    private @Nullable Component statusTextPageLabel;
    private @Nullable Component statusTextNote;
    private int statusTextSelection = -1;
    private @Nullable Object statusTextLanguage;

    // 底栏三处文本的裁剪结果：来源文本或可用宽度不变时不再逐字符测宽
    private String statusTrimmed = "";
    private @Nullable Component statusTrimmedSource;
    private int statusTrimmedWidth = -1;
    private int statusTrimmedOffset = -1;
    private String pageTrimmed = "";
    private String pageTrimmedSource = "";
    private int pageTrimmedWidth = -1;
    private String clearTrimmed = "";
    private @Nullable Object clearTrimmedLanguage;
    private int clearTrimmedWidth = -1;

    // 焦点格与滚动滑块的矩形按值复用：静止帧不重复分配
    private UiRect focusedCellRect = new UiRect(0, 0, 0, 0);
    private UiRect scrollThumbRect = new UiRect(0, 0, 0, 0);

    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private boolean loading;
    private boolean explicitLoading;
    private @Nullable Component error;
    private @Nullable Component statusNote;
    private @Nullable Component pageLabel;
    private boolean searchActive;
    private int page;
    private int pageCount;
    private boolean canPrev = true;
    private boolean canNext = true;
    private int scrollRow;
    private int cursor = -1;
    private int hoveredIndex = -1;
    private int hoverScrollTicks;
    private boolean draggingScrollbar;

    // 宿主可显示标签分组的页名，普通目录继续使用数字页码。
    public void setPageLabel(@Nullable Component label) {
        pageLabel = retainSameText(pageLabel, label);
    }

    // 宿主的附加状态文字（例如「共 1204 项」），填空表示不显示。
    public void setStatusNote(@Nullable Component note) {
        statusNote = retainSameText(statusNote, note);
    }

    // 注入的宿主主题
    private final UiHostTheme theme;
    public UiCatalogGrid(UiHostTheme theme, Font font, boolean multiSelect, Texts texts, Listener listener) {
        this.theme = UiHostThemes.require(theme);
        this.font = Objects.requireNonNull(font, "font");
        this.multiSelect = multiSelect;
        this.texts = Objects.requireNonNull(texts, "texts");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.search = new UiTextInput(theme, font, texts.searchHint());
        this.search.setMaxLength(128);
        this.search.setAccessibleName(texts.searchHint());
        this.search.setOnValueChanged(() -> {
            scrollRow = 0;
            cursor = -1;
            listener.onSearch(search.value().trim());
        });
        this.search.setOnCommit(value -> {
            searchActive = false;
            search.setFocused(false);
            listener.onSearch(value == null ? "" : value.trim());
        });
        this.search.setOnCancel(() -> {
            searchActive = false;
            search.setFocused(false);
            search.clear();
            listener.onSearch("");
        });
    }

    // 注入条目策略：null 表示页面不隐藏任何条目，也不出现范围按钮
    public void setEntryPolicy(@Nullable EntryPolicy next) {
        this.policy = next;
        layout(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    // 注入条目源：宿主返回同一缓存实例时只在内容变化后重建列表
    public UiCatalogGrid setEntrySource(@Nullable Supplier<@Nullable List<Entry>> source) {
        this.entrySource = source;
        this.sourceCache = null;
        syncSource();
        return this;
    }

    // 直接设置条目（测试或宿主已就绪的缓存）
    public UiCatalogGrid setEntries(List<Entry> next) {
        this.entrySource = null;
        this.sourceCache = next;
        this.entries.clear();
        if (next != null) {
            this.entries.addAll(next);
        }
        entriesVersion++;
        clampCursor();
        clampScroll();
        return this;
    }

    // 条目列表（当前已同步的视图）
    public List<Entry> entries() {
        syncSource();
        return List.copyOf(entries);
    }

    public int size() {
        syncSource();
        return entries.size();
    }

    public List<String> selection() {
        return List.copyOf(selection);
    }

    /**
     * 回填完整选择，跨页与过滤保留；程序化回填不触发编辑回调。
     * @param ids 当前字段引用，单选模式只采用第一项
     */
    public void setSelection(List<String> ids) {
        selection.clear();
        for (String id : ids) {
            if (id == null || id.isBlank() || selection.contains(id)) continue;
            selection.add(id);
            if (!multiSelect) break;
        }
    }

    /**
     * 注入不可新增选择的原因；已有冲突选择仍可取消，原因进入行尾标记与悬停提示。
     * @param reason 按条目 ID 查询原因，返回 null 表示可选
     */
    public void setUnavailableReason(Function<String, @Nullable Component> reason) {
        unavailableReason = Objects.requireNonNull(reason, "reason");
    }

    public boolean isSelected(String id) {
        return id != null && selection.contains(id);
    }

    public UiCatalogGrid setFilter(String filter) {
        search.setValue(filter == null ? "" : filter);
        return this;
    }

    public String filter() {
        return search.value();
    }

    public UiCatalogGrid setError(@Nullable Component error) {
        this.error = retainSameText(this.error, error);
        return this;
    }

    public UiCatalogGrid setLoading(boolean loading) {
        explicitLoading = loading;
        return this;
    }

    public UiCatalogGrid setOnRetry(Runnable retry) {
        onRetry = retry;
        return this;
    }

    public UiCatalogGrid setTooltipSuffix(Function<String, Component> suffix) {
        tooltipSuffix = suffix == null ? ignored -> null : suffix;
        return this;
    }

    // 页码状态：page 从 0 开始；pageCount <= 0 表示未知（只显示当前页号）
    public UiCatalogGrid setPage(int page, int pageCount) {
        int nextPage = Math.max(0, page);
        int nextPageCount = Math.max(0, pageCount);
        if (nextPage != this.page) {
            scrollRow = 0;
            cursor = -1;
        }
        String nextPageNumber = pageNumberText(nextPage, nextPageCount);
        this.page = nextPage;
        this.pageCount = nextPageCount;
        if (!nextPageNumber.equals(pageNumber)) {
            pageNumber = nextPageNumber;
            // 页码宽度参与底栏布局：页码真正变化时显式重排一次，不再依赖每帧重排兜底
            layout(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }
        return this;
    }

    // 分页边界：由宿主按目录缓存的 lastPage / 返回条数判定，边界外不响应翻页
    public UiCatalogGrid setPageLimits(boolean canPrev, boolean canNext) {
        this.canPrev = canPrev;
        this.canNext = canNext;
        return this;
    }

    public UiCatalogGrid setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            draggingScrollbar = false;
            if (searchActive) {
                searchActive = false;
                search.setFocused(false);
            }
        }
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiCatalogGrid setVisible(boolean visible) {
        search.setVisible(visible);
        this.visible = visible;
        return this;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        layout(x, y, width, height);
    }

    // 弹窗按钮行托管底部内容：按按钮实际占用的右侧宽度与行高重新布局，不为此多占一行
    @Override
    public void setFooterReserved(int right, int height) {
        if (right == footerReservedRight && height == footerReservedHeight) {
            return;
        }
        footerReservedRight = right;
        footerReservedHeight = height;
        layout(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    // 统一布局：工具栏、列表内凹面板、底部状态下标与翻页按钮都在这里定位。
    // 布局输入（外框、弹窗让位、页码、范围按钮、策略与语言）全等时直接返回：静止帧不再重建 12 个矩形。
    private void layout(int x, int y, int width, int height) {
        int nextWidth = Math.max(0, width);
        int nextHeight = Math.max(0, height);
        boolean nextScoped = scoped();
        Object language = Language.getInstance();
        if (layoutApplied && layoutX == x && layoutY == y && layoutWidth == nextWidth
                && layoutHeight == nextHeight && layoutFooterRight == footerReservedRight
                && layoutFooterHeight == footerReservedHeight && layoutPage == page
                && layoutPageCount == pageCount && layoutScoped == nextScoped && layoutPolicy == policy
                && layoutLanguage == language) {
            return;
        }
        this.bounds = new UiRect(x, y, nextWidth, nextHeight);
        int w = bounds.width();
        int h = bounds.height();
        int toolbarHeight = Math.min(TOOLBAR_HEIGHT, h);
        this.toolbarRect = new UiRect(x, y, w, toolbarHeight);
        int scopeWidth = scoped() ? scopeButtonWidth() : 0;
        int searchWidth = Math.max(1, w - (scopeWidth > 0 ? scopeWidth + GAP : 0));
        this.searchRect = new UiRect(x, y, searchWidth, toolbarHeight);
        search.setBounds(searchRect.x(), searchRect.y(), Math.max(1, searchRect.width()), searchRect.height());
        // 范围轮询按钮贴右：显示当前范围短标签，点击切到下一个
        this.scopeRect = new UiRect(x + Math.max(0, w - scopeWidth), y, scopeWidth, toolbarHeight);

        // 底部行由弹窗按钮行托管时用自己的高度，否则占满自身底部；右侧让出的宽度归按钮
        int footerHeight = footerReservedHeight > 0 ? footerReservedHeight
                : Math.clamp(h - toolbarHeight, 0, FOOTER_HEIGHT);
        int footerWidth = Math.max(0, w - footerReservedRight);
        this.footerRect = new UiRect(x, y + h - footerHeight, footerWidth, footerHeight);
        int listTop = toolbarRect.bottom() + GAP;
        int listHeight = Math.max(0, footerRect.y() - GAP - listTop);
        this.listRect = new UiRect(x, listTop, w, listHeight);
        int cellWidth = Math.max(0, listRect.width() - LIST_BORDER * 2 - SCROLLBAR_WIDTH);
        this.cellsRect = new UiRect(listRect.x() + LIST_BORDER, listRect.y() + LIST_BORDER, cellWidth,
                Math.max(0, listRect.height() - LIST_BORDER * 2));
        this.scrollbarRect = new UiRect(listRect.right() - LIST_BORDER - SCROLLBAR_WIDTH, cellsRect.y(),
                SCROLLBAR_WIDTH, cellsRect.height());

        // 翻页在最左：标记按钮 + 页码；其后是状态文字；最右留给「清空已选」
        int buttonHeight = Math.min(theme.rowHeight(), footerRect.height());
        int buttonY = footerRect.y() + Math.max(0, (footerRect.height() - buttonHeight) / 2);
        int pageWidth = Math.clamp(font.width(pageNumber) + PAGER_PADDING, PAGE_LABEL_MIN_WIDTH,
                Math.max(PAGE_LABEL_MIN_WIDTH, footerWidth / 3));
        this.prevRect = new UiRect(x, buttonY, PAGER_SYMBOL_WIDTH, buttonHeight);
        this.pageRect = new UiRect(prevRect.right() + GAP, buttonY, pageWidth, buttonHeight);
        this.nextRect = new UiRect(pageRect.right() + GAP, buttonY, PAGER_SYMBOL_WIDTH, buttonHeight);
        int tailX = nextRect.right() + GAP * 3;
        int tailWidth = Math.max(0, footerRect.right() - tailX);
        // 清空链接的位置与选中项无关地预留出来，选择变化时不触发重新布局
        int clearWidth = multiSelect ? Math.min(font.width(texts.clear().getString()) + PAGER_PADDING, tailWidth) : 0;
        this.clearRect = new UiRect(footerRect.right() - clearWidth, footerRect.y(), clearWidth,
                footerRect.height());
        this.statusRect = new UiRect(tailX, footerRect.y(), Math.max(0, clearRect.x() - GAP - tailX),
                footerRect.height());
        clampScroll();
        layoutX = x;
        layoutY = y;
        layoutWidth = nextWidth;
        layoutHeight = nextHeight;
        layoutFooterRight = footerReservedRight;
        layoutFooterHeight = footerReservedHeight;
        layoutPage = page;
        layoutPageCount = pageCount;
        layoutScoped = nextScoped;
        layoutPolicy = policy;
        layoutLanguage = language;
        layoutApplied = true;
    }

    // 是否出现范围按钮：只有同时存在主分类与标签的目录页才需要
    private boolean scoped() {
        return policy != null && policy.scoped();
    }

    // 范围轮询顺序：只主分类 → 只标签 → 一起
    private static Scope nextScope(Scope current) {
        return switch (current) {
            case ITEM -> Scope.TAG;
            case TAG -> Scope.BOTH;
            case BOTH -> Scope.ITEM;
        };
    }

    private Component scopeText(Scope scope) {
        return switch (scope) {
            case ITEM -> texts.scopeItem();
            case TAG -> texts.scopeTag();
            case BOTH -> texts.scopeBoth();
        };
    }

    // 按钮上的短标签：只写当前能搜到哪类条目，点击后整词替换
    private Component scopeLabel(Scope scope) {
        return switch (scope) {
            case ITEM -> texts.scopeItemLabel();
            case TAG -> texts.scopeTagLabel();
            case BOTH -> texts.scopeBothLabel();
        };
    }

    // 按钮宽度取三种短标签里最宽的一个：切范围时搜索框不跟着抖动
    private int scopeButtonWidth() {
        int widest = Math.max(font.width(scopeLabel(Scope.ITEM).getString()),
                Math.max(font.width(scopeLabel(Scope.TAG).getString()),
                        font.width(scopeLabel(Scope.BOTH).getString())));
        return widest + SCOPE_SIDE_PADDING * 2;
    }

    // 滚动条命中：内容超过可见行时，抓取范围比 4 像素的视觉宽度宽一点
    private boolean scrollbarHit(double mouseX, double mouseY) {
        if (scrollbarRect.width() <= 0 || rowCount() <= visibleRows()) {
            return false;
        }
        return mouseX >= scrollbarRect.x() - SCROLLBAR_GRAB_MARGIN
                && mouseX <= scrollbarRect.right() + SCROLLBAR_GRAB_MARGIN
                && mouseY >= scrollbarRect.y() && mouseY <= scrollbarRect.bottom();
    }

    // 把滑块中心对到指针位置：点轨道即跳转，按住拖动连续滚动
    private void scrollToPointer(double mouseY) {
        int rows = rowCount();
        int visible = visibleRows();
        int maxScroll = Math.max(0, rows - visible);
        if (maxScroll == 0 || scrollbarRect.height() == 0) {
            return;
        }
        int thumbHeight = thumbHeight(rows, visible);
        int travel = Math.max(1, scrollbarRect.height() - thumbHeight);
        int offset = Math.clamp((int) Math.round(mouseY - scrollbarRect.y() - thumbHeight / 2.0), 0, travel);
        scrollRow = Math.round(offset * (float) maxScroll / travel);
    }

    // 滑块高度：按可见行占比缩放，最矮也留得住
    private int thumbHeight(int rows, int visible) {
        return Math.max(MIN_SCROLL_THUMB, scrollbarRect.height() * visible / rows);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    private void syncSource() {
        Supplier<@Nullable List<Entry>> source = entrySource;
        if (source == null) {
            return;
        }
        List<Entry> next = source.get();
        this.loading = next == null;
        if (next == sourceCache) {
            return;
        }
        sourceCache = next;
        entries.clear();
        if (next != null) {
            entries.addAll(next);
        }
        entriesVersion++;
        // 条目集合换过一批：旧标签缓存以条目对象身份为键，直接丢弃
        rowLabels.clear();
        rowLabelsLanguage = null;
        hoveredIndex = -1;
        hoverScrollTicks = 0;
        clampCursor();
        clampScroll();
    }

    // ---- 网格几何 ----

    private int columns() {
        return Math.clamp(cellsRect.width() / MIN_COLUMN_WIDTH, 1, MAX_COLUMNS);
    }

    private int columnWidth() {
        int columns = columns();
        return Math.max(1, cellsRect.width() / columns);
    }

    private int visibleRows() {
        return Math.max(1, cellsRect.height() / ROW_HEIGHT);
    }

    private int rowCount() {
        return (entries.size() + columns() - 1) / columns();
    }

    private void clampScroll() {
        scrollRow = Math.clamp(scrollRow, 0, Math.max(0, rowCount() - visibleRows()));
    }

    private void clampCursor() {
        cursor = entries.isEmpty() ? -1 : Math.clamp(Math.max(0, cursor), 0, entries.size() - 1);
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        if (cursor < 0) {
            return;
        }
        int row = cursor / columns();
        if (row < scrollRow) {
            scrollRow = row;
        } else if (row >= scrollRow + visibleRows()) {
            scrollRow = Math.clamp(row - visibleRows() + 1, 0, Math.max(0, rowCount() - visibleRows()));
        }
        clampScroll();
    }

    // 焦点格矩形按值复用：每帧最多一个焦点格，静止帧不重复分配
    private UiRect focusRect(int x, int y) {
        int width = columnWidth();
        if (focusedCellRect.x() != x || focusedCellRect.y() != y
                || focusedCellRect.width() != width || focusedCellRect.height() != ROW_HEIGHT) {
            focusedCellRect = new UiRect(x, y, width, ROW_HEIGHT);
        }
        return focusedCellRect;
    }

    // 滚动滑块矩形按值复用：只有滚动位置或轨道尺寸变化时才重建
    private UiRect scrollThumbRect(int y, int height) {
        if (scrollThumbRect.x() != scrollbarRect.x() || scrollThumbRect.y() != y
                || scrollThumbRect.width() != scrollbarRect.width() || scrollThumbRect.height() != height) {
            scrollThumbRect = new UiRect(scrollbarRect.x(), y, scrollbarRect.width(), height);
        }
        return scrollThumbRect;
    }

    // 纯查表：不做任何状态写入，供命中与 tooltip 复用
    private int cellIndexAt(double mouseX, double mouseY) {
        if (!cellsRect.contains(mouseX, mouseY)) {
            return -1;
        }
        int col = (int) Math.floor((mouseX - cellsRect.x()) / (double) columnWidth());
        int row = scrollRow + (int) Math.floor((mouseY - cellsRect.y()) / (double) ROW_HEIGHT);
        if (col < 0 || col >= columns()) {
            return -1;
        }
        int index = row * columns() + col;
        return index >= 0 && index < entries.size() ? index : -1;
    }

    // 每帧推进悬停行与名称滚动计时，指针换行立刻回到起点
    private void indexAt(double mouseX, double mouseY) {
        int index = cellIndexAt(mouseX, mouseY);
        if (index != hoveredIndex) {
            hoveredIndex = index;
            hoverScrollTicks = 0;
        } else if (hoveredIndex >= 0) {
            hoverScrollTicks++;
        }
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        syncSource();
        indexAt(mouseX, mouseY);
        drawToolbar(graphics, mouseX, mouseY);
        drawList(graphics);
        drawFooter(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // 悬停提示按行交给原版：单个 Component 的 renderTooltip 重载不处理换行
    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> lines = tooltipLines(mouseX, mouseY);
        if (lines.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> sequences = new ArrayList<>(lines.size());
        for (Component line : lines) {
            sequences.addAll(font.split(line, TOOLTIP_MAX_WIDTH));
        }
        UiFlatText.tooltip(graphics, font, sequences, mouseX, mouseY);
    }

    private void drawToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        search.render(graphics, font, mouseX, mouseY);
        drawScopeButton(graphics, mouseX, mouseY);
        theme.drawDivider(graphics, toolbarRect.x(), toolbarRect.bottom(), toolbarRect.width());
    }

    // 范围轮询按钮：按钮上写当前范围短标签，点击切到下一个，完整说明只进悬停提示
    private void drawScopeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (policy == null || !policy.scoped() || scopeRect.width() <= 0) {
            return;
        }
        boolean hovered = enabled && scopeRect.contains(mouseX, mouseY);
        graphics.fill(scopeRect.x(), scopeRect.y(), scopeRect.right(), scopeRect.bottom(),
                hovered ? theme.palette().controlHover() : theme.palette().controlFill());
        theme.drawBevel(graphics, scopeRect, theme.palette().controlBevelLight(), theme.palette().controlBevelDark());
        String label = scopeLabel(policy.scope()).getString();
        graphics.drawString(font, label, scopeRect.x() + Math.max(0, (scopeRect.width() - font.width(label)) / 2),
                scopeRect.y() + Math.max(0, (scopeRect.height() - font.lineHeight) / 2),
                enabled ? theme.palette().textPrimary() : theme.palette().textDisabled(), false);
    }

    private void drawList(GuiGraphics graphics) {
        if (listRect.width() > 0 && listRect.height() > 0) {
            theme.drawInset(graphics, listRect);
        }
        if (cellsRect.width() > 0 && cellsRect.height() > 0) {
            int columns = columns();
            int columnWidth = columnWidth();
            int lastRow = Math.min(rowCount(), scrollRow + visibleRows());
            for (int row = scrollRow; row < lastRow; row++) {
                int cellY = cellsRect.y() + (row - scrollRow) * ROW_HEIGHT;
                int index = row * columns;
                for (int col = 0; col < columns; col++, index++) {
                    if (index >= entries.size()) {
                        break;
                    }
                    // 直接传整数坐标：可见格不再逐个分配矩形对象
                    drawRow(graphics, entries.get(index), cellsRect.x() + col * columnWidth, cellY,
                            row, index == hoveredIndex, focused && index == cursor);
                }
            }
        }
        drawScrollbar(graphics);
    }

    // 一行 = 图标 + 名称；已选整行高亮并在行尾打勾，悬停行名称超宽时滚动
    private void drawRow(GuiGraphics graphics, Entry entry, int cellX, int cellY, int row, boolean hovered,
                         boolean cursorAt) {
        boolean chosen = isSelected(entry.id());
        boolean unavailable = unavailableReason.apply(entry.id()) != null;
        int cellRight = cellX + columnWidth();
        int fill = !enabled ? theme.palette().slotFill()
                : chosen ? theme.palette().controlSelected()
                : hovered ? theme.palette().controlHover()
                : (row % 2 == 0 ? theme.palette().slotFill() : theme.palette().listRowOdd());
        graphics.fill(cellX, cellY, cellRight, cellY + ROW_HEIGHT, fill);

        if (entry.icon() != null) {
            drawIcon(graphics, entry.icon(), cellX + PADDING_X, cellY);
        }
        int textX = cellX + PADDING_X + ICON_BOX + ICON_GAP;
        int textWidth = Math.max(0, cellRight - MARK_WIDTH - textX);
        String label = rowLabelText(entry);
        int color = enabled && !unavailable ? theme.palette().textPrimary() : theme.palette().textDisabled();
        int textY = cellY + Math.max(0, (ROW_HEIGHT - font.lineHeight) / 2);
        TextScroll.draw(graphics, font, label, textX, textY,
                textWidth, color, enabled && hovered, hovered ? hoverScrollTicks : 0, false);
        if (unavailable) {
            drawRowMark(graphics, Mark.UNAVAILABLE, theme.palette().warning(), cellRight, cellY);
        } else if (chosen) {
            drawRowMark(graphics, Mark.CHECK, theme.palette().accentPressed(), cellRight, cellY);
        }
        if (cursorAt) {
            theme.drawFocusOutline(graphics, focusRect(cellX, cellY));
        }
    }

    // 行标签文本：语言变化会让本地化名称整批失效，其余情况按条目身份复用上一次结果
    private String rowLabelText(Entry entry) {
        Object language = Language.getInstance();
        if (rowLabelsLanguage != language) {
            rowLabels.clear();
            rowLabelsLanguage = language;
        }
        String cached = rowLabels.get(entry);
        if (cached != null) {
            return cached;
        }
        String text = rowLabel(entry).getString();
        rowLabels.put(entry, text);
        return text;
    }

    // 标签名称由共用方法生成，避免目录行和标签快照各添加一次前缀。
    private static Component rowLabel(Entry entry) {
        Component label = entry.label() == null ? Component.literal(entry.id()) : entry.label();
        return entry.id().startsWith("#") ? RuleDisplayLabels.tagLabel(entry.id()) : label;
    }

    // 图标槽固定 16 像素：比槽更大的贴图按比例缩到槽内，避免压到上下相邻行
    private void drawIcon(GuiGraphics graphics, UiIcon icon, int x, int rowY) {
        int size = Math.max(icon.width(), icon.height());
        int y = rowY + Math.max(0, (ROW_HEIGHT - icon.height()) / 2);
        if (size <= ICON_BOX) {
            icon.render(graphics, x + Math.max(0, (ICON_BOX - icon.width()) / 2), y);
            return;
        }
        float scale = ICON_BOX / (float) size;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, rowY + (ROW_HEIGHT - ICON_BOX) / 2F, 0F);
            graphics.pose().scale(scale, scale, 1F);
            icon.render(graphics, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    // 行尾标记：在 MARK_WIDTH 宽的行尾槽与 ROW_HEIGHT 行高内居中
    private void drawRowMark(GuiGraphics graphics, Mark mark, int color, int cellRight, int rowY) {
        UiIcon icon = UiMarkIcons.icon(mark, color);
        icon.render(graphics, cellRight - MARK_WIDTH + (MARK_WIDTH - icon.width()) / 2,
                rowY + (ROW_HEIGHT - icon.height()) / 2);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int rows = rowCount();
        int visible = visibleRows();
        if (rows <= visible || scrollbarRect.width() <= 0 || scrollbarRect.height() <= 0) {
            return;
        }
        int trackHeight = scrollbarRect.height();
        int thumbHeight = thumbHeight(rows, visible);
        int maxScroll = rows - visible;
        int thumbY = scrollbarRect.y() + (maxScroll <= 0 ? 0 : (trackHeight - thumbHeight) * scrollRow / maxScroll);
        theme.drawScrollTrack(graphics, scrollbarRect);
        theme.drawScrollThumb(graphics, scrollThumbRect(thumbY, thumbHeight));
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        if (footerRect.height() <= 0) {
            return;
        }
        theme.drawDivider(graphics, bounds.x(), footerRect.y() - 1, bounds.width());
        Component status = statusText();
        boolean busy = (loading || explicitLoading) && error == null;
        if (busy) {
            UiSpinner.render(graphics, statusRect.x(), statusRect.y() + Math.max(0, (statusRect.height() - 10) / 2),
                    Util.getMillis(), theme.secondaryTextColor());
        }
        int textOffset = busy ? 14 : 0;
        String trimmed = trimmedStatus(status, Math.max(0, statusRect.width() - textOffset), textOffset);
        graphics.drawString(font, trimmed, statusRect.x() + textOffset,
                statusRect.y() + Math.max(0, (statusRect.height() - font.lineHeight) / 2),
                error != null ? theme.palette().danger() : theme.palette().textSecondary(), false);
        if (canClear()) {
            boolean hovered = clearRect.contains(mouseX, mouseY);
            int color = hovered ? theme.palette().accentPressed() : theme.palette().link();
            String link = trimmedClear(Math.max(1, clearRect.width()));
            int clearY = clearRect.y() + Math.max(0, (clearRect.height() - font.lineHeight) / 2);
            graphics.drawString(font, link, clearRect.x(), clearY, color, false);
            graphics.fill(clearRect.x(), clearY + font.lineHeight - 1, clearRect.x() + font.width(link),
                    clearY + font.lineHeight, color);
        }
        drawPagerButton(graphics, prevRect, Mark.PREVIOUS, canPrev && enabled, mouseX, mouseY);
        drawPagerButton(graphics, nextRect, Mark.NEXT, canNext && enabled, mouseX, mouseY);
        String pages = trimmedPage(Math.max(1, pageRect.width()));
        graphics.drawString(font, pages,
                pageRect.x() + Math.max(0, (pageRect.width() - font.width(pages)) / 2),
                pageRect.y() + Math.max(0, (pageRect.height() - font.lineHeight) / 2),
                theme.palette().textPrimary(), false);
    }

    private void drawPagerButton(GuiGraphics graphics, UiRect rect, Mark mark, boolean allowed, int mouseX,
                                 int mouseY) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        boolean hovered = enabled && allowed && rect.contains(mouseX, mouseY);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                !allowed ? theme.palette().controlDisabled() : (hovered ? theme.palette().controlHover() : theme.palette().controlFill()));
        theme.drawBevel(graphics, rect, theme.palette().controlBevelLight(), theme.palette().controlBevelDark());
        UiIcon icon = UiMarkIcons.icon(mark,
                allowed ? theme.palette().textPrimary() : theme.palette().textDisabled());
        icon.render(graphics, rect.x() + Math.max(0, (rect.width() - icon.width()) / 2),
                rect.y() + Math.max(0, (rect.height() - icon.height()) / 2));
    }

    // 底部状态：错误 > 加载 > 空结果；正常时是「分组标签 · 总数 · 已选」，页码由翻页按钮旁的分子分母承担。
    // 依赖未变时复用上一次结果：宿主可能每帧重配页码标签，但文本归一化后实例稳定，这里不会每帧重拼。
    private Component statusText() {
        Object language = Language.getInstance();
        if (statusTextCache != null && statusTextLanguage == language
                && statusTextError == error && statusTextLoading == loading
                && statusTextExplicitLoading == explicitLoading
                && statusTextEntriesVersion == entriesVersion
                && statusTextPageLabel == pageLabel && statusTextNote == statusNote
                && statusTextSelection == selection.size()) {
            return statusTextCache;
        }
        Component text = buildStatusText();
        statusTextCache = text;
        statusTextError = error;
        statusTextLoading = loading;
        statusTextExplicitLoading = explicitLoading;
        statusTextEntriesVersion = entriesVersion;
        statusTextPageLabel = pageLabel;
        statusTextNote = statusNote;
        statusTextSelection = selection.size();
        statusTextLanguage = language;
        return text;
    }

    private Component buildStatusText() {
        if (error != null) {
            return texts.error().copy().append(" ").append(error);
        }
        if (loading || explicitLoading) {
            return texts.loading();
        }
        if (entries.isEmpty() && pageLabel == null && statusNote == null) {
            return texts.empty();
        }
        Component text = Component.empty();
        if (pageLabel != null) {
            text = text.copy().append(pageLabel);
        }
        if (statusNote != null) {
            text = appendStatus(text, statusNote);
        }
        text = appendStatus(text, texts.selection().copy().append(" " + selection.size()));
        return entries.isEmpty() ? appendStatus(text, texts.empty()) : text;
    }

    // 状态块之间统一一个分隔符，首个块不带
    private static Component appendStatus(Component text, Component part) {
        return text.getString().isEmpty() ? part.copy() : text.copy().append(STATUS_SEPARATOR).append(part);
    }

    // 页码文本：纯字符串，布局测宽与底栏绘制共用，不再每帧构造 Component
    private static String pageNumberText(int page, int pageCount) {
        return (page + 1) + (pageCount > 0 ? "/" + pageCount : "");
    }

    // 宿主每帧重配状态文案时（例如黑名单页码标签）文本未变就保留旧实例，
    // 状态文案缓存因此可以按实例身份判断，避免逐帧重建 Component。
    private static @Nullable Component retainSameText(@Nullable Component current, @Nullable Component next) {
        if (next == null) {
            return null;
        }
        if (current != null && current.getString().equals(next.getString())) {
            return current;
        }
        return next;
    }

    // 状态文本裁剪结果按「来源 Component + 可用宽度 + 加载图标偏移」缓存
    private String trimmedStatus(Component status, int width, int offset) {
        if (statusTrimmedSource == status && statusTrimmedWidth == width && statusTrimmedOffset == offset) {
            return statusTrimmed;
        }
        statusTrimmed = TextScroll.trimToWidth(font, status.getString(), width);
        statusTrimmedSource = status;
        statusTrimmedWidth = width;
        statusTrimmedOffset = offset;
        return statusTrimmed;
    }

    // 页码文本裁剪结果按「页码文本 + 页码按钮宽度」缓存
    private String trimmedPage(int width) {
        if (pageTrimmedWidth == width && pageTrimmedSource.equals(pageNumber)) {
            return pageTrimmed;
        }
        pageTrimmed = TextScroll.trimToWidth(font, pageNumber, width);
        pageTrimmedSource = pageNumber;
        pageTrimmedWidth = width;
        return pageTrimmed;
    }

    // 清空链接裁剪结果按「语言 + 链接区宽度」缓存；语言变化后链接文案与宽度都会变
    private String trimmedClear(int width) {
        Object language = Language.getInstance();
        if (clearTrimmedWidth == width && clearTrimmedLanguage == language) {
            return clearTrimmed;
        }
        clearTrimmed = TextScroll.trimToWidth(font, texts.clear().getString(), width);
        clearTrimmedLanguage = language;
        clearTrimmedWidth = width;
        return clearTrimmed;
    }

    private boolean canClear() {
        return enabled && multiSelect && !selection.isEmpty();
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (error != null && statusRect.contains(mouseX, mouseY) && onRetry != null) {
            onRetry.run();
            return true;
        }
        if (searchRect.contains(mouseX, mouseY) || search.isFocused()) {
            searchActive = true;
            search.setFocused(true);
            if (search.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (policy != null && policy.scoped() && scopeRect.contains(mouseX, mouseY)) {
            searchActive = false;
            search.setFocused(false);
            policy.setScope(nextScope(policy.scope()));
            scrollRow = 0;
            cursor = -1;
            hoveredIndex = -1;
            return true;
        }
        // 滚动条：点轨道即跳到该处，按住不放继续拖
        if (scrollbarHit(mouseX, mouseY)) {
            searchActive = false;
            search.setFocused(false);
            draggingScrollbar = true;
            scrollToPointer(mouseY);
            return true;
        }
        if (canClear() && clearRect.contains(mouseX, mouseY)) {
            selection.clear();
            listener.onSelectionChanged(selection());
            return true;
        }
        if (canPrev && prevRect.contains(mouseX, mouseY)) {
            searchActive = false;
            search.setFocused(false);
            listener.onPageChange(-1);
            return true;
        }
        if (canNext && nextRect.contains(mouseX, mouseY)) {
            searchActive = false;
            search.setFocused(false);
            listener.onPageChange(1);
            return true;
        }
        int hit = cellIndexAt(mouseX, mouseY);
        if (hit >= 0) {
            searchActive = false;
            search.setFocused(false);
            cursor = hit;
            toggle(entries.get(hit).id());
            return true;
        }
        return bounds.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return search.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToPointer(mouseY);
            return true;
        }
        return search.isFocused() && search.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !enabled || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        scrollRow -= (int) Math.signum(scrollY) * WHEEL_ROWS;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (searchActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchActive = false;
                search.setFocused(false);
                search.clear();
                listener.onSearch("");
                return true;
            }
            return search.keyPressed(keyCode, scanCode, modifiers);
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> moveCursor(-1);
            case GLFW.GLFW_KEY_RIGHT -> moveCursor(1);
            case GLFW.GLFW_KEY_UP -> moveCursor(-columns());
            case GLFW.GLFW_KEY_DOWN -> moveCursor(columns());
            case GLFW.GLFW_KEY_HOME -> setCursor(0);
            case GLFW.GLFW_KEY_END -> setCursor(entries.size() - 1);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollRows(-visibleRows());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollRows(visibleRows());
            case GLFW.GLFW_KEY_SPACE -> toggleCursor();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateCursor();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return searchActive && search.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (!searchActive) {
            searchActive = true;
            search.setFocused(true);
            search.clear();
        }
        return search.charTyped(codePoint, modifiers);
    }

    private void moveCursor(int delta) {
        if (entries.isEmpty()) {
            return;
        }
        setCursor(Math.max(0, cursor) + delta);
    }

    private void setCursor(int index) {
        if (entries.isEmpty()) {
            cursor = -1;
            return;
        }
        cursor = Math.clamp(index, 0, entries.size() - 1);
        ensureCursorVisible();
    }

    // 翻页按钮之外的行级翻屏：列表比一页高，PageUp/PageDown 按可见行滚动
    private void scrollRows(int delta) {
        scrollRow += delta;
        clampScroll();
    }

    private void toggleCursor() {
        if (cursor < 0 || cursor >= entries.size()) {
            return;
        }
        toggle(entries.get(cursor).id());
    }

    private void activateCursor() {
        if (cursor < 0 || cursor >= entries.size()) {
            return;
        }
        String id = entries.get(cursor).id();
        if (unavailableReason.apply(id) == null) listener.onActivate(id);
    }

    // 选择切换：单选模式替换旧选择，多选模式切换单项
    private void toggle(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        // 冲突项禁止新增，但保留取消已有冲突项的修复入口。
        if (!selection.contains(id) && unavailableReason.apply(id) != null) return;
        if (multiSelect) {
            if (!selection.remove(id)) {
                selection.add(id);
            }
        } else {
            selection.clear();
            selection.add(id);
        }
        listener.onSelectionChanged(selection());
    }

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (!focused && searchActive) {
            searchActive = false;
            search.setFocused(false);
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        if (!enabled || cursor < 0 || cursor >= entries.size()) {
            return false;
        }
        activateCursor();
        return true;
    }

    @Override
    public Component accessibleName() {
        return Component.empty().append(texts.selection()).append(" " + selection.size() + "/" + entries.size());
    }

    // 悬停提示的每一行：条目只给显示名与注册名，宿主附加说明（标签成员、预览不可用）另起一行
    private List<Component> tooltipLines(double mouseX, double mouseY) {
        if (policy != null && policy.scoped() && scopeRect.contains(mouseX, mouseY)) {
            return List.of(scopeText(policy.scope()));
        }
        if (enabled && canPrev && prevRect.contains(mouseX, mouseY)) {
            return List.of(texts.prevPage());
        }
        if (enabled && canNext && nextRect.contains(mouseX, mouseY)) {
            return List.of(texts.nextPage());
        }
        if (canClear() && clearRect.contains(mouseX, mouseY)) {
            return List.of(texts.clear());
        }
        int hit = cellIndexAt(mouseX, mouseY);
        if (hit < 0) {
            return List.of();
        }
        Entry entry = entries.get(hit);
        List<Component> lines = new ArrayList<>(3);
        lines.add(rowLabel(entry));
        lines.add(Component.literal(entry.id()));
        Component reason = unavailableReason.apply(entry.id());
        if (reason != null) lines.add(reason);
        Component suffix = tooltipSuffix.apply(entry.id());
        if (suffix != null) {
            lines.add(suffix);
        }
        return lines;
    }
}
