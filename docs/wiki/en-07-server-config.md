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
- Fields that the table below does not list are not read and do not affect the game.

The **overlay directory** is the folder under `config/` that holds your own rule files, and `overlay_directory` is the only setting that moves it. How `server.json` differs from rule files:

| Item | `server.json` | Rule Files |
|---|---|---|
| What it is | Server run parameters | Conversion content, meaning what turns into what |
| Scope | One file for the whole server, shared by every rule | Each rule is independent |
| Location | `config/itemdespawntowhat/server.json` | `config/itemdespawntowhat/rules/*.json`, or `data/<namespace>/idtw/rules/*.json` in a data pack |
| How it applies | Restart the server | `/idtw config reload` |

You can indent and wrap the file however you like. The game writes it as one compact JSON line on the first launch, and the two forms are equivalent.

## Default Configuration

The block below is the complete content the game creates on the first launch, and it is also the same as leaving every setting at its default:

```json
{
  "check_interval_ticks": 20,
  "backoff_max_ticks": 100,
  "max_checks_per_tick": 512,
  "overlay_directory": "itemdespawntowhat",
  "fabric_lifespan_fallback_ticks": 6000,
  "debug_logging": false,
  "server_budget_us": 2000,
  "effects_work_units_per_tick": 64,
  "check_spread_window_ticks": 20,
  "dispatch_batch_size": 64,
  "new_product_protection_seconds": 2,
  "conversion_cooldown_seconds": 5,
  "position_search_checks_per_tick": 16,
  "debug_scenario_prepare_batch_size": 128,
  "debug_scenario_prepare_budget_us": 2000,
  "max_place_block_radius": 8,
  "prepare_groups_per_step": 8,
  "nearby_products": {
    "item_limit": 1024,
    "item_radius": 6,
    "entity_limit": 128,
    "entity_radius": 6,
    "experience_orb_limit": 64,
    "experience_radius": 6
  },
  "rescan_entities_per_step": 16,
  "cancels_per_step": 1
}
```

`max_work_units_per_tick` does not appear in the file by default, because its fallback applies only while the entry is absent. See the next section. Its absence from the generated file is normal.

## All Fields

Time values are in game ticks, and 20 ticks equal 1 second on a normal server.

