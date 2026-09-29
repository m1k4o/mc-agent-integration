# mc-agent-integration

AI-agent bridge for Minecraft 26.1.2 (Fabric). The agent runs the **`mcai` terminal
command** and gets game state as JSON on stdout — no screenshots, no guessing.

- Mod: `mc-agent-integration-1.0.0.jar` (in PolyMC `26.1.2 Main/.minecraft/mods`)
- CLI: `bin/mcai` (installed to `~/.local/bin/mcai`, on PATH)
- MC 26.1.2, Fabric Loader 0.19.5, Fabric API 0.155.3+26.1.2, Java 25
- Client-side only: works in singleplayer and on any server, no server install.
- Transport: file bridge in `<gameDir>/mcai/` (no network server).
  CLI writes `cmd.json`, the mod answers `resp.json` on the client tick thread.
  Snapshots (`state/hud/inventory/container/alive.json`) refresh ~4x/sec.

## Terminal usage (what the agent runs)

```bash
mcai                      # clean readable list, split into OUTPUT / INPUT
mcai state                # full snapshot JSON -> stdout
mcai hud                  # chat+titles+sidebar+bossbars in one call
mcai press forward 20     # hold W for 20 ticks -> JSON receipt
mcai --snap state         # read last snapshot file only (no live request)
mcai --dir <gameDir> ...  # override game dir (or $MCAI_DIR)
mcai --timeout 3 ...      # live-request timeout in sec (default 8)
```

`mcai` with no args prints the human/agent-readable command list.
Every other command prints a JSON object to stdout (exit 0 on `ok:true`).

## Commands

`/mcai` and `/mc-agent-integration` (alias, help only). `/mcai` with no args
prints help JSON separated into `input` and `output`.

All output is compact JSON in chat (screenshot-readable, no human tables).

### Output (sensors — read from memory, no screenshots needed)

| Command | Reads |
|---|---|
| `/mcai state` | pos/yaw/pitch, dimension, health/food/air/armor, flags, xp, world time/weather/difficulty, biome/light, held keys |
| `/mcai pos` | x y z yaw pitch |
| `/mcai world` | gameTime/dayTime/raining/thundering/difficulty + biome/light |
| `/mcai chat [limit]` | last N chat/system messages (mixin + Fabric events) |
| `/mcai titles` | title/subtitle/actionbar |
| `/mcai sidebar` | scoreboard sidebar objective + lines |
| `/mcai bossbars` | boss bars with progress/color |
| `/mcai inventory` | player slots (non-empty only + `emptyRanges` like `["8-29"]`) + selected/offhand/mainhand + `openContainer` when a container is open |
| `/mcai container` | open menu slots (same compact format) + `containerSize`/`playerSlotsFrom` split + title + screen name + carried stack |
| `/mcai screen` | which GUI is open (name/title/isContainer/isChat/isInventory) |
| `/mcai alive` | liveness ping: hasWorld + open screen |
| `/mcai recipe <item> [limit]` | recipe-book entries crafting an item, with ingredient options + `recipeId` |
| `/mcai held` | mainhand + offhand |
| `/mcai entities [radius] [limit]` | nearby entities type/pos/dist/hp (default 32/32) |
| `/mcai target` | crosshair block/entity + 5-block raycast |
| `/mcai scan [radius]` | block census cube (default r=8) — agent map without screenshots |
| `/mcai effects` | mob effects amp/ticks |
| `/mcai hud` | chat+titles+sidebar+bossbars in one call |
| `/mcai keys` | held simulated keys |
| `/mcai events [limit]` | recent captured events |

### Input (actuators — key-sim only, no teleport/pathfind)

| Command | Does |
|---|---|
| `/mcai say <msg>` | send chat |
| `/mcai run <cmd>` | run client command (no leading slash) |
| `/mcai look <yaw> <pitch>` | set rotation |
| `/mcai lookat <x> <y> <z>` | look at coords |
| `/mcai press <key> [ticks]` | hold key: forward back left right jump sneak sprint attack use (max 1200 ticks) |
| `/mcai release <key>` | release key |
| `/mcai stop` | release all |
| `/mcai click <slot> [button] [mode]` | click a slot in the open container/menu (button 0=left 1=right, mode pickup\|quickmove\|swap\|throw). Response includes `after` + `carried`. Get slot indexes from `/mcai container` or `inventory.openContainer` |
| `/mcai select <0-8>` | hotbar select |
| `/mcai drop [all]` | drop selected stack (1 or all) |
| `/mcai attack` / `/mcai use` | 1-tick pulse (hold `attack` via `press` to break blocks; `state.flags.breaking` confirms) |
| `/mcai interact [hand]` | right-click the crosshair entity |
| `/mcai place [hand]` | place held block on the crosshair face |
| `/mcai craft <recipeId> [one]` | place a recipe into the open crafting grid (default shift = full stack) |
| `/mcai equip <slot>` | shift-click a menu slot to auto-equip armor |
| `/mcai close` | close the open screen |
| `/mcai respawn` | respawn if dead |
| `/mcai clear` | clear chat/event buffers |

Example agent loop (screenshot the chat after each command):
```
/mcai state
/mcai hud
/mcai entities 24 10
/mcai press forward 20
/mcai look 90 0
/mcai target
/mcai inventory
```

## Build

Needs JDK 25 (PolyMC instance uses `/usr/lib/jvm/jdk-25.0.4.1-oracle-x64`):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-25.0.4.1-oracle-x64
./gradlew build
# jar -> build/libs/mc-agent-integration-1.0.0.jar
# copy to PolyMC mods to test
```

## Notes / future ideas (not in MVP)

- `events` polling already covers chat/title/actionbar/send; could add death/hurt/container-change hooks.
- `hud` single-call dump saves round-trips vs 4 separate calls.
- `scan` block census + `entities` + `target` replace most screenshot needs; visual screenshot still useful for terrain shape.
- Possible later: `waypoints`, `script` queues with tick delays, recipe/craftable queries, raycast-entity `interact`.
- No HTTP/file bridge, no Baritone — per request. Agent screenshots the game window and types `/mcai ...`.
