# Rule Fields

This page covers the top-level fields a rule accepts: what each field does, which values it takes, what it defaults to, and when you need to touch it.

## Field Overview

| Field | What it does | Values and ranges | Default | Changes behavior? |
| --- | --- | --- | --- | --- |
| `id` | The identity of the rule | `namespace:path` string | Derived from the file path when a single-rule file omits it | Yes; the same ID overrides |
| `display_name` | The display name people read | String, at most 128 characters | None; optional | No, display only |
| `notes` | Notes you write for yourself | String, line breaks allowed | None; optional | No, display only |
| `enabled` | Whether the rule takes part in matching | `true` / `false` | `true` | Yes |
| `priority` | Which rule wins when several claim one item | Integer, negative values allowed | `0` | Yes |
| `schema_version` | The structure version of the rule | Positive integer; only `1` is accepted | `1` | Yes; any other value is rejected at load |
| `source` | Which dropped items the rule claims | Object: `items` required and non-empty, `exclude` optional | None; required | Yes |
| `triggers` | Which trigger kinds apply | Array of `natural` / `fire` / `lava` / `cactus` | `natural` only when omitted | Yes |
| `trigger_after_seconds` | How many seconds the item survives under natural despawn | Non-negative integer in seconds | `300` | Yes |

The fields `effects`, `outcomes`, `combination`, `source_cost`, and `catalyst_cost` belong to outcome candidates and settlement. For their syntax, see [Outcome Candidates and Settlement](en-05-outcomes-and-settlement).

## id: The Identity of the Rule

- **What it does**: identifies one rule. The mod merges sources by ID at load time, so only one rule with a given ID ends up active. Commands, the editor, and error messages all use it to locate a rule.
- **Values and ranges**: a string in the form `namespace:path`, for example `myrules:chicken_to_rotten_flesh`. The namespace and the path accept only lowercase letters, digits, underscores, dots, and hyphens, and the path can use `/` to nest.
- **Default**: it is optional. The mod derives it from the file path only when the file holds a single rule. Every entry in a multi-rule file must declare `id`, or the mod rejects that entry at load. For the derivation rules, see [Getting Started](en-01-getting-started).
- **When to change it**: when you name a new rule; when you want to disable or replace an existing rule through the overlay, which requires the exact same ID (see [Source Layers and Overrides](en-06-layers-and-overrides)); or when you move a rule from one file to another.

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

## display_name and notes: Display Only

- **What they do**: `display_name` is the display name the mod shows instead of the ID in command output and in the editor. `notes` holds remarks such as why the rule waits 30 seconds or which rule it works with.
- **Values and ranges**: both are strings. `display_name` takes at most 128 characters counted by code point, and a longer value is rejected at load. `notes` allows line breaks.
- **Default**: both are optional. When you omit `display_name`, the mod generates a title from the source and the effects; the rule itself is unaffected.
- **When to change them**: once you have many rules and want to recognize one at a glance. `notes` helps most in multiplayer work or when you return after a long break.

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "display_name": "鸡肉腐化 / Chicken to Rotten Flesh",
  "notes": "和内置示例同样 300 秒，但只在主世界生效。",
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

These two fields affect display only. Changing them does not change what the rule matches, when it fires, or what it produces.

## enabled: The Switch

- **What it does**: with `false`, the mod still reads the rule and still lists it in `/idtw config list`, but the rule takes no part in matching, which temporarily turns it off.
- **Values and ranges**: `true` or `false`. Any other type, such as the string `false`, rejects that rule at load.
- **Default**: `true`.
- **When to change it**: to turn a rule off while you watch the rest, or to keep a working rule around without using it. To turn off a builtin rule for good, use an overlay control entry rather than editing the builtin file, which is read-only (see [Source Layers and Overrides](en-06-layers-and-overrides)).

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "enabled": false,
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

## source: Which Dropped Items the Rule Claims

- **What it does**: decides which dropped items the rule applies to. Only dropped items that `source` matches become candidates for this rule.
- **Values and ranges**: an object. `items` is required and must be a non-empty array. `exclude` is optional and is also an array. Together they hold at most 256 entries. Each entry is either an item ID such as `minecraft:chicken` or an item tag such as `#minecraft:saplings`.
- **Default**: none; `source` is required. When you omit `exclude`, the rule excludes nothing.

### Item IDs and #Tags

