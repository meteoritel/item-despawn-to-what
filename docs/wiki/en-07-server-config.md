# Server Configuration (server.json)

This page explains what each setting in `server.json` does, which values it accepts, and when you need to change it.

These settings control how the server runs: how often the mod checks, how much work it does per tick, and how it protects and cools down its products. They do not decide what a drop turns into. For that, see [Rule Fields](en-02-rule-basics).

## What This File Is

- Location: `config/itemdespawntowhat/server.json`, inside the game instance's `config` directory. It has nothing to do with a world or a data pack.
- One file for both loaders: Fabric and NeoForge read the same file and the same values.
- When it appears: the game creates it on the first launch of the client or the server. After that the mod never rewrites an existing file, so your edits stay.
- When it takes effect: the server reads it once at startup. Restart the server after a change. `/idtw config reload` reloads rules only; it does not reread this file.
- Missing fields take their default values, so write only the entries you want to change.
- What happens when a value is wrong: one bad entry voids the whole file, which then falls back to its default values. A bad entry is a value out of range, a wrong type such as text where a number belongs, or JSON that does not parse, from a stray bracket or comma to content that is not a JSON object. Correct entries elsewhere do not apply either, and the startup log records one error. The mod never rewrites the file, so check the startup log after an edit.
- Unknown fields produce a startup WARN with the full path, such as `nearby_products.item_limt`. Other valid fields still apply. Correct the spelling and restart the server.

The **overlay directory** is the folder under `config/` that holds your own rule files, and `overlay_directory` is the only setting that moves it. How `server.json` differs from rule files:

| Item | `server.json` | Rule Files |
|---|---|---|
| What it is | Server run parameters | Conversion content, meaning what turns into what |
| Scope | One file for the whole server, shared by every rule | Each rule is independent |
| Location | `config/itemdespawntowhat/server.json` | `config/itemdespawntowhat/rules/*.json`, or `data/<namespace>/idtw/rules/*.json` in a data pack |
| How it applies | Restart the server | `/idtw config reload` |

You can indent and wrap the file however you like. The game writes it as one compact JSON line on the first launch, and the two forms are equivalent.

## Default Configuration

The following lists the complete default values. The Codec omits keys equal to their defaults when saving. Configuration keys remain flat, with `nearby_products` as a nested object:

<!-- config-reference:defaults:begin -->
```json
{
  "check_interval_ticks": 20,
  "backoff_max_ticks": 100,
  "max_checks_per_tick": 512,
  "server_budget_us": 10000,
  "effects_work_units_per_tick": 64,
  "check_spread_window_ticks": 20,
  "dispatch_batch_size": 64,
  "rescan_entities_per_step": 16,
  "cancels_per_step": 1,
  "prepare_groups_per_step": 8,
  "settlement_recovery_max_attempts": 3,
  "fabric_lifespan_fallback_ticks": 6000,
  "new_product_protection_seconds": 2,
  "conversion_cooldown_seconds": 5,
  "position_search_checks_per_tick": 16,
  "max_place_block_radius": 8,
  "max_effect_offset_x": 6,
  "max_effect_offset_y": 6,
  "max_effect_offset_z": 6,
  "debug_logging": false,
  "debug_scenario_prepare_batch_size": 128,
  "debug_scenario_prepare_budget_us": 2000,
  "overlay_directory": "itemdespawntowhat",
  "nearby_products": {
    "item_limit": 1024,
    "item_radius": 6,
    "entity_limit": 128,
    "entity_radius": 6,
    "experience_orb_limit": 64,
    "experience_radius": 6
  }
}
```
<!-- config-reference:defaults:end -->

`max_work_units_per_tick` does not appear in the file by default, because its fallback applies only while the entry is absent. See the next section. Its absence from the generated file is normal.

## All Fields

See the table for each time field's unit. At the normal game speed, 20 ticks equal 1 second.

