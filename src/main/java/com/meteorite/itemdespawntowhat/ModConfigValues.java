package com.meteorite.itemdespawntowhat;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfigValues {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue LIGHTNING_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue EXPLOSION_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue ARROW_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue BLOCK_PLACE_INTERVAL_TICKS;
    public static final ModConfigSpec.EnumValue<CircleShape> BLOCK_PLACE_CIRCLE_SHAPE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("World effect interval settings").push("world_effects");

        LIGHTNING_INTERVAL_TICKS = builder
                .comment("Ticks between each lightning strike (default: 8)")
                .defineInRange("lightning_interval_ticks", 8, 1, 100);

        EXPLOSION_INTERVAL_TICKS = builder
                .comment("Ticks between each explosion (default: 5)")
                .defineInRange("explosion_interval_ticks", 5, 1, 100);

        ARROW_INTERVAL_TICKS = builder
                .comment("Ticks between each arrow spawn (default: 2)")
                .defineInRange("arrow_interval_ticks", 2, 1, 100);

        builder.pop();

        builder.comment("Block placement task settings").push("block_placement");

        BLOCK_PLACE_INTERVAL_TICKS = builder
                .comment("Ticks between placing each ring of blocks (default: 1)")
                .defineInRange("block_place_interval_ticks", 1, 1, 100);

        BLOCK_PLACE_CIRCLE_SHAPE = builder
                .comment("Shape of each placement ring: SQUARE (Chebyshev distance) or CIRCLE (Euclidean distance)")
                .defineEnum("block_place_circle_shape", CircleShape.SQUARE);

        builder.pop();

        SPEC = builder.build();
    }

    public enum CircleShape {
        SQUARE,
        CIRCLE
    }
}
