# Conditions

This page explains how to write the `conditions` field and when the mod checks it.

For the other rule fields, see [Rule Fields](en-02-rule-basics.md). For effects and material costs, see [Effects](en-04-effects.md) and [Outcome Candidates and Settlement](en-05-outcomes-and-settlement.md).

## What Conditions Do

- A condition only decides whether it is satisfied. It consumes no items and no fluids; the consuming effects take the materials.
- Leave out the whole `conditions` field to run a rule with no conditions. The check then passes at any time.
- The rule is selected and converts only when the result is satisfied. An unsatisfied, unavailable, or error result never triggers it.
- A condition that stays unsatisfied is not an error. It only means the rule rarely fires.
- A malformed condition is a separate matter. A broken structure or an out-of-range value rejects the whole rule, and the load log and `/idtw config validate` report why.

## The Four Node Types

A **condition tree**, the value of the `conditions` field, has exactly one root node. Every node is a JSON object, and `op` says which type it is:

| `op` | Meaning | Child field | Fields allowed here |
|---|---|---|---|
| `all_of` | Matches when every child matches (AND) | `terms` | `op`, `terms` |
| `any_of` | Matches when any child matches (OR) | `terms` | `op`, `terms` |
| `inverted` | Swaps satisfied and unsatisfied (NOT) | `term` | `op`, `term` |
| `leaf` | One concrete condition | `condition` | `op`, `condition` |

Three things that are easy to get wrong:

- `terms` is an array, used by `all_of` and `any_of`. `term` is a single node, used by `inverted`. `condition` is a **condition object**, used by `leaf`. The wrong field rejects the whole rule.
- A condition object is flat. `type` and the parameters of that type sit in the same object, with no extra layer around them.
- A node accepts only the fields listed above, so one extra field, such as `terms` inside a `leaf`, is rejected. The `terms` array of `all_of` and `any_of` cannot be empty. To run without conditions, omit the `conditions` field instead.

### Condition Leaf (leaf)

