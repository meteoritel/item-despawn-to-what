# Effects (Actions)

This page explains what a rule can do after it fires: which `type` each effect uses, which parameters it takes, and what players see in game.

After a rule fires, the mod runs every effect object in the `effects` array in order: it hands out items, spawns entities, awards experience, places blocks, strikes lightning, or changes the weather. In JSON, **one effect is one object**, and `type` says which effect it is. You can write effects in the top-level `effects` array of a rule or in the `effects` array of a candidate. For conditions, see [Conditions](en-03-conditions); for costs and group settlement, see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).

| Effect | `type` | One-shot | What it does |
|---|---|---|---|
| Entities | `itemdespawntowhat:spawn_entity` | No | Hands out items, spawns entities, or awards experience |
| Arrow rain | `itemdespawntowhat:arrow_rain` | Yes | Drops a volley of arrows from above the trigger position, with optional potion effects |
| Place block | `itemdespawntowhat:place_block` | No | Places blocks near the trigger position |
| Loot table | `itemdespawntowhat:loot_table` | No | Rolls a loot table and drops the results |
| Lightning | `itemdespawntowhat:lightning` | Yes | Strikes 1 to 16 bolts |
| Explosion | `itemdespawntowhat:explosion` | Yes | Blows up once at the trigger position |
| Weather | `itemdespawntowhat:weather` | Yes | Makes the current dimension rain or clear up |

This page starts with the fields every effect shares, then walks through the table row by row.

## Fields Every Effect Shares

Every effect object can carry the three fields below. `type` is required and the rest are optional. The parameters of the effect itself, such as `item` or `count`, sit **at the same level** as these three fields. Do not wrap them in another object such as `value`.

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `type` | Says which effect this is | An effect id that starts with `itemdespawntowhat:`, listed in the table above | None, required | A wrong name rejects the rule, and the message lists the registered types |
| `delay_ticks` | How long to wait before running this effect | Integer (unit: ticks, 20 ticks = 1 second) | `0` | Counts from the moment the rule fires; a negative value rejects the rule, and the message names `delay_ticks` |
| `chance` | The chance this effect passes, rolled when its turn comes | `0.0` to `1.0` | `1.0` | A value outside this range rejects the rule with a message about `chance`; `1` or `1.0` always runs |

Three things worth knowing:

- **A rejected rule never loads**: when the content of a rule has a problem, the mod does not load it and reports why. A field the effect does not recognize also rejects the rule, and the message carries the field name.
- To refer to an item, block, entity, fluid, or potion effect, write a concrete id (`minecraft:water`) or a **tag**, which is a reference that starts with `#`, such as `#minecraft:logs`.
- Tags mean two different things. A producing reference, such as the `item` and `entity` of `spawn_entity`, the `block` of `place_block`, or the `effect` of a potion effect, takes **one random member** every time it runs. A matching reference, such as a rule-level `source_cost` or `catalyst_cost` reference, matches **any member of the tag** and never picks at random.

## How Several Effects Run

- **Effects start in written order**: the object written first starts first. Every effect carries its own delay, so effects do not always finish in written order; an arrow rain that starts early can finish after an explosion that starts later.
- **Work spreads over several ticks**: when one effect has to produce many things, the mod does not stack them all up in a single tick but finishes them over several ticks, with at most 16 item entities per batch, 8 entities per tick, and 128 block positions per tick. Players watch the results appear one batch at a time.
- **Chance belongs to one effect**: every effect rolls its own `chance`. When one effect fails its roll, the other effects of the same group still run.
- **One effect failing does not stop the others**: when an effect errors out at run time, for example because an item it refers to no longer exists after a data pack update, the mod records that failure and continues with the next effect. Nothing already done rolls back, and the whole rule is not cancelled. The failure appears in the result; for how to trace it, see [Getting Started](en-01-getting-started).
- **Timing of the check**: effects that need their input fixed when the conversion succeeds, such as `place_block` (which decides at that moment what to place, where the candidates are and the cap), roll `chance` right then; a later delay or an already consumed source item no longer changes the result. Effects without fixed input still roll `chance` at the moment they are due to run, after their delay.
- **Every group runs the effects once**: each group that starts runs the effects once, so `count` is the amount for that single run and a loot table rolls once per group. For how a whole stack splits into groups, see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).
- **A cap of 32**: one rule can hold at most 32 effects in total, counting the `effects` of every candidate. A rule above the cap is rejected.
- **One-shot effects**: `arrow_rain`, `lightning`, `explosion`, and `weather` are one-shot. They play once for a given source, they do not repeat with the group count, and they take no part in the capacity check of a candidate. Each source gets at most one attempt at them: a failed roll and a run-time error both count as that one attempt.

