# Source Layers and Overrides

This page explains where the mod reads rules from, which layer wins, how to take over a built-in rule from the overlay, how to switch a rule on or off for one save, and which rule runs when several rules match the same dropped item.

The mod reads rules from three places and applies them together, and each place is a **source layer** (`builtin`, `world`, `overlay`). A **built-in datapack** (`builtin`) ships inside the mod, and a mod update replaces it. A **world datapack** (`world`) is a datapack you add to the world save. A **config overlay** (`overlay`) is a file under `config/itemdespawntowhat/rules/`, and it is the only layer you write by hand. Priority is fixed: config overlay > world datapack > built-in datapack.

For the basic rule fields (`id`, `priority`, `source`, and more), see [Rule Fields](en-02-rule-basics). Whether a conversion happens depends on conditions, covered in [Conditions](en-03-conditions). What a rule produces is covered in [Effects](en-04-effects) and [Outcome Candidates and Settlement](en-05-outcomes-and-settlement). How a file change takes effect is covered in [Getting Started](en-01-getting-started).

## The Three Source Layers and Their Priority

| Source layer | Rule file location | Can you edit it directly? | Priority |
| --- | --- | --- | --- |
| Built-in datapack (`builtin`) | `data/<namespace>/idtw/rules/*.json` inside the mod | No. A mod update replaces it. | Lowest |
| World datapack (`world`) | `datapacks/<pack name>/data/<namespace>/idtw/rules/*.json` in the world save | Yes, by shipping a datapack. | Middle |
| Config overlay (`overlay`) | Any `*.json` under `config/itemdespawntowhat/rules/`, subdirectories included | Yes. Commands and the rule editor write only here. | Highest |

- The mod reads all three layers at once and merges them. Priority decides only which copy wins for the same ID; it never drops rules that use another `id` in a lower layer.
- An overlay file name does not matter. The `id` inside the file is the identity of the rule, and subdirectories are read as well.
- A datapack file must sit under `data/<namespace>/idtw/rules/` and end in `.json`. When several datapacks contain the same file path, the copy from the higher-priority pack is applied later and therefore wins for the same ID (a later entry wins; see the next section).

## Overriding by ID: Full Replacement

**Override by ID**: when rules in different source layers carry exactly the same `id`, only the copy from the highest-priority layer stays, and it takes over as a **full replacement** rather than a field-by-field merge. The merge works by `id`:

- **Different IDs**: rules stack independently and do not affect each other.
- **The same ID**: only the highest-priority copy survives.

Example: the built-in rule `itemdespawntowhat:builtin_item_to_item` turns a chicken into rotten flesh after 300 seconds. The `display_name` and `notes` fields, which affect display only, are omitted here:

```json
{
  "id": "itemdespawntowhat:builtin_item_to_item",
  "enabled": true,
  "source": {
    "items": [
      "minecraft:chicken"
    ]
  },
  "trigger_after_seconds": 300,
  "triggers": [
    "natural"
  ],
  "source_cost": {"minecraft:chicken": 1},
  "combination": "round_robin",
  "schema_version": 1,
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "item": "minecraft:rotten_flesh",
      "count": 1,
      "variant": "item"
    }
  ]
}
```

Save the next file as `config/itemdespawntowhat/rules/chicken_to_gold.json`. It carries the **same `id`**:

```json
{
  "id": "itemdespawntowhat:builtin_item_to_item",
  "enabled": true,
  "priority": 10,
  "source": {
    "items": [
      "minecraft:chicken"
    ]
  },
  "trigger_after_seconds": 60,
  "triggers": [
    "natural"
  ],
  "source_cost": {"minecraft:chicken": 1},
  "combination": "round_robin",
  "schema_version": 1,
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "item": "minecraft:gold_ingot",
      "count": 1,
      "variant": "item"
    }
  ]
}
```

What happens after this change:

- The dropped item is still a chicken, because the `source` in effect is the one you wrote.
- The wait time drops from 300 seconds to 60 seconds, the product changes from rotten flesh to a gold ingot, and `priority` becomes 10.
- Only the overlay copy runs. The content of the built-in rule no longer takes part, and the fields you wrote are the only ones that count.
- A field you leave out falls back to its default instead of inheriting the base rule: an omitted `conditions` is always satisfied, an omitted `trigger_after_seconds` is 300, an omitted `enabled` is `true`, an omitted `combination` is `round_robin`, and an omitted `schema_version` is 1. **Write the whole rule when you override**, not only the fields you want to change.
- To undo the override, delete this overlay file, or change its `id` so the file becomes a new rule.

