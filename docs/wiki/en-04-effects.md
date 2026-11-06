# Effects (Actions)

> Applies to **2.0.0** (Minecraft **1.21.1**).

This page details the **effects** (called "Actions" in the GUI editor) executed when a rule triggers. When dropped items satisfy their conditions, the mod executes your configured effect list in sequence.

---

## Effect Types Overview

Each effect in JSON is an object whose `type` specifies what it does:

| Effect Type | `type` | One-Shot? | Gameplay Description |
| --- | --- | --- | --- |
| **Entity Spawn** | `idtw:spawn_entity` | No (per group) | Spawn items, mobs, vehicles/entities, or experience orbs |
| **Arrow Rain** | `idtw:arrow_rain` | No (per group) | Rain down a shower of arrows from above (optional potion effects) |
| **Place Block** | `idtw:place_block` | No (per group) | Place blocks around the trigger location (e.g. saplings taking root) |
| **Loot Table** | `idtw:loot_table` | No (per group) | Roll a vanilla loot table (e.g. dungeon chest) for random drops |
| **Lightning** | `idtw:lightning` | **Yes** (one-shot) | Strike 1 to 16 real or cosmetic lightning bolts |
| **Explosion** | `idtw:explosion` | **Yes** (one-shot) | Create an explosion with custom radius and block damage settings |
| **Weather** | `idtw:weather` | **Yes** (one-shot) | Change world weather to clear, rain, or thunder for a duration |

*Note: Lightning, explosions, and weather changes are "one-shot" effects. Even if 64 items convert at once, they fire only once to prevent extreme server lag.*

---

## Common Effect Fields

All effect types support these 3 common fields (`type` is required; others are optional):

| Field | Description | Type / Range | Default |
| --- | --- | --- | --- |
| `type` | Unique effect identifier | Starting with `idtw:` | **Required** |
| `delay_ticks` | Ticks to wait after rule triggers before executing this effect | Non-negative integer (20 ticks = 1s) | `0` (immediate) |
| `chance` | Probability of this effect succeeding | Float `0.0` to `1.0` | `1.0` (100% chance) |

```json
{
  "type": "idtw:lightning",
  "delay_ticks": 40,
  "chance": 0.5
}
```
*Waits 2 seconds (40 ticks), then has a 50% chance to strike lightning.*

---

## 7 Available Effect Types

### 1. spawn_entity

Use `variant` to specify the entity subtype: `item`, `mob`, `entity`, or `experience`.

#### A. Spawn Items (`variant: "item"`)
- `item`: Item ID or tag (randomly picks one member from a tag);
- `count`: Quantity (1 to 64).

```json
{
  "type": "idtw:spawn_entity",
  "variant": "item",
  "item": "minecraft:diamond",
  "count": 1
}
```
*Gameplay detail*: If the source item was moving horizontally when destroyed (e.g. thrown by a player), spawned products receive a slight reverse momentum, **flying back toward the thrower**.

#### B. Spawn Mobs (`variant: "mob"`)
- `entity`: Mob type ID or tag;
- `count`: Quantity (1 to 64);
- `age`: Mob age (-24000 for baby, 0 for adult).

```json
{
  "type": "idtw:spawn_entity",
  "variant": "mob",
  "entity": "minecraft:cow",
  "count": 1,
  "age": -24000
}
```
*Safe spawn protection*: Enabled by default. Automatically searches nearby ground for a safe spot so mobs don't suffocate inside blocks or spawn in lava.

#### C. Spawn Other Entities (`variant: "entity"`)
- Used for minecarts, boats, and other non-living entities.

```json
{
  "type": "idtw:spawn_entity",
  "variant": "entity",
  "entity": "minecraft:minecart",
  "count": 1
}
```

#### D. Spawn Experience (`variant: "experience"`)
- `amount`: Experience points (1 to 10000).

```json
{
  "type": "idtw:spawn_entity",
  "variant": "experience",
  "amount": 20
}
```

---

### 2. place_block

Places blocks into the world around the item.

| Field | Description | Range | Default |
| --- | --- | --- | --- |
| `block` | Block ID or tag to place | Block ID/tag | Matches source item if `use_source_block` is true |
| `use_source_block` | Use the block corresponding to the source item | `true` / `false` | `false` |
| `shape` | Area shape | `circle`, `square`, `cube` | `circle` |
| `radius` | Placement radius | 1 to 8 | `1` |
| `count` | Number of blocks to place | 1 to 64 | `1` |

```json
{
  "type": "idtw:place_block",
  "block": "minecraft:ice",
  "shape": "circle",
  "radius": 2,
  "count": 4
}
```

---

### 3. loot_table

Rolls a vanilla loot table for random items.

- `table`: Loot table resource location;
- `seed`: Optional random seed.

```json
{
  "type": "idtw:loot_table",
  "table": "minecraft:chests/simple_dungeon"
}
```

---

### 4. lightning

- `visual_only`: Cosmetic bolt without damage or fire;
- `count`: Number of bolts (1 to 16).

```json
{
  "type": "idtw:lightning",
  "visual_only": false,
  "count": 1
}
```

---

### 5. explosion

- `radius`: Blast radius (0.0 to 32.0; vanilla TNT is 4.0);
- `causes_fire`: Whether the explosion ignites fires;
- `block_interaction`: Block destruction: `keep` (no block damage), `destroy` (normal destruction), `trigger` (blocks drop and trigger).

```json
{
  "type": "idtw:explosion",
  "radius": 3.0,
  "causes_fire": false,
  "block_interaction": "keep"
}
```

---

### 6. weather

- `weather`: Target weather: `clear`, `rain`, `thunder`;
- `duration_ticks`: Duration in ticks (e.g. 6000 ticks = 5 minutes).

```json
{
  "type": "idtw:weather",
  "weather": "clear",
  "duration_ticks": 12000
}
```

---

### 7. arrow_rain

Rains arrows from above the item position.

- `count`: Arrow count (1 to 128);
- `pickup`: Pickup mode: `allowed`, `disallowed`, `creative_only`;
- `potion_effects`: Optional potion effects array.

```json
{
  "type": "idtw:arrow_rain",
  "count": 16,
  "pickup": "disallowed"
}
```

---

## Performance & Safety Protections

The mod includes safeguards to preserve server TPS and fair play:
1. **Spread delivery**: Large batches of items spawn across multiple game ticks to prevent lag spikes;
2. **Safe mob spawning**: Spawning algorithms search for solid ground away from lava and the void;
3. **Product cooldown**: Newly converted items have temporary immunity against instant burn damage or secondary rule triggering.

---

## Next Steps

- [Outcome Candidates and Settlement](en-05-outcomes-and-settlement): Multiple outcomes, weights, costs, and refunds.
- [Rule Fields](en-02-rule-basics): Top-level rule properties and timing options.
