# ItemDespawnToWhat Player Guide

Install the mod, enter a world, and type `/idtw config edit` to open the configuration editor. **To try the mod, open the editor and follow what the screen shows**; the rest of this page is an index for when you need to look something up.

## Open the in-game editor first

1. Enter a world.
2. Type `/idtw config edit` and press Enter.
3. Pick a rule in the list and click **Edit**; the **Basic / Input / Trigger / Results** tabs change what it becomes and when it converts.

The editor ships with example rules, so you can play without writing any file first:

- **New rule** builds one from scratch;
- **Duplicate** copies an example to adjust;
- **Enable / Disable** decides whether a rule takes part;
- click **Save**, and the change takes effect right away.

When you are not sure what a field or button does, hover over it.

**Permissions and occupancy**: a single-player world works directly; a multiplayer server needs an administrator (permission level 2). Only one player can edit at a time; when the message says the editor is locked by someone else, wait for them to close it, or have an admin run `/idtw config edit-lock release`.

To write rules as files instead, read [Getting Started](en-01-getting-started): it has a minimal rule you can copy, and it covers where the file goes and how to make the rule take effect.

## How to use this page

Each page solves one problem. Read them in the order below, or jump straight to the table you need.

## Reading Order

| Stage | Read | What you get |
| --- | --- | --- |
| First use | [Getting Started](en-01-getting-started) | What the mod does, and a rule you can copy and run |
| Learning the fields | [Rule Fields](en-02-rule-basics) | Which fields a rule can fill, their defaults, and when to change them |
| Adding conditions and changing results | [Conditions](en-03-conditions), [Effects](en-04-effects) | When a conversion happens, and what it produces |
| Several results and their cost | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) | How results rotate, what each group costs, and where unconverted items go |
| Overriding, tuning, and checking status | [Source Layers and Overrides](en-06-layers-and-overrides), [Server Configuration](en-07-server-config), [Command Reference](en-08-commands) | How to override a built-in example, tune the pace, and read command output |

## Document Index

| Page | What you will find |
| --- | --- |
| [Getting Started](en-01-getting-started) | What the mod does, where the config files go, a minimal working rule, and the steps to make a rule take effect plus a checklist for rules that do nothing. |
| [Rule Fields](en-02-rule-basics) | Which fields a rule can carry at the top level, what each one does, its values and defaults. |
| [Conditions](en-03-conditions) | How to write `conditions`: which nodes a condition tree has, what each condition type needs, and what happens when conditions are not satisfied. |
| [Effects](en-04-effects) | What a rule can do once it is satisfied: how to write each effect type, and what you see in game. |
| [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) | The two ways to write `effects` and `outcomes`, rotation between results, the cost of each group, and how a whole stack is settled and returned. |
| [Source Layers and Overrides](en-06-layers-and-overrides) | The priority of the three source layers, replacing a rule by id, disabling or deleting a built-in rule, and which rule wins when several rules share one dropped item. |
| [Server Configuration](en-07-server-config) | What each item in `server.json` controls and its default, and what to change when the server lags or the pace is wrong. |
| [Command Reference](en-08-commands) | Every command, the permission each one requires, and how to read its output. |

## Quick Lookup

| I want to... | Go to |
| --- | --- |
| Edit rules in game | Type `/idtw config edit` to open the editor; see [Command Reference](en-08-commands) for details |
| Write my first rule (for example, chicken to rotten flesh after 10 seconds) | [Getting Started](en-01-getting-started) |
| Change what a dropped item becomes (an item, a mob, a block, experience, weather) | [Effects](en-04-effects) |
| Convert only under certain conditions (dimension, biome, time, weather, height, light level...) | [Conditions](en-03-conditions) |
| Change how long the wait is, or trigger on fire, lava, or cactus destruction | [Rule Fields](en-02-rule-basics) |
| Make one rule run before other rules | [Rule Fields](en-02-rule-basics) |
| Disable or delete a built-in example rule | [Source Layers and Overrides](en-06-layers-and-overrides) |
| Make one item alternate between results, or add a cost to a conversion | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) |
| Fix server lag, conversions that fire too often, or too many products piling up | [Server Configuration](en-07-server-config) |
| Find out why a rule change did nothing, or read an error message | [Getting Started](en-01-getting-started), [Command Reference](en-08-commands) |
| The editor is held by another player | The edit-lock section of [Command Reference](en-08-commands) |

For how the mod works and how it is structured, see [docs/README.md](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/README.md) and the [architecture overview](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/architecture/overview.md) in the repository.