## Entities (spawn_entity)

`type`: `itemdespawntowhat:spawn_entity`

The **variant** field decides what comes out: `variant` is required and takes `item`, `entity`, or `experience`. The parameters of the variant sit at the same level as `type` and `variant`.

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `variant` | Which variant to use | `item` / `entity` / `experience` | None, required | Leaving it out rejects the rule, and the message asks for an outcome variant |

### Variant item: Items

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `item` | Which item to produce | Item id or tag | None, required | A tag takes one random member |
| `count` | How many items to produce | 1 to 64 | `1` | When the amount is above the stack size of the item, the mod splits it into several stacks; at most 16 stacks per tick, the rest continue on later ticks |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
```

What you see in game: one piece of rotten flesh lands near the trigger position and players can pick it up right away. If the source item was still moving horizontally when it was destroyed, the product's horizontal velocity points the opposite way, so it flies back toward the direction the item was thrown from and is easier to pick up; a source item at rest has no direction to mirror, so the product keeps the original small scatter (within 0.15 blocks horizontally). Larger amounts arrive over several ticks instead of all at once; which item comes out and how many pieces is decided the moment the conversion succeeds, not later. The new drop carries protection and a conversion cooldown so that the rules do not consume it again at once; see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).

### Variant entity: Entities

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `entity` | Which entity to spawn | Entity type id or tag | None, required | When a candidate turns on `safe_spawn`, the mod first looks for a spot to stand on nearby; see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) |
| `count` | How many entities to spawn | 1 to 64 | `1` | The mod spawns at most 8 per tick and continues on later ticks |
| `age` | Age | Integer, positive or negative | `0` | Applies only to mobs that grow up, such as farm animals and villagers; `0` is an adult and `-24000` is a newborn |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "entity", "entity": "minecraft:chicken", "count": 1, "age": -24000 }
```

What you see in game: a baby chicken spawns near the trigger position. With `safe_spawn` on, the mod searches the 5x5x5 block box centered on the trigger position, 125 blocks in all, from near to far for a spot the entity can stand on. When it finds none, it falls back to the area around the trigger position. This switch is off by default.

Note: item entities (`minecraft:item`) and experience orbs (`minecraft:experience_orb`) do not belong here. Use the two dedicated variants instead, or the rule is rejected.

