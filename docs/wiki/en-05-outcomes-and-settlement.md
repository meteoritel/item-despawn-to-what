# Outcome Candidates and Settlement

This page explains what a rule produces, what each conversion group pays, and what happens to a whole stack of dropped items from settlement through output and return.

An **outcome candidate** (an element of `outcomes`) is one possible output a conversion can pick. A **conversion group** is one unit of settlement: it picks one outcome candidate and pays the cost of that group.

Effect parameters, such as what `spawn_entity` produces or how `place_block` places blocks, are on [Effects](en-04-effects). Nearby product limits and per-tick check counts are server parameters on [Server Configuration](en-07-server-config). Whether a conversion starts at all depends on conditions, see [Conditions](en-03-conditions).

## Declaring Output: Effects or Outcomes

A rule must answer what it converts into. There are two ways to write that answer:

| Form | What it does | Number of outcomes |
| --- | --- | --- |
| `effects` only | The rule has one outcome: the mod reads it as an implicit candidate whose `id` is `default`. | 1 |
| `outcomes` | The rule lists several outcome candidates, and every conversion group picks one by `combination`. | 1 or more |

- Neither field: the rule is rejected, because a rule needs at least one outcome.
- Both fields: the rule is rejected, because only one of them is allowed. To turn one outcome into several, move the content of `effects` into `outcomes` as the `effects` of one candidate.
- With `effects` only you do not write a candidate `id`: the implicit candidate always uses `default`. With `outcomes`, every candidate needs its own `id`.
- The `effects` array of a candidate cannot be empty, and several effects inside one candidate run together in the same conversion group.
- A rule can carry at most 32 outcome candidates, a limit it shares with effects. A rule above the limit is rejected.

### Outcome Candidate Fields

| Field | What it does | Values | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | The name of this candidate | A non-empty string, unique inside one rule | — (required) | A candidate built from `effects` uses `default` |
| `effects` | The effects this candidate runs | An array of effects | — (required, cannot be empty) | Effect types and parameters are on [Effects](en-04-effects) |
| `safe_spawn` | Whether to look for a safe spot before spawning a mob | `true` / `false` | `false` | Affects only the `entity` variant of `spawn_entity` |
| `fill_origin` | Whether `place_block` may fill the trigger position itself | `true` / `false` | `true` | Affects only `place_block` |

An outcome candidate accepts only these four fields. One extra field rejects the rule.

### Safe Spawning (safe_spawn)

- `false` (the default): the mod does not look for a spot and places the mob at the position of the dropped item, with a small random offset on the horizontal axes.
- `true`: the mod scans a box centered on the trigger position, 2 blocks to each horizontal side and 2 blocks up and down (5×5×5, 125 positions), from the closest position to the farthest, and takes the first position where a mob can stand: inside the world border, allowed by the vanilla spawn rules of that mob, no block inside the spawn collision box, and no fire, lava, or cactus at the feet or the head. The mob appears in the center of that block.
- When no position is safe, the mod falls back to spawning near the trigger position with the same small random offset. A missing safe spot never cancels the spawn.
- This switch works only for spawning mobs. Spawning items or experience ignores it.
- The search runs across several ticks: the mod checks a limited number of positions per tick, a number the server configuration controls, and continues on the next tick when a batch is unfinished. A mob can therefore appear slightly later than an item.

### Filling the Origin Block (fill_origin)

- `true` (the default): the fill candidates of `place_block` include the block at the trigger position.
- `false`: the mod removes the trigger position itself from the fill candidates and leaves the other positions and their order unchanged. Use it when you do not want a block to land exactly where the dropped item was.
- This switch works only for `place_block`, and different candidates of one rule can set it differently.

### A Two-Outcome Example

Save this file as `config/itemdespawntowhat/rules/myrules/chicken_two_forms.json`:

