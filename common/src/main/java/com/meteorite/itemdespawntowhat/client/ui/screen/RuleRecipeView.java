package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;

import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.shared.BlockPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.ProductKind;
import com.meteorite.itemdespawntowhat.core.model.RuleCategoryClassifier;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** 配方行的只读展示快照：保留数组顺序，复用图标，角标与完整说明使用同一份数据。 */
public final class RuleRecipeView {
    private static final String UI = TypeLabels.UI_PREFIX + "recipe.";
    private static final String OWN = "itemdespawntowhat:";
    private static final int ICON_SIZE = 18;
    private static final int OBJECT_CONTENT_HEIGHT = 20;
    static final int CONTENT_HEIGHT = RuleTriggerView.HEIGHT;
    private static final int CONSUMED_COLOR = 0xFFFFA500;
    private static final UiIcon UNKNOWN = new UiIcon.Item(new ItemStack(Items.BARRIER));
    // 效果解码器：预览按产出类别元数据判断产出，内置类型固定，进程内只建一次
    private static @Nullable Codec<Effect> effectCodec;
    private final List<Part> parts = new ArrayList<>();
    private final Component summary;
    private final Component tooltip;
    private final RuleTriggerView triggerView;
    private final UiScrollView clip = new UiScrollView();
    private long hoverStarted;
    private long lastDraw;
    // 每项宽度只随字体与 parts 变化：字体不变时复用测量结果，避免每帧逐项测文本
    private @Nullable Font measuredFont;
    private int[] compactWidths = new int[0];
    private int[] nameWidths = new int[0];

    /** 一项对象及其数量要求；同名催化剂的不同要求保留原顺序，不错误地累加或取最大值。 */
    private static final class ObjectPart {
        private final Component name;
        private final UiIcon icon;
        private final LinkedHashSet<Integer> quantities = new LinkedHashSet<>();
        private final LinkedHashSet<Integer> consumedQuantities = new LinkedHashSet<>();
        private boolean fluidConsumed;
        private boolean originFilled;
        private String quantityText = "";
        private @Nullable TagPreviewIcons.Tag tag;

        private ObjectPart(Component name, @Nullable UiIcon icon, int quantity) {
            this.name = name;
            this.icon = icon == null ? UNKNOWN : icon;
            if (quantity > 0) quantities.add(quantity);
        }
    }

    /** 普通分隔符显示文字，转换箭头承载触发信息；对象与文本摘要保持相同顺序。 */
    private record Part(@Nullable ObjectPart object, Component separator, boolean transition) {
        private Part(@Nullable ObjectPart object, Component separator) { this(object, separator, false); }
    }

    /** 一条产出效果及其所属候选的起点填充策略；顶层 effects 视为隐式候选。 */
    private record ResultEffect(RuleValue effect, boolean fillOrigin) { }

