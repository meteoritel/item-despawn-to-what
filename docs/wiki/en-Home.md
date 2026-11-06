# ItemDespawnToWhat Player Guide

> Applies to **2.0.0** (Minecraft **1.21.1**).

**ItemDespawnToWhat** lets you customize what happens when dropped items despawn: whether an item expires naturally or is destroyed by fire, lava, or cactus, you can transform it into other items, summon mobs, place blocks, grant experience, or trigger weather events.

You can configure rules using the intuitive **in-game GUI editor**, or write custom JSON rule files and world datapacks for advanced setups.

---

## 30-Second Quick Start

1. **Install and enter a world**: Singleplayer world owners can jump right in. For multiplayer, the mod must be installed on the server and on the client of any player opening the editor.
2. **Open the GUI editor**: Type and run `/idtw editor` (or `/idtw editor open`) in chat.
3. **Create a rule**: Click **New rule**, pick your input item (e.g. raw chicken), set the trigger time (e.g. 10 seconds), and choose the product (e.g. rotten flesh).
4. **Apply and play**: Click **Apply changes** at the bottom and wait for confirmation. The rule takes effect immediately!

Drop the item on the ground and wait for the set time to see it transform. See [Getting Started](en-01-getting-started) for a full screenshot walkthrough.

---

## Suggested Reading Order

| Goal | Recommended Page | What You Learn |
| --- | --- | --- |
| First time using the mod | [Getting Started](en-01-getting-started) | Step-by-step GUI guide, file paths, and troubleshooting checklist |
| Understand all available fields | [Rule Fields](en-02-rule-basics) | Top-level parameters: ID, source items, triggers, and priority |
| Add restrictions (weather, biome, height) | [Conditions](en-03-conditions) | Filter conversions by dimension, biome, time of day, and blocks |
| Customize outputs and effects | [Effects](en-04-effects) | Spawning items/mobs, placing blocks, arrow rain, and lightning |
| Multiple outcomes, probabilities, and costs | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) | Rotate outcomes, require catalysts, and refund leftover items |
| Datapack overrides and rule conflicts | [Source Layers and Overrides](en-06-layers-and-overrides) | How overlay configs take precedence over world datapacks |
| Server performance and pacing | [Server Configuration](en-07-server-config) | Adjust lag-prevention limits and check pacing in `server.json` |
| Full list of in-game commands | [Command Reference](en-08-commands) | Rule inspection, toggles, reload/validate, and editor sessions |

---

## Quick Lookup

| I want to... | Go to |
| --- | --- |
| Visually edit rules in-game | Follow the walkthrough in [Getting Started](en-01-getting-started) |
| Create my first rule (e.g. chicken to rotten flesh) | [Getting Started](en-01-getting-started) |
| Make items trigger instantly on fire, lava, or cactus | Check `triggers` in [Rule Fields](en-02-rule-basics) |
| Convert only in rain, at night, or in specific biomes | [Conditions](en-03-conditions) |
| Spawn entities, place blocks, or roll loot tables | [Effects](en-04-effects) |
| Alternate outcomes with weights or require extra items | [Outcome Candidates and Settlement](en-05-outcomes-and-settlement) |
| Replace a datapack rule with a local config file | [Source Layers and Overrides](en-06-layers-and-overrides) |
| Prevent server lag when many items drop | [Server Configuration](en-07-server-config) |
| Validate handwritten JSON files for typos | Run `/idtw rule validate` in [Command Reference](en-08-commands) |
| Fix "Editor occupied by another player" | See the Editor Sessions section in [Command Reference](en-08-commands) |

---

> For internal architecture and development details, see [docs/README.md](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/README.md) and the [architecture overview](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/architecture/overview.md) in the repository.