<!-- config-reference:fields:begin -->
| Field | What It Does | Type / Range | Default | When to Change It |
| --- | --- | --- | --- | --- |
| `check_interval_ticks` | How long to wait before checking a condition again while it does not hold. The first retry uses this interval, and every retry after that doubles it | Integer [1, 1200] ticks | `20` | Lower it to convert sooner after a condition starts holding. Raise it to check less often when many drops are loaded |
| `backoff_max_ticks` | The cap on that doubling. A condition that stays false waits at most this long before the next check | Integer [1, 72000] ticks | `100` | Lower it so a rule that will hold later retries sooner. Raise it to save more performance at the cost of slower reaction |
| `max_checks_per_tick` | The checks-per-tick ceiling, used only while `max_work_units_per_tick` is absent | Integer [1, 100000] | `512` | Change it only when you want to control how many checks run per tick. Most servers never need this |
| `server_budget_us` | The time in microseconds the mod may spend per game tick. Work that does not fit continues over the following ticks | Integer [100, 100000] microseconds | `10000` | Lower it for extra safety on a busy server. Raise it when conversions lag and the server has spare capacity |
| `max_work_units_per_tick` | The total work-unit ceiling per tick. It applies together with `server_budget_us`, and whichever comes first wins | Integer [1, 100000] | Absent: follows `max_checks_per_tick` | Leave it absent. Write it only when you need to set the work per tick on its own |
| `effects_work_units_per_tick` | The per-tick quota reserved for running effects, such as spawning items, placing blocks, and granting experience, so that heavy output does not crowd out the checks | Integer [1, 100000] | `64` | Raise it when a large batch of products takes too long to appear. Lower it to keep the checks prompt |
| `check_spread_window_ticks` | When many drops come due at once, the mod spreads those checks over a window of this many game ticks | Integer [1, 1200] ticks | `20` | Raise it when a simultaneous batch causes lag, which is smoother but delays individual conversions. Lower it to keep conversions on time when a single tick can take the load |
| `dispatch_batch_size` | How many queued conversions the mod advances per tick at most, and it also caps the returned items delivered in one batch | Integer [1, 100000] | `64` | Raise it when large batches back up |
| `rescan_entities_per_step` | How many loaded drops one step rebuilds when tracking is rebuilt after startup, a dimension load, or a rule reload. The rest continues over later ticks | Integer [1, 4096] | `16` | Lower it when a server with tens of thousands of drops still hitches on a single tick after startup or a reload. Raise it to finish sooner, at the cost of more work per tick |
| `cancels_per_step` | How many queued tasks one step cancels when a rule reload, a dimension unload, or a server stop detaches them. The rest continues over later ticks | Integer [1, 4096] | `1` | Keep it at the default (or lower) when a reload or unload still spikes on a single tick. Raise it to finish clearing old queues sooner, at the cost of more cancel callbacks per tick |
| `prepare_groups_per_step` | How many groups one step prepares fixed effect inputs for during the preparation stage of a settlement, spreading the preparation of a whole stack over several game ticks | Integer [1, 4096] | `8` | Lower it when many settlements start at once and preparation hitches a single tick. Raise it to start producing sooner for very large stacks, at the cost of more work per tick |
| `settlement_recovery_max_attempts` | How many times the mod retries a settlement that failed mid-run before giving up for this session and leaving the record for the next startup scan | Integer [1, 100] | `3` | Lower it when a dimension stays unavailable for long and you would rather let the next restart take over. Raise it only when a settlement fails for reasons that clear up after a few seconds |
| `fabric_lifespan_fallback_ticks` | Fabric only: the natural lifespan in ticks used until the current server run samples a reference drop lifespan | Integer [1, 72000] ticks | `6000` | The default matches how long vanilla drops live. Adjust it only when the server changes drop lifespan rules |
| `new_product_protection_seconds` | How many seconds a freshly converted product (dropped item, mob, or other entity) resists destruction by fire, lava, and cacti | Integer [0, 3600] seconds | `2` | Raise it when products burn as soon as they appear, for example next to lava. Set `0` to let products be destroyed right away |
| `conversion_cooldown_seconds` | How many seconds a freshly converted item cannot start another conversion | Integer [0, 3600] seconds | `5` | Raise it when products convert again immediately under another rule. Set `0` to let a product convert again at once |
| `position_search_checks_per_tick` | How many candidate positions the mod tries per tick when it looks for a spot for a product or a returned item | Integer [1, 4096] | `16` | Raise it when items stay undelivered because the terrain leaves few spots. Lower it to save performance |
| `max_place_block_radius` | Largest `radius` a single `place_block` action may use; rules above it are rejected when loaded or saved | Integer [1, 2147483647] | `8` | Raise it to let one action cover a wider area, but the cost of one placement grows with the cube of the radius, so keep it modest; lowering it rejects existing rules that now exceed the cap, so edit those rules as well |
| `max_effect_offset_x` | Maximum absolute trigger offset on the X axis per action, in whole blocks; defaults to 6. Set 0 to fix this axis at zero | Integer [0, 2147483647] | `6` | Reopen the editor after changing it. Loading and saving validate against the current limits; reduce offsets in rules that exceed them |
| `max_effect_offset_y` | Maximum absolute trigger offset on the Y axis per action, in whole blocks; defaults to 6. Set 0 to fix this axis at zero | Integer [0, 2147483647] | `6` | Reopen the editor after changing it. Loading and saving validate against the current limits; reduce offsets in rules that exceed them |
| `max_effect_offset_z` | Maximum absolute trigger offset on the Z axis per action, in whole blocks; defaults to 6. Set 0 to fix this axis at zero | Integer [0, 2147483647] | `6` | Reopen the editor after changing it. Loading and saving validate against the current limits; reduce offsets in rules that exceed them |
| `debug_logging` | Whether the mod writes its run-time progress to the log, such as a drop whose condition does not hold and how many ticks until the next retry | Boolean | `false` | Turn it on temporarily while you diagnose why something does not convert. Keep it off otherwise, because it floods the log |
| `debug_scenario_prepare_batch_size` | Affects only the preparation stage of test scenarios in a development environment, with no effect on normal play | Integer [1, 100000] | `128` | Players never need to change it |
| `debug_scenario_prepare_budget_us` | The time cap in microseconds for that same preparation stage | Integer [1, 100000] microseconds | `2000` | Players never need to change it |
| `overlay_directory` | The name of the rule overlay directory, relative to `config/`, so rules live in `config/<this value>/rules/` | String | `"itemdespawntowhat"` | Change it only to rename the config directory. Move the whole directory with it, or the overlay rules stop loading. The location of `server.json` itself does not change |
| `nearby_products` | The nearby product limits. The count of products already nearby, together with the declared output per group, decides how many groups this settlement may open. See the next section | Object | See sub-fields | Lower it when piled-up products cause lag. Set the matching limit to `0` to remove the cap |
<!-- config-reference:fields:end -->

