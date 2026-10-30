package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import org.lwjgl.glfw.GLFW;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiDescriptionTooltip;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;

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
public final class RuleEditorScreen extends Screen implements UiLayoutSource {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private final UiHostTheme THEME = UiHostThemes.defaultTheme();

    // 布局常量（页自身的内边距由页声明）
    private static final int PAD = 4;
    private static final int NOTICE_H = 10;
    // 实心提示色块的左右内边距
    private static final int NOTICE_CHIP_PAD = 3;
    // i18n 前缀
    private static final String UI = TypeLabels.UI_PREFIX;

    private final EditorShell shell;
    private final EditorInputRouter input;

    private @Nullable RuleSaveOverlay saveOverlay;
    // 提示的折行判定与命中矩形每帧重算，属于渲染态，不进外壳
    private boolean noticeTruncated;
    private @Nullable UiRect noticeRect;
    private @Nullable UiRect truncatedTitleRect;

    public RuleEditorScreen(RuleEditClientWorkspace workspace) {
        super(Component.translatable(UI + "title"));
        BuiltinEditorDescriptors.bootstrap();
        RuleEditorModel model = new RuleEditorModel(workspace);
        // 屏幕构造期 this.font 尚未注入，外壳与页面直接取客户端字体实例
        this.shell = new EditorShell(THEME, UiHostThemes.flatTheme(), workspace, model, new RuleListDisplayCache(model), Minecraft.getInstance().font);
        this.input = new EditorInputRouter(shell, this::saveOverlay);
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
        shell.listPage().refreshList();
        shell.editPage().rebuildEdit();
        shell.rebuildFocus();
    }

    // ---- 渲染 ----

