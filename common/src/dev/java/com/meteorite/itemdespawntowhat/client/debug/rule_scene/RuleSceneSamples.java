package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * debug 工作台 rule_scene 场景的内置样例规则集。
 *
 * <p>每条样例保存完整的规则 JSON，并经 {@link RuleSceneModel#of(RuleValue)} 解析为展示模型，
 * 因此场景消费的是与规则编辑器同一套 JSON 语义，而不是另建一套演示数据。13 条样例覆盖全部
 * 条件类型与所有产出效果类型，用于检查舞台动画在不同条件、不同效果下的可辨识度。</p>
 */
public final class RuleSceneSamples {

    /** 单条样例：稳定标识、本地化标题与副标题，以及解析后的场景模型。 */
    public record Sample(String id, Component title, Component subtitle, RuleSceneModel model) { }

    private static final String WATER_CHICKEN = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/water_chicken",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 3,
              "source": {
                "items": ["minecraft:egg"],
                "exclude": []
              },
              "source_cost": {"minecraft:egg": 1},
              "catalyst_cost": {
                "items": ["minecraft:sugar"],
                "counts": {"minecraft:sugar": 1},
                "consume_counts": {"minecraft:sugar": 1},
                "radius": 2
              },
              "conditions": {
                "op": "all_of",
                "terms": [
                  {"op": "leaf", "condition": {"type": "idtw:fluid_present", "fluids": ["minecraft:water"]}},
                  {"op": "leaf", "condition": {"type": "idtw:catalyst_present", "items": ["minecraft:sugar"]}},
                  {"op": "leaf", "condition": {"type": "idtw:surrounding_blocks", "down": "minecraft:magma_block"}}
                ]
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "mob", "entity": "minecraft:chicken", "count": 1, "age": -24000}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String BIOME_PLAINS = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/biome_plains",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 8,
              "source": {
                "items": ["minecraft:dirt"],
                "exclude": []
              },
              "source_cost": {"minecraft:dirt": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:biome", "mode": "exact", "biomes": ["minecraft:plains"]}
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "item", "item": "minecraft:wheat_seeds", "count": 3}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String BIOME_SNOWY = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/biome_snowy",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 12,
              "source": {
                "items": ["minecraft:snow_block"],
                "exclude": []
              },
              "source_cost": {"minecraft:snow_block": 1},
              "conditions": {
                "op": "leaf",
                "condition": {
                  "type": "idtw:biome",
                  "mode": "exact",
                  "biomes": ["minecraft:snowy_plains", "minecraft:frozen_peaks"]
                }
              },
              "effects": [
                {"type": "idtw:place_block", "block": "minecraft:powder_snow", "shape": "square", "count": 6, "radius": 2}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String BIOME_DESERT = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/biome_desert",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 10,
              "source": {
                "items": ["minecraft:sand"],
                "exclude": []
              },
              "source_cost": {"minecraft:sand": 1},
              "conditions": {
                "op": "leaf",
                "condition": {
                  "type": "idtw:biome",
                  "mode": "climate",
                  "temperature": {"min": 1.5, "max": 2.0},
                  "humidity": {"min": -1.0, "max": -0.5}
                }
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "item", "item": "minecraft:dead_bush", "count": 2}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String DIMENSION_NETHER = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/dimension_nether",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 12,
              "source": {
                "items": ["minecraft:blaze_rod"],
                "exclude": []
              },
              "source_cost": {"minecraft:blaze_rod": 1},
              "consume_fluid": true,
              "conditions": {
                "op": "all_of",
                "terms": [
                  {"op": "leaf", "condition": {"type": "idtw:dimension", "dimensions": ["minecraft:the_nether"]}},
                  {"op": "leaf", "condition": {"type": "idtw:fluid_present", "fluids": ["minecraft:lava"]}}
                ]
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "item", "item": "minecraft:blaze_powder", "count": 2}
              ],
              "triggers": ["lava"]
            }
            """;

    private static final String DIMENSION_END = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/dimension_end",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 30,
              "source": {
                "items": ["minecraft:chorus_fruit"],
                "exclude": []
              },
              "source_cost": {"minecraft:chorus_fruit": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:dimension", "dimensions": ["minecraft:the_end"]}
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "item", "item": "minecraft:ender_pearl", "count": 2},
                {"type": "idtw:spawn_entity", "variant": "entity", "entity": "minecraft:end_crystal", "count": 1}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String TIME_NIGHT = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/time_night",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 60,
              "source": {
                "items": ["minecraft:rotten_flesh"],
                "exclude": []
              },
              "source_cost": {"minecraft:rotten_flesh": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:time_of_day", "from": 13000, "to": 23000}
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "item", "item": "minecraft:phantom_membrane", "count": 1, "chance": 0.25}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String WEATHER_RAIN = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/weather_rain",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 30,
              "source": {
                "items": ["minecraft:bucket"],
                "exclude": []
              },
              "source_cost": {"minecraft:bucket": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:weather", "weather": "rain"}
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "item", "item": "minecraft:water_bucket", "count": 1}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String WEATHER_THUNDER = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/weather_thunder",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 20,
              "source": {
                "items": ["minecraft:copper_ingot"],
                "exclude": []
              },
              "source_cost": {"minecraft:copper_ingot": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:weather", "weather": "thunder"}
              },
              "effects": [
                {"type": "idtw:lightning", "count": 1, "chance": 0.75}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String LIGHT_DARK = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/light_dark",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 10,
              "source": {
                "items": ["minecraft:gunpowder"],
                "exclude": []
              },
              "source_cost": {"minecraft:gunpowder": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:light_level", "min": 0, "max": 4}
              },
              "effects": [
                {"type": "idtw:explosion", "power": 4.0, "fire": false, "visual_only": false}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String Y_DEEP = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/y_deep",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 45,
              "source": {
                "items": ["minecraft:deepslate"],
                "exclude": []
              },
              "source_cost": {"minecraft:deepslate": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:y_level", "min": -64, "max": 0}
              },
              "effects": [
                {"type": "idtw:loot_table", "loot_table": "minecraft:chests/ancient_city", "luck": 0.0},
                {"type": "idtw:spawn_entity", "variant": "experience", "amount": 8}
              ],
              "triggers": ["natural"]
            }
            """;

    private static final String OUTDOOR_OPEN = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/outdoor_open",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 15,
              "source": {
                "items": ["minecraft:oak_log"],
                "exclude": []
              },
              "source_cost": {"minecraft:oak_log": 1},
              "conditions": {
                "op": "leaf",
                "condition": {"type": "idtw:outdoor"}
              },
              "effects": [
                {"type": "idtw:spawn_entity", "variant": "item", "item": "minecraft:charcoal", "count": 2, "chance": 0.5}
              ],
              "triggers": ["fire"]
            }
            """;

    private static final String SURROUND_CAGE = """
            {
              "schema_version": 1,
              "id": "idtw:debug/rule_scene/surround_cage",
              "enabled": true,
              "priority": 0,
              "trigger_after_seconds": 25,
              "source": {
                "items": ["minecraft:stone"],
                "exclude": []
              },
              "source_cost": {"minecraft:stone": 1},
              "conditions": {
                "op": "leaf",
                "condition": {
                  "type": "idtw:surrounding_blocks",
                  "up": "minecraft:stone",
                  "north": "minecraft:stone",
                  "south": "minecraft:stone",
                  "east": "minecraft:stone",
                  "west": "minecraft:stone"
                }
              },
              "effects": [
                {"type": "idtw:place_block", "block": "minecraft:sand", "shape": "square", "count": 8, "radius": 3}
              ],
              "triggers": ["cactus"]
            }
            """;

    /** 全部样例，顺序即界面上的浏览顺序。 */
    public static List<Sample> all() {
        return List.of(
                sample("water_chicken", WATER_CHICKEN),
                sample("biome_plains", BIOME_PLAINS),
                sample("biome_snowy", BIOME_SNOWY),
                sample("biome_desert", BIOME_DESERT),
                sample("dimension_nether", DIMENSION_NETHER),
                sample("dimension_end", DIMENSION_END),
                sample("time_night", TIME_NIGHT),
                sample("weather_rain", WEATHER_RAIN),
                sample("weather_thunder", WEATHER_THUNDER),
                sample("light_dark", LIGHT_DARK),
                sample("y_deep", Y_DEEP),
                sample("outdoor_open", OUTDOOR_OPEN),
                sample("surround_cage", SURROUND_CAGE)
        );
    }

    // 标题与副标题的 key 统一在这里拼装，避免样例定义处重复前缀
    private static Sample sample(String id, String ruleJson) {
        return new Sample(id,
                Component.translatable(RuleSceneModel.UI + "sample." + id),
                Component.translatable(RuleSceneModel.UI + "sample." + id + ".description"),
                RuleSceneModel.of(RuleValue.parse(ruleJson)));
    }

    private RuleSceneSamples() {
    }
}
