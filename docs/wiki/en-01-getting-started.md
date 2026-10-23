# Getting Started

This page walks you through writing your first rule, putting it in the right place, and confirming with commands that it takes effect.

## What This Mod Does

ItemDespawnToWhat converts a dropped item into an item, mob, block, experience, or world event when one of its trigger kinds applies.

A **dropped item** is the single item stack that lands on the ground after you drop it or break a block. A **trigger kind** (the `triggers` field) is the way a dropped item is destroyed: natural despawn, fire, lava, or cactus.

| Trigger kind | When it is checked | Does it wait |
| --- | --- | --- |
| `natural` | The moment the dropped item reaches its lifespan | Yes, for `trigger_after_seconds` |
| `fire` | The moment the item burns up | No |
| `lava` | The moment lava destroys the item | No |
| `cactus` | The moment a cactus destroys the item | No |

Here are a few results you can build with rules:

| What the rule produces | Description |
| --- | --- |
| Item on expiry | A chicken turns into rotten flesh after 300 seconds (enabled by default, so you can watch it) |
| Mob on expiry | An egg hatches a baby chicken after 300 seconds outdoors in the Overworld |
| Block placement | Places the sapling block that matches the source item |
| Loot drop | Rolls a dungeon chest loot table and produces nothing 50% of the time |
| Thunder and weather | Strikes lightning, or turns a thunderstorm into rain |
| Explosion | Detonates once in low light or in weather that is not clear |

After a rule matches, the mod settles the work in groups: one group consumes the declared number of source items and expands its output for the whole group. If fewer source items are present than one group needs, the mod returns the rest to you or leaves it on the ground. The **return** step is called a payback. For details, see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).

## Where Configuration Lives

Rules come from two locations with different write access, and the mod merges them by ID when it loads them:

| Location | Write access | Notes |
| --- | --- | --- |
| `config/itemdespawntowhat/rules/**/*.json` | Writable (config overlay) | Write your own rules here; this is also where the editor and `/idtw config edit` write |
| `data/<namespace>/idtw/rules/**/*.json` | Read-only (datapack) | Supplied by datapacks, including the built-in samples; syncs through the vanilla datapack mechanism |

A **source layer** is one of those locations. It decides which copy of a rule wins. Merge order, from lowest to highest, is builtin, world, then config overlay. When two layers hold the same ID, the higher layer replaces the lower one as a whole.

On first start, the mod also creates `config/itemdespawntowhat/server.json`. That file holds only server performance and operations settings, such as check intervals, batch limits, and debug logging. It has nothing to do with rule content; for the full field list, see [Server Configuration](en-07-server-config). Its `overlay_directory` field is the name of the overlay folder and defaults to `itemdespawntowhat`, which is why the writable path above reads `config/itemdespawntowhat`. Leave it alone unless you have a specific reason to change it.

You can put overlay rules in any subfolder of `rules/`, and the extension must be `.json`, for example `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json`. The subfolder only affects the derived rule ID; it does not affect loading.

## Rule IDs and File Names

Every rule needs an ID in the form `namespace:path`, for example `myrules:chicken_to_rotten_flesh`. You can supply it in two ways:

- **Write the `id` field**: works in any file shape, and it is the only accepted way for a file that holds several rules.
- **Omit `id`**: allowed only when the file holds exactly one rule. The mod then derives the ID from the file path.

The derivation depends on where the file lives:

| Rule file path | Derived ID |
| --- | --- |
| `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json` | `itemdespawntowhat:myrules/chicken_to_rotten_flesh` |
| `config/itemdespawntowhat/rules/egg_hatch.json` | `itemdespawntowhat:egg_hatch` |
| Datapack `data/myreload/idtw/rules/crops/bean.json` | `myreload:crops/bean` |

Key points:

- For the overlay, the namespace is always the mod namespace `itemdespawntowhat`, and the path is the relative path after `rules/` with `.json` removed.
- For a datapack, the namespace is the pack's own namespace, and the path is the relative path after `idtw/rules/` with `.json` removed.
- To keep IDs clean and predictable, write `id` in the JSON. The file name then does not matter.
- When the file holds an array of several rules, every entry must declare `id`. Otherwise the mod rejects that entry at load.
- IDs must be unique within one file. An invalid ID, such as one with uppercase letters or spaces, also rejects that entry.

## A Minimal Working Rule

Save this content as `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json`:

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "source": {
    "items": [
      "minecraft:chicken"
    ]
  },
  "trigger_after_seconds": 10,
  "effects": [
    {
      "type": "itemdespawntowhat:spawn_entity",
      "variant": "item",
      "item": "minecraft:rotten_flesh",
      "count": 1
    }
  ]
}
```

Field by field:

| Field | What it does | Values and ranges | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | The identity of the rule | `namespace:path` | Derived from the file path when a single-rule file omits it | See the section above |
| `source` | Which dropped items the rule claims | Object; `items` is required and non-empty, `exclude` is optional | None; required | Each entry in `items` is an item ID or an item tag such as `"#minecraft:saplings"` |
| `trigger_after_seconds` | How many seconds the dropped item survives before the rule triggers | Non-negative integer | `300` | The rule never fires later than the item's natural despawn |
| `effects` | What happens when the rule triggers | Non-empty array; use either `effects` or `outcomes` | None; at least one of the two | Each element is an **effect** and must carry `type` |

The rule above omits two fields you can ignore for now: `enabled` (already `true` by default; write `false` to keep the rule out of matching) and `triggers` (defaults to `natural` only). For the full description, see [Rule Fields](en-02-rule-basics).

**What happens next**: any `minecraft:chicken` dropped item disappears at second 10 and spawns one `minecraft:rotten_flesh` dropped item in the same place. Because the rule declares no source cost, the group consumes one source item by default, which is one chicken. Without such a rule, the chicken waits for its natural despawn and vanilla removes it.

## Making a Rule Take Effect

The whole flow has three steps:

1. **Place the file**: put the JSON under `config/itemdespawntowhat/rules/` or under `data/<namespace>/idtw/rules/` in a datapack. Save the file to disk after every edit.
2. **Check for errors**: run `/idtw config validate`. It reads every source again from what is on disk and reports once. It first prints a **summary line** (total rules, the count per source layer, errors, warnings), then lists each **issue line** in the form `[error] message origin#field_path`. It only checks; it does not change the rules that are active and does not apply your edits.
3. **Apply**: run `/idtw config reload`. It reads every rule again and applies the new content immediately, printing the same summary line and issue list as `validate`.

