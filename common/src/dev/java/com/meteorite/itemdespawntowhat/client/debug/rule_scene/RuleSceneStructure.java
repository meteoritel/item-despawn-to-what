package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewStructure;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 把规则条件翻译成可渲染的方块场景：底板、液面、周围遮挡与天花切角。
 *
 * <p>只做视觉近似，不追求方块语义精确：群系与维度决定底板，流体条件决定液面，
 * 周围方块条件决定侧墙与切角天花板。</p>
 */
final class RuleSceneStructure {

    private static final int WIDTH = 7;
    private static final int HEIGHT = 4;
    private static final int DEPTH = 7;
    private static final int CENTER = 3;

    private RuleSceneStructure() { }

    /** 构建场景结构；无条件时退化为草地空场。 */
    static PreviewStructure build(RuleSceneModel model) {
        Palette palette = new Palette();
        List<PreviewStructure.Cell> cells = new ArrayList<>();
        BlockState floor = floorState(model);
        BlockState fluid = fluidState(model);
        Map<String, BlockState> sides = sides(model);
        // 向下的周围方块条件替换中心底板，形成「水里压在岩浆块上」这类布局
        BlockState under = sides.getOrDefault("down", floor);
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                BlockState state = x == CENTER && z == CENTER ? under : floor;
                cells.add(cell(palette, x, 0, z, state));
            }
        }
        if (fluid != null) {
            for (int x = 1; x < WIDTH - 1; x++) {
                for (int z = 1; z < DEPTH - 1; z++) {
                    cells.add(cell(palette, x, 1, z, fluid));
                }
            }
        }
        if (sides.containsKey("north")) wall(palette, cells, sides.get("north"), 0, true);
        if (sides.containsKey("south")) wall(palette, cells, sides.get("south"), DEPTH - 1, true);
        if (sides.containsKey("west")) wall(palette, cells, sides.get("west"), 0, false);
        if (sides.containsKey("east")) wall(palette, cells, sides.get("east"), WIDTH - 1, false);
        if (sides.containsKey("up")) roof(palette, cells, sides.get("up"));
        return new PreviewStructure(new BlockPos(WIDTH, HEIGHT, DEPTH), palette.states(), cells, List.of());
    }

    // 侧墙只铺一层，避免俯视时遮住场景内容；z 轴方向沿 x 铺，x 轴方向沿 z 铺
    private static void wall(Palette palette, List<PreviewStructure.Cell> cells, BlockState state,
                             int fixed, boolean alongX) {
        for (int step = 1; step < WIDTH - 1; step++) {
            cells.add(alongX ? cell(palette, step, 1, fixed, state) : cell(palette, fixed, 1, step, state));
        }
    }

    // 天花板保留一个切口，俯视相机仍能看见内部
    private static void roof(Palette palette, List<PreviewStructure.Cell> cells, BlockState state) {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                if (x >= 4 && z <= 2) continue;
                cells.add(cell(palette, x, HEIGHT - 1, z, state));
            }
        }
    }

    private static PreviewStructure.Cell cell(Palette palette, int x, int y, int z, BlockState state) {
        return new PreviewStructure.Cell(new BlockPos(x, y, z), palette.index(state), null);
    }

    // 底板：维度优先，其次高度（深层深板岩、高空石头），再按群系名与气候轴近似材质
    private static BlockState floorState(RuleSceneModel model) {
        String dimension = firstId(model, RuleSceneModel.Dimension.class);
        if (dimension.endsWith("the_nether")) return Blocks.NETHERRACK.defaultBlockState();
        if (dimension.endsWith("the_end")) return Blocks.END_STONE.defaultBlockState();
        RuleSceneModel.YLevel yLevel = first(model, RuleSceneModel.YLevel.class);
        if (yLevel != null) {
            double midpoint = (yLevel.min() + yLevel.max()) / 2.0D;
            if (midpoint < 0) return Blocks.DEEPSLATE.defaultBlockState();
            if (midpoint >= 100) return Blocks.STONE.defaultBlockState();
        }
        RuleSceneModel.Biome climate = first(model, RuleSceneModel.Biome.class);
        if (climate != null && climate.climate()) {
            if (climate.temperature() >= 1.0D) return Blocks.SAND.defaultBlockState();
            if (climate.temperature() <= -0.5D) return Blocks.SNOW_BLOCK.defaultBlockState();
            if (climate.humidity() >= 0.7D) return Blocks.MOSS_BLOCK.defaultBlockState();
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        String biome = firstId(model, RuleSceneModel.Biome.class);
        if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice")) return Blocks.SNOW_BLOCK.defaultBlockState();
        if (biome.contains("desert") || biome.contains("badlands") || biome.contains("beach")) return Blocks.SAND.defaultBlockState();
        if (biome.contains("ocean") || biome.contains("river") || biome.contains("swamp")) return Blocks.GRAVEL.defaultBlockState();
        if (biome.contains("mushroom")) return Blocks.MYCELIUM.defaultBlockState();
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    // 液面：只有声明了流体条件才出现；未指定种类时按水处理
    private static BlockState fluidState(RuleSceneModel model) {
        for (RuleSceneModel.Condition condition : model.conditions()) {
            if (!(condition instanceof RuleSceneModel.FluidPresent fluid)) continue;
            String id = fluid.fluids().isEmpty() ? "minecraft:water" : fluid.fluids().get(0);
            return id.contains("lava") ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
        }
        return null;
    }

    private static Map<String, BlockState> sides(RuleSceneModel model) {
        for (RuleSceneModel.Condition condition : model.conditions()) {
            if (condition instanceof RuleSceneModel.Surrounding surrounding) {
                Map<String, BlockState> sides = new LinkedHashMap<>();
                surrounding.sides().forEach((side, id) -> sides.put(side, block(id)));
                return sides;
            }
        }
        return Map.of();
    }

    private static BlockState block(String reference) {
        TaggedId tagged = TaggedId.tryParse(reference);
        if (tagged == null || tagged.tag()) return Blocks.STONE.defaultBlockState();
        Block block = BuiltInRegistries.BLOCK.getOptional(tagged.id()).orElse(Blocks.STONE);
        return block.defaultBlockState();
    }

    private static <T extends RuleSceneModel.Condition> T first(RuleSceneModel model, Class<T> type) {
        for (RuleSceneModel.Condition condition : model.conditions()) {
            if (type.isInstance(condition)) return type.cast(condition);
        }
        return null;
    }

    private static String firstId(RuleSceneModel model, Class<? extends RuleSceneModel.Condition> type) {
        for (RuleSceneModel.Condition condition : model.conditions()) {
            if (!type.isInstance(condition)) continue;
            List<String> ids = condition instanceof RuleSceneModel.Biome biome ? biome.biomes()
                    : ((RuleSceneModel.Dimension) condition).dimensions();
            if (!ids.isEmpty()) return ids.get(0);
        }
        return "";
    }

    // 调色板按首次出现顺序编号，重复方块复用同一条目
    private static final class Palette {
        private final List<BlockState> states = new ArrayList<>();
        private final Map<BlockState, Integer> indexes = new LinkedHashMap<>();

        private int index(BlockState state) {
            Integer index = indexes.get(state);
            if (index != null) return index;
            indexes.put(state, states.size());
            states.add(state);
            return states.size() - 1;
        }

        private List<BlockState> states() { return List.copyOf(states); }
    }
}