| Form | Meaning | What it matches |
| --- | --- | --- |
| `minecraft:oak_sapling` | One exact item | That item alone |
| `#minecraft:saplings` | An item tag, meaning every item the tag holds | The tag members in the current datapacks |

Key points:

- A tag always starts with `#`. Without it, the mod reads the text as an item name, no such item exists, and that rule is rejected at load.
- Datapack tag files decide the members, so a tag follows the datapacks you load. A tag covers a whole group such as all saplings at once, and new items join the group automatically.
- A wrong item ID, meaning an item that does not exist, rejects that rule at load. A wrong tag name reports nothing and never matches, so check the tag name first when you troubleshoot.

### exclude Wins over items

Matching checks `exclude` first and `items` second. If a dropped item matches any entry in `exclude`, the rule does not apply, even when the item also matches `items`. So `exclude` is the subtraction you use to carve a few exceptions out of a group.

```json
{
  "id": "myrules:saplings_to_blocks",
  "display_name": "树苗落地（不含橡树苗）",
  "source": {
    "items": ["#minecraft:saplings"],
    "exclude": ["minecraft:oak_sapling"]
  },
  "trigger_after_seconds": 60,
  "effects": [
    { "type": "itemdespawntowhat:place_block", "use_source_block": true, "shape": "square", "count": 1, "radius": 1 }
  ]
}
```

This rule matches every sapling except the oak sapling.

More notes:

- When `exclude` repeats an entry that is already in `items`, the mod rejects that rule. This applies to the same item ID or the same `#tag`.
- Repeating an entry inside `items` also rejects the rule. Repeating one inside `exclude` is allowed, but the duplicate changes nothing.
- **When to change it**: by default, list only the items you want to convert. Switch to a tag when a whole group changes together, and use `exclude` for the few members that should stay untouched.

## priority: Who Goes First When Rules Compete

- **What it does**: one dropped item can match several rules, but only one rule runs. `priority` decides which rule the mod tries first: a larger number moves the rule ahead.
- **Values and ranges**: an integer, negative values allowed, with no other limit.
- **Default**: `0`.
- **When to change it**: when several rules match one item and you want a result other than the first one. Give a special rule a positive number and a fallback rule a negative number, which is the simplest approach.

```json
{
  "id": "myrules:chicken_to_diamond",
  "priority": 10,
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:diamond", "count": 1 }
  ]
}
```

This rule has `priority` 10, so the mod tries it before the chicken rule that also sits at `0`.

More sorting rules apply when priorities tie. For the full candidate order and for how the mod picks the single rule to run, see [Source Layers and Overrides](en-06-layers-and-overrides).

## triggers: Which Trigger Kinds Apply

- **What it does**: declares which trigger kinds the rule answers to: natural despawn at the end of the lifespan, burning in fire, destruction by lava, or destruction by a cactus.
- **Values and ranges**: an array whose values are the lowercase `natural`, `fire`, `lava`, or `cactus`. List several at once when you need them.
- **Default**: an omitted `triggers` field means `natural` only.

| Value | Meaning | Waits? |
| --- | --- | --- |
| `natural` | The dropped item reaches its lifespan | Yes, for `trigger_after_seconds` |
| `fire` | Fire burns the item up | No, the mod decides at that moment |
| `lava` | Lava destroys the item | No, the mod decides at that moment |
| `cactus` | A cactus destroys the item | No, the mod decides at that moment |

What the three environment kinds share and how they differ:

- **Decided on the spot, no waiting**: the instant an item burns up, sinks into lava, or is destroyed by a cactus, the mod evaluates the conditions once against the situation at that moment. `trigger_after_seconds` does nothing for these kinds. A rule that lists only `fire` can convert an item a few seconds after it drops, as long as the conditions hold then.
- **Fire and lava are separate**: fire covers direct ignition and fire damage over time, while lava counts as lava alone. The two never mix. Causes outside fire, lava, and cactus, such as a player picking the item up or an explosion destroying it, belong to none of the three and trigger nothing.
- **Not listed means not included**: a trigger kind missing from `triggers` takes no part. With the default `natural` only, an item burned by fire is destroyed without converting.

```json
{
  "id": "myrules:burning_arrow_rain",
  "display_name": "烧成箭雨",
  "source": { "items": ["minecraft:arrow"] },
  "triggers": ["fire", "lava"],
  "effects": [
    { "type": "itemdespawntowhat:arrow_rain", "count": 16, "pickup": "allowed" }
  ]
}
```

