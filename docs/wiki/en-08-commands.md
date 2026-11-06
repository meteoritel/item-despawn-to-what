# Command Reference (/idtw)

Run `/idtw` for the entry points available to your identity. Production commands are grouped into rule management and the editor; development environments also register `/idtw debug`.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/idtw` | Show help | Everyone |
| `/idtw editor open` (or `/idtw editor`) | Acquire the exclusive editor session and open the GUI | An in-game player with management access and a compatible client editor channel |
| `/idtw editor status` | Inspect the editor session | Everyone |
| `/idtw editor release` | Force-release the editor session | Permission level ≥2 |
| `/idtw rule list [page] [filter]` | Browse published rules by page | Management access |
| `/idtw rule show <id>` | Inspect a published rule summary | Management access |
| `/idtw rule show <id> raw [page]` | Read raw JSON from the winning source | Management access |
| `/idtw rule enable <id>` | Set a server enable override | Management access |
| `/idtw rule disable <id>` | Set a server disable override | Management access |
| `/idtw rule reset <id>` | Remove the override and inherit the default | Management access |
| `/idtw rule validate [page]` | Reread sources, validate, and page through issues | Management access |
| `/idtw rule reload` | Reload rules and queue a rescan of existing dropped items | Management access |
| `/idtw rule orphans [page]` | Inspect server enable records with no matching rule | Management access |
| `/idtw rule orphans clear` | Clear orphan enable records | Management access |

## Access and Saving

Management access is granted directly to the singleplayer save owner; other sources need permission level ≥2. LAN guests follow the permission-level requirement. `editor release` always requires permission level ≥2, including for the singleplayer owner.

The `rule` branch is available to authorized sources. Running `editor` without access produces a permission message. The console can manage rules and editor sessions; an in-game player opens the GUI.

During saving, lists, summaries, and ID suggestions continue to use the last published view. File validation, reloading, raw-source reads, enable changes, and orphan checks ask you to wait for the save to finish. Commands report when the server runtime is still starting.

## Lists and Summaries

`rule list` sorts by ID and shows 8 entries per page, including disabled rules. Its header gives the filtered rule count and enabled/disabled counts. Rows show the display name, ID, effective state, and a server-override badge when applicable.

```text
/idtw rule list
/idtw rule list 2
/idtw rule list 1 diamond
```

The filter is a case-insensitive substring match against IDs and display names and may contain spaces. Page links preserve it; an out-of-range page reports the page count. Hover over a row for the source and override. Click a row or a page link to fill the command input, then press Enter to execute. The console receives the full command as text.

Lists and ordinary `show` read the view published with the runtime. After editing an overlay file, use `rule reload` to update them. Startup, reloads, and successfully applied editor or command changes update the view; a failed reload preserves the previous view.

`rule show <id>` displays the published source, effective/default/override enable states, priority, trigger kinds, natural-despawn age threshold, outcome count, and action count. Actions inside outcomes are included. Entries with an identifiable ID that failed validation receive an invalid-entry message and a raw-source link.

### Raw Sources

`rule show <id> raw [page]` asynchronously reads current overlay files and the currently loaded datapack base. The overlay wins. Unknown fields and declared values are preserved. Raw source output is labelled separately from the published summary, and reading it leaves the runtime unchanged.

JSON is serialized again and split into bounded fragments and pages. Click a fragment to copy it; concatenate all fragments in output order to recover the full JSON. Each page request rereads the sources. The first message acknowledges acceptance; subsequent feedback carries the result. You can supply a full ID to read a newly added overlay rule before reloading.

### ID Suggestions

Press Tab at the ID argument of `show`, `enable`, `disable`, or `reset` to select a real rule ID from the published view. Tooltips include its display name, effective state, server override, and source. Suggestions read memory only.

`show` includes identifiable invalid entries. Enable/disable candidates contain valid rules, and `reset` suggests valid rules with an explicit override. Enable and disable retain all valid candidates: pinning an inherited value as an explicit override is a meaningful change.

## Server Enable State

`enable`, `disable`, and `reset` store settings in the save, rebuild from published rule content, and queue rescanning of existing dropped items. Feedback includes the effective state and override. Repeated settings report no change.

The server override and the rule file's `enabled` default are separate. `reset` removes the override and follows the default; a default-disabled rule becomes disabled after reset. The editor's server switch and reset button provide the same operations, submitted when you save.

## Validation and Reloading

After editing rule files, run `/idtw rule validate`, then `/idtw rule reload`.

Validation reports whether it passed or found errors, followed by counts of valid rules per source layer and errors/warnings, then paged issues. Issues include source and field paths; errors are red and warnings yellow. Use `rule validate 2` for the next page. Each invocation checks the sources again. Validation returns 0 when errors exist and 1 on success.

Reloading prepares new rules before publishing them. Rescanning existing dropped items then proceeds within the server budget. When some entries or files are rejected, feedback explicitly says the remaining valid rules were applied. Read or build failures preserve the previous rules and tracking. Reload previews are bounded by the page size; use `validate` for all current file issues.

`server.json` is read at server startup. Restart after changing it; see [Server Configuration](en-07-server-config).

## Orphan Records

`rule orphans` asynchronously reads raw sources and compares their IDs against saved enable records. A rule whose content fails validation still counts as present when its ID can be identified. Read, parse, or ID errors stop inspection and clearing, preserving all saved records.

`rule orphans clear` removes orphan records only. If a rule returns later, a retained record applies again; a cleared record leaves it following the default. Both commands first acknowledge acceptance and deliver the final result in subsequent feedback.

## Editor Sessions

Run `/idtw editor open` to open the rule editor, or use `/idtw editor` directly. Tab lists `open`, `status`, and `release` according to your permissions.

`editor open` checks permission and the client channel, acquires the exclusive session, and sends the GUI opening request. When occupied, it names the holder and points to the status command.

`editor status` shows waiting to open, editing, or saving/rolling back. Owners can inspect their own session; other authorized sources see the holder's name. Ordinary guests see that the editor is occupied. Session timeouts use server activity ticks, so pausing preserves the remaining time.

`editor release` releases an opening or active session and notifies the online holder that it expired. When saving or rollback is in progress, release takes effect after that work finishes.

## Related

- [Getting Started](en-01-getting-started)
- [Source Layers and Overrides](en-06-layers-and-overrides)
- [Server Configuration](en-07-server-config)
