# Server Configuration (server.json)

> Applies to **2.0.0** (Minecraft **1.21.1**).

This page details the settings in `server.json`, their default values, and common tuning scenarios.

These settings govern server runtime performance and safety budgets (such as check frequency, per-tick processing budgets, and product protection cooldowns). They **do not define conversion products**. To add or edit conversion rules, see [Rule Fields](en-02-rule-basics).

---

## File Behavior & Application

- **File location**: `config/itemdespawntowhat/server.json`, located in the global `config` directory;
- **Unified configuration**: Fabric and NeoForge use the exact same file and format;
- **Safe modifications**: Generated automatically on first launch; missing fields fall back to their official defaults;
- **Applying changes**: **You must restart the server or game client for edits to take effect**. The command `/idtw rule reload` reloads rule files only, not server configuration.

### Server Config vs Rule Files

| Dimension | Server Config (`server.json`) | Rule Files (`rules/*.json`) |
| --- | --- | --- |
| **Main Purpose** | Performance budgets and lag prevention | What items convert into |
| **Scope** | Server-wide; affects all rules | Independent per rule |
| **Location** | `config/itemdespawntowhat/server.json` | `config/itemdespawntowhat/rules/` or datapacks |
| **How to Apply** | **Restart server/game** | Run `/idtw rule reload` |

---

## Default Configuration

Here is the default configuration generated on first startup:

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

---

## Field Reference

Time parameters are specified in game ticks (normally 20 ticks = 1 second).

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

---

## Nearby Product Density Limits (`nearby_products`)

When items or entities accumulate heavily in one location, the mod pauses new conversions in that radius. Excess items are safely returned to the ground to protect server TPS.

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

*Tip: Setting `item_limit` or `entity_limit` to `0` removes the limit for that product type.*

---

## Common Tuning Scenarios

Add any of these snippets to `config/itemdespawntowhat/server.json`. **Restart the server or game client after modifying**.

### Scenario 1: Low-spec servers or mob farms with massive drops

Lower the time budget and increase intervals to protect TPS:

```json
{
  "server_budget_us": 2000,
  "check_interval_ticks": 40,
  "backoff_max_ticks": 200,
  "check_spread_window_ticks": 40,
  "dispatch_batch_size": 32
}
```

### Scenario 2: Responsive environment reactions

Lower check intervals so items convert faster when conditions are met:

```json
{
  "check_interval_ticks": 10,
  "backoff_max_ticks": 40
}
```

### Scenario 3: Prevent products burning in lava or fire immediately

Increase new product protection time (e.g. 10 seconds):

```json
{
  "new_product_protection_seconds": 10
}
```

### Scenario 4: Prevent endless conversion loops

Increase cooldown to prevent secondary rules immediately consuming new outputs:

```json
{
  "conversion_cooldown_seconds": 15
}
```

---

## Next Steps

- [Getting Started](en-01-getting-started): Walkthrough and setup.
- [Rule Fields](en-02-rule-basics): Complete reference for rule options.
- [Command Reference](en-08-commands): All in-game commands.