The `condition` of a `leaf` is an object built from `type` and the parameters of that type. Every `type` carries the namespace prefix `itemdespawntowhat:`:

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:dimension",
    "dimensions": ["minecraft:overworld"]
  }
}
```

### Combining Nodes (all_of, any_of, inverted)

```json
{
  "op": "all_of",
  "terms": [
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:outdoor" } },
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "itemdespawntowhat:weather", "weather": "thunder" }
      }
    }
  ]
}
```

This example means "outdoors, and not a thunderstorm".

### Put the Tree in a Rule

`conditions` is one top-level rule field, and its value is the tree above. A rule has exactly one root node. The example lists only the condition-related fields; see [Rule Fields](en-02-rule-basics.md) for the required fields it leaves out:

```json
{
  "id": "example:only_in_forest_at_night",
  "source": { "items": ["minecraft:apple"] },
  "conditions": {
    "op": "all_of",
    "terms": [
      {
        "op": "leaf",
        "condition": {
          "type": "itemdespawntowhat:biome",
          "mode": "exact",
          "biomes": ["#minecraft:is_forest"]
        }
      },
      {
        "op": "leaf",
        "condition": { "type": "itemdespawntowhat:time_of_day", "from": 13000, "to": 23000 }
      }
    ]
  },
  "trigger_after_seconds": 300
}
```

What happens after you set this up: the apple converts when the rule comes due only if it sits in a forest biome and the world time is at night, from 13000 to 23000 ticks. Otherwise the apple stays where it is and despawns the vanilla way once its time runs out.

To run without conditions, delete the whole `conditions` line. Do not write `"conditions": {}` or `"conditions": []`.

### Old Formats Are Gone

Every format below is reported as a clear error rather than accepted silently. If you hit one, rewrite it as the right column shows:

| Old format | Write this instead |
|---|---|
| `conditions` is a plain JSON array, `[ {...} ]` | Wrap the entries in an `all_of` or `any_of` node |
| A `groups` block under `conditions` | Wrap the entries in an `all_of` or `any_of` node |
| `negated` inside a condition leaf | Use `{ "op": "inverted", "term": ... }` |
| A condition object inlined directly under `conditions`, with `type` at that level | Wrap it in `{ "op": "leaf", "condition": { ... } }` |

## Tree Limits

| Item | Limit | How it is counted |
|---|---|---|
| Condition leaves | 128 | Each `leaf` node counts as 1 |
| Condition nodes | 256 | `all_of`, `any_of`, `inverted`, and `leaf` all count |
| Tree depth | 16 | The root sits at depth 1, and each step down adds 1 |

Exceeding any of these rejects the whole rule, so that rule does not run. The load log and `/idtw config validate` report that the leaf count, node count, or depth is over the limit, and the other rules in the same file still load. An empty `terms` array in `all_of` or `any_of`, or an `inverted` node with no `term`, is a structure error as well, and the rule is rejected.

What this means in practice: flattening hundreds of conditions into one `all_of` layer goes over the leaf limit, and nesting deeper than 16 levels, such as an AND inside an AND, is rejected outright. Restructure such a tree to be flatter.

## How a Tree Is Judged

A condition tree returns one of four results, not a simple yes or no:

| Result | Meaning | What happens |
|---|---|---|
| Satisfied | The condition is true | The rule takes part in selection and converts once selected |
| Unsatisfied | The condition is false | The mod does not convert the rule this time and retries at a later check |
| Unavailable | The mod cannot decide yet, for example when the nearby chunks are not loaded | The mod does not convert the rule this time and retries at a later check, and the result never counts as satisfied |
| Error | Something went wrong while judging | The rule is skipped, and the log records the error |

Only a satisfied result triggers a conversion. That matters for `inverted` too:

- `inverted` swaps satisfied and unsatisfied only. Unavailable and error pass through unchanged.
- Inverting a condition you cannot judge yet therefore never turns it into a satisfied result, and never wastes materials.
- Combining nodes settle this way. `all_of` returns unsatisfied as soon as a child is unsatisfied, and `any_of` returns satisfied as soon as a child is satisfied. In every other case unavailable outranks error, and neither makes the rule convert.

The typical case where you see unavailable is `surrounding_blocks`. When the neighboring chunks are not loaded, the mod cannot decide. The next check after those chunks load gives a real answer.

### An Unsatisfied Condition Retries Automatically

When a condition is unsatisfied, the mod does not drop the rule. It schedules another check later:

- The backoff starts at the check interval and doubles on every failure until it reaches the ceiling. The default check interval is 20 ticks and the ceiling is 100 ticks, so 1 second, then 2, then 4, then at most 5. Two server settings control these numbers: `check_interval_ticks` and `backoff_max_ticks`. See [Server Configuration](en-07-server-config.md).
- Retries continue until shortly before the drop would despawn on its own. If no rule has converted the drop by then, it despawns the vanilla way and never converts.

A condition with a window, such as "only at night", is a normal way to write a rule. The drop converts when the window opens, so you do not need to shrink `trigger_after_seconds`. The stricter the condition, the longer the wait before the condition is satisfied and the greater the chance that the drop is picked up or despawns early. That is a trade-off, not a fault.

A rule whose condition can never be satisfied gives no warning at all: the item despawns as usual. To check your work, run `/idtw config validate` to test the syntax and the parameter values, or use `/idtw config edit` to open the rule in the editor.

## Condition Types at a Glance

Every `type` carries the namespace prefix `itemdespawntowhat:`. Reference parameters, meaning items, blocks, fluids, and biomes, accept the `#` tag form except in `dimension`, for example `"#minecraft:logs"`.

