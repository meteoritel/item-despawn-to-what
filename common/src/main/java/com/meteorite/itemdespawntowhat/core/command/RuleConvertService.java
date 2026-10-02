package com.meteorite.itemdespawntowhat.core.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.load.RulePaths;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.service.RuleOverlayWriter;
import com.meteorite.itemdespawntowhat.core.service.RuleSubmissionValidator;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.load.OverlayRuleReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧链路配置 → 新覆盖层规则的一次性转换（/idtw config convert，规划书第五节映射表）。
 * 实现约束：本类**不引用旧链路 config/** 的任何类**，只按字段名直接读取旧 JSON 文本，
 * 从而在新旧链路切换完成前保持两条链路零耦合。
 * 语义原则（Q4/Q19）：能无歧义映射就转换，否则显式拒载并报告，绝不静默丢弃；
 * 字段级损失（新类型没有对应参数）作为 note 一并回报，不阻断转换。
 * 转换前把命中的旧文件整体备份到 {@code <覆盖层>/_old_chain_backup/}（保留相对路径）。
 */
public final class RuleConvertService {

    // 备份目录名：位于覆盖层根目录下、rules/ 之外，因此不会被规则读取器扫描
    public static final String BACKUP_DIRECTORY = "_old_chain_backup";

    // 旧链路 9 个内置转化类型 → 新效果类型路径
    private static final Map<String, String> TYPE_TO_EFFECT = new LinkedHashMap<>();

    static {
        TYPE_TO_EFFECT.put("item_to_item", "spawn_item");
        TYPE_TO_EFFECT.put("item_to_mob", "spawn_entity");
        TYPE_TO_EFFECT.put("item_to_block", "place_block");
        TYPE_TO_EFFECT.put("item_to_xp_orb", "spawn_xp");
        TYPE_TO_EFFECT.put("item_to_lightning", "lightning");
        TYPE_TO_EFFECT.put("item_to_explosion", "explosion");
        TYPE_TO_EFFECT.put("item_to_arrow_rain", "arrow_rain");
        TYPE_TO_EFFECT.put("item_to_weather", "weather");
        TYPE_TO_EFFECT.put("item_to_loot", "loot_table");
    }

    // 旧链路 7 个条件类型 → 新条件类型路径（同名保留）
    private static final Map<String, String> CONDITIONS = Map.of(
            "dimension", "dimension",
            "outdoor", "outdoor",
            "surrounding_blocks", "surrounding_blocks",
            "catalyst_present", "catalyst_present",
            "fluid_present", "fluid_present",
            "biome", "biome",
            "weather", "weather"
    );

    // 转换结果报告：成功条数、备份文件数、写入文件数、无法映射条目、字段级提示
    public record Report(int converted, int backedUp, int writtenFiles,
                         List<String> unmapped, List<String> notes) {
        public Report {
            unmapped = List.copyOf(unmapped);
            notes = List.copyOf(notes);
        }
    }