Two points to remember:

- **Reload after every file change** (run `/idtw config reload`, or reload datapacks, or restart the server). Otherwise the game never reads your edits.
- **Only the broken part is rejected**: when the whole file fails to parse as JSON, or when the root is neither a rule object nor a rule array, none of that file takes effect. A field error in one rule rejects only that rule; other rules in the same file still load.

The commands need you to own the singleplayer save, or to hold permission level 2 or higher. For the other commands, see [Command Reference](en-08-commands).

To edit rules in the built-in editor instead, `/idtw config edit` is an equivalent entry point. A player runs it, and the server opens the editor after taking the edit lock.

## Built-in Sample Rules

The mod ships a set of sample rules in the builtin datapack. Some of them are enabled by default, so you can observe results right away:

| Enabled | Rule ID | Effect |
| --- | --- | --- |
| Yes | `itemdespawntowhat:builtin_item_to_item` | A chicken becomes rotten flesh after 300 seconds |
| Yes | `itemdespawntowhat:builtin_item_to_entity` | An egg hatches a baby chicken outdoors in the Overworld |
| Yes | `itemdespawntowhat:builtin_catalyst_and_fluid` | Redstone becomes glowstone dust next to fluid and a catalyst |
| Yes | `itemdespawntowhat:builtin_loot_and_chance` | A gold nugget rolls a loot table, 50% chance |
| Yes | `itemdespawntowhat:builtin_multi_effect` | A diamond outside the height range grants experience and strikes lightning |
| Yes | `itemdespawntowhat:builtin_thunder_condensation` | A wet sponge changes the weather in a thunderstorm at sufficient height and consumes water |
| No | `itemdespawntowhat:builtin_item_to_block` | A sapling plants itself |
| No | `itemdespawntowhat:builtin_arrow_rain` | Rain in the Overworld brings down a volley of arrows |
| No | `itemdespawntowhat:builtin_biome_or_time` | An apple becomes a golden apple in a forest or in daytime |
| No | `itemdespawntowhat:builtin_dark_blast` | Gunpowder explodes in the dark or in weather that is not clear |

**How to inspect them**: run `/idtw config list` to list the active rules one by one. Each line shows the ID, source layer, priority, trigger seconds, effect count, and enabled state. You can also open `data/itemdespawntowhat/idtw/rules/` in the datapack and read the samples.

**How to disable or delete them**: builtin rules are read-only, so you cannot edit them directly. Use an **override control entry** with the same ID in the config overlay to disable or delete one. For that syntax and the merge rules, see [Source Layers and Overrides](en-06-layers-and-overrides).

## Rule Not Working? Check in Order

1. **Is the file in the right place?** It must sit under `config/itemdespawntowhat/rules/` or under `data/<namespace>/idtw/rules/` in a datapack, with the extension `.json`. A file in the wrong folder does nothing.
2. **Is the JSON valid?** A syntax error, a stray comma, or a root that is neither a rule object nor a rule array keeps the whole file out, and `validate` reports "JSON 解析失败" or "规则文件顶层必须是规则对象或规则数组".
3. **Is `enabled` set to `false`?** A disabled rule still appears in the load list (`config list` shows "启用=false"), but it does not take part in matching. A builtin rule you disabled through an overlay also shows as not enabled.
4. **Does `source` match?** Check the spelling of the item ID, its namespace, and the tag name. An unknown static item ID rejects that rule. Also check that `exclude` is not removing the item you want.
5. **Are the trigger kind and timing right?** With no `triggers` field, only `natural` applies. To watch a burn, a lava, or a cactus case, list that value in `triggers`. When `trigger_after_seconds` is longer than the item actually survives, the item despawns first and the rule never fires.
6. **Are the conditions met?** If any condition in `conditions` fails, the mod never picks that rule. For condition types and semantics, see [Conditions](en-03-conditions).
7. **Did a higher-priority rule win?** One dropped item runs one rule at a time. The mod sorts candidates by higher `priority` first, then by more condition leaves, then by definition order, and picks the first rule whose trigger kind matches and whose conditions hold. When several rules claim the same item, raise the `priority` of the one you want, narrow its `source`, or tighten its conditions.

Start with the output of `/idtw config validate`. It tells you which origin and which field path holds the problem.

## Related

- [Rule Fields](en-02-rule-basics): which top-level fields a rule accepts and what each default is.
- [Conditions](en-03-conditions): when a rule is allowed to fire.
- [Effects](en-04-effects): what a rule does when it fires.
- [Command Reference](en-08-commands): every command for checking and applying your edits.