```json
{
  "id": "myrules:chicken_two_forms",
  "source": {
    "items": [
      "minecraft:chicken"
    ]
  },
  "trigger_after_seconds": 300,
  "source_cost": {
    "minecraft:chicken": 1
  },
  "combination": "round_robin",
  "outcomes": [
    {
      "id": "rotten_flesh",
      "effects": [
        {
          "type": "itemdespawntowhat:spawn_entity",
          "variant": "item",
          "item": "minecraft:rotten_flesh",
          "count": 1
        }
      ]
    },
    {
      "id": "chick",
      "effects": [
        {
          "type": "itemdespawntowhat:spawn_entity",
          "variant": "entity",
          "entity": "minecraft:chicken",
          "age": -24000,
          "count": 1
        }
      ],
      "safe_spawn": true
    }
  ],
  "schema_version": 1
}
```

What happens after you load it: a chicken either turns into rotten flesh or hatches a baby chicken, where `age` set to `-24000` means a baby. The hatched chicken does not get stuck in a wall, because the second candidate sets `safe_spawn`.

## Combination Modes (combination)

| Value | What it does | Default |
| --- | --- | --- |
| `round_robin` | Round robin: this group uses one candidate, and the next group uses the next one. | Default |
| `priority` | First available in order: every conversion group walks from the first candidate in definition order and uses the first one that can still open a group. | — |

The value is case-insensitive: `round_robin` and `ROUND_ROBIN` both work. Any other value rejects the rule. When a rule has only one candidate, this field has no effect.

### Round Robin: How the Turn Order Is Remembered

- The mod does not store the turn position on the dropped item. It stores the position per rule id and dimension in the world save, so every dropped item of one rule in one dimension shares the same position.
- Every group that opens moves the position one step forward. A whole stack that opens 3 groups therefore moves through 3 candidates in round robin order.
- The position is saved with the world: it continues after a server restart or a trip out of the world and back, and each dimension keeps its own position.
- The first candidate in definition order is the starting point. The position restarts from the first candidate when the candidate structure changes, such as a changed candidate `id`, a changed `safe_spawn` or `fill_origin`, or changed effect types and parameters.
- Round robin picks only candidates that can still open a group. A candidate whose matching products already reached the nearby limit is skipped, so the next pick is not always the next one in definition order.

### Priority: Candidates in a Fixed Order

- Every conversion group walks the candidates in definition order and uses the first one that can open a group.
- As long as an earlier candidate can open a group, a later one is never used. Writing `priority` orders the candidates and treats the later ones as fallbacks for the moment an earlier one stops working.

### Which Outcome One Drop Gets Each Time

The example above uses `source_cost` of 1 chicken per group. Assume this rule has never run a round robin pass yet:

- You drop 1 chicken: the first group opens with `rotten_flesh`, so you get rotten flesh, and the turn position moves to `chick`.
- You drop 1 chicken again: the group starts at `chick` and hatches a baby chicken, and the position moves back to `rotten_flesh`.
- You drop 5 chickens in one stack: the stack splits into 5 groups and yields rotten flesh, chicken, rotten flesh, chicken, rotten flesh in that order.
- Change `combination` to `priority`: every group yields rotten flesh, and the chicken candidate is used only when the rotten flesh path cannot open a group, for example because its products reached the limit.

## What Each Group Pays: Fixed Costs and Consumption

`source_cost` is both the source threshold and payment for each group, defaulting to 1. Catalyst `counts` are thresholds and `consume_counts` are independent payments. Rule-level `consume_fluid` enables fluid source consumption. Inputs are checked and paid before each group starts.

### Source Cost (source_cost)

Write an object that maps a source item reference to a positive integer. The number is how many items each group costs:

```json
{
  "source_cost": { "minecraft:chicken": 1 }
}
```

```json
{
  "source_cost": { "#minecraft:saplings": 1 }
}
```

