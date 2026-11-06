# ItemDespawnToWhat Player Guide

> Applies to **2.0.0** (Minecraft **1.21.1**).

2.0.0 ships without a built-in rule datapack. To get started, create rules in the in-game editor, add your own rule JSON files, or install a world datapack.

## Create your first rule

1. Install the mod and enter a world. In multiplayer, install it on the server and on the client of the player using the editor.
2. Run `/idtw editor open` in chat. `/idtw editor` also works.
3. Click **New rule** and configure the input items, trigger timing, conditions, and results.
4. Click **Apply changes** at the bottom and wait for a successful save. Rules reload and take effect automatically.

You can duplicate an existing rule to create a variation. The list's server toggle switches a rule on or off for the current save. **Enabled by default** supplies the state when that save has no override; **Reset to default** makes the save follow it again. Hover over fields and buttons to read their tooltips.

**Permissions and occupancy**: single-player world owners have access directly; other players need permission level 2 or higher. Only one player can edit at a time. When the editor is occupied, wait for its holder to close it, or have an administrator run `/idtw editor release`.

Follow the screenshots in [Getting Started](en-01-getting-started) to create your first rule with the GUI. The guide also includes an optional JSON example, file locations, and steps to apply file changes.

## Reading Order

| Stage | Read | What you get |
| --- | --- | --- |
| First use | [Getting Started](en-01-getting-started) | A screenshot walkthrough to create and try your first rule in the GUI |
| Learning the fields | [Rule Fields](en-02-rule-basics) | Which fields a rule can fill, their defaults, and when to change them |
| Adding conditions and changing results | [Conditions](en-03-conditions), [Effects](en-04-effects) | When a conversion happens, and what it produces |
| Several results and their cost | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) | How results rotate, what each group costs, and where unconverted items go |
| Overriding, tuning, and checking status | [Source Layers and Overrides](en-06-layers-and-overrides), [Server Configuration](en-07-server-config), [Command Reference](en-08-commands) | How to override a datapack rule, tune the pace, and read command output |

## Document Index

| Page | What you will find |
| --- | --- |
| [Getting Started](en-01-getting-started) | A GUI walkthrough with screenshots, configuration locations, an optional JSON example, and a troubleshooting checklist. |
| [Rule Fields](en-02-rule-basics) | Which fields a rule can carry at the top level, what each one does, its values and defaults. |
| [Conditions](en-03-conditions) | How to write `conditions`: which nodes a condition tree has, what each condition type needs, and what happens when conditions are not satisfied. |
| [Effects](en-04-effects) | What a rule can do once it is satisfied: how to write each effect type, and what you see in game. |
| [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) | The two ways to write `effects` and `outcomes`, rotation between results, the cost of each group, and how a whole stack is settled and returned. |
| [Source Layers and Overrides](en-06-layers-and-overrides) | World datapack and config overlay priority, replacing a datapack rule by ID, switching a rule on or off per save, and which rule wins when several rules share one dropped item. |
| [Server Configuration](en-07-server-config) | What each item in `server.json` controls and its default, and what to change when the server lags or the pace is wrong. |
| [Command Reference](en-08-commands) | Every command, the permission each one requires, and how to read its output. |

## Quick Lookup

| I want to... | Go to |
| --- | --- |
| Edit rules in game | Type `/idtw editor open` and follow the [screenshot walkthrough](en-01-getting-started) |
| Write my first rule (for example, chicken to rotten flesh after 10 seconds) | [Getting Started](en-01-getting-started) |
| Change what a dropped item becomes (an item, a mob, a block, experience, weather) | [Effects](en-04-effects) |
| Convert only under certain conditions (dimension, biome, time, weather, height, light level...) | [Conditions](en-03-conditions) |
| Change how long the wait is, or trigger on fire, lava, or cactus destruction | [Rule Fields](en-02-rule-basics) |
| Make one rule run before other rules | [Rule Fields](en-02-rule-basics) |
| Override a datapack rule or switch a rule on or off per save | [Source Layers and Overrides](en-06-layers-and-overrides) |
| Make one item alternate between results, or add a cost to a conversion | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) |
| Fix server lag, conversions that fire too often, or too many products piling up | [Server Configuration](en-07-server-config) |
| Find out why a rule change did nothing, or read an error message | [Getting Started](en-01-getting-started), [Command Reference](en-08-commands) |
| The editor is held by another player | The Editor Sessions section of [Command Reference](en-08-commands) |

For how the mod works and how it is structured, see [docs/README.md](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/README.md) and the [architecture overview](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/architecture/overview.md) in the repository.
