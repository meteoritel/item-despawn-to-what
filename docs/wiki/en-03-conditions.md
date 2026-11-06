# Conditions (`conditions`)

> Applies to **2.0.0** (Minecraft **1.21.1**).

This page explains how to use `conditions` to restrict when rules trigger. For example: allowing apples to ferment only at night, ice to melt only in hot biomes, or rituals to complete only during thunderstorms.

---

## Core Concepts

- **Gatekeeping only**: Conditions check whether the current environment allows the rule to trigger; they **never consume items or fluids**. Material costs are deducted by the rule's settlement configuration (see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement)).
- **Unconditional triggers**: To let a rule trigger under any circumstance, simply **omit the `conditions` field entirely**.
- **Automatic retry**: If a dropped item reaches its trigger time while conditions are not met (e.g. waiting for rain on a sunny day), the mod **does not delete the item**. Instead, it retries at backoff intervals until conditions are met or the item despawns naturally.

---

## Condition Tree Structure

In JSON, `conditions` forms a **condition tree** made of logical nodes. Each node specifies its logical operation with `op`:

| `op` Type | Meaning | When to Use | Child Node Field |
| --- | --- | --- | --- |
| `leaf` | **Concrete condition leaf** | Wraps an actual condition evaluation object | `condition` |
| `all_of` | **Logical AND** | Succeeds only if **all conditions in the list** pass | `terms` (array) |
| `any_of` | **Logical OR** | Succeeds if **any condition in the list** passes | `terms` (array) |
| `inverted` | **Logical NOT** | Inverts child condition result (e.g. "not thundering") | `term` (single node) |

### 1. Single Condition (`leaf`)

Each `leaf` node holds a `condition` object. Its `type` must start with `idtw:`:

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:dimension",
    "dimensions": ["minecraft:overworld"]
  }
}
```

### 2. Combining Conditions (`all_of`, `any_of`, `inverted`)

Nodes can be nested freely. For example, to require **"outdoors AND not during a thunderstorm"**:

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "leaf",
      "condition": { "type": "idtw:outdoor" }
    },
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "idtw:weather", "weather": "thunder" }
      }
    }
  ]
}
```

### 3. Tree Limits

To protect server performance, single-rule condition trees observe these bounds:
- Up to **128** condition leaves (`leaf`);
- Up to **256** total nodes (all leaves and composite nodes combined);
- Maximum tree depth of **16** levels.

---

## 10 Available Condition Types

All condition types are prefixed with the `idtw:` namespace. References to items, blocks, fluids, and biomes support `#` tag notation.

| Type ID | Name | What It Checks |
| --- | --- | --- |
| `idtw:dimension` | Dimension | Checks if the dropped item is in specified dimensions |
| `idtw:biome` | Biome | Matches biome IDs or vanilla climate parameters (temperature/humidity) |
| `idtw:time_of_day` | Time of Day | Matches world daytime in ticks (supports wrapping midnight) |
| `idtw:weather` | Weather | Checks world weather (`clear`, `rain`, `thunder`) |
| `idtw:light_level` | Light Level | Checks block light level at item position (0 to 15) |
| `idtw:y_level` | Y Height | Checks if item altitude is within a range (min/max) |
| `idtw:outdoor` | Outdoors | Checks if there are no solid blocks directly above to the sky |
| `idtw:surrounding_blocks` | Surrounding Blocks | Matches 6 adjacent blocks (up, down, north, south, east, west) |
| `idtw:fluid_present` | Fluid Present | Checks if specified fluid exists at the item's location |
| `idtw:catalyst_present` | Catalyst Present | Checks for sufficient catalyst item entities nearby |

---

## Parameter Reference & Examples

### 1. dimension

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:dimension",
    "dimensions": ["minecraft:the_nether"]
  }
}
```
*Triggers only in the Nether.*

### 2. biome

Supports two modes:
- `mode: "exact"`: Match biome IDs or `#` tags;
- `mode: "climate"`: Match climate ranges (temperature, humidity, etc.).

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_forest"]
  }
}
```
*Triggers only in forest biomes.*

### 3. time_of_day

One Minecraft day has 24,000 ticks (0 = dawn, 6000 = noon, 12000 = dusk, 18000 = midnight). Ranges are inclusive:

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:time_of_day",
    "from": 13000,
    "to": 23000
  }
}
```
*Triggers during the night (13000 to 23000). Setting `from` greater than `to` (e.g. 22000 to 2000) matches across midnight.*

### 4. weather

Options: `clear`, `rain`, `thunder`.

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:weather",
    "weather": "rain"
  }
}
```

### 5. light_level and y_level

- `light_level`: Integer 0 to 15, with `min` and `max`.
- `y_level`: World coordinate height, with `min` and `max` (either may be omitted).

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:light_level",
    "min": 0,
    "max": 4
  }
}
```
*Triggers only in dark areas (light ≤ 4).*

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:y_level",
    "min": 64
  }
}
```
*Triggers at surface level or above (Y ≥ 64).*

### 6. outdoor

No extra parameters. Satisfied when there are no opaque blocks between the item and the sky.

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:outdoor"
  }
}
```

### 7. surrounding_blocks

Checks 6 adjacent blocks (`up`, `down`, `north`, `south`, `east`, `west`). Unspecified directions are ignored.

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:surrounding_blocks",
    "down": "#minecraft:dirt"
  }
}
```
*Requires a dirt block directly beneath the item.*

### 8. fluid_present

Checks for fluid at the item's position. Use `fluid` for single entries or `fluids` for an array.

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:fluid_present",
    "fluid": "minecraft:water"
  }
}
```

### 9. catalyst_present

Checks whether sufficient dropped item entities exist nearby.
- `items`: Item IDs or tags;
- `count`: Required quantity.

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:catalyst_present",
    "items": ["minecraft:redstone"],
    "count": 4
  }
}
```
*Requires at least 4 dropped redstone dust items nearby.*

---

## Full Example

Here is a complete rule where **an apple transforms into an enchanted golden apple only on rainy nights outdoors in a forest**:

```json
{
  "id": "myrules:mystic_apple",
  "display_name": "Mystic Apple Transformation",
  "source": { "items": ["minecraft:apple"] },
  "trigger_after_seconds": 60,
  "conditions": {
    "op": "all_of",
    "terms": [
      {
        "op": "leaf",
        "condition": { "type": "idtw:outdoor" }
      },
      {
        "op": "leaf",
        "condition": { "type": "idtw:weather", "weather": "rain" }
      },
      {
        "op": "leaf",
        "condition": { "type": "idtw:time_of_day", "from": 13000, "to": 23000 }
      },
      {
        "op": "leaf",
        "condition": {
          "type": "idtw:biome",
          "mode": "exact",
          "biomes": ["#minecraft:is_forest"]
        }
      }
    ]
  },
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "variant": "item",
      "item": "minecraft:enchanted_golden_apple",
      "count": 1
    }
  ]
}
```

---

## Next Steps

- [Effects](en-04-effects): Actions, entities, block placing, lightning, and weather.
- [Outcome Candidates and Settlement](en-05-outcomes-and-settlement): Multiple outcomes, weights, costs, and refunds.