    private RuleConvertService() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 执行一次转换：扫描旧文件 → 逐条映射 → 备份 → 写覆盖层
    // overlayRoot 即 config/itemdespawntowhat：旧文件与新的 rules/ 覆盖层同处这一层
    public static Report convert(Path overlayRoot, String namespace,
                                 int expectedVersion, IssueCollector issues, MinecraftServer server, BuiltinTypeRegistries types) {
        Path rulesDir = overlayRoot.resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        List<String> unmapped = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<RuleEdit> edits = new ArrayList<>();
        List<OldFile> oldFiles = scanOldFiles(overlayRoot, unmapped);

        int backedUp = 0;
        var existing = OverlayRuleReader.read(overlayRoot, namespace, new IssueCollector()).stream()
                .map(entry -> entry.id()).collect(java.util.stream.Collectors.toSet());
        for (OldFile oldFile : oldFiles) {
            if (!backup(overlayRoot, oldFile, issues)) {
                unmapped.add(oldFile.relative() + ": 备份失败，未进行转换");
                continue;
            }
            backedUp++;
            int index = 0;
            for (JsonObject entry : oldFile.entries()) {
                String type = oldFile.type();
                try {
                    JsonObject rule = mapRule(type, entry, index, namespace, notes);
                    ResourceLocation id = ResourceLocation.tryParse(namespace + ":legacy/" + stripExtension(oldFile.relative()) + "_" + index);
                    if (id == null) {
                        unmapped.add(oldFile.relative() + "#" + index + ": 推导出的规则 id 非法");
                    } else {
                        rule.addProperty("id", id.toString());
                        if (existing.contains(id)) {
                            notes.add("已有迁移规则，保留当前编辑内容: " + id);
                        } else {
                            RuleEdit edit = new RuleEdit(id, RuleEdit.Action.UPSERT, rule);
                            IssueCollector validation = new IssueCollector();
                            if (RuleSubmissionValidator.validate(new RuleEditChangeSet(expectedVersion, List.of(edit)),
                                    server, types, validation)) {
                                edits.add(edit);
                            } else {
                                for (var issue : validation.issues()) { unmapped.add(oldFile.relative() + "#" + index + ": " + issue.format()); }
                            }
                        }
                    }
                } catch (Unmappable e) {
                    unmapped.add(oldFile.relative() + "#" + index + ": " + e.getMessage());
                } catch (RuntimeException e) {
                    unmapped.add(oldFile.relative() + "#" + index + ": 读取字段失败(" + e.getClass().getSimpleName() + ")");
                }
                index++;
            }
        }

        int written = 0;
        if (!edits.isEmpty()) {
            RuleOverlayWriter.ApplyResult applied = new RuleOverlayWriter(overlayRoot, namespace)
                    .apply(new RuleEditChangeSet(expectedVersion, edits), issues);
            written = applied.writtenFiles();
            unmapped.addAll(applied.conflicts());
        }
        if (Files.isDirectory(rulesDir)) {
            notes.add("覆盖层规则目录: " + rulesDir);
        }
        return new Report(written > 0 ? edits.size() : 0, backedUp, written, unmapped, notes);
    }

    // ========== 旧文件扫描 ==========

    // 旧配置文件的解析结果：相对路径、类型名与条目标
    private record OldFile(String relative, String type, List<JsonObject> entries) {
    }

    // 扫描旧配置目录：只看 *.json，排除 rules/、备份目录与 server.json
    private static List<OldFile> scanOldFiles(Path overlayRoot, List<String> unmapped) {
        List<OldFile> result = new ArrayList<>();
        if (!Files.isDirectory(overlayRoot)) {
            return result;
        }
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(overlayRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(overlayRoot)
                            && (name.equals(RulePaths.OVERLAY_RULES_DIRECTORY) || name.equals(BACKUP_DIRECTORY))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(RulePaths.RULE_FILE_EXTENSION) && !name.equals("server.json")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            unmapped.add("扫描旧配置目录失败: " + e.getMessage());
            return result;
        }
        files.sort(java.util.Comparator.comparing(Path::toString));

        for (Path file : files) {
            String relative = overlayRoot.relativize(file).toString().replace('\\', '/');
            String type = stripExtension(file.getFileName().toString());
            if (!TYPE_TO_EFFECT.containsKey(type)) {
                unmapped.add(relative + ": 未知的旧转化类型（本命令只处理 9 个内置类型）");
                continue;
            }
            List<JsonObject> entries = readEntries(file, relative, unmapped);
            if (entries == null) {
                continue;
            }
            result.add(new OldFile(relative, type, entries));
        }
        return result;
    }

