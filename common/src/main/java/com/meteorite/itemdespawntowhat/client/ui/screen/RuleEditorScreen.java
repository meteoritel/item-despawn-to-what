package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import org.lwjgl.glfw.GLFW;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiThemeSelection;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemeContext;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemeSet;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiThemeScheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiDescriptionTooltip;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiThemedScreen;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemePublication;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FormattedCharSequence;

/**
 * 规则编辑器主屏。
 * <p>屏幕只保留生命周期、窗口排版、绘制与输入派发：编辑目标、编辑会话、提示、可编辑判定与跨页命令
 * 都由 {@link EditorShell} 持有，三个顶层页也由外壳组装。屏幕不缓存外壳已拥有的状态，
 * 也不按模式分支——窗口几何与标题带、提示带、页脚带统一排版，页通过 {@link EditorPage} 声明差异并接收派发。
 * <p>列表页与编辑页的字段编辑走描述符驱动的
 * {@link FormView}，条件树走
 * {@link UiConditionTreeSummary}，界面任何位置都不出现可编辑 JSON 文本框。
 * <p>只有工作区处于 ACTIVE 时可编辑；APPLYING 冻结；工作区进入 FREE 时外壳登记关屏请求。
 */
public final class RuleEditorScreen extends UiThemedScreen implements UiLayoutSource {

    private UiThemeScheme themeScheme;
    private final UiHostThemeContext THEME;
    private final UiHostThemeContext chromeTheme;
    private final UiThemePublication<UiThemeScheme> themePublication =
            new UiThemePublication<>(() -> themeScheme, this::publishTheme);

    // 布局常量（页自身的内边距由页声明）
    private static final int PAD = 4;
    private static final int NOTICE_H = 10;
    // 实心提示色块的左右内边距
    private static final int NOTICE_CHIP_PAD = 3;
    // 顶部栏状态标签的左右内边距
    private static final int STATE_PAD = 4;
    // 顶部栏必须为标题保留的最小宽度：状态标签在窄窗口下优先压缩，标题按剩余宽度截断
    private static final int HEADER_TITLE_MIN_W = 48;
    // i18n 前缀
    private static final String UI = TypeLabels.UI_PREFIX;

    private final EditorShell shell;
    private final EditorLayers layers;

    private @Nullable RuleSaveOverlay saveOverlay;
    private UiRect contentArea = new UiRect(0, 0, 0, 0);
    private UiRect footerArea = new UiRect(0, 0, 0, 0);
    private int noticeY;
    // 顶部栏：标题、状态标签与换肤按钮同处一栏，栏高由控件高度决定
    private int headerBandHeight;
    private UiRect stateArea = new UiRect(0, 0, 0, 0);
    private List<FormattedCharSequence> stateLines = List.of();
    // 提示的折行判定与命中矩形每帧重算，属于渲染态，不进外壳
    private boolean noticeTruncated;
    private @Nullable UiRect noticeRect;
    private @Nullable UiRect truncatedTitleRect;

    /** 根据进程内记忆恢复外观，工作区继续拥有业务会话。 */
    public RuleEditorScreen(RuleEditClientWorkspace workspace) {
        this(workspace, UiThemeSelection.current());
    }

    private RuleEditorScreen(RuleEditClientWorkspace workspace, UiThemeScheme scheme) {
        this(workspace, scheme, scheme.themes());
    }

    private RuleEditorScreen(RuleEditClientWorkspace workspace, UiThemeScheme scheme, UiHostThemeSet initial) {
        this(workspace, scheme, new UiHostThemeContext(initial.content()), new UiHostThemeContext(initial.chrome()));
    }

    private RuleEditorScreen(RuleEditClientWorkspace workspace, UiThemeScheme scheme,
                             UiHostThemeContext content, UiHostThemeContext chrome) {
        super(Component.translatable(UI + "title"), content);
        THEME = content;
        chromeTheme = chrome;
        themeScheme = scheme;
        BuiltinEditorDescriptors.bootstrap();
        RuleEditorModel model = new RuleEditorModel(workspace);
        // 屏幕构造期 this.font 尚未注入，外壳与页面直接取客户端字体实例
        this.shell = new EditorShell(THEME, chromeTheme, workspace, model, new RuleListDisplayCache(model), Minecraft.getInstance().font, themeLabel(),
                () -> requestTheme(themeScheme.next()));
        this.layers = new EditorLayers(shell, this::saveOverlay, this::renderPage, this::pageLayoutNodes);
        shell.modals().setOnScopeChanged(this::refreshUiState);
        shell.setInputChanged(this::refreshUiState);
        shell.setCloseInput(inputRouter()::close);
    }