    public RuleRecipeView(@Nullable RuleValue rule) {
        RuleValue body = object(rule);
        triggerView = new RuleTriggerView(rule);
        List<ResultEffect> effects = effects(body);
        List<ObjectPart> sources = new ArrayList<>();
        for (RuleValue entry : array(object(body.child(RuleFields.SOURCE)).child(RuleFields.SOURCE_ITEMS)).items()) {
            if (entry.isText()) {
                String reference = entry.asText();
                sources.add(resource(reference, RuleCatalogType.ITEM, sourceCost(body, reference)));
            }
        }
        if (sources.isEmpty()) sources.add(placeholder("source_missing"));
        addParts(sources, "sources");

        Map<String, ObjectPart> catalysts = new LinkedHashMap<>();
        Map<String, ObjectPart> fluids = new LinkedHashMap<>();
        RuleValue cost = object(body.child("catalyst_cost"));
        addCatalysts(catalysts, cost, true);
        // 催化剂与流体只来自规则级条件树：效果不再声明自己的条件
        collectConditions(body.child("conditions"), false, catalysts, fluids);
        if (body.flag(RuleFields.CONSUME_FLUID, false)) {
            if (fluids.isEmpty()) fluid(fluids, "");
            fluids.values().forEach(part -> part.fluidConsumed = true);
        }
        for (ObjectPart catalyst : catalysts.values()) { separator("plus"); parts.add(new Part(catalyst, Component.empty())); }
        for (ObjectPart fluid : fluids.values()) { separator("plus"); parts.add(new Part(fluid, Component.empty())); }
        parts.add(new Part(null, Component.translatable(UI + "arrow"), true));
        List<ObjectPart> products = new ArrayList<>();
        for (ResultEffect resultEffect : effects) {
            // 产出判断只读效果类型的产出类别元数据：NONE 为消耗类效果，不进入产出区
            ProductKind kind = productKind(resultEffect.effect());
            if (kind != ProductKind.NONE) {
                products.add(product(resultEffect.effect(), kind, body, resultEffect.fillOrigin()));
            }
        }
        if (products.isEmpty()) products.add(placeholder(effects.isEmpty() ? "result_missing" : "no_output"));
        addParts(products, "results");

        for (Part part : parts) if (part.object() != null) part.object().quantityText = numbers(part.object().quantities);
        MutableComponent plain = Component.empty();
        for (Part part : parts) plain.append(part.object() == null ? part.separator() : part.object().name);
        summary = plain;
        MutableComponent details = Component.empty();
        appendDetails(details, "input", sources);
        appendDetails(details, "catalyst", new ArrayList<>(catalysts.values()));
        appendDetails(details, "fluid", new ArrayList<>(fluids.values()));
        appendDetails(details, "output", products);
        if (sources.size() > 1)
            details.append("\n").append(Component.translatable(UI + "matching_help"));
        if (!catalysts.isEmpty()) details.append("\n").append(Component.translatable(UI + "catalyst_help"));
        if (!fluids.isEmpty()) details.append("\n").append(Component.translatable(UI + "fluid_help"));
        if (parts.stream().anyMatch(part -> part.object() != null && part.object().quantities.size() > 1))
            details.append("\n").append(Component.translatable(UI + "quantity_help"));
        if (array(body.child("outcomes")).size() > 1)
            details.append("\n").append(Component.translatable(UI + "outcomes_help"));
        tooltip = details;
    }

    public Component summary() { return summary; }
    public Component tooltip() { return tooltip; }

    // 管理树和变更清单共用两行条目：标题与状态在上，输入及产出关系在下。
    static void renderRow(GuiGraphics graphics, Font font, UiRect row, String title, Component state,
                          int color, int stateColor, @Nullable RuleRecipeView recipe, boolean hovered) {
        renderRow(graphics, font, row, title, state, color, stateColor, recipe, hovered, true);
    }

    static void renderRow(GuiGraphics graphics, Font font, UiRect row, String title, Component state,
                          int color, int stateColor, @Nullable RuleRecipeView recipe, boolean hovered, boolean active) {
        int stateWidth = font.width(state);
        graphics.drawString(font, TextScroll.trimToWidth(font, title,
                Math.max(0, row.width() - stateWidth - 6)), row.x() + 2, row.y() + 2, color, false);
        if (!state.getString().isBlank()) graphics.drawString(font, state, row.right() - stateWidth - 2,
                row.y() + 2, stateColor, false);
        if (recipe != null) recipe.render(graphics, font, recipeBounds(font, row), color, hovered, active);
    }

    static UiRect recipeBounds(Font font, UiRect row) {
        return new UiRect(row.x() + 2, row.y() + font.lineHeight + 3,
                Math.max(0, row.width() - 4), CONTENT_HEIGHT);
    }

    // 每种对象各占一行；流体不计件数，消耗符号与催化剂共用。
    private static void appendDetails(MutableComponent details, String role, List<ObjectPart> objects) {
        for (ObjectPart value : objects) {
            if (!details.getString().isEmpty()) details.append("\n");
            Component name = value.tag == null ? value.name : value.tag.tooltip();
            Component item = value.quantities.isEmpty() ? name.copy()
                    : Component.translatable(UI + "quantity", name, value.quantityText);
            if (!value.consumedQuantities.isEmpty()) item = item.copy().append(Component.translatable(UI + "consumption",
                    numbers(value.consumedQuantities)));
            if (value.fluidConsumed) item = item.copy().append(Component.translatable(UI + "fluid_consumption"));
            details.append(Component.translatable(UI + role, item));
        }
    }

