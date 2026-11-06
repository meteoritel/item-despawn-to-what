# Getting Started

> Applies to **2.0.0** (Minecraft **1.21.1**).

Create your first rule with the in-game GUI. Version 2.0.0 ships without a built-in rule datapack, and configurations from older versions need to be recreated.

## Create Your First Rule with the GUI

This example turns a dropped chicken item into rotten flesh after 10 seconds. The screenshots show the English interface and control locations; choose chicken and rotten flesh for this example.

Install the mod and enter a world. In multiplayer, install it on the server and on the client of the player using the editor. Single-player world owners have access directly; other players need permission level 2 or higher. One player can edit at a time.

### 1. Open the Editor

Run this command in chat:

```text
/idtw editor open
```

The shorter command `/idtw editor` also works.

![Entering the command that opens the editor](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/wiki-assets/2.0.0/editor-open-command.webp?raw=true)

### 2. Create a Rule

Click **New rule**, replace the name with `myrules:chicken_to_rotten_flesh`, and click **Confirm**. The rule ID identifies the rule. Use lowercase letters, numbers, and underscores; the part before the colon is the namespace.

![The new rule dialog and name field](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/wiki-assets/2.0.0/editor-new-rule.webp?raw=true)

### 3. Choose the Input Item

On the **Input** tab, click **Add** under **Source items** and select Chicken (`minecraft:chicken`). Set its **Source item consumption** to `1` so that each conversion group consumes one chicken.

![Source item selection and consumption on the Input tab](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/wiki-assets/2.0.0/editor-input.webp?raw=true)

### 4. Set the Trigger Time

Switch to **Trigger**, keep **Natural (item despawn)** selected, and set **Trigger time (seconds)** to `10`. Leave the conditions empty for this first rule.

### 5. Set the Result

Switch to **Results**, click `+` in the **Actions** panel on the left, and add the **Entities** action.

![The add action button on the Results tab](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/wiki-assets/2.0.0/editor-add-action.webp?raw=true)

On the right, set **Product** to **Items**, click **Add** next to **Item**, choose Rotten Flesh (`minecraft:rotten_flesh`), and set **Count** to `1`.

![Item selection and output count in the Entities action](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/wiki-assets/2.0.0/editor-item-output.webp?raw=true)

### 6. Apply and Try the Rule

Click **Apply changes (1 rules)** at the bottom. Keep the screen open while saving and wait for a successful save. The GUI reloads rules automatically after a successful save.

Return to the game, drop one chicken on the ground, and wait about 10 seconds. One rotten flesh item will appear at the same location. You can also run `/idtw rule list` to find the rule, or `/idtw rule show myrules:chicken_to_rotten_flesh` to read its summary.

### Add Conditions When You Need Them

To restrict the rule to specific weather, dimensions, or surroundings, open the condition tree on the **Trigger** tab. Click `+`, choose a condition type, and fill in its parameters. The screenshot shows where to select a weather condition. See [Conditions](en-03-conditions) for condition types and combinations.

![Choosing a condition type in the condition tree](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/wiki-assets/2.0.0/editor-conditions.webp?raw=true)

Click **Apply changes** again after editing. Hover over fields and buttons to read their tooltips.

## What This Mod Does

ItemDespawnToWhat converts a dropped item into an item, mob, block, experience, or world event when one of its trigger kinds applies.

A **dropped item** is the single item stack that lands on the ground after you drop it or break a block. A **trigger kind** (the `triggers` field) is the way a dropped item is destroyed: natural despawn, fire, lava, or cactus.

| Trigger kind | When it is checked | Does it wait |
| --- | --- | --- |
| `natural` | The moment the dropped item reaches its lifespan | Yes, for `trigger_after_seconds` |
| `fire` | The moment the item burns up | No |
| `lava` | The moment lava destroys the item | No |
| `cactus` | The moment a cactus destroys the item | No |

Here are examples of results you can configure:

| What the rule produces | Description |
| --- | --- |
| Item on expiry | A dropped chicken item turns into rotten flesh after 300 seconds |
| Mob on expiry | An egg hatches a baby chicken after 300 seconds outdoors in the Overworld |
| Block placement | Places the sapling block that matches the source item |
| Loot drop | Rolls a dungeon chest loot table and produces nothing 50% of the time |
| Thunder and weather | Strikes lightning, or turns a thunderstorm into rain |
| Explosion | Detonates once in low light or in weather that is not clear |

After a rule matches, the mod settles the work in groups: one group consumes the declared number of source items and expands its output for the whole group. If fewer source items are present than one group needs, the mod returns the rest to you or leaves it on the ground. The **return** step is called a payback. For details, see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).

## Where Configuration Lives

Supply rules through config files or world datapacks. The mod merges them by ID. The paths below are on the server; single-player uses your local game directory and world save.

| Location | How to edit | Notes |
| --- | --- | --- |
| `config/itemdespawntowhat/rules/**/*.json` | GUI or manual JSON edits | The config overlay, where the GUI saves rule content |
| `<world save>/datapacks/<pack>/data/<namespace>/idtw/rules/**/*.json` | Edit datapack files | World datapack rules, used as a read-only base in the GUI |

A **source layer** decides which copy of a rule wins for the same ID. World datapacks load first, followed by the config overlay. For the same ID, the complete overlay rule replaces the datapack rule. Rules with different IDs are kept independently.

On first start, the mod also creates `config/itemdespawntowhat/server.json`. That file holds only server performance and operations settings, such as check intervals, batch limits, and debug logging. It has nothing to do with rule content; for the full field list, see [Server Configuration](en-07-server-config). Its `overlay_directory` field is the name of the overlay folder and defaults to `itemdespawntowhat`, which is why the writable path above reads `config/itemdespawntowhat`. Leave it alone unless you have a specific reason to change it.

