package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 卡片列表：对象数组的每条元素折成一张紧凑卡片。
 *
 * <p>元素声明里的第一个目录引用字段画成卡片首行的选择胶囊（图标 + 显示名，点开目录替换），
 * 其余数值字段并排画在第二行；卡片右上角是删除按钮，而新增入口挂在控件自绘的标题栏右侧。
 * 与 {@link SublistControl} 相比，每条元素只占两行、不再逐项展开成完整子表单，
 * 适合「一个引用 + 少量数值」构成的列表（例如箭雨的药水效果）。</p>
 *
 * <p>字段行标签由本控件自绘（{@link #usesLabelColumn()} 返回 false，表单不再预留标签列），因此行内问题
 * 不再有红标签可依附，而是经 {@link #issues(String)} 汇总到校验面板，并由胶囊的空态文案就地表达
 * 「还缺一个引用」。</p>
 */
final class CardListControl extends FormControl {

    // 自绘标题栏高度：字段标题与「添加」胶囊共用一行
    private static final int HEADER_HEIGHT = 16;
    // 卡片内边距与卡片间距
    private static final int CARD_PADDING = 4;
    private static final int CARD_GAP = 3;
    // 卡片首行高度取状态效果图标边长，胶囊与图标同高
    private static final int CHOICE_HEIGHT = 18;
    // 数值行行高：加高的行会拿到宽松档滑块外观
    private static final int VALUE_HEIGHT = 18;
    // 数值控件之间、列与列之间的间隙
    private static final int COLUMN_GAP = 4;
    // 数值控件的最小可读宽度：再窄就退回到每行一列
    private static final int MIN_VALUE_WIDTH = 96;
    // 引用胶囊的最窄宽度
    private static final int MIN_CHOICE_WIDTH = 56;
    // 删除按钮占位边长
    private static final int REMOVE_BUTTON_WIDTH = 20;

    private static final String UI = TypeLabels.UI_PREFIX + "list.";

    private final Font font;
    private final FormView owner;
    private final Runnable onChanged;
    // 卡片首行的目录引用字段；元素声明里没有引用字段时为 null，此时卡片只剩数值行
    private final @Nullable EditorField choiceField;
    // 卡片第二行的数值字段，顺序即并排顺序
    private final List<EditorField> valueFields;
    private final UiButton addButton;
    private final List<Entry> entries = new ArrayList<>();
    private int measuredHeight = DEFAULT_HEIGHT;
    private UiRect titleBounds = new UiRect(0, 0, 0, 0);
    private @Nullable UiRect emptyBounds;

    CardListControl(Font font, FormView owner, EditorField field, Runnable onChanged) {
        super(owner.theme(), field);
        this.font = font;
        this.owner = owner;
        this.onChanged = onChanged;
        this.choiceField = firstReference(field.subFields());
        this.valueFields = numericFields(field.subFields());
        this.addButton = new UiButton(THEME, font, Component.translatable(UI + "add"),
                UiButtonVariant.SECONDARY, this::addEntry).setChipStyle(true);
    }

    // 元素声明里的引用字段：卡片首行胶囊只描述一个引用
    private static @Nullable EditorField firstReference(List<EditorField> subFields) {
        for (EditorField sub : subFields) {
            if (FieldCatalogTypes.catalogTypeOf(sub) != null) {
                return sub;
            }
        }
        return null;
    }

    // 卡片第二行的数值字段：其余可独立编辑的标量
    private static List<EditorField> numericFields(List<EditorField> subFields) {
        List<EditorField> values = new ArrayList<>();
        for (EditorField sub : subFields) {
            if (sub.isNumeric()) {
                values.add(sub);
            }
        }
        return List.copyOf(values);
    }

    // ---- 结构 ----

    // 新增条目：目录按多选打开并回填已有引用，确认一次即可补齐多张卡片
    private void addEntry() {
        if (choiceField == null) {
            insertEntry();
            return;
        }
        List<String> current = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.reference != null) {
                current.add(entry.reference);
            }
        }
        // 选择范围是整段列表，目录策略按列表字段自身的路径解析
        owner.openMultiReferenceChoice(choiceField, owner.childPath(field.name()), current, this::applyReferences);
    }

    private void insertEntry() {
        Entry entry = createEntry(null);
        refreshChoice(entry);
        mutate(() -> entries.add(entry));
    }

    // 目录确认的选择快照即卡片集合：留下的卡片保留原有数值，新勾选的补默认值，取消勾选的移除
    private void applyReferences(List<String> picked) {
        mutate(() -> {
            List<Entry> pool = new ArrayList<>(entries);
            List<Entry> next = new ArrayList<>();
            for (String reference : new LinkedHashSet<>(picked)) {
                Entry matched = takeReference(pool, reference);
                next.add(matched != null ? matched : newEntry(reference));
            }
            entries.clear();
            entries.addAll(next);
        });
    }

    private static @Nullable Entry takeReference(List<Entry> pool, String reference) {
        for (Entry candidate : pool) {
            if (reference.equals(candidate.reference)) {
                pool.remove(candidate);
                return candidate;
            }
        }
        return null;
    }

    private Entry newEntry(@Nullable String reference) {
        Entry entry = createEntry(null);
        entry.reference = reference;
        refreshChoice(entry);
        return entry;
    }

    // 已存在条目换引用：只改这张卡的首行，数值保持不动
    private void chooseReference(Entry entry) {
        if (choiceField == null) {
            return;
        }
        int index = entries.indexOf(entry);
        if (index < 0) {
            return;
        }
        owner.openReferenceChoice(choiceField, entryChoicePath(choiceField, index), entry.reference, picked -> mutate(() -> {
            entry.reference = picked;
            refreshChoice(entry);
        }));
    }

    private void removeEntry(Entry entry) {
        if (!entries.contains(entry)) {
            return;
        }
        mutate(() -> entries.remove(entry));
    }

    // 结构改动立即写入草稿：增删与换引用不等下一次行级提交，卡片增减当场生效
    private void mutate(Runnable change) {
        owner.changeChildStructure(field.name(), () -> {
            markEdited();
            change.run();
        }, this::store);
        onChanged.run();
    }

    private Entry createEntry(@Nullable RuleValue raw) {
        Entry entry = new Entry();
        entry.choice = new UiButton(THEME, font, Component.empty(), UiButtonVariant.SECONDARY,
                () -> chooseReference(entry)).setChipStyle(true);
        entry.remove = new UiButton(THEME, font, Component.empty(), UiButtonVariant.DANGER,
                () -> removeEntry(entry)).setChipStyle(true)
                .setIcon(UiMarkIcons.icon(UiMarkIcons.Mark.CLOSE, THEME.palette().danger()))
                .setAccessibleName(Component.translatable(UI + "remove"));
        for (EditorField sub : valueFields) {
            FormControl control = FormControl.create(font, owner, sub, () -> {
                markEdited();
                onChanged.run();
            });
            control.setHeightOverride(VALUE_HEIGHT);
            entry.values.add(control);
        }
        loadEntry(entry, raw);
        return entry;
    }

    // 一条卡片的取值装载：引用取自首行字段，数值缺省时显示声明的默认值
    private void loadEntry(Entry entry, @Nullable RuleValue raw) {
        entry.original = raw == null ? null : raw.deepCopy();
        RuleValue object = raw != null && raw.isMapping() ? raw : null;
        entry.reference = choiceField == null || object == null ? null : referenceText(object.child(choiceField.name()));
        for (int i = 0; i < entry.values.size(); i++) {
            EditorField sub = valueFields.get(i);
            RuleValue stored = object == null ? null : object.child(sub.name());
            entry.values.get(i).load(stored != null ? stored.deepCopy() : sub.defaultValue());
        }
        refreshChoice(entry);
    }

    // 首行胶囊的内容：显示名、图标与完整注册名提示
    private void refreshChoice(Entry entry) {
        if (choiceField == null) {
            return;
        }
        String reference = entry.reference;
        if (reference == null || reference.isBlank()) {
            entry.choice.setLabel(Component.translatable(UI + "choose"));
            entry.choice.setIcon(null);
            return;
        }
        RuleCatalogType type = FieldCatalogTypes.catalogTypeOf(choiceField);
        Component name = null;
        UiIcon icon = null;
        if (!reference.startsWith("#")) {
            ResourceLocation key = ResourceLocation.tryParse(reference);
            name = key == null ? null : RuleDisplayLabels.catalogLabel(type, key);
            icon = CatalogPreviewIcons.iconFor(type, reference);
        }
        entry.choice.setLabel(name != null ? name : Component.literal(reference));
        entry.choice.setIcon(icon);
    }

    // ---- 布局 ----

    // 字段标题与新增入口由本控件自绘，表单不再预留左侧标签列
    @Override
    boolean usesLabelColumn() {
        return false;
    }

    @Override
    int height() {
        return measuredHeight;
    }

    @Override
    void measure(int width) {
        layoutAt(0, 0, width);
    }

    @Override
    void setBounds(int x, int y, int width) {
        layoutAt(x, y, width);
    }

    // 标题栏 + 每张卡片（首行胶囊与删除、第二行数值并排），顺带算出总高度
    private void layoutAt(int x, int y, int width) {
        int cursor = y;
        int addWidth = Math.clamp(Math.max(36, addButton.preferredWidth(6)), 0, width);
        addButton.setBounds(x + width - addWidth, cursor, addWidth, HEADER_HEIGHT);
        titleBounds = new UiRect(x, cursor, Math.max(0, width - addWidth - COLUMN_GAP), HEADER_HEIGHT);
        cursor += HEADER_HEIGHT + CARD_GAP;
        if (entries.isEmpty()) {
            emptyBounds = new UiRect(x, cursor, width, DEFAULT_HEIGHT);
            cursor += DEFAULT_HEIGHT;
        } else {
            emptyBounds = null;
        }
        for (Entry entry : entries) {
            cursor = layoutCard(entry, x, cursor, width) + CARD_GAP;
        }
        measuredHeight = Math.max(DEFAULT_HEIGHT, cursor - y - (entries.isEmpty() ? 0 : CARD_GAP));
    }

    // 单张卡片排版，返回卡片底边
    private int layoutCard(Entry entry, int x, int cardTop, int width) {
        int innerLeft = x + CARD_PADDING;
        int innerWidth = Math.max(0, width - CARD_PADDING * 2);
        int removeWidth = Math.clamp(Math.max(REMOVE_BUTTON_WIDTH, entry.remove.preferredWidth(4)), 0, innerWidth);
        int choiceRoom = Math.max(0, innerWidth - removeWidth - COLUMN_GAP);
        int choiceWidth = Math.clamp(Math.max(MIN_CHOICE_WIDTH, entry.choice.preferredWidth(6)), 0, choiceRoom);
        entry.choice.setBounds(innerLeft, cardTop + CARD_PADDING, choiceWidth, CHOICE_HEIGHT);
        entry.remove.setBounds(x + width - CARD_PADDING - removeWidth, cardTop + CARD_PADDING,
                removeWidth, CHOICE_HEIGHT);
        int valuesTop = cardTop + CARD_PADDING + CHOICE_HEIGHT + CARD_GAP;
        int bottom = valuesTop;
        int count = entry.values.size();
        entry.captions.clear();
        if (count > 0) {
            int caption = captionHeight();
            int columns = Math.clamp((innerWidth + COLUMN_GAP) / (MIN_VALUE_WIDTH + COLUMN_GAP), 1, count);
            int columnWidth = Math.max(0, (innerWidth - (columns - 1) * COLUMN_GAP) / columns);
            int rows = (count + columns - 1) / columns;
            for (int i = 0; i < count; i++) {
                int column = i % columns;
                int columnX = innerLeft + column * (columnWidth + COLUMN_GAP);
                int columnEnd = column == columns - 1 ? innerLeft + innerWidth : columnX + columnWidth;
                int valueWidth = Math.max(0, columnEnd - columnX);
                int rowTop = valuesTop + (i / columns) * (caption + VALUE_HEIGHT + CARD_GAP);
                entry.captions.add(new UiRect(columnX, rowTop, valueWidth, caption));
                entry.values.get(i).setBounds(columnX, rowTop + caption, valueWidth);
            }
            bottom = valuesTop + rows * (caption + VALUE_HEIGHT) + (rows - 1) * CARD_GAP;
        }
        int cardBottom = bottom + CARD_PADDING;
        entry.card = new UiRect(x, cardTop, width, Math.max(0, cardBottom - cardTop));
        return cardBottom;
    }

    // ---- 绘制 ----

    @Override
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        graphics.drawString(renderFont, label(), titleBounds.x(),
                titleBounds.y() + Math.max(0, (titleBounds.height() - renderFont.lineHeight) / 2),
                THEME.palette().textSecondary(), false);
        addButton.render(graphics, renderFont, mouseX, mouseY);
        if (emptyBounds != null) {
            graphics.drawString(renderFont, Component.translatable(TypeLabels.UI_PREFIX + "list.empty"),
                    emptyBounds.x() + CARD_PADDING,
                    emptyBounds.y() + Math.max(0, (emptyBounds.height() - renderFont.lineHeight) / 2),
                    THEME.palette().textSecondary(), false);
            return;
        }
        for (Entry entry : entries) {
            if (entry.card != null) {
                THEME.drawCard(graphics, entry.card);
            }
        }
        for (Entry entry : entries) {
            entry.choice.render(graphics, renderFont, mouseX, mouseY);
            entry.remove.render(graphics, renderFont, mouseX, mouseY);
            for (int i = 0; i < entry.values.size(); i++) {
                drawCaption(graphics, renderFont, valueFields.get(i), entry.captions.get(i));
                entry.values.get(i).render(graphics, renderFont, mouseX, mouseY);
            }
        }
    }

    // 数值控件的标题：只写字段名，按列宽裁剪，颜色让位给下方可编辑内容
    private void drawCaption(GuiGraphics graphics, Font renderFont, EditorField sub, UiRect box) {
        String text = TextScroll.trimToWidth(renderFont, Component.translatable(sub.labelKey()).getString(), box.width());
        graphics.drawString(renderFont, text, box.x(), box.y() + Math.max(0, (box.height() - renderFont.lineHeight) / 2),
                THEME.palette().textSecondary(), false);
    }

    // 标题行高度：跟随字体行高，小字号主题下同比例收紧
    private int captionHeight() {
        return font.lineHeight + 1;
    }

    // ---- 装载与回写 ----

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        retainChildInputs();
        entries.clear();
        if (value != null && value.isList()) {
            for (RuleValue element : value.items()) {
                entries.add(createEntry(element));
            }
        }
        layoutAt(0, 0, 1);
    }

    @Override
    @Nullable RuleValue store() {
        // 未编辑：原样返回装载数组，保持省略键与未知字段不变
        if (!isEdited()) {
            return originalValue();
        }
        RuleValue.Sequence array = RuleValue.list();
        for (Entry entry : entries) {
            array.add(storeEntry(entry));
        }
        return array.build();
    }

    private RuleValue storeEntry(Entry entry) {
        if (entry.original != null && !entry.original.isMapping()) {
            return entry.original.deepCopy();
        }
        RuleValue.Mapping object = RuleValue.mapping(entry.original);
        if (choiceField != null && entry.reference != null && !entry.reference.isBlank()) {
            object.put(choiceField.name(), RuleValue.text(entry.reference));
        }
        for (int i = 0; i < entry.values.size(); i++) {
            RuleValue value = entry.values.get(i).store();
            if (value != null) {
                object.put(valueFields.get(i).name(), value.deepCopy());
            }
        }
        return object.build();
    }

    // ---- 问题与提示 ----

    @Override
    List<FormIssue> issues(String path) {
        List<FormIssue> list = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String entryPath = DraftPaths.indexPath(path, i);
            Entry entry = entries.get(i);
            if (choiceField != null && (entry.reference == null || entry.reference.isBlank())) {
                list.add(FormIssue.required(DraftPaths.child(entryPath, choiceField.name()),
                        Component.translatable(choiceField.labelKey())));
            }
            for (int v = 0; v < entry.values.size(); v++) {
                list.addAll(entry.values.get(v).issues(DraftPaths.child(entryPath, valueFields.get(v).name())));
            }
        }
        return list;
    }

    // 悬停提示给出完整注册名：显示名可能被同名资源混淆，完整 id 只在悬停时出现
    @Override
    @Nullable Component tooltipAt(double x, double y) {
        for (Entry entry : entries) {
            if (entry.reference != null && !entry.reference.isBlank() && entry.choice.bounds().contains(x, y)) {
                return Component.literal(entry.reference);
            }
        }
        return null;
    }

    // ---- 输入转发 ----

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (addButton.mousePressed(context)) {
            return true;
        }
        for (Entry entry : entries) {
            if (entry.choice.mousePressed(context) || entry.remove.mousePressed(context)) {
                return true;
            }
            for (FormControl value : entry.values) {
                if (value.mousePressed(context)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        boolean consumed = addButton.mouseReleased(context);
        for (Entry entry : entries) {
            consumed |= entry.choice.mouseReleased(context);
            consumed |= entry.remove.mouseReleased(context);
            for (FormControl value : entry.values) {
                consumed |= value.mouseReleased(context);
            }
        }
        return consumed;
    }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        boolean consumed = false;
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                consumed |= value.mouseDragged(context, dragX, dragY);
            }
        }
        return consumed;
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        boolean consumed = false;
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                consumed |= value.mouseScrolled(context, scrollX, scrollY);
            }
        }
        return consumed;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean consumed = false;
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                consumed |= value.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return consumed;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean consumed = false;
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                consumed |= value.charTyped(codePoint, modifiers);
            }
        }
        return consumed;
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(addButton);
        for (Entry entry : entries) {
            out.add(entry.choice);
            for (FormControl value : entry.values) {
                value.addFocusTargets(out);
            }
            out.add(entry.remove);
        }
    }

    @Override
    void setEnabled(boolean enabled) {
        addButton.setEnabled(enabled);
        for (Entry entry : entries) {
            entry.choice.setEnabled(enabled);
            entry.remove.setEnabled(enabled);
            for (FormControl value : entry.values) {
                value.setEnabled(enabled);
            }
        }
    }

    @Override
    void endInteractions(UiInteractionSession.EndReason reason) {
        cancelButtonPresses(List.of(addButton));
        for (Entry entry : entries) {
            cancelButtonPresses(List.of(entry.choice, entry.remove));
            for (FormControl value : entry.values) {
                value.endInteractions(reason);
            }
        }
    }

    @Override
    boolean isEditing() {
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                if (value.isEditing()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    boolean hasPendingInput() {
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                if (value.hasPendingInput()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    void finishInput() {
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                value.finishInput();
            }
        }
    }

    @Override
    void inputAccepted() {
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                value.inputAccepted();
            }
        }
    }

    @Override
    void retainChildInputs() {
        for (Entry entry : entries) {
            for (FormControl value : entry.values) {
                value.retainChildInputs();
            }
        }
    }

    // 数值子控件的文本不进行级快照：行内问题不该因为子控件有文本而挡住整行写入
    @Override
    void appendInputText(List<String> out) {
    }

    // 条目在草稿里的引用字段路径：目录入口需要完整路径
    private String entryChoicePath(EditorField choiceField, int index) {
        return DraftPaths.child(owner.childPath(DraftPaths.indexPath(field.name(), index)), choiceField.name());
    }

    // 引用字段的 JSON 文本；非文本元素视为缺省
    private static @Nullable String referenceText(@Nullable RuleValue value) {
        return value != null && value.isText() ? value.asText() : null;
    }

    // 一条卡片的全部子控件
    private static final class Entry {
        private UiButton choice;
        private UiButton remove;
        private final List<FormControl> values = new ArrayList<>();
        // 每个数值控件的上方标题行，与 values 同序
        private final List<UiRect> captions = new ArrayList<>();
        private @Nullable String reference;
        private @Nullable RuleValue original;
        private @Nullable UiRect card;
    }
}