### Where the Rule ID Comes From

- The file declares an `id`: the mod uses it.
- The file holds exactly one rule and omits `id`: the mod derives the ID from the file path, in the form `idtw:<relative path without .json>`. Subdirectory names count too, so `rules/mypack/chicken_to_gold.json` becomes `idtw:mypack/chicken_to_gold`.
- The file holds a rule array (several rules): every entry must declare its own `id`, and an entry without one is rejected at load and reports an error.
- Two entries in the same file share an `id`: the later one is rejected at load.
- To override a built-in or datapack rule, your `id` must match the target exactly. One wrong character adds a new rule, and both rules stay. Use only lowercase letters, digits, and `_`, `-`, `.`, `/` in an `id`; an uppercase letter makes the mod reject that rule.

## Overlay Entries: Full Takeover

Every entry in the overlay is a **full rule** that uses the same fields and validation as a built-in or datapack rule:

- Writing a rule with the same `id` takes the rule over: the overlay entry replaces the whole base rule, and fields you leave out fall back to their defaults (see the previous section).
- Undoing a takeover means removing the overlay entry: delete the whole file, or remove that entry from a rule array, and the original built-in or datapack rule comes back unchanged. Change the `id` instead, and the entry becomes a new rule.
- When a config file carries an unregistered top-level field, loading reports a warning and keeps that field as written; the rule is still parsed as a full rule and its other fields apply as usual.

## Switching Rules On and Off Per Save: Local Enable State

Whether a rule runs is decided by two things:

- `enabled` inside the rule content is the **default enable state**: whether the author wants the rule on or off by default.
- The effective value is the setting this save holds for that rule, or the default enable state when the save holds none. That setting is the **local enable state**, and it is stored with the save rather than written into rule files.

Change it with commands (they need permission; the host of a singleplayer world always passes):

| Command | What it does |
| --- | --- |
| `/idtw rule <id> disable` | Turns the rule off in this save only; the rule content stays as written |
| `/idtw rule <id> enable` | Turns the rule back on in this save |
| `/idtw rule <id> default` | Drops the save's setting so the rule follows its default enable state again |

- Switching a rule off affects only whether it runs: content, priority, and conditions are read from the file as written, so a mod or datapack update still changes them.
- The setting travels with the save: another save or another server keeps its own state, and every player on a multiplayer server shares the same local state.
- A disabled rule still appears in `/idtw config list` and `/idtw rule list`, and that line shows "enabled=false".
- When the rule content disappears, for example because you deleted the overlay file or a datapack no longer provides the ID, the save's setting has no rule left to apply to and loading ignores it. `/idtw rule orphans` lists those records and `/idtw rule orphans clear` removes them. If the rule comes back later, an uncleared record applies again; a cleared one follows the default enable state.

What you want to do, and the tool for it:

| I want to... | Use |
| --- | --- |
| Replace all the content of a built-in or datapack rule | Write a full rule with the same `id` in the overlay |
| Undo a takeover and return to the built-in or datapack version | Delete the overlay file or the array entry |
| Turn a rule off for now, keeping its content and restoring it later | The local enable commands |
| Change the default switch state for new saves | `enabled` inside the rule JSON |

## Application Order Inside One Layer

1. **Between layers**: the mod always works from the built-in datapack to the world datapack to the config overlay, whatever the file names are.
2. **The same ID several times in one layer**: a later file wins. In a datapack layer the packs are compared first, so the higher-priority pack comes later and wins, then the file path decides; in the overlay the file path decides.
3. **The merged order**: it equals the order in which each `id` is first read (the built-in datapack, then the world datapack, then the config overlay, and the file order above inside a layer). That order is the **definition order** used in the next section.
4. **The enable state stays out of the merge**: the merge produces rule content in the order above, and the save's switch settings are applied to each rule afterwards, at the end of assembly.

## When Several Rules Match the Same Dropped Item

One dropped item runs exactly **one** rule at a time. The rules queue up by three keys, and the mod takes the first rule that declares the **trigger kind** for this destruction path and whose conditions match. A **trigger kind** (`triggers`) is one way a dropped item is destroyed: `natural`, `fire`, `lava`, or `cactus`. A rule that omits `triggers` applies to `natural` only.

1. **Priority** (`priority`, an integer, default 0): a higher value goes first. See [Rule Fields](en-02-rule-basics) for the field itself.
2. **Condition leaf count** (the number of leaf nodes in the condition tree): more leaves go first. Under `all_of` two leaves count as 2, `inverted` around one leaf counts as 1, and a combination node does not count by itself. A rule with more specific conditions is more likely to win; see [Conditions](en-03-conditions) for the syntax.
3. **Definition order** (the order in which each `id` is first read): when the first two keys tie, the rule read first runs first.

