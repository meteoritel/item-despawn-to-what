package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/*** 配方行的只读展示快照：保留数组顺序，复用图标，角标与完整说明使用同一份数据。 */
public final class RuleRecipeView {
    private static final String UI = "gui.itemdespawntowhat.edit.recipe.";
    private static final String OWN = "itemdespawntowhat:";
    private static final int ICON_SIZE = 18;
    static final int CONTENT_HEIGHT = 20;
    private static final int CONSUMED_COLOR = 0xFFFFA500;
    private static final UiIcon UNKNOWN = new UiIcon.Item(new ItemStack(Items.BARRIER));
    private final List<Part> parts = new ArrayList<>();
    private final Component summary;
    private final Component tooltip;
    private final UiScrollView clip = new UiScrollView();
    private long hoverStarted;
    private long lastDraw;

    /*** 一项对象及其数量要求；同名催化剂的不同要求保留原顺序，不错误地累加或取最大值。 */
    private static final class ObjectPart {
        private final Component name;
        private final UiIcon icon;
        private final LinkedHashSet<Integer> quantities = new LinkedHashSet<>();
        private final LinkedHashSet<Integer> consumedQuantities = new LinkedHashSet<>();
        private boolean perSourceItem;
        private String quantityText = "";
        private @Nullable TagPreviewIcons.Tag tag;

        private ObjectPart(Component name, @Nullable UiIcon icon, int quantity) {
            this.name = name;
            this.icon = icon == null ? UNKNOWN : icon;
            if (quantity > 0) quantities.add(quantity);
        }
    }

    /*** 分隔符没有图标，对象没有额外前缀，按相同顺序生成文字和图标关系。 */
    private record Part(@Nullable ObjectPart object, Component separator) { }

