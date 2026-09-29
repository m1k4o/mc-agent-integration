---
name: mcai
description: Control Minecraft via the mcai terminal command (mc-agent-integration mod). Use when the task involves playing Minecraft, reading game state (inventory, HUD, screen, entities, blocks, recipes), or driving the player (move, look, click slots, craft).
---

# mcai — Minecraft agent bridge

The `mcai` shell command talks to a running Minecraft 26.1.2 client (Fabric mod
`mc-agent-integration`) through a file bridge. Every command prints one JSON
object to stdout. Exit code 0 means `"ok": true`.

Timing: 20 ticks = 1 second. Snapshots refresh ~4x/sec; a live request usually
answers in 1–3 ticks. Prefer a single `press forward 40` over many 1-tick presses.

## Prerequisites

- PolyMC instance `26.1.2 Main` launched, world open, mod jar in `mods/`.
- Bridge dir `<gameDir>/mcai/` exists once the game has ticked.
- Pre-flight: `mcai alive` (or `mcai --snap alive`). If a command fails with
  `bridge dir not found` or a stale-heartbeat warning, the game is not running —
  tell the user to launch it, do not retry in a loop.
- One agent at a time: the bridge has a single `cmd.json` slot; concurrent
  processes clobber each other's requests.

## Bare command lists the API

- `mcai` (no args) prints the readable command list split into
  OUTPUT (sensors) and INPUT (actuators). Start here when unsure.
- `mcai --json help` prints the same catalog as JSON.

## Sensors (read-only, prefer these over screenshots)

- `mcai state` — pos/yaw/pitch, dimension, health/food, flags (incl. `breaking`),
  xp, world, biome/light, held keys.
- `mcai hud` — chat + titles + sidebar + bossbars in one call (cheapest full picture).
- `mcai chat [limit]` / `mcai events [limit]` / `mcai titles` / `mcai sidebar` / `mcai bossbars`.
- `mcai screen` — which GUI is open: `{open, name, title, isContainer, isChat, isInventory}`.
  Check this when unsure what the player is looking at (chest vs death vs pause).
- `mcai inventory` — player slots **plus `openContainer` when a container is open**
  (`containerOpen: true/false`). Always check this before clicking container slots.
- `mcai container` — open menu only: title, screen, slots, carried cursor stack.
- `mcai entities [radius] [limit]` (defaults 32/32, cap 128) — type/pos/dist/hp.
- `mcai target` — crosshair block/entity + 5-block raycast.
- `mcai scan [radius]` (default 8, cap 24) — block census around the player.
- `mcai recipe <item> [limit]` — recipe-book entries crafting an item: ingredient
  options, `craftable` flag, and `recipeId` for `craft`.
- `mcai effects` / `mcai keys` / `mcai pos` / `mcai world` / `mcai held` / `mcai alive`.

### Slot format (token-efficient)

`slots` lists **only non-empty slots** as `{slot, id, count, name}`.
Contiguous empty runs collapse into `emptyRanges`, e.g. `"emptyRanges": ["8-29"]`
means slots 8 through 29 are all empty. `size` and `nonEmpty` give the totals.
Never ask for slots one by one — read the whole dump once.

### Slot index spaces (do not mix them up)

- `mcai inventory` → player-inventory indexes (0–40).
- `mcai container` / `inventory.openContainer` → **menu** indexes for the open
  screen. `0..containerSize-1` is the chest/furnace/table side
  (`playerSlotsFrom == containerSize`); the rest is the player's own inventory.
  Always use menu indexes with `click`/`equip`, never inventory indexes.

## Actuators (key-simulation, no teleport/pathfind)

- Movement: `mcai press <forward|back|left|right|jump|sneak|sprint|attack|use> [ticks]`
  (cap 1200), `mcai release <key>`, `mcai stop` (release all).
- Look: `mcai look <yaw> <pitch>`, `mcai lookat <x> <y> <z>`.
- Chat/command: `mcai say <message>`, `mcai run <command>` (no leading slash).
  Fire-and-forget: to confirm an effect, poll `mcai chat`/`mcai state` after.
- Inventory: `mcai select <0-8>`, `mcai drop [all]`, `mcai equip <slot>`
  (shift-clicks a menu slot to auto-equip armor).
- Container clicks: `mcai click <slot> [button] [mode]` — menu indexes from
  `mcai container` / `inventory.openContainer.slots`; button `0`=left, `1`=right;
  mode `pickup` (default), `quickmove` (shift-click), `swap`, `throw`.
  The response includes `after` (slot now) and `carried` (cursor).
  Typical take-item flow: `click <slot> 0` then check `carried`.
- Targeted interaction: `mcai interact [hand]` (right-click crosshair entity —
  traders, boats, minecarts), `mcai place [hand]` (place held block on crosshair
  face). Verify `target` first; both return a `result` string.
- Crafting: `mcai recipe <item>` → pick a `recipeId` with `craftable: true` →
  open the table in-game → `mcai craft <recipeId>` (add `one` for a single item).
- `mcai attack` / `mcai use` (1-tick pulses; hold `attack` via `press` to break
  blocks — `state.flags.breaking` confirms), `mcai close` (close any screen),
  `mcai respawn`, `mcai clear`.

## Flags

- `--dir <gameDir>` (or `$MCAI_DIR`) — point at another instance's `.minecraft`.
- `--timeout <sec>` — live-request timeout, default 8.
- `--snap <sensor>` — read the last snapshot file without waking the game
  (`state|hud|inventory|container|alive|screen`).

## Worked examples

Chest loot: `target` (aim at chest) → `use` → `container`
(read `containerSize`, pick a filled menu slot) → `click <slot> 0 quickmove`
→ verify `carried`/inventory → `close`.

Move-look-verify: `state` (note pos) → `press forward 40` → `state` (compare pos;
unchanged = stuck → `target`+`scan` for the obstacle, `jump` or steer with `look`).

Craft sticks: `recipe stick` → open crafting table (`use` on it) →
`craft <recipeId>` → `inventory` (verify).

## Failure playbook

- Key stuck (keeps moving): `mcai stop`.
- `click` no-op: `carried` unchanged and `after` unchanged → wrong slot space
  (menu vs inventory indexes) or container closed mid-flow (`containerOpen: false` → re-open).
- `no_entity_target` / `no_block_target`: crosshair is off — `look`/`lookat` first, confirm with `target`.
- `craft` silently does nothing: crafting screen not open, or recipe not `craftable` (missing ingredients per `recipe`).
- Screenshots of the game window are for terrain shape/orientation only —
  text, inventories, and entities come from `mcai`, never from OCR.
