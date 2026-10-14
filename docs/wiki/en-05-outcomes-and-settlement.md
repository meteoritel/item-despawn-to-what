# Outcome Candidates and Settlement

This page explains what a rule produces, what each conversion group pays, and what happens to a whole stack of dropped items from settlement through output and return.

An **outcome candidate** (an element of `outcomes`) is one possible output a conversion can pick. A **conversion group** is one unit of settlement: it picks one outcome candidate and pays the cost of that group.

Effect parameters, such as what `spawn_entity` produces or how `place_block` places blocks, are on [Effects](en-04-effects.md). Nearby product limits and per-tick check counts are server parameters on [Server Configuration](en-07-server-config.md). Whether a conversion starts at all depends on conditions, see [Conditions](en-03-conditions.md).

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
| `effects` | The effects this candidate runs | An array of effects | — (required, cannot be empty) | Effect types and parameters are on [Effects](en-04-effects.md) |
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

A **fixed cost** is a price you pay for every conversion group that opens. Source items and catalysts are both fixed costs, so they take no chance, no condition, and no delay. A rule can declare fixed costs (`source_cost`, `catalyst_cost`) or use **consumption effects** (`consume_source`, `consume_catalyst`, `consume_fluid`) that consume materials when a group opens.

The two forms overlap, but the same material can go in only one of them: `source_cost` and `consume_source` cannot appear in the same rule, and neither can `catalyst_cost` and `consume_catalyst`. A rule that breaks this rule is rejected, which keeps the same material from being consumed twice.

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

### Catalyst Cost (catalyst_cost)

| Field | What it does | Values | Default | Notes |
| --- | --- | --- | --- | --- |
| `items` | Which catalysts the rule needs | An array of items or `#tags` | — (required, cannot be empty) | The mod collects them within a radius of the trigger position |
| `count` | How many of each catalyst one group consumes | 1 to 64 | 1 | Except the kinds that `counts` lists separately |
| `radius` | How far from the trigger position to search for catalysts | 1 to 8 (blocks) | 1 | Includes the block of the trigger position |
| `counts` | Separate amounts for some catalysts | `{ "reference": number }` | `{}` (all use `count`) | Every key must already appear in `items` |

- The catalyst cost accepts only these four fields and must be an object. An empty `items`, a `counts` key that names something outside `items`, or an amount outside its range rejects the rule. Adding `chance`, `conditions`, or `delay_ticks` also rejects the rule, because a fixed cost has no chance to miss and no delay to wait out.
- The mod consumes every reference in `items` its own amount: two catalysts are both consumed, rather than a single payment for the pair.
- The mod searches dropped items within `radius` blocks of the trigger position and pays from the closest to the farthest. The source dropped item never counts.
- The mod consumes the materials before it opens every group, so the cost applies per group rather than once for the whole stack.
- When the radius does not contain enough items, the group does not open: the mod consumes no source items, produces nothing for that candidate, and returns the remaining part at the end.

### How Consumption Effects Map to Rule Costs

| Consumption effect | What it consumes | Relation to rule costs |
| --- | --- | --- |
| `consume_source` | The source items of each group | Matches `source_cost`, and a rule can declare only one of the two |
| `consume_catalyst` | The catalysts of each group, within a radius of the trigger position | Matches `catalyst_cost`, and a rule can declare only one of the two |
| `consume_fluid` | Fluid near the trigger position | Has no rule-level field, so you can only write it as an effect |

- Effect parameters and shared fields, such as `delay_ticks`, `chance`, and `conditions`, are on [Effects](en-04-effects.md).
- One rule may declare each consumption effect only once, counting the top-level `effects` together with the `effects` of every candidate. A repeated declaration is rejected.
- A consumption effect written inside a candidate runs only when that candidate is picked and the group opens.

### Implicit Consumption When You Write Nothing

**Implicit consumption** applies when a rule declares no consumption effect and no `source_cost` or `catalyst_cost`: the mod then consumes 1 source item before every group it opens, which means one source item buys one output.

- As soon as you declare any consumption effect or fixed cost, the implicit item no longer stacks on top. You never pay one extra source item on top of a cost you wrote.
- One common mistake: declaring `consume_catalyst` or `consume_fluid` while writing neither `consume_source` nor `source_cost`. Source items then take no part in splitting the stack, so the whole stack counts as one group and the catalysts or fluid are paid for one group. To convert a stack one item at a time, write `source_cost` or `consume_source`.

## Settlement and Return for the Whole Stack

When a rule picks up a dropped item, the whole stack leaves the world and enters settlement. The mod then works through these steps:

1. **Count the groups**: the number of groups is the stack size divided by the source cost per group, rounded down. When the source cost per group is 0, meaning the rule consumes no source items, the whole stack counts as one group. The **nearby product limits** (`nearby_products`) can lower that number: the mod looks at what this candidate actually produces and keeps the smaller of the two counts, and a candidate that holds only one-shot effects always counts as one group.
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

1. **The rule was never picked**: when conditions are not satisfied, the mod does not convert the dropped item and retries on a backoff rhythm, from 1 second to 2 seconds to 4 seconds until it reaches the ceiling. The item is either too young or in the wrong place. See [Conditions](en-03-conditions.md).
2. **The rule was not loaded or not enabled**: `enabled` is `false`, another rule with the same id from a higher source layer replaces this one, or a wrong JSON field rejects the rule. A rule change takes effect only after a reload. See [Getting Started](en-01-getting-started.md) and [Source Layers and Overrides](en-06-layers-and-overrides.md).
3. **The stack is smaller than one group**: when the stack size is below the source cost per group, a cost of 2 with only 1 item left for example, no group can open. You will see the dropped item vanish on time and a matching stack reappear nearby. That is the return, and the returned item never converts again.
4. **The nearby product limit is full**: when matching products around the trigger point, such as items, mobs, or experience orbs, already reached the limit from the server configuration, the mod skips every candidate that cannot open a group. When no candidate can open a group, the whole stack returns unchanged. See [Server Configuration](en-07-server-config.md).
5. **The candidate itself was skipped**: its effect-level `conditions` were not satisfied, or its `chance` missed. The mod still consumes the source items, and you see no output. See [Effects](en-04-effects.md).
6. **The output was delayed**: an effect that sets `delay_ticks` produces its output later than the moment the dropped item disappears, which happens with delayed lightning or with mobs that spawn in batches.
7. **The output landed somewhere else**: a `place_block` effect with `fill_origin` set to `false` never fills the trigger position itself, and a mob spawned with `safe_spawn` may appear on a neighboring block.

A one-line order to troubleshoot: first confirm that the rule itself was picked, meaning it is enabled, loaded, and its conditions are satisfied; then check costs and counts, meaning whether the stack fills a group and whether catalysts are enough; then check the nearby product limits; and only at the end check the conditions, chance, and delay of the effects.

## Related

- [Rule Fields](en-02-rule-basics.md)
- [Conditions](en-03-conditions.md)
- [Effects](en-04-effects.md)
- [Source Layers and Overrides](en-06-layers-and-overrides.md)
- [Server Configuration](en-07-server-config.md)