| `type` | Name | What It Does | Parameters |
|---|---|---|---|
| `itemdespawntowhat:dimension` | Dimension | The drop is in one of the listed dimensions | `dimensions` |
| `itemdespawntowhat:biome` | Biome | The biome matches, or the climate parameters fall inside the ranges | `mode`, `biomes`, six climate ranges |
| `itemdespawntowhat:time_of_day` | Time of day | The world time falls inside a range | `from`, `to` |
| `itemdespawntowhat:weather` | Weather | The current weather equals the given value | `weather` |
| `itemdespawntowhat:light_level` | Light level | The light level at the position falls inside a range | `min`, `max` |
| `itemdespawntowhat:y_level` | Y level | The height falls inside a range | `min`, `max` |
| `itemdespawntowhat:outdoor` | Outdoor | Nothing blocks the sky above the drop | None |
| `itemdespawntowhat:surrounding_blocks` | Surrounding blocks | The six neighboring blocks match | `up`, `down`, `north`, `south`, `east`, `west` |
| `itemdespawntowhat:fluid_present` | Fluid present | The position contains the named fluid | `fluid`, `fluids`, `require_source` |
| `itemdespawntowhat:catalyst_present` | Catalyst present | The drop's block cell contains enough catalyst | `items`, `count`, `counts` |

## Condition Types in Detail

### dimension (Dimension)

The condition matches when the drop's dimension is any entry in `dimensions`.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `dimensions` | Dimension list | Array of dimension IDs, at least 1 entry | Required |

Only dimension IDs are accepted. The `#` tag form is not supported.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:dimension",
    "dimensions": ["minecraft:overworld", "minecraft:the_end"]
  }
}
```

Typical uses: keep a rule in the Nether, or wrap it in `inverted` to mean "not the Overworld".

### biome (Biome)

The condition matches the biome at the drop's block position, and `mode` picks how.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `mode` | Match mode | `exact` or `climate` | Required |
| `biomes` | Biome reference list | Biome IDs or `#` tags, at least 1 entry when `mode=exact` | Empty |
| `temperature` | Temperature range | `{"min": number, "max": number}`, values from -1 to 1 | No limit on this parameter |
| `humidity` | Humidity range | Same as above | No limit on this parameter |
| `continentalness` | Continentalness range | Same as above | No limit on this parameter |
| `erosion` | Erosion range | Same as above | No limit on this parameter |
| `depth` | Depth range | Same as above | No limit on this parameter |
| `weirdness` | Weirdness range | Same as above | No limit on this parameter |

- `exact` compares the biome ID at that position and matches when any entry matches. A tag such as `#minecraft:is_forest` covers several biomes at once.
- `climate` reads the vanilla climate parameters at that position and matches only when every parameter you wrote falls inside its range. At least one parameter must be bounded on one side, because a range that is empty at both ends, such as `{ "temperature": {} }`, counts as unwritten.
- Either end of a range may be left out. `min` alone sets a floor, and `max` alone sets a ceiling.
- When `mode=exact` also carries climate ranges, or `mode=climate` also carries `biomes`, the mod ignores the extra content and logs a warning. It is not an error, and it has no effect.
- A place that is not a multi-noise biome source, such as the End, has no climate parameters in `climate` mode. The result is unsatisfied, not unavailable.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_forest"]
  }
}
```

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:biome",
    "mode": "climate",
    "temperature": { "min": 0.2 },
    "humidity": { "max": -0.2 }
  }
}
```

Typical uses: `exact` for "only these biomes", and `climate` for "only this kind of climate", such as a hot and dry place, without listing biomes one by one.

### time_of_day (Time of day)

The condition matches a range of world time. One day is 24000 ticks.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `from` | Start tick | Integer from 0 to 23999 | Required |
| `to` | End tick | Integer from 0 to 23999 | Required |

- When `from` is not greater than `to`, the range is a plain interval that includes both ends. When `from` is greater than `to`, the range wraps past midnight, as in 22000 to 2000. That is not an error.
- For reference: tick 0 is about 6:00 in the morning, tick 6000 is noon, tick 12000 is evening, night starts at tick 13000, and tick 18000 is midnight.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:time_of_day",
    "from": 13000,
    "to": 23000
  }
}
```

Typical uses: convert only at night, or use 22000 to 2000 for the small hours.

### weather (Weather)

The condition matches the current weather in the drop's dimension.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `weather` | Target weather | `clear`, `rain`, or `thunder` | Required |

What each value means:

- `clear`: it is neither raining nor thundering.
- `rain`: it is raining, and it is not a thunderstorm.
- `thunder`: it is thundering, which usually means it is raining as well.

A thunderstorm therefore never matches `rain`. To accept both rain and thunder, list `rain` and `thunder` side by side under `any_of`.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:weather",
    "weather": "thunder"
  }
}
```

