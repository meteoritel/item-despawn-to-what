# Command Reference (/idtw)

This page lists every player-facing command, the permission each one needs, and how to read the output. Commands only check and reload your configuration; the rules themselves live in JSON files. A development environment also exposes an `/idtw debug` branch for debugging the conversion pipeline; released builds do not register it, so this page does not cover it.

## Command List

| Command | What it does | Permission | Changes the active rules? |
| --- | --- | --- | --- |
| `/idtw config validate` | Checks every source against what is on disk and reports only problems | Permission required | No |
| `/idtw config reload` | Reads every source again and applies it at once | Permission required | Yes |
| `/idtw config list` | Lists the active rules one by one with priority, trigger seconds, and effect count | Permission required | No |
| `/idtw rule list` | Prints a compact list of the active rules | Permission required | No |
| `/idtw rule show <id>` | Prints the JSON of a rule as it is active now | Permission required | No |
| `/idtw rule <id> enable` | Switches the rule on in this save; rule files stay untouched | Permission required | Yes |
| `/idtw rule <id> disable` | Switches the rule off in this save; rule files stay untouched | Permission required | Yes |
| `/idtw rule <id> default` | Drops the save's setting for the rule so it follows the default enable state | Permission required | Yes |
| `/idtw rule orphans` | Lists the save's switch records that no longer have a matching rule | Permission required | No |
| `/idtw rule orphans clear` | Removes those orphan records | Permission required | No, it only clears records |
| `/idtw config edit` | Opens the graphical editor | Permission required, and a player must run it | Yes, after a save |
| `/idtw config edit-lock status` | Shows who holds the edit lock | Every player | No |
| `/idtw config edit-lock release` | Forces the edit lock open | Permission level 2 or higher | No, it only releases the lock |

## How Permissions Work

- **Permission required**: the owner of a singleplayer save always passes, even without cheats. Anyone else, including friends who join that save over LAN, needs permission level 2 or higher, which is the usual admin or OP level 2.
- Without permission, the game does not recognize the command, and the client shows the vanilla message for an unknown or incomplete command. The mod adds no explanation.
- `/idtw config edit-lock status` needs no permission, so every player can check it.
- `/idtw config edit-lock release` looks only at permission level 2 or higher, not at singleplayer. A singleplayer save without cheats sits at permission level 0, so this command does not work there; the other commands are unaffected.
- While the server is still starting, a command reports that the rule runtime is not ready yet. Try again a moment later.
- While the server is saving editor changes, `config validate`, `config reload`, `config list`, `rule list`, `rule show`, `rule <id> enable|disable|default`, and `rule orphans` reply that the rules are being saved and ask you to retry after the save finishes.

## The Shared Format of a Check

`validate`, `reload`, `list`, and `rule list` all start with the same summary line and then add an issue list when needed. Here is `validate` reporting one error:

```text
Rule load [validate]: 10 rule(s) total (builtin 10 / world datapack 0 / overlay 0), 1 error(s), 0 warning(s)
[error] trigger_after_seconds 不能为负数: -1 config 覆盖层:rules/paper.json#trigger_after_seconds
```

(The second line is an illustration; the actual text depends on the problem.)

- **Summary line**: the stage inside `Rule load [stage]` names this run. The four values are `validate`, `reload`, `list`, and `rule_list`. `N rule(s) total` counts the rules this run read successfully, and the parentheses break that count down by the three source layers. `error(s)` and `warning(s)` count the problems.
- **Issue line**: written as `[severity] message origin#field_path`.
  - The severity is either `error` or `warn`. An `error` keeps the offending rule or the whole file out of the active set. A `warn` is a reminder and does not affect loading.
  - The origin looks like `config 覆盖层:rules/paper.json` for the overlay, whose path is relative to `config/itemdespawntowhat`, or `世界数据包[pack id]:data/<namespace>/idtw/rules/<file>.json` for the world layer, or `内置数据包[pack id]:data/<namespace>/idtw/rules/<file>.json` for the builtin layer.
  - The field path points at one field, for example `#trigger_after_seconds` or `#source.items`. When only the origin appears and no `#field` follows, the problem covers the whole file, such as a JSON syntax error.
- Issue lines report problems only, and the total does not count rejected entries. A count lower than you expect is itself a clue.

## /idtw config validate

Syntax: `/idtw config validate`

This command reads every source again from the rule files on disk, checks them, and prints the summary line and issue list.