| Field | What It Does | Range | Default | When to Change It |
|---|---|---|---|---|
| `check_interval_ticks` | How long to wait before checking a condition again while it does not hold. The first retry uses this interval, and every retry after that doubles it | 1 to 1200 ticks | `20` (1 second) | Lower it to convert sooner after a condition starts holding. Raise it to check less often when many drops are loaded |
| `backoff_max_ticks` | The cap on that doubling. A condition that stays false waits at most this long before the next check | 1 to 72000 ticks | `100` (5 seconds) | Lower it so a rule that will hold later retries sooner. Raise it to save more performance at the cost of slower reaction |
| `max_checks_per_tick` | The checks-per-tick ceiling, used only while `max_work_units_per_tick` is absent | 1 to 100000 | `512` | Change it only when you want to control how many checks run per tick. Most servers never need this |
| `overlay_directory` | The name of the rule overlay directory, relative to `config/`, so rules live in `config/<this value>/rules/` | Any directory name (string) | `"itemdespawntowhat"` | Change it only to rename the config directory. Move the whole directory with it, or the overlay rules stop loading. The location of `server.json` itself does not change |
| `fabric_lifespan_fallback_ticks` | Fabric only: the tick count to assume for how long a drop can still live when the mod cannot read its real lifespan | 1 to 72000 ticks | `6000` (5 minutes) | The default matches how long vanilla drops live. Adjust it only when the server changes drop lifespan rules |
| `debug_logging` | Whether the mod writes its run-time progress to the log, such as a drop whose condition does not hold and how many ticks until the next retry | `true` or `false` | `false` | Turn it on temporarily while you diagnose why something does not convert. Keep it off otherwise, because it floods the log |
| `server_budget_us` | The time in microseconds the mod may spend per game tick. Work that does not fit continues over the following ticks | 100 to 100000 microseconds | `2000` (about 2 milliseconds) | Lower it for extra safety on a busy server. Raise it when conversions lag and the server has spare capacity |
| `max_work_units_per_tick` | The total work-unit ceiling per tick. It applies together with `server_budget_us`, and whichever comes first wins | 1 to 100000 | Absent, which uses the `max_checks_per_tick` value of `512` | Leave it absent. Write it only when you need to set the work per tick on its own |
| `effects_work_units_per_tick` | The per-tick quota reserved for running effects, such as spawning items, placing blocks, and granting experience, so that heavy output does not crowd out the checks | 1 to 100000 | `64` | Raise it when a large batch of products takes too long to appear. Lower it to keep the checks prompt |
| `check_spread_window_ticks` | When many drops come due at once, the mod spreads those checks over a window of this many game ticks | 1 to 1200 ticks | `20` (1 second) | Raise it when a simultaneous batch causes lag, which is smoother but delays individual conversions. Lower it to keep conversions on time when a single tick can take the load |
| `dispatch_batch_size` | How many queued conversions the mod advances per tick at most, and it also caps the returned items delivered in one batch | 1 to 100000 | `64` | Raise it when large batches back up |
| `prepare_groups_per_step` | How many conversion groups one settlement fixes its effect inputs for per tick before it starts paying groups. The remaining groups continue on later ticks | 1 to 4096 | `8` | Raise it to finish heavy preparation sooner at the cost of more per-tick work. Lower it to spread preparation over more ticks |
| `new_product_protection_seconds` | How many seconds a freshly converted item resists destruction by fire, lava, and cacti | 0 to 3600 seconds | `2` | Raise it when products burn as soon as they appear, for example next to lava. Set `0` to let products be destroyed right away |
| `conversion_cooldown_seconds` | How many seconds a freshly converted item cannot start another conversion | 0 to 3600 seconds | `5` | Raise it when products convert again immediately under another rule. Set `0` to let a product convert again at once |
| `position_search_checks_per_tick` | How many candidate positions the mod tries per tick when it looks for a spot for a product or a returned item | 1 to 4096 | `16` | Raise it when items stay undelivered because the terrain leaves few spots. Lower it to save performance |
| `rescan_entities_per_step` | How many loaded drops one step rebuilds when tracking is rebuilt after startup, a dimension load, or a rule reload. The rest continues over later ticks | 1 to 4096 | `16` | Lower it when a server with tens of thousands of drops still hitches on a single tick after startup or a reload. Raise it to finish sooner, at the cost of more work per tick |
| `cancels_per_step` | How many queued tasks one step cancels when a rule reload, a dimension unload, or a server stop detaches them. The rest continues over later ticks | 1 to 4096 | `1` | Keep it at the default (or lower) when a reload or unload still spikes on a single tick. Raise it to finish clearing old queues sooner, at the cost of more cancel callbacks per tick |
| `debug_scenario_prepare_batch_size` | Affects only the preparation stage of test scenarios in a development environment, with no effect on normal play | 1 to 100000 | `128` | Players never need to change it |
| `debug_scenario_prepare_budget_us` | The time cap in microseconds for that same preparation stage | 1 to 100000 microseconds | `2000` | Players never need to change it |
| `max_place_block_radius` | Largest `radius` a single `place_block` action may use; rules above it are rejected when loaded or saved | 1 and above | `8` | Raise it to let one action cover a wider area, but the cost of one placement grows with the cube of the radius, so keep it modest; lowering it rejects existing rules that now exceed the cap, so edit those rules as well |
| `nearby_products` | The nearby product limits. The number of products already near the destination decides whether a conversion group can start. See the next section | Object | See the next section | Lower it when piled-up products cause lag. Set the matching limit to `0` to remove the cap |

`new_product_protection_seconds` and `conversion_cooldown_seconds` accept `0`, and no other field does. `server_budget_us` has a minimum of `100`, not `1`. Any value outside its range makes the whole file fall back to the defaults and logs an error, as described in the previous section.

## Nearby Product Limits

Before a conversion group starts, the mod counts how many similar products already sit near the destination. If the count has reached the limit, that group does not start. The source items it would have used are then not consumed, as the notes below explain. A group that has already started is never cut short when the count grows, so it finishes normally and can push the count past the limit. This check covers products only: it counts the items and generic entities a candidate would produce, and nearby experience orbs never stop a group over experience storage. A group that also produces items or generic entities still passes their own capacity checks.