- Every key must match an entry you wrote in `source.items`, and it must be written the same way: an item where you wrote an item, a `#tag` where you wrote a `#tag`. The number must be a positive integer. A rule that breaks either condition is rejected.
- You can list several entries, for example `{ "minecraft:egg": 2, "#minecraft:saplings": 1 }`. The current stack decides which entry applies: the mod first looks for an entry that names the item exactly, then walks `source.items` in definition order for the first matching `#tag` entry, and consumes 1 when nothing matches.
- The meaning is per group and does not depend on the stack size: a value of 2 opens one group per 2 source items, and a stack of 5 gives 2 groups and returns the remaining item.

### Catalyst Thresholds and Payments (catalyst_cost)

| Field | Meaning | Default | Range |
| --- | --- | --- | --- |
| `items` | Item or tag references that must each qualify | Required, nonempty | Array |
| `counts` | Threshold per reference | 1 per missing reference | Object, values 1–64 |
| `consume_counts` | Payment per reference per group | 0 per missing reference: detection only | Object, values 0–64 |
| `radius` | Shared detection and payment radius | 1 | 1–8 blocks |

Quantity keys must belong to `items`. Every group checks all thresholds before paying the full consumption amounts. Overlapping item and tag references share inventory allocation. The source entity is excluded. Insufficient inputs stop later groups and return unused source items.

```json
{
  "catalyst_cost": {
    "items": ["minecraft:bone_meal"],
    "counts": {"minecraft:bone_meal": 5},
    "consume_counts": {"minecraft:bone_meal": 2},
    "radius": 3
  }
}
```

With 7 catalysts, threshold 5 and payment 2, two groups can start: 7→5→3. The third group fails its threshold. Enabling consumption in the input page copies each threshold into a second slider row; the two values can then be edited independently. The shared radius is at the bottom of the card.

### Fluid Source Consumption (consume_fluid)

Rule-level `"consume_fluid": true` consumes one actual fluid source per group; the default is `false`. Presence conditions accept both source and flowing fluid. Consumption starts at the contacted fluid and traces increasing upstream levels, including upward waterfall paths, choosing the nearest corresponding source. Only loaded chunks are read, with at most 512 visited nodes and 64 steps per path. Missing sources or changed paths stop the group and return unused source items. Waterlogged blocks retain their block body.

### Default Source Payment

Without `source_cost`, each group's source threshold and payment are 1. Catalyst and fluid settings leave this default at 1.

## Settlement and Return for the Whole Stack

When a rule picks up a dropped item, the whole stack leaves the world and enters settlement. The mod then works through these steps:

1. **Count the groups**: the number of groups is the stack size divided by the source cost per group, rounded down. The **nearby product limits** (`nearby_products`) can lower that number: the mod looks at the items and generic entities this candidate produces and keeps the smaller of the two counts. Experience output takes no part in that count. A candidate that holds only one-shot effects always counts as one group.
2. **Open groups one by one**: each conversion group first pays the catalyst cost, when the rule sets `catalyst_cost`, then loses its source items, and then runs every effect of the picked candidate. When any step cannot be paid, that group does not open.
3. **Return what is left**: the unused part of the stack comes back to the world as dropped items near the trigger position. When no position is free nearby, the mod searches upward and, in the last resort, spawns the items above the build limit so that they fall back down.
4. **A returned item never converts again**: a returned source item carries permanent protection, so it neither converts again nor splits the stack once more. This keeps the mod out of a loop of return, convert, and return.

About counting only real output:

- The output ledger records only what actually took effect. When an effect in a group produces nothing because its chance missed, its own conditions did not match, or it failed, the mod does not count that group as successful output: a planned amount and a successful amount are two different numbers.
- A cost that was consumed stays consumed even when the output fails. A missed chance still consumes the source items of that group, so a retry is not free.
- The other way around, a cost for a group that never opened never happened at all: those source items stay in the stack and come back as returned items.

About group counts and effect runs:

- Every effect runs once in every conversion group, and `count` is the amount it produces in that one run. A stack that opens several groups runs the effects several times.
- One-shot effects, meaning `arrow_rain`, `lightning`, `explosion`, and `weather`, are the exception: they run once per source and do not repeat with the group count, and they take no part in the nearby product limit calculation.