    // 读取旧文件的条目数组；形状非法时整文件拒载并报告
    private static @Nullable List<JsonObject> readEntries(Path file, String relative, List<String> unmapped) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            unmapped.add(relative + ": 读取或解析失败(" + e.getMessage() + ")");
            return null;
        }
        List<JsonObject> entries = new ArrayList<>();
        if (parsed.isJsonArray()) {
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    entries.add(element.getAsJsonObject());
                } else {
                    unmapped.add(relative + ": 数组元素不是规则对象，已跳过");
                }
            }
        } else if (parsed.isJsonObject()) {
            // 旧实现只写数组，这里容许单条对象，行为等价
            entries.add(parsed.getAsJsonObject());
        } else {
            unmapped.add(relative + ": 顶层既不是数组也不是对象，整文件拒载");
            return null;
        }
        return entries;
    }

    // 备份旧文件到 <覆盖层>/_old_chain_backup/<相对路径>；保留首次备份，失败则拒绝该文件转换
    private static boolean backup(Path overlayRoot, OldFile oldFile, IssueCollector issues) {
        Path source = overlayRoot.resolve(oldFile.relative());
        Path target = overlayRoot.resolve(BACKUP_DIRECTORY).resolve(oldFile.relative());
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(target)) { Files.copy(source, target); }
            return true;
        } catch (IOException e) {
            issues.warn("备份旧配置失败: " + oldFile.relative() + " (" + e.getMessage() + ")",
                    source.toString(), null);
            return false;
        }
    }

    // ========== 单条规则映射 ==========

    // 把一条旧配置映射为新规则 JSON；无法无歧义映射时抛 Unmappable
    private static JsonObject mapRule(String type, JsonObject old, int index, String namespace, List<String> notes) {
        for (String field : List.of("dimension", "need_outdoor", "surrounding_blocks", "catalyst_items", "consume_catalyst", "inner_fluid", "consume_fluid")) {
            if (old.has(field)) { throw new Unmappable("检测到 v1 扁平字段 " + field + "，请按迁移指南手动映射，避免丢失限制条件"); }
        }
        JsonObject rule = new JsonObject();
        rule.addProperty("enabled", optBoolean(old, "enabled", true));
        rule.addProperty("priority", optInt(old, "priority", 0));
        String notesText = optString(old, "notes", null);
        if (notesText != null && !notesText.isBlank()) {
            rule.addProperty("notes", notesText);
        }

        String item = requireString(old, "item", "缺少 item 字段");
        JsonObject source = new JsonObject();
        JsonArray items = new JsonArray();
        items.add(item);
        source.add("items", items);
        rule.add("source", source);

        rule.addProperty("trigger_after_seconds", Math.max(1, optInt(old, "conversion_time", 300)));

        JsonArray conditions = mapConditions(old.get("conditions"), namespace);
        if (conditions != null && !conditions.isEmpty()) {
            rule.add("conditions", conditions);
        }

        int sourceMultiple = Math.max(1, optInt(old, "source_multiple", 1));
        int resultMultiple = Math.max(1, optInt(old, "result_multiple", 1));
        JsonArray effects = new JsonArray();
        // 旧实现的每轮固定消耗 source_multiple 个源物品；显式声明以覆盖运行时的隐式消耗默认值
        JsonObject consumeSource = effect(namespace, "consume_source");
        consumeSource.addProperty("count", sourceMultiple);
        effects.add(consumeSource);
        addConsumptionEffects(old.get("consumption"), namespace, sourceMultiple,
                index, effects, notes);
        effects.add(mapTypeEffect(type, old, namespace, sourceMultiple, resultMultiple, index, notes));
        rule.add("effects", effects);
        return rule;
    }

    // 旧 consumption 指令 → 新 consume_catalyst / consume_fluid 效果
    private static void addConsumptionEffects(JsonElement consumption, String namespace,
                                              int sourceMultiple, int index, JsonArray effects, List<String> notes) {
        if (consumption == null || !consumption.isJsonObject()) {
            return;
        }
        JsonObject directive = consumption.getAsJsonObject();
        if (directive.has("catalyst_items") && directive.get("catalyst_items").isJsonArray()) {
            JsonArray catalysts = directive.getAsJsonArray("catalyst_items");
            if (catalysts.size() > 1) {
                throw new Unmappable("新 consume_catalyst 只支持统一的 items+count，旧配置声明了 "
                        + catalysts.size() + " 组不同数量的催化剂，无法无歧义映射");
            }
            if (catalysts.size() == 1 && catalysts.get(0).isJsonObject()) {
                JsonObject entry = catalysts.get(0).getAsJsonObject();
                String itemId = requireString(entry, "item", "催化剂条目缺少 item");
                int perRound = optInt(entry, "count", 1) * sourceMultiple;
                JsonObject catalyst = effect(namespace, "consume_catalyst");
                JsonArray items = new JsonArray();
                items.add(itemId);
                catalyst.add("items", items);
                catalyst.addProperty("count", Math.max(1, perRound));
                effects.add(catalyst);
            }
        }
        if (directive.has("inner_fluid") && directive.get("inner_fluid").isJsonObject()) {
            JsonObject fluid = directive.getAsJsonObject("inner_fluid");
            JsonObject consumeFluid = effect(namespace, "consume_fluid");
            String fluidId = optString(fluid, "fluid", null);
            if (fluidId != null && !fluidId.isBlank()) {
                consumeFluid.addProperty("fluid", fluidId);
            }
            consumeFluid.addProperty("require_source", optBoolean(fluid, "require_source", true));
            effects.add(consumeFluid);
        }
    }

    // 9 个内置类型各自的主效果映射
    private static JsonObject mapTypeEffect(String type, JsonObject old, String namespace,
                                            int sourceMultiple, int resultMultiple, int index, List<String> notes) {
        String effectPath = TYPE_TO_EFFECT.get(type);
        if (effectPath == null) {
            throw new Unmappable("未知的旧转化类型: " + type);
        }
        JsonObject effect = effect(namespace, effectPath);
        int resultLimit = optInt(old, "result_limit", 30);
        int searchRadius = optInt(old, "search_radius", 6);
        switch (type) {
            case "item_to_item" -> {
                effect.addProperty("item", requireString(old, "result", "缺少 result 物品"));
                effect.addProperty("count", resultMultiple);
                putLimitRadius(effect, resultLimit, searchRadius, notes, index);
            }
            case "item_to_mob" -> {
                effect.addProperty("entity", requireString(old, "result", "缺少 result 实体"));
                effect.addProperty("count", resultMultiple);
                effect.addProperty("age", optInt(old, "entity_age", -24000));
                putLimitRadius(effect, resultLimit, searchRadius, notes, index);
            }
            case "item_to_block" -> {
                boolean useSourceBlock = optBoolean(old, "block_of_item", false);
                effect.addProperty("use_source_block", useSourceBlock);
                if (!useSourceBlock) {
                    effect.addProperty("block", requireString(old, "result", "缺少 result 方块"));
                }
                effect.addProperty("shape", lowercase(optString(old, "block_place_shape", "SQUARE")));
                effect.addProperty("count", resultMultiple);
                int radius = optInt(old, "radius_limit", 6);
                if (radius < 1) {
                    notes.add("第 " + index + " 条: radius_limit=" + radius + " 小于新 radius 下限 1，已按 1 转换");
                    radius = 1;
                }
                effect.addProperty("radius", radius);
                putLimit(effect, resultLimit, notes, index);
                // 旧 search_radius 是"结果上限的统计半径"，新 place_block 没有独立参数与之对应；
                // 只有旧文件显式声明该字段时才记 note，避免对未写该字段的配置产生噪音
                if (old.has("search_radius")) {
                    notes.add("第 " + index + " 条: 旧字段 search_radius=" + searchRadius
                            + "（结果上限的统计半径）在新 place_block 效果中没有独立参数，"
                            + "仅 radius_limit→radius 生效，该字段已丢弃");
                }
            }
            case "item_to_xp_orb" -> {
                long amount = (long) resultMultiple * optInt(old, "xp_per_item", 1);
                if (amount < 1 || amount > 65536) {
                    throw new Unmappable("经验值总量 " + amount + " 超出新 spawn_xp.amount 区间 [1,65536]");
                }
                effect.addProperty("amount", amount);
                effect.addProperty("per_source_item", false);
            }
            case "item_to_lightning" -> {
                effect.addProperty("count", resultMultiple);
                if (optBoolean(old, "visual_only", false)) {
                    notes.add("第 " + index + " 条: 旧字段 visual_only=true 在新 lightning 效果中无对应参数，已丢弃");
                }
            }
            case "item_to_explosion" -> {
                effect.addProperty("power", optFloat(old, "explosion_power", 1.0F));
                effect.addProperty("fire", optBoolean(old, "explosion_fire", false));
                notes.add("第 " + index + " 条: 旧字段 explosion_direction_type 与 result_multiple 在新 explosion 效果中无对应参数，已丢弃");
            }
            case "item_to_arrow_rain" -> {
                effect.addProperty("count", resultMultiple);
                effect.addProperty("pickup", lowercase(optString(old, "arrow_pickup_status", "DISALLOWED")));
                JsonArray potions = mapPotionEffects(old.get("arrow_potion_effects"), index, notes);
                if (potions != null && !potions.isEmpty()) {
                    effect.add("potion_effects", potions);
                }
            }
            case "item_to_weather" -> {
                effect.addProperty("mode", lowercase(optString(old, "weather_mode", "RAIN")));
                effect.addProperty("duration_ticks", optInt(old, "weather_duration_ticks", 6000));
                effect.addProperty("thundering", optBoolean(old, "is_thundering", false));
            }
            case "item_to_loot" -> {
                effect.addProperty("loot_table", requireString(old, "result", "缺少 result 战利品表"));
                effect.addProperty("luck", optFloat(old, "luck", 0.0F));
                notes.add("第 " + index + " 条: 旧字段 result_multiple(掷取次数)、result_limit、search_radius 在新 loot_table 效果中无对应参数，已丢弃");
            }
            default -> throw new Unmappable("未知的旧转化类型: " + type);
        }
        return effect;
    }

    // 旧箭矢药水效果 → 新 potion_effects（duration 改名为 duration_ticks）
    private static @Nullable JsonArray mapPotionEffects(JsonElement element, int index, List<String> notes) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray result = new JsonArray();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject potion = entry.getAsJsonObject();
            String effectId = optString(potion, "effect", null);
            if (effectId == null || effectId.isBlank()) {
                notes.add("第 " + index + " 条: arrow_potion_effects 中有一条缺少 effect，已跳过");
                continue;
            }
            JsonObject converted = new JsonObject();
            converted.addProperty("effect", effectId);
            converted.addProperty("duration_ticks", Math.max(1, optInt(potion, "duration", 100)));
            converted.addProperty("amplifier", Math.max(0, optInt(potion, "amplifier", 0)));
            result.add(converted);
        }
        return result;
    }

    // 旧 DNF 对象 {groups:[{conditions:[{type,params,negated}]}]} → 新二维数组 [[{...扁平...}]]
    private static @Nullable JsonArray mapConditions(JsonElement element, String namespace) {
        if (element == null || element.isJsonNull()) { return null; }
        if (!element.isJsonObject()) { throw new Unmappable("conditions 必须为对象"); }
        JsonObject expression = element.getAsJsonObject();
        if (!expression.has("groups") || !expression.get("groups").isJsonArray()) {
            throw new Unmappable("conditions 缺少合法 groups 数组");
        }
        JsonArray groups = new JsonArray();
        for (JsonElement groupElement : expression.getAsJsonArray("groups")) {
            JsonArray oldLeaves;
            if (groupElement.isJsonArray()) { oldLeaves = groupElement.getAsJsonArray(); }
            else if (groupElement.isJsonObject() && groupElement.getAsJsonObject().has("conditions")
                    && groupElement.getAsJsonObject().get("conditions").isJsonArray()) {
                oldLeaves = groupElement.getAsJsonObject().getAsJsonArray("conditions");
            } else { throw new Unmappable("条件组缺少合法叶数组"); }
            JsonArray leaves = new JsonArray();
            for (JsonElement leafElement : oldLeaves) {
                    JsonObject leaf = mapLeaf(leafElement, namespace);
                    // ANY 天气等价于恒真：丢弃该叶；若整组因此为空则无法表达（新模型拒载空组）
                if (leaf != null) { leaves.add(leaf); }
            }
            if (leaves.isEmpty()) {
                throw new Unmappable("条件组在转换后为空（旧条件恒真或全部无法映射），新模型不接受空条件组");
            }
            groups.add(leaves);
        }
        return groups.isEmpty() ? null : groups;
    }

    // 单个条件叶映射；返回 null 表示该叶恒真（可安全丢弃）
    private static @Nullable JsonObject mapLeaf(JsonElement element, String namespace) {
        if (!element.isJsonObject()) { throw new Unmappable("条件叶不是对象"); }
        JsonObject old = element.getAsJsonObject();
        String rawType = requireString(old, "type", "条件叶缺少 type");
        ResourceLocation parsed = ResourceLocation.tryParse(rawType);
        if (parsed == null) {
            throw new Unmappable("条件叶 type 非法: " + rawType);
        }
        if (!parsed.getNamespace().equals(com.meteorite.itemdespawntowhat.Constants.MOD_ID)) {
            throw new Unmappable("第三方条件类型不能仅按名称映射: " + rawType);
        }
        String path = parsed.getPath();
        String mapped = CONDITIONS.get(path);
        if (mapped == null) {
            throw new Unmappable("条件类型 " + rawType + " 在新链路没有对应条件，无法映射");
        }
        JsonObject params = old.has("params") && old.get("params").isJsonObject()
                ? old.getAsJsonObject("params") : new JsonObject();
        JsonObject leaf = new JsonObject();
        leaf.addProperty("type", namespace + ":" + mapped);
        switch (mapped) {
            case "dimension" -> {
                String dimension = optString(params, "dimension", null);
                if (dimension == null || dimension.isBlank()) {
                    throw new Unmappable("dimension 条件缺少 dimension 参数");
                }
                JsonArray dimensions = new JsonArray();
                dimensions.add(dimension);
                leaf.add("dimensions", dimensions);
            }
            case "outdoor" -> {
                // 无参数
            }
            case "surrounding_blocks" -> {
                JsonObject blocks = params.has("blocks") && params.get("blocks").isJsonObject()
                        ? params.getAsJsonObject("blocks") : null;
                if (blocks == null) {
                    throw new Unmappable("surrounding_blocks 条件缺少 blocks 参数");
                }
                boolean any = false;
                for (String direction : new String[]{"north", "south", "east", "west", "up", "down"}) {
                    String value = optString(blocks, direction, null);
                    if (value != null && !value.isBlank()) {
                        leaf.addProperty(direction, value);
                        any = true;
                    }
                }
                if (!any) {
                    throw new Unmappable("surrounding_blocks 条件六个方向全为空");
                }
            }
            case "catalyst_present" -> {
                JsonArray catalystEntries = params.has("items") && params.get("items").isJsonArray()
                        ? params.getAsJsonArray("items") : new JsonArray();
                if (catalystEntries.isEmpty()) {
                    throw new Unmappable("catalyst_present 条件缺少 items 参数");
                }
                if (catalystEntries.size() > 1) {
                    throw new Unmappable("catalyst_present 的 items 有 "
                            + catalystEntries.size() + " 组不同数量，新条件只支持统一的 items+count");
                }
                JsonObject entry = catalystEntries.get(0).getAsJsonObject();
                JsonArray items = new JsonArray();
                items.add(requireString(entry, "item", "catalyst_present 条目缺少 item"));
                leaf.add("items", items);
                leaf.addProperty("count", Math.max(1, optInt(entry, "count", 1)));
            }
            case "fluid_present" -> {
                String fluid = optString(params, "fluid", null);
                if (fluid != null && !fluid.isBlank()) {
                    leaf.addProperty("fluid", fluid);
                }
                leaf.addProperty("require_source", optBoolean(params, "require_source", true));
            }
            case "biome" -> {
                String biome = optString(params, "biome", null);
                if (biome == null || biome.isBlank()) {
                    throw new Unmappable("biome 条件缺少 biome 参数");
                }
                leaf.addProperty("mode", "exact");
                JsonArray biomes = new JsonArray();
                biomes.add(biome);
                leaf.add("biomes", biomes);
            }
            case "weather" -> {
                String weather = optString(params, "weather", null);
                if (weather == null || weather.isBlank()) {
                    throw new Unmappable("weather 条件缺少 weather 参数");
                }
                String normalized = weather.trim().toUpperCase(java.util.Locale.ROOT);
                switch (normalized) {
                    case "ANY" -> {
                        if (optBoolean(old, "negated", false)) {
                            throw new Unmappable("取反的 ANY 天气恒假，不能删除该叶改变条件语义");
                        }
                        return null;
                    }
                    case "CLEAR" -> leaf.addProperty("weather", "clear");
                    case "RAINING", "RAIN" -> leaf.addProperty("weather", "rain");
                    case "THUNDERING", "THUNDER" -> leaf.addProperty("weather", "thunder");
                    default -> throw new Unmappable("未知的 weather 取值: " + weather);
                }
            }
            default -> throw new Unmappable("条件类型 " + rawType + " 没有映射实现");
        }
        leaf.addProperty("negated", optBoolean(old, "negated", false));
        return leaf;
    }

    // ========== 小工具 ==========

    // 映射无法无歧义完成时抛出，由调用方转成"无法映射条目"报告
    private static final class Unmappable extends RuntimeException {
        Unmappable(String reason) {
            super(reason);
        }
    }

    // 新效果对象的骨架：只填 type
    private static JsonObject effect(String namespace, String path) {
        JsonObject effect = new JsonObject();
        effect.addProperty("type", namespace + ":" + path);
        return effect;
    }

    // 结果上限字段：旧 result_limit > 0 时写入 limit
    private static void putLimit(JsonObject effect, int resultLimit, List<String> notes, int index) {
        if (resultLimit <= 0) {
            notes.add("第 " + index + " 条: result_limit=" + resultLimit + " 不是有效上限，新效果的 limit 已省略（不限制）");
            return;
        }
        effect.addProperty("limit", resultLimit);
    }

    // 结果上限 + 搜索半径：半径 0 在新模型中非法，按 1 处理并提示
    private static void putLimitRadius(JsonObject effect, int resultLimit, int searchRadius,
                                       List<String> notes, int index) {
        putLimit(effect, resultLimit, notes, index);
        if (searchRadius < 1) {
            notes.add("第 " + index + " 条: search_radius=" + searchRadius
                    + " 小于新 radius 下限 1，已按 1 转换（同格范围）");
            effect.addProperty("radius", 1);
            return;
        }
        effect.addProperty("radius", searchRadius);
    }

    private static String stripExtension(String name) {
        return RulePaths.stripExtension(name);
    }

    private static String lowercase(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireString(JsonObject object, String field, String error) {
        String value = optString(object, field, null);
        if (value == null || value.isBlank()) {
            throw new Unmappable(error);
        }
        return value;
    }

    private static @Nullable String optString(JsonObject object, String field, @Nullable String fallback) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()) {
            return fallback;
        }
        return object.get(field).getAsString();
    }

    private static int optInt(JsonObject object, String field, int fallback) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(field).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float optFloat(JsonObject object, String field, float fallback) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(field).getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean optBoolean(JsonObject object, String field, boolean fallback) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(field).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