Typical uses: conversions that happen only in a thunderstorm, often paired with `outdoor` so that a drop inside a building does not count as rained on.

### light_level (Light level)

The condition matches a range of the light level, from 0 to 15, at the drop's position.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `min` | Lower bound | Integer from 0 to 15 | No limit |
| `max` | Upper bound | Integer from 0 to 15 | No limit |

Light level here means the larger of the time-adjusted sky light and the block light, the same value that entities and mob spawning use. An open spot is bright by day and darker at night in the same place. `min` cannot be greater than `max`.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:light_level",
    "max": 7
  }
}
```

Typical uses: convert only in dim light, with `max` around 7. Paired with `outdoor`, it separates an open spot in daylight from a lit spot in a cave.

### y_level (Y level)

The condition matches a range of the Y coordinate of the drop's block.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `min` | Lower bound | Integer, -2048 or more | No limit |
| `max` | Upper bound | Integer, -2048 or more | No limit |

The height parameters have no upper bound, so values above 512 or 2048 are allowed for mods that extend world height. The parameters still use 32-bit signed integers. `min` cannot be greater than `max`.

With both ends omitted the condition is always satisfied, which is legal but pointless. One end alone means at or below, or at or above, that height.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:y_level",
    "max": 0
  }
}
```

Typical uses: conversions underground, with `max` at 0 or lower, or a special rule that fires only up high, with `min` at 100.

### outdoor (Outdoor)

The condition decides whether the top of the drop is open to the sky. It has no parameters.

To decide, the mod reads the highest "motion blocking, no leaves" heightmap surface in that column. The condition matches when that surface is no higher than the item's height plus 1. A spot under a tree can therefore still count as outdoors, while a spot under a roof or in a cave does not.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:outdoor"
  }
}
```

Typical uses: convert outdoors only. Wrap the condition in `inverted` when the rule should fire indoors instead.

### surrounding_blocks (Surrounding blocks)

The condition matches the six blocks next to the drop: up, down, north, south, east, and west.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `up` | Block above | Block ID or `#` tag | No limit on this direction |
| `down` | Block below | Same as above | No limit on this direction |
| `north` | Block to the north | Same as above | No limit on this direction |
| `south` | Block to the south | Same as above | No limit on this direction |
| `east` | Block to the east | Same as above | No limit on this direction |
| `west` | Block to the west | Same as above | No limit on this direction |

- The mod checks only the directions you fill in, and every direction you fill in must match. The directions are ANDed.
- Leaving all six directions out is invalid and rejects the rule.
- When a neighboring chunk is not loaded, the mod cannot decide. The result is unavailable, not unsatisfied, and a retry after those chunks load gives an answer. This matters most with `inverted`: a condition you cannot judge stays undecided, and inverting it never turns it into a satisfied result.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:surrounding_blocks",
    "down": "#minecraft:logs"
  }
}
```

Typical uses: a drop on top of a log, a drop packed in stone, or a drop against a certain kind of block.

### fluid_present (Fluid present)

The condition looks at the fluid at the drop's block position.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `fluid` | One fluid reference | Fluid ID or `#` tag | Empty |
| `fluids` | Fluid reference list | Fluid IDs or `#` tags; any entry matching is enough | Empty |
| `require_source` | Whether only fluid source blocks count | `true` or `false` | `true` |