### Variant experience: Experience

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `amount` | Experience points per source item | 1 to 65536 | `1` | The group total is `amount` times the source items that group actually took. The mod fills orbs up to 32767 points each, so anything smaller is one orb, and the remainder goes into the last orb. It spawns at most 16 orbs per batch and keeps going until all points are out |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "experience", "amount": 100 }
```

What you see in game: with a group that paid one source item, 100 experience points fit inside one orb, so you see exactly **one** experience orb near the trigger position. Orbs land at random points on a disc of radius 0.5 blocks around the trigger position, raised 0.2 blocks above it. Only a larger amount splits into more orbs: at most 16 orbs per batch, with the next batch waiting at least one more game tick, until all points are out. Each experience effect and each group computes its own points from the source items that group actually took, and points from different effects or groups never merge. Collect them by walking over them.

The number of nearby experience orbs never blocks a group over experience storage. A group that also produces items or generic entities still passes their separate capacity checks.

## Arrow Rain (arrow_rain)

`type`: `itemdespawntowhat:arrow_rain`

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `count` | How many arrows | 1 to 256 | `16` | One arrow every 2 ticks |
| `pickup` | Whether players may pick the arrows up | `disallowed` / `allowed` / `creative_only` | `disallowed` | The three values mean never, always, and only in creative mode |
| `potion_effects` | Potion effects carried by the arrows | Array, optional | Omit for none | The fields of each entry are in the table below |

Each entry in `potion_effects`:

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `effect` | The potion effect | Potion effect id or tag | None, required | A tag takes one random member, and the whole volley shares that result |
| `duration_ticks` | How long it lasts (ticks) | 1 to 1000000 | `100` | 20 ticks = 1 second |
| `amplifier` | Level | 0 to 255 | `0` | `0` is level I and `1` is level II |

```json
{ "type": "itemdespawntowhat:arrow_rain", "count": 32, "pickup": "allowed", "delay_ticks": 10 }
```

```json
{
  "type": "itemdespawntowhat:arrow_rain",
  "count": 16,
  "pickup": "creative_only",
  "potion_effects": [
    { "effect": "minecraft:poison", "duration_ticks": 200, "amplifier": 0 }
  ]
}
```

What you see in game: after a 10 tick delay the volley starts and drops 32 downward arrows every 2 ticks from 80 blocks above the trigger position, spread over roughly 5 blocks around it, and players can pick them up. With potion effects, an entity the arrows hit takes the matching effect.

## Place Block (place_block)

`type`: `itemdespawntowhat:place_block`

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `block` | Which block to place | Block id or tag | Omit | Write this or `use_source_block`, and at least one of the two |
| `use_source_block` | Places the block that matches the source item | `true` / `false` | `false` | A dirt source places dirt; when the source item has no matching block, this run fails |
| `shape` | Shape of the candidate positions | `square` / `circle` / `cross` | `square` | A square, a circle, or a cross that keeps only the cells along the x axis or the z axis; not case sensitive |
| `count` | Result budget per group | 1 to 64 | `1` | Positions are tried from the inside out, subject to the environment budget |
| `radius` | Search radius (blocks) | 1 to the server setting `max_place_block_radius` (default 8) | `6` | The horizontal range at the same height as the trigger position; rules above the server cap are rejected, and the editor takes the same setting as the radius slider window |
| `limit` | Cap on how many of this block may sit in the box | 1 or more | Omit for no limit | The mod counts the blocks of the same type already inside the radius box and fills up to the cap; in the editor the slider window and starting value both equal the current radius box capacity (2 × radius + 1)³, and a value above that capacity means no limit |

Notes:

- When you write neither field, the rule is rejected with a message about `block` and `use_source_block`. When you write both, `block` wins.
- Blocks go only at **the same height as the trigger position**; nothing stacks above or below.
- Only replaceable positions, such as grass and water, are used, and the block needs support. A position that does not fit is skipped, and the mod never steps outside `radius` to reach `count`.
- The **result budget** is the output available from qualifying source items. The **environment budget** is the amount allowed by usable positions inside the radius and by `limit`. When the task ends, source cost follows actual placement and unused source items return: with 10 source items and 2 blocks per source, placing 6 returns 7 sources; placing 7 consumes 4 and returns 6.
- Each group rounds `placed × source_cost ÷ count` up to whole source items. Multiple block effects in one group share the highest cost used; a dispatched non-block effect, a missed chance or an invalid target uses the full group cost. Catalysts and fluid sources are paid when the group opens.
- Groups with the same candidate, slot and target block share one position scan while retaining their individual chance results and due times. Counting skips whole chunk sections whose palette lacks the target. Placement remains subject to the shared tick budget.
- Blocks that need a direction or support, such as buttons, torches, and ladders, get the orientation vanilla would give them: the mod tries standing on the floor, then hanging from the ceiling, then attaching to each of the four walls, and places the first orientation that can stand; when none can, that position is skipped.
- `limit` counts a box centered on the trigger position with side length `2 × radius + 1`: `radius` decides how far the mod may place, `limit` only caps how many, and never widens the area. When one candidate produces several groups of blocks in the same conversion, those groups share one count and one remaining allowance, so the box never ends up holding more than `limit` of the block.
- The `limit` slider maximum follows `radius`: it is at most the number of positions in the current radius box, (2 × radius + 1)³ (2197 when `radius` is 6). Typing a larger value is not rejected, because the box cannot hold more of that block anyway, so the value means no limit; a valid value outside the slider window can still be typed and saved.
- A new place action writes `limit` as the capacity of the current radius box, so the slider sits at its maximum; when `radius` changes afterwards, `limit` follows to the new capacity. Once you edit `limit` yourself that follow stops and changing the radius no longer overwrites it; switching to another action or rule starts the follow again.
- Whether the trigger position itself takes part depends on the fill origin switch of the candidate; see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).

```json
{ "type": "itemdespawntowhat:place_block", "use_source_block": true, "shape": "square", "count": 1, "radius": 1 }
```

What you see in game: the block that matches the source item this run used appears within 1 block of the trigger position, at most 1 block.

```json
{ "type": "itemdespawntowhat:place_block", "block": "minecraft:cobblestone", "shape": "circle", "count": 32, "radius": 4, "limit": 64 }
```

What you see in game: cobblestone fills in from the inside out across a circle of radius 4 around the trigger position, up to 32 blocks. When that volume already holds 64 cobblestone, the mod places nothing at all.

## Loot Table (loot_table)

`type`: `itemdespawntowhat:loot_table`

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `loot_table` | Which loot table to use | Loot table id | None, required | A concrete id only; `#` tags are not supported |
| `luck` | Luck value | `-100` to `100` | `0` | Affects entries that carry a luck check and may be negative |