About interruption and recovery:

- Leaving the world, stopping the server, or unloading the target dimension never makes the undelivered part vanish. Consumed costs stay on record, and the mod delivers the source items that still owe a return once that dimension loads again. It prefers the original trigger position, and an older save without position data falls back to the area around the spawn point of that dimension.
- A returned item that cannot be delivered yet, because chunks are still loading, becomes a **pending return**. The mod delivers it once that dimension loads again.
- The recovery step does one thing only: it hands back the source items that owe a return. It does not run the rule again and does not re-run effects that were already dispatched. Output that was dispatched but has not reported back never counts as a source item that owes a return, so source items and output never pay for each other.

## Protection and Conversion Ban for Products

- A freshly produced dropped item is a **new product**, and it receives two temporary states: **environmental damage protection** (`new_product_protection_seconds`, 2 seconds by default) and a **conversion cooldown** (`conversion_cooldown_seconds`, 5 seconds by default).
- During protection the item ignores environmental damage from fire, lava, and cactus. During the cooldown the mod never picks it for conversion again; once the cooldown ends it can take part again.
- When both times are set to 0, the mod writes no state at all and the new product behaves like any other dropped item.
- Returned items receive the permanent version instead: permanent environmental damage protection and a permanent conversion ban. Such a dropped item never converts again for the rest of its life.
- These states live on the dropped item entity, not on the item itself. Pick a new product up and drop it again, and you get a fresh dropped item without protection or cooldown that can convert again.

## When a Conversion Seems Not to Happen

Check in this order:

1. **The rule was never picked**: when conditions are not satisfied, the mod does not convert the dropped item and retries on a backoff rhythm, from 1 second to 2 seconds to 4 seconds until it reaches the ceiling. The item is either too young or in the wrong place. See [Conditions](en-03-conditions).
2. **The rule was not loaded or not enabled**: `enabled` is `false`, another rule with the same id from a higher source layer replaces this one, or a wrong JSON field rejects the rule. A rule change takes effect only after a reload. See [Getting Started](en-01-getting-started) and [Source Layers and Overrides](en-06-layers-and-overrides).
3. **The stack is smaller than one group**: when the stack size is below the source cost per group, a cost of 2 with only 1 item left for example, no group can open. You will see the dropped item vanish on time and a matching stack reappear nearby. That is the return, and the returned item never converts again.
4. **The nearby product limit is full**: when matching products around the trigger point, meaning items or mobs, already reached the limit from the server configuration, the mod skips every candidate that cannot open its groups in full. When no candidate can open a group, the whole stack returns unchanged. Nearby experience orbs only matter to experience output itself: experience ignores this limit, while the items and generic entities a mixed candidate produces still answer to their own limits. See [Server Configuration](en-07-server-config).
5. **The candidate itself was skipped**: its effect-level `conditions` were not satisfied, or its `chance` missed. The mod still consumes the source items, and you see no output. See [Effects](en-04-effects).
6. **The output was delayed**: an effect that sets `delay_ticks` produces its output later than the moment the dropped item disappears, which happens with delayed lightning or with mobs that spawn in batches.
7. **The output landed somewhere else**: a `place_block` effect with `fill_origin` set to `false` never fills the trigger position itself, and a mob spawned with `safe_spawn` may appear on a neighboring block.

A one-line order to troubleshoot: first confirm that the rule itself was picked, meaning it is enabled, loaded, and its conditions are satisfied; then check costs and counts, meaning whether the stack fills a group and whether catalysts are enough; then check the nearby product limits; and only at the end check the conditions, chance, and delay of the effects.

## Related

- [Rule Fields](en-02-rule-basics)
- [Conditions](en-03-conditions)
- [Effects](en-04-effects)
- [Source Layers and Overrides](en-06-layers-and-overrides)
- [Server Configuration](en-07-server-config)