    private Component themeLabel() {
        return Component.translatable(UI + "theme.button", Component.translatable(UI + "theme." + themeScheme.id()));
    }

    /** 记录完整方案选择；根在手势正常释放后的准备点发布。 */
    public void requestTheme(UiThemeScheme scheme) {
        UiThemeSelection.select(scheme);
        themePublication.request(scheme);
    }

    @Override
    protected void prepareUi() {
        themePublication.prepare(inputRouter().active());
        shell.prepareUi();
    }

    private void publishTheme(UiThemeScheme scheme) {
        UiHostThemeSet next = scheme.themes();
        themeScheme = scheme;
        shell.themeButton().setLabel(themeLabel());
        THEME.setTheme(next.content());
        chromeTheme.setTheme(next.chrome());
        shell.modals().refreshLayout();
    }

    // 编辑器不做游戏内暂停
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * @return 保存期间禁止 Esc 关闭；断线收尾由工作区控制
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return !shell.saving();
    }

    // ---- 初始化 ----

    @Override
    protected void init() {
        super.init();
        shell.setScreenSize(this.width, this.height);
        // resize 会再次调用 init；保留控件、输入缓冲、选择与滚动，仅在渲染时更新几何。
        if (shell.listPage().initialized()) {
            shell.modals().setBounds(0, 0, width, height);
            return;
        }
        shell.listPage().init();
        shell.editPage().init();
        shell.debugPage().init();
        shell.requestWorkspaceRefresh();
        shell.prepareUi();
    }

    @Override
    protected void layoutUi() {
        shell.setScreenSize(width, height);
        shell.modals().setBounds(0, 0, width, height);
        EditorPage page = shell.activePage();
        int pad = page.pagePadding();
        int available = Math.max(0, width - pad * 2);
        layoutHeader(page, pad, available);
        int top = page.layoutHeader(pad, headerBandHeight + PAD);
        int footerHeight = page.footerHeight(available);
        int footerY = height - pad - footerHeight;
        noticeY = footerY - PAD - (shell.notice() == null ? 0 : NOTICE_H);
        contentArea = new UiRect(pad, top, available, Math.max(0, noticeY - top - pad));
        footerArea = new UiRect(pad, footerY, available, footerHeight);
        page.layout(contentArea, footerArea);
        UiTypePicker picker = page.overlay();
        if (picker != null) picker.setBounds(contentArea.x(), contentArea.y(), contentArea.width(), contentArea.height());
        if (shell.saving()) saveOverlay().layout(width, height);
        else saveOverlay = null;
    }

    /**
     * 排版顶部栏：标题占左侧，状态标签与换肤按钮靠右，三者同处一栏。
     *
     * <p>栏高取「页声明的最小标题带高度」与「控件高度 + 上下留白」的较大值，三页共用同一栏高，
     * 因此状态与换肤入口不再额外占用一行。状态标签的宽度上限为「可用宽度扣除换肤按钮与标题最小宽度」，
     * 窄窗口下先压缩状态标签、再由标题截断兜底；两者都放不下时隐藏状态标签以保证标题与按钮可用。</p>
     *
     * @param page      当前活动页，用于取内边距、最小标题带高度与状态文案
     * @param pad       页内边距
     * @param available 窗口内容的可用宽度
     */
    private void layoutHeader(EditorPage page, int pad, int available) {
        UiButton themeButton = shell.themeButton();
        int buttonWidth = Math.min(available, themeButton.preferredWidth(6));
        int buttonHeight = Math.max(RuleEditorPageSupport.DIALOG_BUTTON_H, THEME.rowHeight());
        String stateText = page.stateLabel();
        int stateLimit = Math.max(0, available - buttonWidth - PAD - HEADER_TITLE_MIN_W);
        int stateWidth = Math.min(font.width(stateText) + STATE_PAD * 2, stateLimit);
        stateLines = stateWidth > STATE_PAD * 2
                ? font.split(Component.literal(stateText), stateWidth - STATE_PAD * 2)
                : List.of();
        int stateHeight = stateLines.isEmpty() ? 0 : stateLines.size() * font.lineHeight + STATE_PAD * 2;
        headerBandHeight = Math.max(page.headerHeight(), Math.max(buttonHeight, stateHeight) + PAD * 2);
        themeButton.setBounds(width - pad - buttonWidth, (headerBandHeight - buttonHeight) / 2, buttonWidth, buttonHeight);
        stateArea = stateLines.isEmpty() ? new UiRect(0, 0, 0, 0)
                : new UiRect(themeButton.bounds().x() - PAD - stateWidth, (headerBandHeight - stateHeight) / 2, stateWidth, stateHeight);
    }

    @Override
    protected void advanceUiFrame() {
        UiTypePicker picker = shell.activePage().overlay();
        if (picker != null && layers.select().activeScope() == picker.inputScope()) picker.advanceAnimation();
    }

    // ---- 渲染 ----

    // 绘制消费根准备阶段已提交的几何
    @Override
    protected void renderThemedUi(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PreviewIcons.beginFrame();
        layers.select().render(graphics, font, mouseX, mouseY);
        graphics.flush();
        PreviewIcons.endFrame();
    }

    private void renderPage(GuiGraphics graphics, Font renderFont, UiPointer pointer, int depth) {
        EditorPage page = shell.activePage();
        int mouseX = (int) pointer.x();
        int mouseY = (int) pointer.y();
        drawHeaderBand(graphics, width);
        drawHeaderText(graphics, page);
        shell.themeButton().render(graphics, font, pointer);
        page.renderHeader(graphics, font, pointer);
        page.render(graphics, font, pointer, contentArea);
        drawNotice(graphics, noticeY, width);
        page.renderFooter(graphics, font, pointer, footerArea);
        if (pointer.allowsTooltip()) {
            Component tip = null;
            if (shell.notice() != null && noticeTruncated && noticeRect != null && noticeRect.contains(mouseX, mouseY)) {
                tip = shell.notice();
            } else if (truncatedTitleRect != null && truncatedTitleRect.contains(mouseX, mouseY)) {
                String detail = page.titleDetail();
                if (detail != null) {
                    new UiDescriptionTooltip(THEME, font).render(graphics, new UiRect(0, 0, width, height),
                            List.of(DescriptionLine.text(Component.literal(detail),
                                    DescriptionLine.Tone.VALUE, 0)), mouseX, mouseY);
                }
            } else if (pointer.hit() != null) {
                pointer.hit().renderTooltip(graphics, font, THEME.tooltipSkin(), mouseX, mouseY);
            }
            if (tip != null) renderTooltip(graphics, tip, mouseX, mouseY);
        }
    }

    // Component 的单行 tooltip 重载不处理换行；按窗口宽度折行后交给无阴影提示面板定位。
    private void renderTooltip(GuiGraphics graphics, Component tip, int mouseX, int mouseY) {
        UiFlatText.tooltip(graphics, font, tip, Math.clamp(width - 16, 1, 240), mouseX, mouseY, THEME.tooltipSkin());
    }

    /** 实际呈现项按绘制顺序导出，隐藏覆盖面不进入快照。 */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        return layers.select().layoutNodes();
    }

    private List<UiLayoutNode> pageLayoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>(shell.activePage().layoutNodes());
        nodes.addAll(headerNodes());
        nodes.addAll(shell.themeButton().layoutNodes());
        return nodes;
    }

    // 顶部栏的状态标签是屏幕直接绘制的文字行：按真实文案与胶囊内带宽导出，供探针按语言 key 断言
    private List<UiLayoutNode> headerNodes() {
        if (stateLines.isEmpty()) {
            return List.of();
        }
        Component label = Component.literal(shell.activePage().stateLabel());
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.ROW, stateArea, label, font.width(label),
                Math.max(0, stateArea.width() - STATE_PAD * 2), true, true, List.of()));
    }

    // 顶部栏底色：窗口内缩一个描边宽度，保留窗口自身的外框；栏底再用分隔线与正文分开
    private void drawHeaderBand(GuiGraphics graphics, int w) {
        int border = THEME.border();
        THEME.drawHeader(graphics, new UiRect(border, border, Math.max(0, w - border * 2), Math.max(0, headerBandHeight - border)));
        THEME.drawDivider(graphics, 0, headerBandHeight, w);
    }

    // 顶部栏文字：左侧标题，右侧状态标签；文本与状态由页声明，屏幕只负责排版与配色
    private void drawHeaderText(GuiGraphics graphics, EditorPage page) {
        truncatedTitleRect = null;
        String title = page.title();
        if (title == null) {
            title = this.getTitle().getString();
        }
        int padding = page.pagePadding();
        // 标题让位给同栏的状态标签与换肤按钮，放不下时按剩余宽度截断并登记悬停详情
        int titleRight = stateLines.isEmpty() ? shell.themeButton().bounds().x() : stateArea.x();
        int textY = (headerBandHeight - font.lineHeight) / 2;
        String fullTitle = title;
        title = TextScroll.trimToWidth(font, title, Math.max(0, titleRight - PAD - padding));
        if (!title.equals(fullTitle)) {
            truncatedTitleRect = new UiRect(padding, textY, font.width(title), font.lineHeight);
        }
        graphics.drawString(font, title, padding, textY, THEME.palette().surfaces().headerText(), false);
        drawStateChip(graphics, page);
    }

    // 状态标签：有底板的页画胶囊，只读警示用警告色填充（与底部警告提示同色，替代额外的常驻说明文字）
    private void drawStateChip(GuiGraphics graphics, EditorPage page) {
        if (stateLines.isEmpty()) {
            return;
        }
        UiHostPalette palette = THEME.palette();
        int stateColor = palette.surfaces().text();
        if (page.statePanel()) {
            if (page.statePanelHighlighted()) {
                THEME.drawSurface(graphics, stateArea, palette.status().warning(), palette.controls().controlBevelDark());
                stateColor = palette.status().filledText();
            } else {
                THEME.drawSurface(graphics, stateArea, UiSurfaceRole.CHIP);
            }
        }
        int stateY = stateArea.y() + (stateArea.height() - stateLines.size() * font.lineHeight) / 2;
        for (FormattedCharSequence line : stateLines) {
            graphics.drawString(font, line, stateArea.x() + STATE_PAD, stateY, stateColor, false);
            stateY += font.lineHeight;
        }
    }

    // 底部状态提示：INFO 为纯文本，WARNING / DANGER 为实心色块（颜色与形状双线索）
    private void drawNotice(GuiGraphics graphics, int y, int w) {
        noticeRect = null;
        noticeTruncated = false;
        if (shell.notice() == null) {
            return;
        }
        int maxWidth = Math.max(0, w - PAD * 2);
        String full = shell.notice().getString();
        String text = TextScroll.trimToWidth(font, full, maxWidth);
        noticeTruncated = !text.equals(full);
        UiHostPalette palette = THEME.palette();
        if (shell.noticeLevel() == EditorShell.NoticeLevel.INFO) {
            graphics.drawString(font, text, PAD, y + 1, palette.surfaces().secondaryText(), false);
            noticeRect = new UiRect(PAD, y, font.width(text), NOTICE_H);
            return;
        }
        int fill = switch (shell.noticeLevel()) {
            case DANGER -> palette.controls().danger();
            case SUCCESS -> palette.status().success();
            default -> palette.status().warning();
        };
        UiRect chip = new UiRect(PAD, y, Math.min(maxWidth, font.width(text) + NOTICE_CHIP_PAD * 2), NOTICE_H);
        THEME.drawSurface(graphics, chip, fill, palette.controls().controlBevelDark());
        graphics.drawString(font, text, chip.x() + NOTICE_CHIP_PAD, y + 1, palette.status().filledText(), false);
        noticeRect = chip;
    }

    private RuleSaveOverlay saveOverlay() {
        if (saveOverlay == null) {
            saveOverlay = new RuleSaveOverlay(font, THEME, shell.workspace());
        }
        return saveOverlay;
    }

    /** 保存与弹层结束后才允许切换独立工具屏幕。 */
    public boolean canOpenDebugTools() { return !shell.saving() && shell.modals().isEmpty(); }

    /** 已授权工作区的草稿工具导航，沿用现有页与草稿命令。 */
    public void showDebugDrafts() { shell.enterDebug(); }

    // ---- 焦点 / 事件 / 生命周期 ----

    @Override protected UiInputScope inputScope() { return layers.select().activeScope(); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F8 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)
            return canOpenDebugTools() && DebugWorkbenchHooks.open();
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) return false;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // 外部切屏或断线也会调用 removed：这条路径不走 onClose，未应用的修改必须在这里写盘。
    @Override
    protected void releaseUiResources() {
        themePublication.discard();
        shell.flushDrafts();
        PreviewResources.screenClosed();
    }

    // 只关屏，不动工作区会话（工作区由服务端租约与协议驱动）
    @Override
    public void onClose() {
        if (shell.saving()) {
            return;
        }
        inputRouter().close();
        PreviewResources.screenClosed();
        shell.flushDrafts();
        super.onClose();
    }

    // 每帧只推进外壳：快照应用、提示存活、按钮状态与关屏请求都由外壳结算
    @Override
    public void tick() {
        shell.setScreenSize(this.width, this.height);
        shell.tick();
        refreshUiState();
        // 外壳只登记关闭请求，真正摘屏在这里执行，避免外壳反向依赖屏幕与渲染类型
        if (shell.takeCloseRequest()) {
            onClose();
        }
    }

    // 外部重新打开时刷新界面（此时工作区已进入 ACTIVE 并发出快照请求）
    public void refreshFromWorkspace() {
        shell.refreshFromWorkspace();
    }
}