    /** 首选图标+名称，窄行改图标关系；超长时悬停横向滚动，离开恢复原序起点。 */
    public void render(GuiGraphics graphics, Font font, UiRect row, int color, boolean hovered) {
        render(graphics, font, row, color, hovered, true);
    }

    private void render(GuiGraphics graphics, Font font, UiRect row, int color, boolean hovered, boolean active) {
        int[] withNames = widths(font, true);
        boolean names = sumWidths(withNames) <= row.width();
        int[] partWidths = names ? withNames : widths(font, false);
        int total = sumWidths(partWidths);
        long now = Util.getMillis();
        if (!hovered || lastDraw == 0 || now - lastDraw > 150) hoverStarted = now;
        lastDraw = now;
        int overflow = Math.max(0, total - row.width());
        int shift = horizontalShift(now, overflow, hovered);
        clip.setViewport(row.x(), row.y(), row.width(), row.height());
        clip.setContentHeight(row.height());
        clip.push(graphics);
        row = new UiRect(0, 0, row.width(), row.height());
        int end = row.right() - (overflow > 0 && !hovered ? font.width("...") + 2 : 0);
        try {
            int x = row.x() - shift;
            int contentY = row.y() + (row.height() - OBJECT_CONTENT_HEIGHT) / 2;
            for (int index = 0; index < parts.size(); index++) {
                Part part = parts.get(index);
                int size = partWidths[index];
                if (!hovered && x + size > end) break;
                if (x >= end) break;
                if (x + size <= row.x()) { x += size; continue; }
                if (part.transition()) triggerView.render(graphics, font,
                        new UiRect(x, row.y(), size, row.height()), part.separator(), color, active);
                else if (part.object() == null) graphics.drawString(font, part.separator(), x, contentY + 5, color, false);
                else {
                    ObjectPart value = part.object();
                    if (value.tag != null) {
                        graphics.fill(x, row.y(), x + size - 2, row.bottom(), 0x224F7299);
                        graphics.fill(x, row.bottom() - 1, x + size - 2, row.bottom(), 0xFF6688AA);
                    }
                    RulePreviewIcons.render(graphics, value.icon, x, contentY + 1, active);
                    if (!value.consumedQuantities.isEmpty() || value.fluidConsumed || value.originFilled)
                        consumedStar(graphics, x + ICON_SIZE - 7, contentY, active ? CONSUMED_COLOR : color);
                    if (!value.quantities.isEmpty()) badge(graphics, font, value.quantityText,
                            x + ICON_SIZE, contentY + 12, active ? 0xFFFFFFFF : color);
                    if (names) graphics.drawString(font, value.name, x + ICON_SIZE + 3, contentY + 5, color, false);
                    if (value.tag != null) for (int step = 0; step < 3; step++)
                        graphics.fill(x + size - 7 + step, contentY + 7 + step,
                                x + size - 6 + step, contentY + 12 - step, color);
                }
                x += size;
            }
            if (overflow > 0 && !hovered) graphics.drawString(font, "...", end + 2, contentY + 5, color, false);
        } finally { clip.pop(graphics); }
    }

    // 起点和终点各停留一秒，避免长配方一进入悬停便跳动。
    private int horizontalShift(long now, int overflow, boolean hovered) {
        if (!hovered || overflow <= 0) return 0;
        long travel = Math.max(1000, overflow * 35L);
        long cycle = Math.floorMod(now - hoverStarted, travel * 2 + 2000);
        if (cycle > 1000 && cycle <= travel + 1000) return (int) ((cycle - 1000) * overflow / travel);
        if (cycle > travel + 1000 && cycle <= travel + 2000) return overflow;
        if (cycle > travel + 2000) return (int) ((travel * 2 + 2000 - cycle) * overflow / travel);
        return 0;
    }