- Leaving out both `fluid` and `fluids` means any fluid, but the position must still contain a fluid or the condition is unsatisfied. When you write both, `fluids` wins.
- `require_source` defaults to `true`, which matches fluid source blocks only. Set it to `false` to match flowing fluid as well. With that setting, `minecraft:water` also matches flowing water, which is the flowing variant of the same fluid.
- Writing `minecraft:empty` matches no real fluid and logs a warning when the rule loads.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:fluid_present",
    "fluid": "minecraft:water",
    "require_source": false
  }
}
```

Typical uses: rules that convert while the drop sits in water. Combined with a catalyst condition, this builds recipes such as water plus bone meal. The built-in rule `itemdespawntowhat:builtin_catalyst_and_fluid` uses this combination.

### catalyst_present (Catalyst present)

The condition decides whether the drop's own block cell contains enough of the named **catalyst** items. It is an on-site check, and it consumes nothing.

| Field | What It Does | Value | Default |
|---|---|---|---|
| `items` | Catalyst reference list | Item IDs or `#` tags, at least 1 entry | Required |
| `count` | Count threshold | Integer from 1 to 64 | When left out, follows what the rule consumes of that catalyst, and 1 when no matching consumption is configured |
| `counts` | Threshold per reference | Object whose keys must appear in `items`, with values from 1 to 64 | Empty |

- Every entry in `items` must reach its own threshold.
- The mod counts inside the 1×1×1 cell of the drop's block. It excludes the drop itself and excludes dead entities, and it adds up several stacks of the same item in that cell.
- Leaving `count` out is the recommended form. It follows the amount of that catalyst the rule consumes, taken from the rule-level `catalyst_cost` or the effect-level `consume_catalyst`, and falls back to 1 only when neither exists. Leaving it out never writes back to the file, so your own declaration is left alone.
- A key in `counts` that does not appear in `items` is an error.

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:catalyst_present",
    "items": ["minecraft:bone_meal"],
    "count": 1
  }
}
```

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:catalyst_present",
    "items": ["minecraft:redstone", "minecraft:glowstone_dust"],
    "counts": { "minecraft:redstone": 4, "minecraft:glowstone_dust": 1 }
  }
}
```

Typical uses: recipe-style rules that need the materials on site. The condition only decides; consuming the catalyst needs a matching consuming effect, as described in [Effects](en-04-effects.md).

## Common Combinations

Each block below gives only the value of `conditions`. Paste it straight into the rule's `conditions` field.

Night or rain, whichever matches. A thunderstorm does not count as `rain`, so list `thunder` as well:

```json
{
  "op": "any_of",
  "terms": [
    {
      "op": "leaf",
      "condition": { "type": "itemdespawntowhat:time_of_day", "from": 13000, "to": 23000 }
    },
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:weather", "weather": "rain" } },
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:weather", "weather": "thunder" } }
  ]
}
```

Certain biomes only, covering a whole group at once:

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_taiga", "minecraft:swamp"]
  }
}
```

Below a given height and in the dark:

```json
{
  "op": "all_of",
  "terms": [
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:y_level", "max": 20 } },
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:light_level", "max": 7 } }
  ]
}
```

Not in the Overworld, and indoors, which shows how to use inverting nodes:

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "itemdespawntowhat:dimension", "dimensions": ["minecraft:overworld"] }
      }
    },
    { "op": "inverted", "term": { "op": "leaf", "condition": { "type": "itemdespawntowhat:outdoor" } } }
  ]
}
```

Flowing water with bone meal nearby. The `conditions` field of the built-in rule `itemdespawntowhat:builtin_catalyst_and_fluid` is written exactly this way, so you can copy it:

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "leaf",
      "condition": {
        "type": "itemdespawntowhat:fluid_present",
        "fluid": "minecraft:water",
        "require_source": false
      }
    },
    {
      "op": "leaf",
      "condition": {
        "type": "itemdespawntowhat:catalyst_present",
        "items": ["minecraft:bone_meal"],
        "count": 1
      }
    }
  ]
}
```

Logs on all six sides, where every direction you fill in must match:

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:surrounding_blocks",
    "up": "#minecraft:logs",
    "down": "#minecraft:logs",
    "north": "#minecraft:logs",
    "south": "#minecraft:logs",
    "east": "#minecraft:logs",
    "west": "#minecraft:logs"
  }
}
```

## Related

- Rule fields and source entries: [Rule Fields](en-02-rule-basics.md)
- Effects and material costs: [Effects](en-04-effects.md)
- Conversion results and the return path: [Outcome Candidates and Settlement](en-05-outcomes-and-settlement.md)