You can put overlay rules in any subfolder of `rules/`, and the extension must be `.json`, for example `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json`. The subfolder only affects the derived rule ID; it does not affect loading.

## Optional: Write Rules as JSON

Edit files directly when you want to manage rule files or create a world datapack. The JSON below produces the same result as the GUI example above.

### Rule IDs and File Names

Every rule needs an ID in the form `namespace:path`, for example `myrules:chicken_to_rotten_flesh`. You can supply it in two ways:

- **Write the `id` field**: works in any file shape, and it is the only accepted way for a file that holds several rules.
- **Omit `id`**: allowed only when the file holds exactly one rule. The mod then derives the ID from the file path.

The derivation depends on where the file lives:

| Rule file path | Derived ID |
| --- | --- |
| `config/itemdespawntowhat/rules/myrules/chicken_to_rotten_flesh.json` | `idtw:myrules/chicken_to_rotten_flesh` |
| `config/itemdespawntowhat/rules/egg_hatch.json` | `idtw:egg_hatch` |
| Datapack `data/myreload/idtw/rules/crops/bean.json` | `myreload:crops/bean` |

Key points:

- For the overlay, the namespace is always the rule namespace `idtw`, and the path is the relative path after `rules/` with `.json` removed.
- For a datapack, the namespace is the pack's own namespace, and the path is the relative path after `idtw/rules/` with `.json` removed.
- To keep IDs clean and predictable, write `id` in the JSON. The file name then does not matter.
- When the file holds an array of several rules, every entry must declare `id`. Otherwise the mod rejects that entry at load.
- IDs must be unique within one file. An invalid ID, such as one with uppercase letters or spaces, also rejects that entry.

### A Minimal Working Rule

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
      "type": "idtw:spawn_entity",
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

### Apply Manual File Changes

The whole flow has three steps:

1. **Place the file**: put the JSON under `config/itemdespawntowhat/rules/` or under `data/<namespace>/idtw/rules/` in a datapack. Save the file to disk after every edit.
2. **Check for errors**: run `/idtw rule validate`. It reads every source again from what is on disk and reports once. It reports the validation outcome and counts of valid rules per layer, errors, and warnings, then pages through issues with source and field paths. It only checks; it does not change the rules that are active and does not apply your edits.
3. **Apply**: run `/idtw rule reload`. It reads every rule again and applies the new content immediately, reporting the applied outcome, counts, and a bounded issue preview. Use `validate` to page through all file issues.

Two points to remember:

- **Reload after every file change** (run `/idtw rule reload`, or reload datapacks, or restart the server). Otherwise the game never reads your edits.
- **Only the broken part is rejected**: when the whole file fails to parse as JSON, or when the root is neither a rule object nor a rule array, none of that file takes effect. A field error in one rule rejects only that rule; other rules in the same file still load.

The commands need you to own the singleplayer save, or to hold permission level 2 or higher. For the other commands, see [Command Reference](en-08-commands).

## Inspect and manage your rules

Run `/idtw rule list` to browse published rules and hover to see their sources. `/idtw rule show <id>` displays a summary; `/idtw rule show <id> raw` reads the original JSON.

Use `/idtw rule enable <id>` and `/idtw rule disable <id>` to switch rules on or off for the current save. To replace a world datapack rule, write a full rule with the same `id` in the config overlay. See [Source Layers and Overrides](en-06-layers-and-overrides).

## Rule Not Working? Check in Order

1. **Is the file in the right place?** It must sit under `config/itemdespawntowhat/rules/` or under `data/<namespace>/idtw/rules/` in a datapack, with the extension `.json`. A file in the wrong folder does nothing.
2. **Is the JSON valid?** A syntax error, a stray comma, or a root that is neither a rule object nor a rule array keeps the whole file out, and `validate` reports "JSON 解析失败" or "规则文件顶层必须是规则对象或规则数组".
3. **Is the rule switched off?** `enabled` in the content is the default; a rule with `false` still loads but does not take part in matching (`rule list` marks it Disabled). A save can also switch a rule off on its own, and that published effective value appears in the state marker; run `/idtw rule reset <id>` to follow the default again.
4. **Does `source` match?** Check the spelling of the item ID, its namespace, and the tag name. An unknown static item ID rejects that rule. Also check that `exclude` is not removing the item you want.
5. **Are the trigger kind and timing right?** With no `triggers` field, only `natural` applies. To watch a burn, a lava, or a cactus case, list that value in `triggers`. When `trigger_after_seconds` is longer than the item actually survives, the trigger moves up to the moment of natural despawn instead of waiting for the configured seconds.
6. **Are the conditions met?** If any condition in `conditions` fails, the mod never picks that rule. For condition types and semantics, see [Conditions](en-03-conditions).
7. **Did a higher-priority rule win?** One dropped item runs one rule at a time. The mod sorts candidates by higher `priority` first, then by more condition leaves, then by definition order, and picks the first rule whose trigger kind matches and whose conditions hold. When several rules claim the same item, raise the `priority` of the one you want, narrow its `source`, or tighten its conditions.

Start with the output of `/idtw rule validate`. It tells you which origin and which field path holds the problem.

## Related

- [Rule Fields](en-02-rule-basics): which top-level fields a rule accepts and what each default is.
- [Conditions](en-03-conditions): when a rule is allowed to fire.
- [Effects](en-04-effects): what a rule does when it fires.
- [Command Reference](en-08-commands): every command for checking and applying your edits.