This rule omits `natural`, so an arrow does nothing at natural despawn. Only fire or lava starts the arrow rain, and it starts at the moment of destruction.

Listing several kinds is also common:

```json
{ "triggers": ["natural", "fire", "lava", "cactus"] }
```

That rule applies to all four trigger kinds. `natural` still waits for `trigger_after_seconds`, while the other three are decided at the moment of destruction.

The editor requires at least one trigger kind. When you edit JSON directly, omitting the `triggers` field equals listing `natural` alone.

## trigger_after_seconds: How Long to Wait

- **What it does**: how many seconds a dropped item must survive before the rule fires under `natural`.
- **Values and ranges**: a non-negative integer in seconds, converted internally at 20 ticks per second. A negative value is rejected at load.
- **Default**: `300` (5 minutes), which matches how vanilla dropped items disappear.
- **When to change it**: to make items convert sooner, such as at 10 seconds, or to make them convert shortly before they disappear, which means a number close to the natural lifespan.

The item's own lifespan caps the actual trigger time:

- The trigger moment is the earlier of `trigger_after_seconds` and the tick before natural despawn. A vanilla dropped item lives 300 seconds (6000 ticks), so `300` or more fires shortly before it disappears instead of waiting out your number.
- `0` means the rule tries to fire as soon as the item enters the world, on the next check.
- The loader supplies the lifespan, so a mod that changes it decides the real value. A dropped item set never to despawn takes no part in conversion.
- When the conditions do not hold at the due time, the rule does not give up. It retries on the backoff schedule until the buffered item disappears.

```json
{
  "id": "myrules:chicken_to_rotten_flesh_10s",
  "source": { "items": ["minecraft:chicken"] },
  "trigger_after_seconds": 10,
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

## schema_version: Rule Structure Version

- **What it does**: marks which version of the rule structure the file follows, so the mod can recognize old files when the structure changes.
- **Values and ranges**: a positive integer, and only `1` is accepted today. Any other value is rejected at load and reports "不支持的 schema_version".
- **Default**: `1`; omitting the field means `1`.
- **When to change it**: almost never. The field exists for structure upgrades, so omit it in everyday work. When you copy someone else's config and see `"schema_version": 1`, leave it as it is.

## A Rule That Uses Every Core Field

```json
{
  "id": "myrules:sapling_planting",
  "display_name": "树苗落地",
  "notes": "除橡树苗外的树苗，在火与自然消失时都会种下。",
  "enabled": true,
  "priority": 5,
  "schema_version": 1,
  "source": {
    "items": ["#minecraft:saplings"],
    "exclude": ["minecraft:oak_sapling"]
  },
  "triggers": ["natural", "fire"],
  "trigger_after_seconds": 60,
  "effects": [
    { "type": "itemdespawntowhat:place_block", "use_source_block": true, "shape": "square", "count": 1, "radius": 1 }
  ]
}
```

Read it this way: any sapling except the oak sapling converts in place after 60 seconds, or shortly before natural despawn, or when fire burns it up. The rule carries priority 5, so the mod tries it before rules that sit at `0`.

## What Happens When You Get It Wrong

A wrong field affects that one rule only. Other rules in the same file still load, and other files stay unaffected. Common mistakes:

| What you wrote | Result |
| --- | --- |
| `id` with uppercase letters or spaces | That rule is rejected at load and reports an invalid rule ID |
| A misspelled item ID, meaning no such item | That rule is rejected at load and reports an unknown source item |
| A misspelled `#` tag name | No report, but it never matches |
| `enabled` written as a string | That rule is rejected at load, since the type must be `true` or `false` |
| A negative `trigger_after_seconds` | That rule is rejected at load |
| `schema_version` other than `1` | That rule is rejected at load |
| An unknown extra top-level field | The rule still loads, and the mod reports it as a warning |

For the troubleshooting flow, see [Getting Started](en-01-getting-started). For conditions, see [Conditions](en-03-conditions); for effects and output, see [Effects](en-04-effects).

## Related

- [Getting Started](en-01-getting-started): where files go and how to apply your edits.
- [Conditions](en-03-conditions): how to limit a rule to specific situations.
- [Effects](en-04-effects): what a rule can do when it fires.
- [Outcome Candidates and Settlement](en-05-outcomes-and-settlement): `outcomes`, `combination`, and the cost fields.