These limits apply to every rule. They are not set per rule.

| Sub-field | What It Does | Range | Default |
|---|---|---|---|
| `item_limit` | The cap on the total number of nearby items of the same kind, counted by the item or item tag that would be produced | 0 to 1000000 | `1024` |
| `item_radius` | The counting radius in blocks for the limit above, as a cube centered on the source block with an edge length of 2 × radius + 1 | 1 to 32 | `6`, an edge of 13 blocks |
| `entity_limit` | The cap on the number of nearby entities of the same kind, counted by the entity type or entity tag that would be produced | 0 to 1000000 | `128` |
| `entity_radius` | The entity counting radius in blocks, with the same meaning as above | 1 to 32 | `6` |
| `experience_orb_limit` | A kept field. It stays in the file so existing configs keep working, and it no longer affects any conversion | 0 to 1000000 | `64` |
| `experience_radius` | A kept field as well. It maps to no counting radius at all | 1 to 32 | `6` |

Notes:

- `0` turns the matching check off, which means no limit. Of the three `*_limit` fields, only the item and generic entity ones still work; the experience one is kept but does nothing. Only `item_radius` and `entity_radius` are real counting radii, and neither accepts `0`. `experience_radius` still accepts 1 to 32, but its value has no effect.
- A limit only decides whether a new conversion group can start. It is not a hard ceiling on what exists nearby, and it does not keep the count at or below the limit.
- A group stopped by a limit consumes no source items. The source items that cannot start a group go through the return path, as described in [Outcome Candidates and Settlement](en-05-outcomes-and-settlement). A returned item never converts again, so the surplus does not queue up for a later slot.
- Lowering a limit makes conversions easier to postpone, and heavy product buildup can stop output for a long time. When the item and generic entity limits are both in use, the tightest one decides.
- The two experience fields exist only so that existing config files keep loading. Within their allowed ranges, no value you write there changes conversion behavior.

Example: lift the nearby count limits for items and generic entities while the other settings keep their defaults. The two experience fields already do nothing, so leave them out:

```json
{
  "nearby_products": {
    "item_limit": 0,
    "entity_limit": 0
  }
}
```

## Product Protection and Conversion Cooldown

These two settings affect only newly produced items. Returned items follow other rules, as the note at the end of this section explains.

| Field | What It Does | Default | What `0` Means |
|---|---|---|---|
| `new_product_protection_seconds` | A freshly produced item resists destruction by fire, lava, and cacti for this long. Other damage sources are unaffected | `2` seconds | No protection. A product can burn, sink into lava, or break on a cactus as soon as it appears |
| `conversion_cooldown_seconds` | A freshly produced item cannot start another conversion for this long, which prevents a product from converting on immediately under another rule | `5` seconds | No cooldown. A product that matches a rule converts again right away |

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

Effect: after a condition fails once, the mod retries sooner, and the wait cap drops from 5 seconds to 2. The cost is more checks in the same time.

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
- `max_checks_per_tick`, `max_work_units_per_tick`, `effects_work_units_per_tick`, `dispatch_batch_size`, `position_search_checks_per_tick`, `rescan_entities_per_step`, `cancels_per_step`: the defaults suit most servers. Change them only when you see lag or a clear backlog.
- `fabric_lifespan_fallback_ticks`: used only when the Fabric side cannot read a drop's lifespan.
- `debug_logging`: turn it on temporarily while you diagnose a problem.
- `debug_scenario_prepare_batch_size`, `debug_scenario_prepare_budget_us`: these serve test scenarios in a development environment only, and normal play never needs them.
- The default limits under `nearby_products` are generous, so normal play rarely reaches them. Its two experience sub-fields no longer do anything, so leave them alone.

## Related

- File location and the first launch: [Getting Started](en-01-getting-started)
- Top-level rule fields, such as `trigger_after_seconds`: [Rule Fields](en-02-rule-basics)
- The overlay directory and source priority: [Source Layers and Overrides](en-06-layers-and-overrides)
- The return path and settlement: [Outcome Candidates and Settlement](en-05-outcomes-and-settlement)
- How to use each command: [Command Reference](en-08-commands)