    // parts 在构造后不再变化，字体实例不变即可整份复用；字体变化时整体重测
    private int[] widths(Font font, boolean names) {
        if (font != measuredFont) {
            int[] compact = new int[parts.size()];
            int[] withNames = new int[parts.size()];
            for (int index = 0; index < parts.size(); index++) {
                Part part = parts.get(index);
                compact[index] = partWidth(font, part, false);
                withNames[index] = partWidth(font, part, true);
            }
            compactWidths = compact;
            nameWidths = withNames;
            measuredFont = font;
        }
        return names ? nameWidths : compactWidths;
    }

    private static int sumWidths(int[] widths) {
        int sum = 0;
        for (int width : widths) {
            sum += width;
        }
        return sum;
    }

    private int partWidth(Font font, Part part, boolean names) {
        if (part.transition()) return triggerView.width(font, part.separator());
        return part.object() == null ? font.width(part.separator()) : ICON_SIZE + 3
                + (names ? font.width(part.object().name) : 0) + (part.object().tag == null ? 0 : 9);
    }

    /** 命中与绘制共用名字省略和横向滚动口径；只展开实际可见的标签容器。 */
    public @Nullable TagPreviewIcons.Tag tagAt(Font font, UiRect row, double mouseX, double mouseY) {
        Part part = partAt(font, row, mouseX, mouseY);
        return part == null || part.object() == null ? null : part.object().tag;
    }

    // 标签命中复用配方绘制的宽度和滚动位移，避免新增箭头宽度后命中错位。
    private @Nullable Part partAt(Font font, UiRect row, double mouseX, double mouseY) {
        if (!row.contains(mouseX, mouseY)) return null;
        int[] withNames = widths(font, true);
        boolean names = sumWidths(withNames) <= row.width();
        int[] partWidths = names ? withNames : widths(font, false);
        int overflow = Math.max(0, sumWidths(partWidths) - row.width());
        int x = row.x() - horizontalShift(Util.getMillis(), overflow, true);
        for (int index = 0; index < parts.size(); index++) {
            int size = partWidths[index];
            if (mouseX >= x && mouseX < x + size) return parts.get(index);
            x += size;
        }
        return null;
    }