    // 每帧重算布局并绘制
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PreviewIcons.beginFrame();
        // 实时尺寸由外壳持有：页与外壳都按它排版
        shell.setScreenSize(this.width, this.height);
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int actualMouseX = mouseX;
        int actualMouseY = mouseY;
        EditorPage page = shell.activePage();
        UiTypePicker picker = page.overlay();
        boolean saving = shell.saving();
        if (saving) {
            saveOverlay();
        } else {
            saveOverlay = null;
        }
        var pointer = UiPointer.gated(input.pageActive(), mouseX, mouseY);
        mouseX = pointer.x();
        mouseY = pointer.y();
        int w = this.width;
        int h = this.height;
        shell.modals().setBounds(0, 0, w, h);
        THEME.drawWindow(graphics, new UiRect(0, 0, w, h));
        int headerHeight = page.headerHeight();
        int pagePad = page.pagePadding();
        THEME.drawDivider(graphics, 0, headerHeight, w);
        drawHeaderText(graphics, page);
        int availableWidth = Math.max(0, w - pagePad * 2);
        // 头部附加内容（页签条）由页自己排布，返回内容区起点
        int y = page.layoutHeader(graphics, font, mouseX, mouseY, pagePad, headerHeight + PAD);
        int footerHeight = page.footerHeight(availableWidth);
        int footerY = h - pagePad - footerHeight;
        int noticeY = footerY - PAD - (shell.notice() == null ? 0 : NOTICE_H);
        UiRect contentArea = new UiRect(pagePad, y, availableWidth, Math.max(0, noticeY - y - pagePad));
        page.render(graphics, font, mouseX, mouseY, contentArea);
        drawNotice(graphics, noticeY, w);
        page.renderFooter(graphics, font, mouseX, mouseY, new UiRect(pagePad, footerY, availableWidth, footerHeight));
        mouseX = actualMouseX;
        mouseY = actualMouseY;
        if (saving) {
            int overlayMouseX = mouseX;
            int overlayMouseY = mouseY;
            UiRenderLayers.draw(graphics, UiRenderLayers.MODAL_STEP,
                    () -> saveOverlay().render(graphics, width, height, overlayMouseX, overlayMouseY, Util.getMillis()));
            graphics.flush();
            PreviewIcons.endFrame();
            return;
        }
        shell.modals().render(graphics, font, mouseX, mouseY);
        if (shell.modals().isEmpty() && picker != null) {
            picker.setBounds(contentArea.x(), contentArea.y(), contentArea.width(), contentArea.height());
            picker.render(graphics, font, mouseX, mouseY);
        }
        if (shell.modals().isEmpty() && picker == null) {
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
            } else if (!page.renderDescriptionTooltip(graphics, mouseX, mouseY)) {
                // 字段说明提示：没有弹窗且页未自行绘制说明时才显示，避免盖住上层内容
                tip = page.tooltipAt(mouseX, mouseY);
            }
            if (tip != null) {
                renderTooltip(graphics, tip, mouseX, mouseY);
            }
        }
        graphics.flush();
        PreviewIcons.endFrame();
    }

    // Component 的单行 tooltip 重载不处理换行；按窗口宽度折行后交给无阴影提示面板定位。
    private void renderTooltip(GuiGraphics graphics, Component tip, int mouseX, int mouseY) {
        UiFlatText.tooltip(graphics, font, tip, Math.clamp(width - 16, 1, 240), mouseX, mouseY);
    }

    /**
     * 导出当前界面层的排版快照：活动页控件在前，模态层在后。
     *
     * <p>只导出活动页：另两页在切换后不再重排，其控件矩形会停留在上一次的屏幕位置，
     * 报出来会让探针误以为那部分内容仍然可见。坐标已是屏幕 GUI 坐标——页每帧按内容区重排，
     * 滚动也在页内部结算完毕。活动类型选择器由编辑页导出；保存遮罩由独立状态持有。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>(shell.activePage().layoutNodes());
        nodes.addAll(shell.modals().layoutNodes());
        return nodes;
    }

    // 顶部标题与状态：文本与状态标签由页声明，屏幕只负责排版与配色
    private void drawHeaderText(GuiGraphics graphics, EditorPage page) {
        truncatedTitleRect = null;
        String title = page.title();
        if (title == null) {
            title = this.getTitle().getString();
        }
        String state = page.stateLabel();
        int padding = page.pagePadding();
        int headerHeight = page.headerHeight();
        // 头部带比默认高度更高时（编辑页）标题垂直居中，其余页贴顶
        int textY = headerHeight > UiTheme.HEADER_HEIGHT ? (headerHeight - font.lineHeight) / 2 : 3;
        // 只读标签带一句完整说明，需要更宽的底色才不显得局促
        int stateWidth = font.width(state) + (page.statePanelHighlighted() ? 20 : 12);
        String fullTitle = title;
        title = TextScroll.trimToWidth(font, title, Math.max(0, this.width - padding * 2 - stateWidth - PAD));
        if (!title.equals(fullTitle)) {
            truncatedTitleRect = new UiRect(padding, textY, font.width(title), font.lineHeight);
        }
        graphics.drawString(font, title, padding, textY, THEME.palette().headerText(), false);
        int stateColor = THEME.palette().headerText();
        if (page.statePanel()) {
            UiRect stateRect = new UiRect(this.width - padding - stateWidth, 4, stateWidth, headerHeight - 8);
            if (page.statePanelHighlighted()) {
                // 只读来源用警告色填充状态标签：与底部警告提示同色，替代额外的常驻说明文字
                THEME.drawSurface(graphics, stateRect, THEME.palette().warning(), THEME.palette().controlBevelDark());
                stateColor = THEME.palette().textPrimary();
            } else {
                THEME.drawPanel(graphics, stateRect);
            }
        }
        graphics.drawString(font, state, this.width - padding - font.width(state) - (page.statePanel() ? 6 : 0),
                textY, stateColor, false);
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
            graphics.drawString(font, text, PAD, y + 1, palette.textSecondary(), false);
            noticeRect = new UiRect(PAD, y, font.width(text), NOTICE_H);
            return;
        }
        int fill = switch (shell.noticeLevel()) {
            case DANGER -> palette.danger();
            case SUCCESS -> palette.success();
            default -> palette.warning();
        };
        UiRect chip = new UiRect(PAD, y, Math.min(maxWidth, font.width(text) + NOTICE_CHIP_PAD * 2), NOTICE_H);
        THEME.drawSurface(graphics, chip, fill, palette.controlBevelDark());
        graphics.drawString(font, text, chip.x() + NOTICE_CHIP_PAD, y + 1, palette.textPrimary(), false);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return input.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return input.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return input.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F8 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            return canOpenDebugTools() && DebugWorkbenchHooks.open();
        }
        return input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return input.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return input.charTyped(codePoint, modifiers);
    }

    // 外部切屏或断线也会调用 removed：这条路径不走 onClose，未应用的修改必须在这里写盘。
    @Override
    public void removed() {
        input.close();
        shell.flushDrafts();
        PreviewResources.screenClosed();
        super.removed();
    }

    // 只关屏，不动工作区会话（工作区由服务端租约与协议驱动）
    @Override
    public void onClose() {
        if (shell.saving()) {
            return;
        }
        input.close();
        PreviewResources.screenClosed();
        shell.flushDrafts();
        super.onClose();
    }

    // 每帧只推进外壳：快照应用、提示存活、按钮状态与关屏请求都由外壳结算
    @Override
    public void tick() {
        shell.setScreenSize(this.width, this.height);
        shell.tick();
        input.synchronize();
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
