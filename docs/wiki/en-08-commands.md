# Command Reference (/idtw)

> Applies to **2.0.0** (Minecraft **1.21.1**).

Type `/idtw` in chat to view available command entries. All commands support Tab completion for IDs, subcommands, and arguments.

---

## Command Quick Reference

| Command | Description | Required Permission |
| --- | --- | --- |
| `/idtw` | Display help and available commands | Everyone |
| `/idtw editor` (or `/idtw editor open`) | Open the in-game GUI rule editor | Admins (Singleplayer owner or OP ≥ 2) |
| `/idtw editor status` | Check if the editor is occupied by another player | Everyone |
| `/idtw editor release` | Force-release an occupied editor session | Admins (Permission level ≥ 2) |
| `/idtw rule list [page] [filter]` | Browse active rules by page | Admins |
| `/idtw rule show <id>` | Inspect summary details of a specific rule | Admins |
| `/idtw rule show <id> raw [page]` | View the raw JSON from disk by page | Admins |
| `/idtw rule validate [page]` | Scan files for syntax errors and parameter issues | Admins |
| `/idtw rule reload` | Reload disk files and apply changes immediately | Admins |
| `/idtw rule enable <id>` | Force-enable a rule for the current world save | Admins |
| `/idtw rule disable <id>` | Force-disable a rule for the current world save | Admins |
| `/idtw rule reset <id>` | Clear save override and revert to file default | Admins |
| `/idtw rule orphans [page]` | Inspect leftover records for deleted rules | Admins |
| `/idtw rule orphans clear` | Clean up leftover records for deleted rules | Admins |

> **Permissions**: In singleplayer, the world owner automatically has management access. In multiplayer servers, management commands require permission level 2 or above.

---

## 1. Rule Editor Commands

### `/idtw editor` or `/idtw editor open`
- **Purpose**: Opens the visual in-game rule editor.
- **Exclusive session**: To prevent conflicting concurrent edits, only one player may hold the editor at a time.
- **Handling occupancy**: If the editor is held by another player, wait for them to finish or have an admin release the session.

### `/idtw editor status`
- **Purpose**: Checks whether a player is currently using the editor.
- **Output**: Shows the current session state and the holder's player name.

### `/idtw editor release`
- **Purpose**: Forcefully closes and releases an active editor session.
- **Usage**: Useful if the current holder is AFK or disconnected.

---

## 2. Rule Inspection & Diagnostics

### `/idtw rule list [page] [filter]`
- **Purpose**: Lists all active rules, 8 entries per page.
- **Examples**:
  - `/idtw rule list`: View page 1;
  - `/idtw rule list 2`: Turn to page 2;
  - `/idtw rule list 1 diamond`: Search rules matching `"diamond"` and show page 1.
- **Interactive features**: Hover over rows to view the rule source; click any row to autofill `/idtw rule show <id>` into chat.

### `/idtw rule show <id>`
- **Purpose**: Displays a summary of the specified rule in chat.
- **Details**: Name, source layer (datapack or overlay), effective status, priority, trigger kinds, wait timer, and action statistics.

### `/idtw rule show <id> raw [page]`
- **Purpose**: Displays the raw formatted JSON directly from disk.
- **Details**: Outputs in paged segments for easy reading and copying.

---

## 3. Validation & Reloading

### `/idtw rule validate [page]`
- **Purpose**: Scans all rule files on disk for syntax errors or invalid IDs without modifying live rules.
- **Feedback**:
  - Shows "Validation passed" if everything is clean;
  - Paginates and highlights errors in red (with file names and field paths) for fast troubleshooting.

### `/idtw rule reload`
- **Purpose**: Reloads all rule files from disk, making updates active immediately in the running world.
- **Fault tolerance**: If individual rules contain errors, only the invalid rules are rejected; all valid rules load normally.

---

## 4. Per-Save Overrides & Cleanup

### `/idtw rule enable <id>` and `/idtw rule disable <id>`
- **Purpose**: Independently enables or disables a rule for the current world save.
- **Save isolation**: Saved in world data without altering JSON files. Even if a JSON has `"enabled": false`, running `enable` forces it active in this save.

### `/idtw rule reset <id>`
- **Purpose**: Removes the per-save override, restoring the rule's original `enabled` state from JSON.

### `/idtw rule orphans` and `/idtw rule orphans clear`
- **Purpose**: Identifies and clears leftover enable/disable records in save data for rules that have since been deleted from disk.

---

## Next Steps

- [Getting Started](en-01-getting-started): Step-by-step GUI guide.
- [Source Layers and Overrides](en-06-layers-and-overrides): Understand datapack and overlay priority.
- [Server Configuration](en-07-server-config): Server performance and limits.
