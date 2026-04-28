package com.meteorite.itemdespawntowhat;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ModConfigValues {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue LIGHTNING_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue EXPLOSION_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue ARROW_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue BLOCK_PLACE_INTERVAL_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_SCALE_OVERRIDES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("World effect interval settings").push("world_effects");

        LIGHTNING_INTERVAL_TICKS = builder
                .comment("Ticks between each lightning strike (default: 8)")
                .defineInRange("lightning_interval_ticks", 8, 1, 100);

        EXPLOSION_INTERVAL_TICKS = builder
                .comment("Ticks between each explosion (default: 5)")
                .defineInRange("explosion_interval_ticks", 8, 1, 100);

        ARROW_INTERVAL_TICKS = builder
                .comment("Ticks between each arrow spawn (default: 2)")
                .defineInRange("arrow_interval_ticks", 2, 1, 100);

        builder.pop();

        builder.comment("Block placement task settings").push("block_placement");

        BLOCK_PLACE_INTERVAL_TICKS = builder
                .comment("Ticks between placing each ring of blocks (default: 1)")
                .defineInRange("block_place_interval_ticks", 1, 1, 100);

        builder.pop();

        builder.comment("Mob icon display settings").push("mob_icon");

        ENTITY_SCALE_OVERRIDES = builder
                .comment("Custom icon scale per entity type. Format: \"namespace:entity_id=scale\", e.g. \"minecraft:ender_dragon=1.5\"")
                .defineList(
                        "entity_scale_overrides",
                        List.of(
                                "minecraft:ghast=2.0",
                                "minecraft:ender_dragon=2.0",
                                "minecraft:iron_golem=3.0",
                                "minecraft:wither=3.0"
                        ),
                        () -> "",
                        e -> e instanceof String s && s.contains("=")
                );

        builder.pop();

        SPEC = builder.build();
    }
}