Use the table for accepted ranges and defaults. A `0` state duration and a `0` nearby capacity limit each have their own meaning. Any out-of-range value makes the whole file fall back to the defaults and logs an error.

## Nearby Product Limits

At the start of a settlement, the mod allocates an output budget from the initial nearby count and the declared theoretical output per group, without discounting probability. With a limit of 100, 90 existing items and one declared item per group, only 10 groups may convert; the remaining source items are returned. Nearby limits are not checked again during generation. Products added elsewhere do not reduce those 10 groups, and the final nearby count may exceed the limit. Actual budget use depends on the conversion type; producing less does not permit extra groups. Items and entities (mobs and other entities) use their respective budgets; experience orb counts do not affect admission.

These limits apply to every rule. They are not set per rule.

<!-- config-reference:nearby-products:begin -->
| Field | What It Does | Type / Range | Default |
| --- | --- | --- | --- |
| `item_limit` | The cap on the total number of nearby items of the same kind, counted by the item or item tag that would be produced | Integer [0, 1000000] | `1024` |
| `item_radius` | The counting radius in blocks for the limit above, as a cube centered on the action output origin block with an edge length of 2 × radius + 1 | Integer [1, 32] | `6` |
| `entity_limit` | The cap on the number of nearby entities of the same kind, counted by the entity type or entity tag that would be produced | Integer [0, 1000000] | `128` |
| `entity_radius` | The entity counting radius in blocks, with the same meaning as above | Integer [1, 32] | `6` |
| `experience_orb_limit` | A kept field. It stays in the file so existing configs keep working, and it no longer affects any conversion | Integer [0, 1000000] | `64` |
| `experience_radius` | A kept field as well. It maps to no counting radius at all | Integer [1, 32] | `6` |
<!-- config-reference:nearby-products:end -->

Notes:

- Set an item or entity capacity limit to `0` to turn that check off. Only `item_radius` and `entity_radius` are active counting radii. Experience fields retain config read/write compatibility and the ranges shown in the table.
- A limit only decides whether a new conversion group can start. It is not a hard ceiling on what exists nearby, and it does not keep the count at or below the limit.
- A group stopped by a limit consumes no source items. The source items that cannot start a group go through the return path, as described in [Outcome Candidates and Settlement](en-05-outcomes-and-settlement). A returned item never converts again, so the surplus does not queue up for a later slot.
- Lowering a limit makes conversions easier to postpone, and heavy product buildup can stop output for a long time. When the item and entity limits are both in use, the tightest one decides.
- The two experience fields exist only so that existing config files keep loading. Within their allowed ranges, no value you write there changes conversion behavior.

Example: lift the nearby count limits for items and entities while the other settings keep their defaults. The two experience fields already do nothing, so leave them out:

```json
{
  "nearby_products": {
    "item_limit": 0,
    "entity_limit": 0
  }
}
```

## Product Protection and Conversion Cooldown

`new_product_protection_seconds` covers every product: dropped items, mobs, and other entities. `conversion_cooldown_seconds` affects dropped items only, because entity products take part in no conversion. Returned items follow other rules, as the note at the end of this section explains.

| Field | What It Does | What `0` Means |
| --- | --- | --- |
| `new_product_protection_seconds` | A freshly produced product resists destruction by fire, lava, and cacti for this long. Other damage sources are unaffected | No protection. A product can burn, sink into lava, or break on a cactus as soon as it appears |
| `conversion_cooldown_seconds` | A freshly produced item cannot start another conversion for this long, which prevents a product from converting on immediately under another rule | No cooldown. A product that matches a rule converts again right away |

Protection for mobs and other entities is not written to entity data: it is kept in memory under the entity identity and ends when the entity unloads or the server restarts. Those products take part in no conversion, so they have no cooldown.

The two settings are independent, so you can set them separately:

- Products often land next to lava and burn: raise `new_product_protection_seconds`, to `10` for example.
- Products turn into something else the moment they appear: raise `conversion_cooldown_seconds`.
- No extra protection wanted, with products destroyable and convertible again right away:

```json
{
  "new_product_protection_seconds": 0,
  "conversion_cooldown_seconds": 0
}
```

One more behavior that has nothing to do with these settings: a returned item, meaning a source item already handed back to the world, stays protected for its whole life and never converts again. Neither setting affects it.

## Tuning by Scenario

Each block below is part of `server.json`. Fields you leave out keep their defaults, so paste a block straight into the file. Restart the server after every change.

A weak server with a lot of drops, where smoothness comes first:

```json
{
  "server_budget_us": 1000,
  "effects_work_units_per_tick": 32,
  "check_interval_ticks": 40,
  "backoff_max_ticks": 200,
  "check_spread_window_ticks": 40,
  "dispatch_batch_size": 32
}
```

Effect: the mod spends less time per tick, checks less often, and spreads a large batch of simultaneous expirations more evenly. The cost is slower conversion overall, so a rule may take a moment to fire after its condition holds.

Faster conversion:

```json
{
  "check_interval_ticks": 10,
  "backoff_max_ticks": 40
}
```

Effect: after a condition fails once, the mod retries sooner, with the example setting the wait cap to 2 seconds. The cost is more checks in the same time.

Setting the work per tick on its own, where the `max_checks_per_tick` value applies while `max_work_units_per_tick` is absent:

```json
{
  "max_checks_per_tick": 512,
  "max_work_units_per_tick": 1024
}
```

Moving the rule overlay to another directory:

```json
{
  "overlay_directory": "my_idtw"
}
```

Effect: rules now load from `config/my_idtw/rules/`. Move the contents of the old `config/itemdespawntowhat/` directory into `config/my_idtw/` first, or none of the existing overlay rules load. `server.json` itself stays in `config/itemdespawntowhat/`.

## Fields You Normally Leave Alone

- `overlay_directory`: change it only when you want a different directory layout, because a mistake stops every overlay rule from loading.
- `max_checks_per_tick`, `max_work_units_per_tick`, `effects_work_units_per_tick`, `dispatch_batch_size`, `prepare_groups_per_step`, `position_search_checks_per_tick`, `rescan_entities_per_step`, `cancels_per_step`: the defaults suit most servers. Change them only when you see lag or a clear backlog.
- `fabric_lifespan_fallback_ticks`: used on Fabric until the current server run samples a reference drop lifespan.
- `debug_logging`: turn it on temporarily while you diagnose a problem.
- `settlement_recovery_max_attempts`: the number of recovery attempts in the current server session. Change it only when a dimension is expected to stay unavailable for a long time.
- `debug_scenario_prepare_batch_size`, `debug_scenario_prepare_budget_us`: these serve test scenarios in a development environment only, and normal play never needs them.
- The default limits under `nearby_products` are generous, so normal play rarely reaches them. Its two experience sub-fields no longer do anything, so leave them alone.

## Related

- File location and the first launch: [Getting Started](en-01-getting-started)
- Top-level rule fields, such as `trigger_after_seconds`: [Rule Fields](en-02-rule-basics)
- The overlay directory and source priority: [Source Layers and Overrides](en-06-layers-and-overrides)
- The return path and settlement: [Outcome Candidates and Settlement](en-05-outcomes-and-settlement)
- How to use each command: [Command Reference](en-08-commands)