- **It only checks and never applies**: it leaves the active rules alone, so you can read the result before you decide to run `reload`.
- It reads the current state on disk, so an edit you saved is visible to `validate` before you run `reload`.
- Running `validate` once after you start the game is the simplest way to find problems. For the full checklist, see [Getting Started](en-01-getting-started).

## /idtw config reload

Syntax: `/idtw config reload`

This command reads the rules from every source again and applies them at once, with the same summary line and issue list as `validate`.

- Dropped items already on the ground are judged again under the new rules, so you do not need to drop them a second time.
- The new rules are built first and only applied once the build succeeds. If reading or building fails, the old rules and tracking stay effective and the reply states that no rule changes were applied.
- Run it after every JSON edit, or reload datapacks, or restart the server. Otherwise the game never reads your changes.
- It reloads rules only and does not read `server.json` again. To change server settings, restart the server; see [Server Configuration](en-07-server-config).

## /idtw config list

Syntax: `/idtw config list`

This command lists the active rules one per line:

```text
Rule load [list]: 2 rule(s) total (builtin 0 / world datapack 0 / overlay 2), 0 error(s), 0 warning(s)
myrules:chicken_to_rotten_flesh | layer=overlay | priority=0 | trigger=10s | effects=1 | enabled=true | origin=config 覆盖层:rules/myrules/chicken_to_rotten_flesh.json
```

- `layer` is one of the English layer names `builtin`, `world`, or `overlay`. `origin` is the layer name plus the source pack ID and the file path; for the overlay it gives the path relative to `config/itemdespawntowhat`.
- `priority` repeats the `priority` field and reads `0` when the rule omits it. `trigger` is the `trigger_after_seconds` value in seconds. `effects` counts the entries in the top-level `effects` list; a rule that declares only `outcomes` shows `0` here, because effects inside an outcome candidate do not count.
- A rule with `enabled=false` still appears in the list; it does not take part in matching. This column is the **effective value for this save**: the local switch setting wins, and the rule's `enabled` applies when the save holds no setting.
- When the same ID exists in several layers, only the one that wins appears. For how the layers merge, see [Source Layers and Overrides](en-06-layers-and-overrides).

## /idtw rule list and /idtw rule show

### /idtw rule list

Syntax: `/idtw rule list`

This command prints fewer columns than `config list`: the ID, the source layer, the enabled state, and the origin.

```text
Rule load [rule_list]: 2 rule(s) total (builtin 0 / world datapack 0 / overlay 2), 0 error(s), 0 warning(s)
myrules:chicken_to_rotten_flesh | layer=overlay | enabled=true | origin=config 覆盖层:rules/myrules/chicken_to_rotten_flesh.json
```

### /idtw rule show <id>

Syntax: `/idtw rule show <id>`, where `<id>` uses the same `namespace:path` form as the `id` field in JSON, for example `itemdespawntowhat:builtin_item_to_item`. It must be a valid lowercase resource location, so it accepts letters, digits, `_`, `-`, `.`, and `/`.

The command prints two lines: a summary of the rule, then the JSON of its currently active content on one line.

```text
Rule myrules:chicken_to_rotten_flesh: priority=0, trigger=10s, effects=1, enabled=true
Raw JSON: {"id":"myrules:chicken_to_rotten_flesh", ... }
```

- The summary carries the same fields as `config list`, arranged as one sentence.
- The JSON on the second line depends on the source: for an overlay rule it is the file text, so it matches what is on disk; for a builtin or world datapack rule it is the model re-encoded, so field order and non-default values may differ from the file layout. It is the best source when you want to know exactly what the game read.
- When no printable content is available, the second line becomes a notice that no raw JSON is available for that ID, because the rule is model-encoded or not editable.
- When the ID is wrong or missing, the command reports that the rule was not found and returns a failure.

## /idtw rule <id> enable|disable|default

Syntax: `/idtw rule <id> enable`, `/idtw rule <id> disable`, or `/idtw rule <id> default`, with `<id>` written as in `rule show`.