    // 角标位于图标内部，矩阵独立恢复，后续物品和文字不受缩放影响。
    private static void badge(GuiGraphics graphics, Font font, String text, int right, int y, int color) {
        float scale = Math.min(0.65F, 18.0F / Math.max(1, font.width(text)));
        int width = (int) Math.ceil(font.width(text) * scale);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(right - width, y, UiRenderLayers.FOREGROUND);
            graphics.pose().translate(0, 1, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, color, false);
        } finally { graphics.pose().popPose(); }
    }

    // 消耗角标使用主题层五角星贴图；颜色随启用态由调用方传入。
    private static void consumedStar(GuiGraphics graphics, int x, int y, int color) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, UiRenderLayers.FOREGROUND);
            UiMarkIcons.icon(UiMarkIcons.Mark.CONSUMED_STAR, color).render(graphics, 0, 0);
        } finally { graphics.pose().popPose(); }
    }

    private void addParts(List<ObjectPart> objects, String separator) {
        for (int i = 0; i < objects.size(); i++) {
            if (i > 0) separator(separator);
            parts.add(new Part(objects.get(i), Component.empty()));
        }
    }
    private void separator(String key) { parts.add(new Part(null, Component.translatable(UI + key))); }

    private static ObjectPart placeholder(String key) { return new ObjectPart(Component.translatable(UI + key), UNKNOWN, 0); }
    // 成本在前，条件按 terms 原序，随后依方案/动作原序；重名只合并角标，保留首次位置。
    private static void addCatalysts(Map<String, ObjectPart> target, RuleValue owner, boolean consumed) {
        for (RuleValue entry : array(owner.child("items")).items()) {
            if (!entry.isText()) continue;
            String id = entry.asText();
            int count = integer(object(owner.child(RuleFields.ITEM_COUNTS)), id, integer(owner, RuleFields.CATALYST_COUNT, 1));
            ObjectPart part = target.computeIfAbsent(id, ignored -> resource(id, RuleCatalogType.ITEM, 0));
            if (count > 0) {
                part.quantities.add(count);
                if (consumed) {
                    int consumeCount = integer(object(owner.child(RuleFields.CONSUME_COUNTS)), id, 0);
                    if (consumeCount > 0) part.consumedQuantities.add(consumeCount);
                }
            }
        }
    }

    private static void fluid(Map<String, ObjectPart> fluids, String reference) {
        fluids.computeIfAbsent(reference, ignored -> reference.isEmpty()
                ? new ObjectPart(Component.translatable(UI + "any_fluid"), new UiIcon.Item(new ItemStack(Items.BUCKET)), 0)
                : resource(reference, RuleCatalogType.FLUID, 0));
    }

    private static void addFluids(Map<String, ObjectPart> fluids, RuleValue condition) {
        List<RuleValue> references = array(condition.child(RuleFields.FLUIDS)).items();
        if (references.isEmpty()) fluid(fluids, text(condition, "fluid"));
        else for (RuleValue reference : references) if (reference.isText()) fluid(fluids, reference.asText());
    }

    // 取反叶不代表需要浸润或催化剂；双重取反恢复正向展示。
    private static void collectConditions(@Nullable RuleValue raw, boolean inverted, Map<String, ObjectPart> catalysts,
                                          Map<String, ObjectPart> fluids) {
        RuleValue node = object(raw);
        String op = text(node, "op");
        if ("inverted".equals(op)) collectConditions(node.child("term"), !inverted, catalysts, fluids);
        else if ("leaf".equals(op)) {
            RuleValue condition = object(node.child("condition"));
            if (!inverted && (OWN + "catalyst_present").equals(text(condition, "type"))) addCatalysts(catalysts, condition, false);
            if (!inverted && (OWN + "fluid_present").equals(text(condition, "type"))) addFluids(fluids, condition);
        } else for (RuleValue term : array(node.child("terms")).items()) collectConditions(term, inverted, catalysts, fluids);
    }

    // 产出展示形态由产出类别决定：实体子类读专有参数，方块产出支持「放置源方块」，其余按效果标题展示
    private static ObjectPart product(RuleValue effect, ProductKind kind, RuleValue rule, boolean fillOrigin) {
        return switch (kind) {
            case ENTITY_ITEM -> resource(text(effect, "item"), RuleCatalogType.ITEM, integer(effect, "count", 1));
            case ENTITY_ENTITY -> {
                ObjectPart part = resource(text(effect, "entity"), RuleCatalogType.ENTITY, integer(effect, "count", 1));
                // 标签引用保留标签图标；具体实体改用动作图标
                if (!text(effect, "entity").startsWith("#")) part = new ObjectPart(part.name,
                        RulePreviewIcons.action(effect), integer(effect, "count", 1));
                yield part;
            }
            case ENTITY_EXPERIENCE -> new ObjectPart(Component.translatable(UI + "experience"),
                    RulePreviewIcons.action(effect), integer(effect, "amount", 1));
            case BLOCK -> {
                ObjectPart part;
                if (RuleValueReads.boolOf(effect, "use_source_block", false)) {
                    part = new ObjectPart(Component.translatable(UI + "source_block"), firstSourceIcon(rule), integer(effect, "count", 1));
                    String source = firstSource(rule);
                    if (source.startsWith("#")) part.tag = TagPreviewIcons.sourceBlocks(source);
                } else {
                    part = resource(text(effect, "block"), RuleCatalogType.BLOCK, integer(effect, "count", 1));
                }
                // 起点填充表示方块会占据触发位置本身，与催化剂/流体的消耗角标同义
                part.originFilled = fillOrigin;
                yield part;
            }
            default -> new ObjectPart(RuleNaming.effectTitle(effect, RuleDisplayLabels::label),
                    RulePreviewIcons.action(effect), integer(effect, "count", 0));
        };
    }

    // 单条效果的产出类别：解码后读取类型元数据；参数不完整无法解码时按未知产出处理（仍作为产出展示）
    private static ProductKind productKind(RuleValue effect) {
        return RuleCategoryClassifier.productKindOf(effect.decode(effectCodec()), ClientTypeRegistries.effects());
    }

    // 效果解码器惰性构建：注册表内容与数据包无关，进程内只建一次
    private static Codec<Effect> effectCodec() {
        Codec<Effect> local = effectCodec;
        if (local == null) {
            local = RuleCodecs.effectCodec(ClientTypeRegistries.effects());
            effectCodec = local;
        }
        return local;
    }

    static String firstSource(RuleValue rule) {
        RuleValue entries = array(object(rule.child("source")).child("items"));
        if (entries.isEmpty()) return "";
        RuleValue first = entries.item(0);
        return first == null || !first.isScalar() ? "" : first.asText();
    }

    static @Nullable UiIcon firstSourceIcon(RuleValue rule) {
        String raw = firstSource(rule);
        if (raw.startsWith("#")) return TagPreviewIcons.sourceBlocks(raw).icon();
        ResourceLocation id = ResourceLocation.tryParse(raw);
        var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item instanceof BlockItem blockItem
                ? BlockPreviewIcons.icon(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString()) : null;
    }

    // 标签不能伪装成同名普通物品；实体/方块名称只查对应注册表。
    private static ObjectPart resource(String raw, RuleCatalogType type, int quantity) {
        if (raw.startsWith("#")) {
            TagPreviewIcons.Tag tag = TagPreviewIcons.resolve(type, raw);
            ObjectPart part = new ObjectPart(tag.label(), tag.icon(), quantity);
            part.tag = tag;
            return part;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        Component name = id == null ? Component.translatable(UI + "object_missing") : switch (type) {
            case ITEM -> BuiltInRegistries.ITEM.getOptional(id).map(Item::getDescription).orElse(Component.literal(raw));
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(id).map(Block::getName).orElse(Component.literal(raw));
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription).orElse(Component.literal(raw));
            case FLUID -> {
                Component label = RuleDisplayLabels.catalogLabel(type, id);
                yield label == null ? Component.literal(raw) : label;
            }
            default -> Component.literal(raw);
        };
        return new ObjectPart(name, CatalogPreviewIcons.iconFor(type, raw), quantity);
    }

    // 顶层 effects 对应隐式候选，fill_origin 取后端默认值；outcomes 各自读取候选级开关。
    private static List<ResultEffect> effects(RuleValue rule) {
        List<ResultEffect> result = new ArrayList<>();
        appendEffects(result, rule.child("effects"), OutcomeCandidate.DEFAULT_FILL_ORIGIN);
        for (RuleValue outcome : array(rule.child("outcomes")).items()) {
            RuleValue candidate = object(outcome);
            appendEffects(result, candidate.child("effects"),
                    RuleValueReads.boolOf(candidate, RuleFields.FILL_ORIGIN, OutcomeCandidate.DEFAULT_FILL_ORIGIN));
        }
        return result;
    }
    private static void appendEffects(List<ResultEffect> target, @Nullable RuleValue raw, boolean fillOrigin) {
        for (RuleValue entry : array(raw).items()) {
            if (entry.isMapping()) target.add(new ResultEffect(entry, fillOrigin));
        }
    }
    private static int sourceCost(RuleValue rule, String reference) {
        if (rule.has(RuleFields.SOURCE_COST)) return integer(object(rule.child(RuleFields.SOURCE_COST)), reference, 1);
        return 1;
    }

    private static String numbers(LinkedHashSet<Integer> values) { return String.join("/", values.stream().map(String::valueOf).toList()); }
    private static RuleValue object(@Nullable RuleValue value) { return value != null && value.isMapping() ? value : RuleValue.mapping().build(); }
    private static RuleValue array(@Nullable RuleValue value) { return value != null && value.isList() ? value : RuleValue.list().build(); }
    private static String text(RuleValue value, String key) {
        RuleValue raw = value.child(key);
        return raw != null && raw.isText() ? raw.asText() : "";
    }
    private static int integer(RuleValue value, String key, int fallback) {
        RuleValue raw = value.child(key);
        if (raw == null) return fallback;
        try {
            if (raw.isNumber()) return Math.max(0, raw.asInteger(0));
            if (raw.isText()) return Math.max(0, Integer.parseInt(raw.asText().trim()));
        } catch (RuntimeException exception) {
            return fallback;
        }
        return fallback;
    }
}