```json
{ "type": "itemdespawntowhat:loot_table", "loot_table": "minecraft:chests/simple_dungeon", "luck": 0, "chance": 0.5 }
```

What you see in game: with a 50 percent chance the mod rolls the simple dungeon chest loot table once and the results scatter near the trigger position, and each product's horizontal velocity is opposite to the source item's motion when it was destroyed, so it flies back toward the direction the item came from. The roll uses the trigger position as its origin and hands the source entity to the table as the acting entity, the same way a chest does. It provides no killer, so entries that need a player kill never apply. The table is rolled exactly once, at the moment the conversion succeeds: a delayed or multi-tick delivery never re-rolls, and an empty roll simply drops nothing.

## Lightning (lightning)

`type`: `itemdespawntowhat:lightning`

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `count` | How many bolts | 1 to 16 | `1` | 8 ticks (0.4 seconds) between bolts |

```json
{ "type": "itemdespawntowhat:lightning", "count": 1, "delay_ticks": 20 }
```

What you see in game: after a 1 second delay one bolt strikes, identical to vanilla lightning, so it deals damage and sets blocks on fire. With `count: 3`, the first bolt keeps the trigger's horizontal coordinates and the other two scatter randomly within 5 blocks. Bolts land on the topmost collision or fluid surface, including slabs and stairs; an underground trigger still strikes the surface above. In the void, where no surface exists, bolts use the heightmap height.

## Explosion (explosion)

`type`: `itemdespawntowhat:explosion`

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `power` | Explosion power | `0` to `16` | `3` | For reference, a creeper is about `3` and TNT is `4` |
| `fire` | Whether fire is left behind | `true` / `false` | `false` | When true, the blast sets nearby blocks on fire |
| `visual_only` | Visuals only, with no world change | `true` / `false` | `false` | When true, the mod plays particles and sound without breaking blocks or hurting entities |

```json
{ "type": "itemdespawntowhat:explosion", "power": 2, "fire": true, "visual_only": false }
```

What you see in game: an explosion of power 2 goes off at the trigger position, breaking blocks and leaving fire behind.

```json
{ "type": "itemdespawntowhat:explosion", "power": 4, "visual_only": true }
```

What you see in game: only the particles and the sound of an explosion, with no effect on the world or on entities. This suits a loud but harmless show.

## Weather (weather)

`type`: `itemdespawntowhat:weather`

| Field | What it does | Values | Default | Notes |
|---|---|---|---|---|
| `mode` | Rain or clear up | `rain` / `clear` | None, required | Leaving it out rejects the rule |
| `duration_ticks` | How long it lasts (ticks) | 1 to 24000 | `6000` | 6000 ticks is 5 minutes and 24000 ticks is 20 minutes |
| `thundering` | Whether to make it a thunderstorm | `true` / `false` | `false` | Applies only with `rain` |

```json
{ "type": "itemdespawntowhat:weather", "mode": "rain", "duration_ticks": 6000, "thundering": true }
```

What you see in game: the current dimension turns into a thunderstorm at once for 6000 ticks (5 minutes).

A few notes: `clear` switches the weather to clear, the mod does not set the weather again when it already matches, which keeps the timer from being refreshed over and over, and dimensions without sky light, such as the Nether, are not affected.

## Input Consumption

Source, catalyst and fluid payments are rule input settings. See [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) for fields and examples. Output effects describe products and world actions.

## About Limit and Radius

`limit` and `radius` are **parameters of an effect**, written inside a concrete effect object rather than as rule-level fields:

- `limit`: the cap on the number of products, counted within `radius`.
- `radius`: the meaning depends on the effect. In `place_block` it is the search radius for placement. It is also the range that `limit` counts.

A server-level protection also stops a conversion when too many products lie nearby. It works per rule and is independent of the parameters of a single effect; see [Server Configuration](en-07-server-config).

## Related

- [Conditions](en-03-conditions)
- [Outcome Candidates and Settlement](en-05-outcomes-and-settlement)
- [Rule Fields](en-02-rule-basics)
- [Server Configuration](en-07-server-config)
