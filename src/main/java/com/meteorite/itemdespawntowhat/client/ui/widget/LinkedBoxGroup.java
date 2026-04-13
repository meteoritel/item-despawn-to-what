package com.meteorite.itemdespawntowhat.client.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class LinkedBoxGroup {
    /** 主框快照（失焦前保存，用于 diff）*/
    private List<String> primarySnapshot = List.of();

    private final FocusAwareEditBox primaryBox;
    private final List<FollowerEntry> followers = new ArrayList<>();

    private LinkedBoxGroup(Builder builder) {
        this.primaryBox = builder.primaryBox;
        this.followers.addAll(builder.followers);

        // 注册主框的焦点监听：获焦时存快照，失焦时触发同步
        this.primaryBox.setFocusListener(focused -> {
            if (focused) {
                primarySnapshot = splitTokens(primaryBox.getValue());
            } else {
                syncFollowers(primarySnapshot);
            }
        });
    }

    // ========== 公开 API ========== //
    // 强制将所有从框的条目数对其主框当前值
    public void syncAllFollowers() {
        List<String> current = splitTokens(primaryBox.getValue());
        for (FollowerEntry fe : followers) {
            List<String> existing = splitTokens(fe.box.getValue());
            List<String> synced = alignToSize(existing, current.size(), fe.defaultSupplier);
            fe.box.setValue(String.join(",", synced));
        }
    }

    // 清空主框和所有从框
    public void clear() {
        primaryBox.setValue("");
        for (FollowerEntry fe : followers) {
            fe.box.setValue("");
        }
        primarySnapshot = List.of();
    }

    // 返回主框，供外部注册
    public FocusAwareEditBox getPrimaryBox() {
        return primaryBox;
    }

    // 返回第 index 个从框
    public EditBox getFollower(int index) {
        return followers.get(index).box;
    }

    // 返回所有框（主框在前，从框按添加顺序在后）
    public List<EditBox> allBoxes() {
        List<EditBox> result = new ArrayList<>();
        result.add(primaryBox);
        for (FollowerEntry fe : followers) {
            result.add(fe.box);
        }
        return result;
    }

    // ========== 数据同步逻辑 ========== //
    private void syncFollowers(List<String> oldPrimary) {
        List<String> newPrimary = splitTokens(primaryBox.getValue());

        for (FollowerEntry fe : followers) {
            List<String> oldValues = splitTokens(fe.box.getValue());

            // 补齐旧值列表，确保与 oldPrimary 等长
            List<String> paddedOld = new ArrayList<>(oldValues);
            while (paddedOld.size() < oldPrimary.size()) {
                paddedOld.add(fe.defaultSupplier.get());
            }

            // diff 映射
            List<String> newValues = new ArrayList<>(newPrimary.size());
            int searchFrom = 0;
            for (String newKey : newPrimary) {
                int foundIdx = -1;
                for (int i = searchFrom; i < oldPrimary.size(); i++) {
                    if (oldPrimary.get(i).equals(newKey)) {
                        foundIdx = i;
                        break;
                    }
                }
                if (foundIdx >= 0) {
                    newValues.add(paddedOld.get(foundIdx));
                    searchFrom = foundIdx + 1;
                } else {
                    newValues.add(fe.defaultSupplier.get());
                }
            }

            fe.box.setValue(String.join(",", newValues));
        }
    }

    // 列表长度限制为与主框相同，超出部分直接截断
    private static List<String> alignToSize(List<String> list, int targetSize,
                                            Supplier<String> defaultSupplier) {
        List<String> result = new ArrayList<>(list);
        while (result.size() < targetSize) {
            result.add(defaultSupplier.get());
        }
        if (result.size() > targetSize) {
            result = result.subList(0, targetSize);
        }
        return result;
    }

    // ========== 文本解析辅助方法 ========== //

    // 将逗号分隔字符串拆分为 token 列表，去除首尾空格，过滤空串
    public static List<String> splitTokens(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.replace(" ", "").split(",", -1))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // 将逗号分隔字符串拆分为值列表，保留尾部空串，以便下标对齐
    public static List<String> splitValues(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        String[] parts = raw.replace(" ", "").split(",", -1);
        List<String> list = new ArrayList<>(Arrays.asList(parts));
        // 去掉末尾空串（不去中间空串，保留占位）
        while (!list.isEmpty() && list.getLast().isEmpty()) {
            list.removeLast();
        }
        return list;
    }

    // ========== 构造主框与从框方法 ========== //
    // 添加主框与从框
    public static Builder builder(Font font, int primaryWidth, int primaryMaxLength) {
        FocusAwareEditBox primary = new FocusAwareEditBox(
                font, 0, 0, primaryWidth, 18, Component.empty());
        primary.setMaxLength(primaryMaxLength);
        return new Builder(primary);
    }

    // 使用外部已创建的 FocusAwareEditBox 作为主框
    public static Builder builder(FocusAwareEditBox existingPrimary) {
        return new Builder(existingPrimary);
    }

    // ========== 内部构造主从框类 ========== //
    public static class Builder {
        private final FocusAwareEditBox primaryBox;
        private final List<FollowerEntry> followers = new ArrayList<>();

        private Builder(FocusAwareEditBox primaryBox) {
            this.primaryBox = primaryBox;
        }

        public Builder follow(@NotNull EditBox box, @NotNull Supplier<String> defaultSupplier) {
            followers.add(new FollowerEntry(box, defaultSupplier));
            return this;
        }

        public LinkedBoxGroup build() {
            return new LinkedBoxGroup(this);
        }
    }
    // ========== 内部数据类 ========== //
    private record FollowerEntry(EditBox box, Supplier<String> defaultSupplier) {}
}