- `disable` switches the rule off in this save only and `enable` switches it back on. The rule content, its source, and its priority stay as written, and no overlay file is created.
- `default` drops the save's setting so the rule follows `enabled` in its JSON again.
- The setting is stored with the save, so every player on a multiplayer server shares it, while another save or server keeps its own.
- A successful write rebuilds the index through the same path as a save and rescans every dimension, so dropped items already on the ground are judged again under the new state. When the state is stored but the runtime reload fails, the reply says it takes effect after the next reload.
- The three success replies report that the rule was enabled in this save, disabled in this save, or returned to its default enable state. `default` on a rule that already follows the default reports that it already does.
- A missing `<id>` reports that the rule was not found, and the command returns a failure.

## /idtw rule orphans and /idtw rule orphans clear

After you delete an overlay entry, or a datapack stops providing an ID, the save's switch record for that ID has no rule left. Loading ignores those records but keeps them until they are cleaned up.

Syntax: `/idtw rule orphans` lists them, and `/idtw rule orphans clear` removes them.

- The list starts with the record count and then prints one line per record as `id | enabled=…`. With no orphan records the command reports that there are none.
- Clearing removes only orphan records, leaves the switch state of existing rules alone, and reports how many records it removed.
- If the rule comes back later, an uncleared record applies again; a cleared one follows the rule's default enable state.
- While the runtime is not ready, the command reports that the rule runtime is not ready yet and asks you to wait for server startup.

## /idtw config edit and the Edit Lock

`/idtw config edit` opens the graphical editor. The server takes the exclusive edit lock before it opens. The editor's enable controls match the enable commands, as described in "Enable Controls in the Editor".

- Only an in-game player can run it. The console sees a message that the subcommand can only be run by a player.
- Without edit permission, the command reports that you do not have permission to edit the overlay rules.
- When another player holds the lock, the command names that player and points you to `/idtw config edit-lock status`. When the lock moves to you, it reports that the editor entry was sent to you.

### Enable Controls in the Editor

- **Enable / Disable** in the rule list and **Server: enabled / Server: disabled** on the Basic tab change this save's setting, exactly like `/idtw rule <id> enable|disable`. Click **Save** to submit them together with content changes.
- **Enabled by default** on the Basic tab is a content field: changing it writes the value into the rule file. **Reset to default** on the same row drops this save's setting so the rule follows **Enabled by default** again.
- A **(local server)** suffix on a list row means the rule has its own setting in this save; a disabled row is greyed out.
- Read-only entries from a datapack can still be switched on or off. When the editor shows **Read-only · changes are not saved**, content changes are not written back and the enable state still follows this save.

### /idtw config edit-lock status

Syntax: `/idtw config edit-lock status`, and every player may run it. The result falls into four cases:

| Case | What the reply means |
| --- | --- |
| Nobody holds it | The rule editor is free, and nobody holds the lock |
| You hold it | You hold the rule editor, with the state and session number. The state is `opening` (the lock is yours and the server waits for your confirmation), `active` (you can edit), or `applying` (a write is in progress) |
| Another player holds it, and you have permission | The reply names that player and the state |
| Another player holds it, and you lack permission | The reply says only that another player holds the editor, without a name or a session number |

The edit lock does not last forever. It releases automatically when the holder sends no heartbeat for 60 seconds, counted in server active time, so a paused game does not count. A lock you took but never confirmed also releases after 15 seconds. Stopping the server or closing the editor also releases it.

### /idtw config edit-lock release

Syntax: `/idtw config edit-lock release`, which needs permission level 2 or higher.

- When the lock is already free, the command reports that it is already free and changes nothing.
- When another player holds it, the command forces the lock open and reports the released owner. If that player is online, the editor receives an expired-session notice at once and must take the lock again to continue.
- When that player is saving or rolling back, the release waits and the reply says the release was requested and the lock stays held until the write finishes.

## Common Ways to Use the Commands

- **After editing files**: run `/idtw config validate` to look for errors, then `/idtw config reload` to apply the changes.
- **To confirm how a rule is active**: find its ID with `/idtw rule list`, then read its current content with `/idtw rule show <id>`.
- **When an error is hard to read**: look at the severity and the `origin#field_path` in the issue line. An `error` means the rule is not active, so fix the JSON at that field path first.
- **When the edit lock is stuck on a multiplayer server**: check the holder with `/idtw config edit-lock status`, then force it open with `/idtw config edit-lock release` if needed.

## Related

- [Getting Started](en-01-getting-started): where files go and how to apply your edits.
- [Source Layers and Overrides](en-06-layers-and-overrides): the three-layer merge behind `layer` and `origin`.
- [Server Configuration](en-07-server-config): the settings `reload` does not re-read.
