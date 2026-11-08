# Rule Fields

> Applies to **2.0.0** (Minecraft **1.21.1**).

This guide covers all basic top-level fields for configuring conversion rules, whether you are using the in-game GUI editor or editing JSON files manually.

---

## Field Overview

| Field | Description | Range / Type | Default | Changes Behavior? |
| --- | --- | --- | --- | --- |
| `id` | Unique rule identifier | `namespace:path` string | Inferred from path for single-rule files | Yes (identifies overrides) |
| `display_name` | Human-readable name for GUI and commands | String (≤ 128 characters) | Auto-generated summary if omitted | No (visual only) |
| `notes` | Personal notes and documentation | String (supports newlines) | None | No (visual only) |
| `enabled` | Whether the rule is enabled by default | `true` / `false` | `true` | Yes |
| `priority` | Priority when multiple rules match the same item | Integer (positive, zero, or negative) | `0` | Yes (higher numbers run first) |
| `source` | Which dropped items this rule targets | Object with non-empty `items` | **Required** | Yes |
| `triggers` | Despawn trigger kinds to listen for | `natural`, `fire`, `lava`, `cactus` | `["natural"]` | Yes |
| `trigger_after_seconds` | Wait duration before natural despawn triggers | Non-negative integer (seconds) | `300` | Yes |
| `schema_version` | Rule schema format version | Currently integer `1` | Missing means fixed baseline `1` | Yes |

*Note: For output products and item costs (`effects`, `outcomes`, `source_cost`, `combination`), see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).*

Loading prepares supported rule formats as current documents. New saves and exports explicitly include `schema_version`. Datapacks are converted in memory; config overlay files that pass preparation are backed up and upgraded during server initialization. Invalid versions or failed conversions appear in the issue list.

---

## Key Fields Explained

### 1. id: Unique Rule Identity

- **Purpose**: Identifies the rule across commands, GUI editors, and error logs. When two sources provide rules with the same ID, the higher priority source **completely replaces** the lower one (see [Source Layers and Overrides](en-06-layers-and-overrides)).
- **Format**: `namespace:path`, e.g. `myrules:chicken_to_rotten_flesh`. Uses only lowercase letters, numbers, underscores, dots, and hyphens.
- **Auto-inference**: When a `.json` file contains only one rule, you can omit the `id` field; it will be inferred from the file path relative to the rules directory.

### 2. display_name and notes: Display and Documentation

- **`display_name`**: A readable label shown in the in-game editor and command outputs, e.g. `"Chicken Corruption"`.
- **`notes`**: Arbitrary notes for yourself or other pack developers. Does not affect gameplay.

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "display_name": "Chicken Corruption",
  "notes": "Dropped raw chicken turns into rotten flesh after 10 seconds.",
  "source": { "items": ["minecraft:chicken"] }
}
```

### 3. enabled: Default Enabled State

- **Purpose**: Sets whether the rule is active by default.
- **Values**: `true` (active) or `false` (loaded and visible in list, but does not match items).
- **Per-save overrides**: Admins can toggle individual rules in-game using `/idtw rule enable|disable <id>`. Save-level overrides take precedence over the file's `enabled` field.

### 4. source: Matching Dropped Items

- **Purpose**: Specifies which dropped item entities match the rule.
- **Format**: Contains `items` (required array) and optional `exclude` (array). Both accept item IDs or `#` tags, up to 256 entries total.

| Example | Meaning |
| --- | --- |
| `"minecraft:oak_sapling"` | Matches this specific item only |
| `"#minecraft:saplings"` | Matches **any sapling** in the tag (including modded saplings) |

#### Using `exclude` for Exceptions

`exclude` takes precedence over `items`. Use it to carve out exceptions from a tag:

```json
{
  "id": "myrules:saplings_plant",
  "source": {
    "items": ["#minecraft:saplings"],
    "exclude": ["minecraft:oak_sapling"]
  }
}
```
*Matches all saplings, except oak saplings.*

### 5. priority: Deciding Which Rule Runs First

- **Purpose**: When an item matches multiple valid rules, the rule with the **highest priority value runs first**.
- **Values**: Any integer (e.g. `10`, `0`, `-5`), default is `0`.
- **Usage**: Give high priority (e.g. `10`) to specific or restricted rules, `0` to normal rules, and negative numbers (e.g. `-10`) to fallback rules.

### 6. triggers: When Conversions Fire

- **Purpose**: Defines which despawn or destruction events trigger this rule.
- **Values**: Array containing `natural`, `fire`, `lava`, or `cactus`. Defaults to `["natural"]`.

| Trigger Kind | How It Fires | Waits for Timer? |
| --- | --- | --- |
| `natural` | Fires when item age reaches `trigger_after_seconds` or natural lifetime | **Waits** for the timer |
| `fire` | Fires the exact tick the item burns in fire | **Fires instantly**, no timer |
| `lava` | Fires the exact tick the item is consumed by lava | **Fires instantly**, no timer |
| `cactus` | Fires the exact tick the item touches cactus | **Fires instantly**, no timer |

```json
{
  "id": "myrules:arrow_in_fire",
  "source": { "items": ["minecraft:arrow"] },
  "triggers": ["fire", "lava"]
}
```
*Arrows dropped on the ground do not transform when expiring naturally; but when burned by fire or lava, the rule triggers immediately on destruction.*

### 7. trigger_after_seconds: Natural Despawn Timer

- **Purpose**: How many seconds a dropped item must exist before natural conversion triggers.
- **Values**: Non-negative integer in seconds, default `300` (5 minutes, matching vanilla dropped item lifetime).
- **Notes**:
  - Setting `10` waits 10 seconds after dropping.
  - Setting `0` tries to trigger immediately upon entering the world.
  - If set longer than the item's natural lifetime, it fires right before the item expires.

---

## Complete Example

Here is an example using common basic fields:

```json
{
  "id": "myrules:sapling_planting",
  "display_name": "Automatic Sapling Planting",
  "notes": "All saplings except oak will plant themselves after 60 seconds or when destroyed by fire.",
  "enabled": true,
  "priority": 5,
  "schema_version": 1,
  "source": {
    "items": ["#minecraft:saplings"],
    "exclude": ["minecraft:oak_sapling"]
  },
  "triggers": ["natural", "fire"],
  "trigger_after_seconds": 60,
  "effects": [
    {
      "type": "idtw:place_block",
      "use_source_block": true,
      "shape": "square",
      "count": 1,
      "radius": 1
    }
  ]
}
```

---

## Validation and Avoiding Mistakes

Run `/idtw rule validate` at any time to verify your rules:

| Common Mistake | Result | Solution |
| --- | --- | --- |
| Uppercase letters or spaces in `id` | Rule rejected (invalid ID) | Use lowercase letters, numbers, and underscores only |
| Typo in item ID | Rule rejected (unknown source item) | Verify the registry name and namespace |
| Missing `#` on a tag | Treated as an ordinary item and rejected | Prefix tags with `#`, e.g. `"#minecraft:logs"` |
| String `"true"` for `enabled` | Rule rejected (type mismatch) | Use boolean `true` or `false` without quotes |
| Negative `trigger_after_seconds` | Rule rejected | Use `0` or positive integers |

---

## Next Steps

- [Conditions](en-03-conditions): Filter rules by weather, time, biomes, or height.
- [Effects](en-04-effects): Configure items, mobs, blocks, lightning, or explosions.
- [Outcome Candidates and Settlement](en-05-outcomes-and-settlement): Multiple outcomes, weights, and costs.
