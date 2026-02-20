package com.meteorite.itemdespawntowhat.ui;


import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/*
 * 物品ID输入建议组件
 * 支持逗号分隔，Tab补全，上下键选择
 */
public class SuggestionWidget {
    private static final int MAX_FETCH = 100;       // 最多匹配条数
    private static final int MAX_VISIBLE = 10;      // 下拉框最多可见条数
    private static final int ENTRY_HEIGHT = 12;
    private static final int PADDING = 3;

    private final Font font;
    private final EditBox attachedBox;
    private final List<String> candidateIds;
    // 当前的建议列表
    private List<String> suggestions = new ArrayList<>();
    // 当前的建议列表索引，-1 代表未选中
    private int selectedIndex = -1;
    // 下拉框可见区域的起始偏移（scroll offset）
    private int scrollOffset = 0;
    private boolean visible = false;

    public SuggestionWidget(Font font, EditBox attachedBox, Registry<?> registry) {
        this.font = font;
        this.attachedBox = attachedBox;
        this.candidateIds = registry.keySet()
                .stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.toList());
    }

    public void updateSuggestions() {
        String fullText = attachedBox.getValue();
        String currentSegment = getCurrentSegment(fullText);

        // 只有输入了至少一个字符才显示
        if (currentSegment.isEmpty()) {
            hide();
            return;
        }

        List<String> matched = new ArrayList<>();
        String lowerSegment = currentSegment.toLowerCase();

        for (String id : candidateIds) {
            if (matched.size() >= MAX_FETCH) break;
            // 匹配完整注册名 或 只匹配 path 部分
            if (id.startsWith(lowerSegment) || pathOf(id).startsWith(lowerSegment)) {
                matched.add(id);
            }
        }

        if (matched.isEmpty()) {
            hide();
            return;
        }

        suggestions = matched;
        selectedIndex = -1;
        scrollOffset = 0;
        visible = true;
    }

    // 从完整文本中提取当前正在编辑的段，从最后一个逗号开始提取
    private String getCurrentSegment(String fullText) {
        int lastComma = fullText.lastIndexOf(',');
        if (lastComma < 0) {
            return fullText.trim();
        }
        return fullText.substring(lastComma + 1).trim();
    }

    // 从 "namespace:path" 中提取 path
    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    // =========== 键盘事件 ========== //
    public boolean keyPressed(int keyCode) {
        if (!visible || suggestions.isEmpty()) return false;

        switch (keyCode) {
            // Tab：补全选中项（未选中则补全第一条可见项）
            case GLFW.GLFW_KEY_TAB -> {
                int idx = selectedIndex >= 0 ? selectedIndex : scrollOffset;
                applySuggestion(suggestions.get(idx));
                return true;
            }

            // 上方向键：向上移动选中项，到顶端时向上滚动
            case GLFW.GLFW_KEY_UP -> {
                if (selectedIndex < 0) {
                    // 未选中 -> 选中末尾
                    selectedIndex = suggestions.size() - 1;
                    scrollOffset = Math.max(0, suggestions.size() - MAX_VISIBLE);
                } else if (selectedIndex > 0) {
                    selectedIndex--;
                    // 如果选中项滚出了可见区域顶端，则上滚
                    if (selectedIndex < scrollOffset) {
                        scrollOffset = selectedIndex;
                    }
                } else {
                    // 已在最顶端，循环到末尾
                    selectedIndex = suggestions.size() - 1;
                    scrollOffset = Math.max(0, suggestions.size() - MAX_VISIBLE);
                }
                return true;
            }

            // 下方向键：向下移动选中项，到底端时向下滚动
            case GLFW.GLFW_KEY_DOWN -> {
                if (selectedIndex < 0) {
                    // 未选中 -> 选中第一条可见项
                    selectedIndex = scrollOffset;
                } else if (selectedIndex < suggestions.size() - 1) {
                    selectedIndex++;
                    // 如果选中项滚出了可见区域底端，则下滚
                    if (selectedIndex >= scrollOffset + MAX_VISIBLE) {
                        scrollOffset = selectedIndex - MAX_VISIBLE + 1;
                    }
                } else {
                    // 已在最底端，循环到顶端
                    selectedIndex = 0;
                    scrollOffset = 0;
                }
                return true;
            }

            // Enter：如果有选中项则补全
            case GLFW.GLFW_KEY_ENTER -> {
                if (selectedIndex >= 0) {
                    applySuggestion(suggestions.get(selectedIndex));
                    return true;
                }
            }

            // Esc：隐藏建议，不消费事件（让 Screen 也能处理关闭）
            case GLFW.GLFW_KEY_ESCAPE -> {
                hide();
                return false;
            }
        }

        return false;
    }

    // 将选中的建议补全到 EditBox
    private void applySuggestion(String suggestion) {
        String fullText = attachedBox.getValue();
        int lastComma = fullText.lastIndexOf(',');
        String newText;
        if (lastComma < 0) {
            newText = suggestion;
        } else {
            // 保留前面的部分 + 逗号 + 空格 + 建议
            newText = fullText.substring(0, lastComma + 1) + " " + suggestion;
        }
        attachedBox.setValue(newText);
        // 将光标移到末尾
        attachedBox.moveCursorToEnd(false);
        hide();
    }

    // =========== 鼠标事件 =========== //
    public boolean mouseClicked(double mouseX, double mouseY, double scrollDelta) {
        if (!visible || suggestions.isEmpty()) return false;
        if (!isMouseOverDropdown(mouseX, mouseY)) return false;

        int dropdownY = attachedBox.getY() + attachedBox.getHeight();
        int relY = (int) mouseY - dropdownY - PADDING;
        int idx = relY / ENTRY_HEIGHT + scrollOffset;
        if (idx >= 0 && idx < suggestions.size()) {
            applySuggestion(suggestions.get(idx));
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (!visible || suggestions.isEmpty()) return false;
        if (!isMouseOverDropdown(mouseX, mouseY)) return false;

        // scrollDelta > 0 向上滚，< 0 向下滚
        int direction = scrollDelta > 0 ? -1 : 1;
        scrollOffset = clampScrollOffset(scrollOffset + direction);

        // 让已选中项跟随可见区域
        if (selectedIndex >= 0) {
            if (selectedIndex < scrollOffset) {
                selectedIndex = scrollOffset;
            } else if (selectedIndex >= scrollOffset + MAX_VISIBLE) {
                selectedIndex = scrollOffset + MAX_VISIBLE - 1;
            }
        }

        return true;
    }

    // ========== 渲染 ========== //
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!visible || suggestions.isEmpty()) return;

        // 渲染选择框
        int visibleCount = Math.min(MAX_VISIBLE, suggestions.size());
        int dropdownX = attachedBox.getX();
        int dropdownY = attachedBox.getY() + attachedBox.getHeight();
        int dropdownWidth = attachedBox.getWidth();
        int dropdownHeight = visibleCount * ENTRY_HEIGHT + PADDING * 2;

        // 背景
        guiGraphics.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, 0xD0000000);
        // 边框
        guiGraphics.renderOutline(dropdownX, dropdownY, dropdownWidth, dropdownHeight, 0xFF888888);

        // 渲染可见条目
        for (int i = 0; i < visibleCount; i++) {
            int realIdx = i + scrollOffset;
            if (realIdx >= suggestions.size()) break;

            int entryY = dropdownY + PADDING + i * ENTRY_HEIGHT;
            boolean isHovered = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth
                    && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;
            boolean isSelected = realIdx == selectedIndex;

            if (isSelected) {
                guiGraphics.fill(dropdownX + 1, entryY, dropdownX + dropdownWidth - 1, entryY + ENTRY_HEIGHT, 0xFF2255AA);
            } else if (isHovered) {
                guiGraphics.fill(dropdownX + 1, entryY, dropdownX + dropdownWidth - 1, entryY + ENTRY_HEIGHT, 0xFF444444);
            }

            String text = suggestions.get(realIdx);
            int textColor = isSelected ? 0xFFFFFF55 : 0xFFCCCCCC;
            String displayText = font.plainSubstrByWidth(text, dropdownWidth - PADDING * 2 - 2);
            guiGraphics.drawString(font, displayText, dropdownX + PADDING + 1, entryY + 2, textColor, false);
        }

        // 如果内容超出可见区，显示滚动条
        if (suggestions.size() > MAX_VISIBLE) {
            renderScrollbar(guiGraphics, dropdownX, dropdownY, dropdownWidth, dropdownHeight, visibleCount);
        }
    }

    // 渲染右侧细滚动条
    private void renderScrollbar(GuiGraphics guiGraphics, int dropdownX, int dropdownY,
                                 int dropdownWidth, int dropdownHeight, int visibleCount) {
        int scrollbarX = dropdownX + dropdownWidth - 3;
        int trackTop = dropdownY + 1;
        int trackBottom = dropdownY + dropdownHeight - 1;
        int trackHeight = trackBottom - trackTop;

        // 滑块高度按比例计算
        int thumbHeight = Math.max(4, trackHeight * visibleCount / suggestions.size());
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset
                / Math.max(1, suggestions.size() - visibleCount);

        // 轨道（深色背景）
        guiGraphics.fill(scrollbarX, trackTop, scrollbarX + 2, trackBottom, 0xFF333333);
        // 滑块
        guiGraphics.fill(scrollbarX, thumbTop, scrollbarX + 2, thumbTop + thumbHeight, 0xFFAAAAAA);
    }

    // ========== 辅助方法 ========== //
    // 鼠标悬浮在框框上
    private boolean isMouseOverDropdown(double mouseX, double mouseY) {
        int visibleCount = Math.min(MAX_VISIBLE, suggestions.size());
        int dropdownX = attachedBox.getX();
        int dropdownY = attachedBox.getY() + attachedBox.getHeight();
        int dropdownWidth = attachedBox.getWidth();
        int dropdownHeight = visibleCount * ENTRY_HEIGHT + PADDING * 2;

        return mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth
                && mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
    }

    // 条目滚动
    private int clampScrollOffset(int offset) {
        int maxOffset = Math.max(0, suggestions.size() - MAX_VISIBLE);
        return Math.max(0, Math.min(offset, maxOffset));
    }

    // ========== 当前状态 =========== //
    public void hide() {
        visible = false;
        suggestions.clear();
        selectedIndex = -1;
    }

    public boolean isVisible() {
        return visible;
    }

    // 绑定的文本框
    public EditBox getAttachedBox() {
        return attachedBox;
    }

}