    public RuleRecipeView(@Nullable JsonObject rule) {
        JsonObject body = object(rule);
        List<JsonObject> effects = effects(body);
        List<ObjectPart> sources = new ArrayList<>();
        for (JsonElement entry : array(object(body.get(RuleFields.SOURCE)).get(RuleFields.SOURCE_ITEMS))) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                String reference = entry.getAsString();
                sources.add(resource(reference, RuleCatalogType.ITEM, sourceCost(body, effects, reference)));
            }
        }
        if (sources.isEmpty()) sources.add(placeholder("source_missing"));
        addParts(sources, "sources");

        Map<String, ObjectPart> catalysts = new LinkedHashMap<>();
        JsonObject cost = object(body.get("catalyst_cost"));
        addCatalysts(catalysts, cost, true);
        collectConditions(body.get("conditions"), false, catalysts);
        for (JsonObject effect : effects) {
            collectConditions(effect.get("conditions"), false, catalysts);
            if ((OWN + "consume_catalyst").equals(text(effect, "type"))) addCatalysts(catalysts, effect, true);
        }
        for (ObjectPart catalyst : catalysts.values()) { separator("plus"); parts.add(new Part(catalyst, Component.empty())); }
        separator("arrow");
        List<ObjectPart> products = new ArrayList<>();
        for (JsonObject effect : effects) {
            String type = text(effect, "type");
            if (!List.of(OWN + "consume_source", OWN + "consume_catalyst", OWN + "consume_fluid").contains(type)) {
                products.add(product(effect, body));
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
        appendDetails(details, "output", products);
        if (sources.size() > 1)
            details.append("\n").append(Component.translatable(UI + "matching_help"));
        if (!catalysts.isEmpty()) details.append("\n").append(Component.translatable(UI + "catalyst_help"));
        if (parts.stream().anyMatch(part -> part.object() != null && part.object().quantities.size() > 1))
            details.append("\n").append(Component.translatable(UI + "quantity_help"));
        if (array(body.get("outcomes")).size() > 1)
            details.append("\n").append(Component.translatable(UI + "outcomes_help"));
        if (parts.stream().anyMatch(part -> part.object() != null && part.object().perSourceItem))
            details.append("\n").append(Component.translatable(UI + "experience_help"));
        tooltip = details;
    }

    public Component summary() { return summary; }
    public Component tooltip() { return tooltip; }

    // 每种对象各占一行，消耗只写在对应催化剂后，不堆叠重复的角标和滚动说明。
    private static void appendDetails(MutableComponent details, String role, List<ObjectPart> objects) {
        for (ObjectPart value : objects) {
            if (!details.getString().isEmpty()) details.append("\n");
            Component name = value.tag == null ? value.name : value.tag.tooltip();
            Component item = value.quantities.isEmpty() ? name.copy()
                    : Component.translatable(UI + "quantity", name, value.quantityText);
            if (!value.consumedQuantities.isEmpty()) item = item.copy().append(Component.translatable(UI + "consumption",
                    numbers(value.consumedQuantities)));
            details.append(Component.translatable(UI + role, item));
        }
    }

    // 首选图标+名称，窄行改图标关系；超长时悬停横向滚动，离开恢复原序起点。
    public void render(GuiGraphics graphics, Font font, UiRect row, int color, boolean hovered) {
        boolean names = width(font, true) <= row.width();
        int total = width(font, names);
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
            for (Part part : parts) {
                int size = partWidth(font, part, names);
                if (!hovered && x + size > end) break;
                if (x >= end) break;
                if (x + size <= row.x()) { x += size; continue; }
                if (part.object() == null) graphics.drawString(font, part.separator(), x, row.y() + 5, color, false);
                else {
                    ObjectPart value = part.object();
                    if (value.tag != null) {
                        graphics.fill(x, row.y(), x + size - 2, row.bottom(), 0x224F7299);
                        graphics.fill(x, row.bottom() - 1, x + size - 2, row.bottom(), 0xFF6688AA);
                    }
                    value.icon.render(graphics, x, row.y() + 1);
                    if (!value.consumedQuantities.isEmpty()) consumedStar(graphics, x + ICON_SIZE - 7, row.y());
                    if (!value.quantities.isEmpty()) badge(graphics, font, value.quantityText,
                            x + ICON_SIZE, row.y() + 12);
                    if (names) graphics.drawString(font, value.name, x + ICON_SIZE + 3, row.y() + 5, color, false);
                    if (value.tag != null) for (int step = 0; step < 3; step++)
                        graphics.fill(x + size - 7 + step, row.y() + 7 + step,
                                x + size - 6 + step, row.y() + 12 - step, color);
                }
                x += size;
            }
            if (overflow > 0 && !hovered) graphics.drawString(font, "...", end + 2, row.y() + 5, color, false);
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

    private int width(Font font, boolean names) { return parts.stream().mapToInt(part -> partWidth(font, part, names)).sum(); }
    private static int partWidth(Font font, Part part, boolean names) {
        return part.object() == null ? font.width(part.separator()) : ICON_SIZE + 3
                + (names ? font.width(part.object().name) : 0) + (part.object().tag == null ? 0 : 9);
    }

    // 命中与绘制共用名字省略和横向滚动口径；只展开实际可见的标签容器。
    public @Nullable TagPreviewIcons.Tag tagAt(Font font, UiRect row, double mouseX, double mouseY) {
        if (!row.contains(mouseX, mouseY)) return null;
        boolean names = width(font, true) <= row.width();
        int overflow = Math.max(0, width(font, names) - row.width());
        int x = row.x() - horizontalShift(Util.getMillis(), overflow, true);
        for (Part part : parts) {
            int size = partWidth(font, part, names);
            if (mouseX >= x && mouseX < x + size && part.object() != null) return part.object().tag;
            x += size;
        }
        return null;
    }

    // 角标位于图标内部，矩阵独立恢复，后续物品和文字不受缩放影响。
    private static void badge(GuiGraphics graphics, Font font, String text, int right, int y) {
        float scale = Math.min(0.65F, 18.0F / Math.max(1, font.width(text)));
        int width = (int) Math.ceil(font.width(text) * scale);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(right - width, y, UiRenderLayers.FOREGROUND);
            graphics.pose().translate(0, 1, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
        } finally { graphics.pose().popPose(); }
    }

    // 透明底橙色五角星：用像素绘制，避免语言或资源包缺少星号字形。
    private static void consumedStar(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, UiRenderLayers.FOREGROUND);
            graphics.fill(3, 0, 4, 2, CONSUMED_COLOR);
            graphics.fill(0, 2, 7, 3, CONSUMED_COLOR);
            graphics.fill(1, 3, 6, 4, CONSUMED_COLOR);
            graphics.fill(2, 4, 5, 5, CONSUMED_COLOR);
            graphics.fill(1, 5, 3, 6, CONSUMED_COLOR);
            graphics.fill(4, 5, 6, 6, CONSUMED_COLOR);
            graphics.fill(0, 6, 2, 7, CONSUMED_COLOR);
            graphics.fill(5, 6, 7, 7, CONSUMED_COLOR);
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
    private static void addCatalysts(Map<String, ObjectPart> target, JsonObject owner, boolean consumed) {
        for (JsonElement entry : array(owner.get("items"))) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) continue;
            String id = entry.getAsString();
            int count = integer(object(owner.get(RuleFields.ITEM_COUNTS)), id, integer(owner, RuleFields.CATALYST_COUNT, 1));
            ObjectPart part = target.computeIfAbsent(id, ignored -> resource(id, RuleCatalogType.ITEM, 0));
            if (count > 0) {
                part.quantities.add(count);
                if (consumed) part.consumedQuantities.add(count);
            }
        }
    }

    private static void collectConditions(JsonElement raw, boolean inverted, Map<String, ObjectPart> catalysts) {
        JsonObject node = object(raw);
        String op = text(node, "op");
        if ("inverted".equals(op)) collectConditions(node.get("term"), !inverted, catalysts);
        else if ("leaf".equals(op)) {
            JsonObject condition = object(node.get("condition"));
            if (!inverted && (OWN + "catalyst_present").equals(text(condition, "type"))) addCatalysts(catalysts, condition, false);
        } else for (JsonElement term : array(node.get("terms"))) collectConditions(term, inverted, catalysts);
    }

    private static ObjectPart product(JsonObject effect, JsonObject rule) {
        String type = text(effect, "type");
        if ((OWN + "spawn_entity").equals(type)) {
            return switch (text(effect, "variant")) {
                case "item" -> resource(text(effect, "item"), RuleCatalogType.ITEM, integer(effect, "count", 1));
                case "entity" -> {
                    ObjectPart part = resource(text(effect, "entity"), RuleCatalogType.ENTITY, integer(effect, "count", 1));
                    if (!text(effect, "entity").startsWith("#")) part = new ObjectPart(part.name,
                            RulePreviewIcons.action(effect), integer(effect, "count", 1));
                    yield part;
                }
                case "experience" -> {
                    ObjectPart xp = new ObjectPart(Component.translatable(UI + "experience"), RulePreviewIcons.action(effect), integer(effect, "amount", 1));
                    xp.perSourceItem = bool(effect, "per_source_item");
                    yield xp;
                }
                default -> placeholder("result_missing");
            };
        }
        if ((OWN + "place_block").equals(type)) {
            if (bool(effect, "use_source_block")) {
                ObjectPart part = new ObjectPart(Component.translatable(UI + "source_block"), firstSourceIcon(rule), integer(effect, "count", 1));
                String source = firstSource(rule);
                if (source.startsWith("#")) part.tag = TagPreviewIcons.sourceBlocks(source);
                return part;
            }
            return resource(text(effect, "block"), RuleCatalogType.BLOCK, integer(effect, "count", 1));
        }
        return new ObjectPart(RuleNaming.effectTitle(effect, RuleDisplayLabels::label), RulePreviewIcons.action(effect), integer(effect, "count", 0));
    }

    static String firstSource(JsonObject rule) {
        JsonArray entries = array(object(rule.get("source")).get("items"));
        return entries.isEmpty() || !entries.get(0).isJsonPrimitive() ? "" : entries.get(0).getAsString();
    }

    static @Nullable UiIcon firstSourceIcon(JsonObject rule) {
        String raw = firstSource(rule);
        if (raw.startsWith("#")) return TagPreviewIcons.sourceBlocks(raw).icon();
        ResourceLocation id = ResourceLocation.tryParse(raw);
        var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item instanceof net.minecraft.world.item.BlockItem blockItem
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
            case ITEM -> BuiltInRegistries.ITEM.getOptional(id).map(net.minecraft.world.item.Item::getDescription).orElse(Component.literal(raw));
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(id).map(net.minecraft.world.level.block.Block::getName).orElse(Component.literal(raw));
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(net.minecraft.world.entity.EntityType::getDescription).orElse(Component.literal(raw));
            default -> Component.literal(raw);
        };
        return new ObjectPart(name, RuleEditorP4Panels.iconFor(type, raw), quantity);
    }

    private static List<JsonObject> effects(JsonObject rule) {
        List<JsonObject> result = new ArrayList<>();
        appendEffects(result, rule.get("effects"));
        for (JsonElement outcome : array(rule.get("outcomes"))) appendEffects(result, object(outcome).get("effects"));
        return result;
    }
    private static void appendEffects(List<JsonObject> target, JsonElement raw) {
        for (JsonElement entry : array(raw)) if (entry instanceof JsonObject effect) target.add(effect);
    }
    private static int sourceCost(JsonObject rule, List<JsonObject> effects, String reference) {
        if (rule.has(RuleFields.SOURCE_COST)) return integer(object(rule.get(RuleFields.SOURCE_COST)), reference, 1);
        int cost = 0;
        boolean consumption = false;
        for (JsonObject effect : effects) {
            String type = text(effect, "type");
            consumption |= List.of(OWN + "consume_source", OWN + "consume_catalyst", OWN + "consume_fluid").contains(type);
            if ((OWN + "consume_source").equals(type)) cost = (int) Math.min(Integer.MAX_VALUE, (long) cost
                    + integer(object(effect.get(RuleFields.ITEM_COUNTS)), reference, integer(effect, RuleFields.CATALYST_COUNT, 1)));
        }
        return consumption ? cost : 1;
    }
    private static String numbers(LinkedHashSet<Integer> values) { return String.join("/", values.stream().map(String::valueOf).toList()); }
    private static JsonObject object(@Nullable JsonElement value) { return value instanceof JsonObject object ? object : new JsonObject(); }
    private static JsonArray array(@Nullable JsonElement value) { return value instanceof JsonArray array ? array : new JsonArray(); }
    private static String text(JsonObject value, String key) {
        JsonElement raw = value.get(key);
        return raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString() ? raw.getAsString() : "";
    }
    private static int integer(JsonObject value, String key, int fallback) {
        try { return value.has(key) ? Math.max(0, value.get(key).getAsInt()) : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }
    private static boolean bool(JsonObject value, String key) {
        JsonElement raw = value.get(key);
        return raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean() && raw.getAsBoolean();
    }
}
