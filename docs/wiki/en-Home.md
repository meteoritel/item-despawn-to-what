# ItemDespawnToWhat Player Guide

This page is the entry point to all player documentation: it says what each page solves and where to look for the task at hand.

## Open the in-game editor first

To try the mod, you do not need to write any files. Run `/idtw config edit` in a world to open the configuration editor, then follow what the screen shows:

1. Pick a rule in the list and click **Edit**.
2. Change it on the **Basic / Input / Trigger / Results** tabs: what it drops or spawns, and under which conditions.
3. Click **Save**; the change takes effect right away.

The bundled example rules are already in the list, so you can start from one: **New rule** builds one from scratch, **Duplicate** copies an example to adjust, and **Enable / Disable** decides whether a rule takes part. Hover over a field or button if you are not sure what it does.

The editor needs a single-player world, or operator level 2 on a server. Only one player can edit at a time; if it is held by someone else, wait for them to close it, or ask an admin to run `/idtw config edit-lock release`.

To write rules as files instead, read [Getting Started](en-01-getting-started); it has a minimal rule you can copy and covers where the file goes and how to make the rule take effect. Use the tables below for everything else.

## Reading Order

| Stage | Read | What you get |
| --- | --- | --- |
| First use | [Getting Started](en-01-getting-started) | A rule that runs, and a way to confirm it took effect |
| Learning the available fields | [Rule Fields](en-02-rule-basics) | Which dropped items a rule covers, when it triggers, and its switch and priority |
| Adding conditions and changing the result | [Conditions](en-03-conditions), [Effects](en-04-effects) | When a conversion happens, and what it produces |
| Several products and their cost | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) | How products alternate in round robin, what each conversion group costs, and where unconverted items go |
| Overrides, tuning, and status | [Source Layers and Overrides](en-06-layers-and-overrides), [Server Configuration](en-07-server-config), [Command Reference](en-08-commands) | How to override a built-in rule, tune the pace, and read command output |

## Document Index

| Page | What you will find |
| --- | --- |
| [Getting Started](en-01-getting-started) | What the mod does, where the config files go, a minimal working rule, and the steps to make a rule take effect plus a checklist for rules that do nothing. |
| [Rule Fields](en-02-rule-basics) | Which fields a rule can carry at the top level, what each one does, its values and defaults, and when you need to change it. |
| [Conditions](en-03-conditions) | How to write `conditions`: the nodes of a condition tree, the parameters of the 10 condition types, and what happens when a condition is unsatisfied or unavailable. |
| [Effects](en-04-effects) | What a rule can do once it is satisfied: the `type` value and the parameters of each of the 7 effect types, and what you see in game. |
| [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) | The two ways to write `effects` and `outcomes`, round robin between products, the fixed cost and consumption of each group, and how the mod consumes items and returns the remainder. |
| [Source Layers and Overrides](en-06-layers-and-overrides) | The priority of the three source layers, full replacement with the same `id`, disabling or deleting a built-in rule, and which rule wins when several rules share one dropped item. |
| [Server Configuration](en-07-server-config) | What each item in `server.json` controls, its default value, and what to change when the server lags or the pace is wrong. |
| [Command Reference](en-08-commands) | Every command, the permission each one requires, and how to read its output. |

## Quick Lookup

| I want to... | Go to |
| --- | --- |
| Write my first rule (for example, chicken to rotten flesh after 10 seconds) | [Getting Started](en-01-getting-started) |
| Change what a dropped item becomes (an item, a mob, a block, experience, weather) | [Effects](en-04-effects) |
| Convert only under certain conditions (dimension, biome, time, weather, height, light level...) | [Conditions](en-03-conditions) |
| Change how long the wait is, or trigger on fire, lava, or cactus destruction | [Rule Fields](en-02-rule-basics) |
| Make one rule run before other rules | [Rule Fields](en-02-rule-basics) |
| Disable or delete a built-in example rule | [Source Layers and Overrides](en-06-layers-and-overrides) |
| Make one item alternate between products in round robin, or add a cost to a conversion | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) |
| Fix server lag, conversions that fire too often, or too many products piling up | [Server Configuration](en-07-server-config) |
| Find out why a rule change did nothing, or read a problem line | [Getting Started](en-01-getting-started), [Command Reference](en-08-commands) |
| Edit rules in game | Run `/idtw config edit`; see [Command Reference](en-08-commands) for the details. |
| The editor is held by another player | The edit-lock section of [Command Reference](en-08-commands) |

For the implementation and the architecture, see the developer documentation in `docs/dev/`, which is not part of this guide.