A rule that queues higher but whose conditions do not match is skipped, and the mod keeps looking until it finds one it can run. When no rule matches, the dropped item does not convert this time, and the mod retries on a **backoff** rhythm: the interval starts at 1 second and grows, up to the cap in `backoff_max_ticks` (see [Server Configuration](en-07-server-config)).

Use `priority` to control the result. The built-in rule `itemdespawntowhat:builtin_multi_effect` (Diamond Thunder) already covers diamonds at priority 0. To send diamonds to a different product without overriding that rule, add a rule with your own `id` and a higher priority:

```json
{
  "id": "mypack:diamond_to_gold",
  "priority": 10,
  "source": {
    "items": [
      "minecraft:diamond"
    ]
  },
  "trigger_after_seconds": 60,
  "triggers": [
    "natural"
  ],
  "effects": [
    {
      "type": "idtw:spawn_entity",
      "item": "minecraft:gold_ingot",
      "count": 1,
      "variant": "item"
    }
  ]
}
```

What happens after this change: the diamond follows your rule, because priority 10 beats 0. The built-in rule is no longer selected, but it stays in the active rule list, where it never gets a turn.

Note that an overlay does **not** outrank a built-in or datapack rule on its own; the layer decides only for the same ID. With a new `id` you need a higher `priority` or more specific conditions to win a turn. To change how a built-in rule behaves, an override by ID is the safest option.

## Finding Out Which Layer and File a Rule Comes From

These three commands answer that question, and each one requires a singleplayer world or permission level 2 or higher. A singleplayer world always passes.

- `/idtw config list` lists the active rules one by one (`id`, source layer, priority, trigger seconds, effect count, enabled state, origin), and its first line is a **summary line** (the total count, the count per source layer, and the number of errors and warnings).
- `/idtw rule list` prints a shorter list (`id`, source layer, enabled state, origin). Use it to scan many rules quickly.
- `/idtw rule show <id>` prints one rule (priority, trigger seconds, effect count, enabled state) plus its raw JSON. For an overlay rule you see your own file text, and for a datapack rule you see an equivalent JSON text you can copy and edit.

The enabled state in all three commands is the **effective value for this save** (the local setting, or `enabled` from the rule content when the save holds none). Change it with `/idtw rule <id> enable|disable|default`; the full syntax is in [Command Reference](en-08-commands).

One line of `/idtw config list` looks like this:

```text
mypack:diamond_to_gold | layer=overlay | priority=10 | trigger=60s | effects=1 | enabled=true | origin=config 覆盖层:rules/diamond_to_gold.json
```

The `layer` value is `builtin`, `world`, or `overlay`. The **origin** (the `origin` value) reads `layer name[pack name]:file path`, and only a datapack layer carries the pack name in brackets, for example `世界数据包[mypack]:data/mypack/idtw/rules/xxx.json`. An overlay rule shows a path relative to `config/itemdespawntowhat`. The layer name inside `origin` comes from the mod and is not translated.

Reload after you change a file. The full syntax of the commands is in [Command Reference](en-08-commands).

## Troubleshooting: An Override or Disable That Seems to Do Nothing

Check these in order:

1. **File location and extension.** An overlay file must sit under `config/itemdespawntowhat/rules/` (subdirectories are allowed) and end in `.json`. A datapack file must sit under `data/<namespace>/idtw/rules/`.
2. **An `id` that matches the target exactly.** One wrong character adds a new rule, and both rules stay, so nothing is overridden.
3. **A successful load.** Run `/idtw config validate` or `/idtw config list` and read the errors and warnings, which name the source and the field path. A JSON syntax error, an empty file, or a top level that is neither a rule object nor a rule array makes the mod reject the whole file. An error inside one rule stops only that rule.
4. **A rule switched off in this save.** `enabled` in the rule content is only the default; the local setting for this save wins. Read the enabled column of `/idtw config list`, which shows the effective value, and use `/idtw rule <id> default` to drop the local setting.
5. **Another rule taking over.** One dropped item runs one rule, so check for a rule with a higher priority or more condition leaves that also covers this item. Use `/idtw config list` and read the priority and the origin.
6. **A reload after the change.** See [Getting Started](en-01-getting-started).

## Related

- [Getting Started](en-01-getting-started)
- [Rule Fields](en-02-rule-basics)
- [Outcome Candidates and Settlement](en-05-outcomes-and-settlement)
- [Command Reference](en-08-commands)
