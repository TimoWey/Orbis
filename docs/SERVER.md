# Server and player guide — 0.1.0-alpha.17

The normal build `orbis-0.1.0-alpha.17.jar` contains the world engine, the minimap and the map/globe atlas. Server and players use the same JAR; map rendering only runs on clients. The developer interface and its automatic singleplayer surveys stay in the separate [debug build](DEBUG_ATLAS.md).

## Versions and installation

- Minecraft Java **26.3**, **Java 25**.
- Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**.
- Server: put Fabric API and `orbis-0.1.0-alpha.17.jar` into the Fabric server's `mods/` folder.
- Players: install the same Fabric Loader, Fabric API and Orbis versions in their own Minecraft profile.
- Move older Orbis JARs out of `mods/` first. Never install the normal and debug builds together; `-sources.jar` does not belong in `mods/`.

Every player needs Orbis. A game without it is turned away while connecting, with the message "This world needs the Orbis mod. Install Orbis *version* for Fabric (Minecraft 26.3) to join." A player whose Orbis cannot play with the server's leaves with both versions named. Servers whose world is not an Orbis world let anyone in. Other mods may have their own client requirements.

## Map controls

**M** opens and closes the atlas (rebindable under Controls). Top row: **Map**, **Globe**, **Center**, **Close**. Drag to move the map or rotate the globe; the mouse wheel zooms (the +/− keys work too). Dragging stops following the player; **Center** (or Home) follows the player again. Bottom row: minimap on/off, corner, size and minimap zoom. **F1** hides the HUD. The unwrapped End always uses the flat map. In the Nether the map shows a fixed slice at Y=32. On an Orbis world the flat map and minimap stay centred on you beside a seam and go on with the other side of the world; yellow lines mark the seams.

Settings are saved atomically in `config/orbis-atlas.properties` and survive restarts. Flat zoom, globe zoom and view mode are stored per dimension; minimap corner, size, visibility and zoom are global. When reopened, the map follows the player again. The settings also apply on other servers, without transferring terrain.

Your own marker is Minecraft's white map cursor with its 16 directions. Other players appear as green vanilla map markers; hovering in the large atlas shows their name. The atlas and minimap show players of the current dimension inside the visible area; zoom out to find distant players. On the globe, markers on the far side stay hidden. Resource packs apply to these textures just like on vanilla maps.

Player positions are sent twice per second, including players outside entity view distance. Invisible players and spectators are hidden, and no chunks are requested for this. The server only sends to clients that support the channel, at most 4,096 players per dimension. Logouts and dimension changes replace the list; it is cleared on disconnect. Stale positions disappear after three seconds without an update.

## How the map gets its terrain

- **On servers** the atlas only draws chunks the client has already received. It uses no world seed, no structure search and no extra chunk requests. With Orbis alpha.12 or newer on the server, everything you explored is kept on your computer under `orbis-atlas-cache/servers/` and shown again after reconnecting. The server sends an opaque world id (a SHA-256 hash, no seed or path), so each server world gets its own map and a reset world starts empty. Older servers only keep up to 8,192 chunks per dimension in memory until you leave.
- **In singleplayer** the map also imports already saved world terrain and optional Distant Horizons data in the background into a persistent cache under `orbis-atlas-cache/` in the Minecraft profile, separated by world and generator settings. A version or modpack change may invalidate it once. No extra live chunks are loaded or generated.
- Loaded chunks are captured nearest-first, with up to 32 captures per tick inside a soft 2 ms budget; known chunks are refreshed after ten seconds. A share of the budget stays reserved for block changes, so edits show up without reconnecting.
- Unexplored terrain is drawn in two shades of gray (darker water, lighter land). It is symbolic, not a prediction of the real terrain.

## Admin check

Operator permission level **3** or the server console:

- `/orbis check`: biomes and structures in the Overworld **and** the Nether.
- `/orbis check biomes` / `/orbis check structures`: only that category, both dimensions.
- `/orbis check status`: progress, or the last result since server start.
- `/orbis check cancel`: stop a running check.

Only one check runs per server. The result appears in chat for the operator who started it, in English or German, and in the server log. It looks for the biomes and structures the current generator can produce in each dimension, including a separate Stronghold portal-room check.

The check does not load or generate world chunks. It queries the biome generator and tests possible structure starts in unsaved scratch chunks. It runs on the server thread with a soft budget of 3 ms per tick, at most one structure placement per tick, up to 33,792 biome queries and 2,048 structure candidates per dimension. Single native generator calls can take longer. After 2,400 ticks per dimension it stops with a note that the time budget was reached (about two minutes per dimension at 20 TPS).

**"Not found" means unconfirmed, not proven missing.** Proving absence would need a much larger search. Structures are generator predictions; removed buildings or chunks saved before a generator change are not checked. Biomes or structures outside the dimension's generator configuration do not count as missing. No preset guarantees full coverage.

## Creating a new server world

1. Install a Fabric server for the versions above; put Fabric API and Orbis into `mods/`.
2. Read and accept the Minecraft EULA as usual.
3. Before the first world is generated, set for example in `server.properties`:

```properties
level-name=orbis-world
level-type=orbis:tiny
level-seed=8675309
online-mode=true
view-distance=8
simulation-distance=6
```

4. Start with Java 25, for example `java -Xms2G -Xmx4G -jar fabric-server-launch.jar nogui` (the file name depends on the Fabric installer). 4 GB is a starting point, not a capacity guarantee.
5. With the whitelist enabled, add players from the console with `whitelist add <name>`.
6. Run `/orbis`. On an Orbis world it shows `Orbis | Overworld: W × W chunks | Nether: W/8 × W/8 | End: unwrapped`; otherwise it reports "This is not an Orbis world".

| `level-type` | Overworld width, blocks | Nether width, blocks |
| --- | ---: | ---: |
| `orbis:tiny` | 16,384 | 2,048 |
| `orbis:small` | 141,312 | 17,664 |
| `orbis:normal` | 1,224,704 | 153,088 |
| `orbis:large` | 10,000,128 | 1,250,016 |
| `orbis:earth` | 23,170,560 | 2,896,320 |

Tiny is the easiest size for a first shared test. Nothing is pregenerated. No preset guarantees every biome and structure.

`level-type` only applies when a **new** world is created. It does not convert or shrink an existing world. Worlds created by earlier alpha builds with the old engine no longer load. For a custom size, create a new Orbis world in singleplayer, quit cleanly and copy the whole save into the server's world folder. Back up existing worlds first.

## Build

```sh
bash gradlew build
python3 tools/verify_standalone.py build/libs/orbis-0.1.0-alpha.17.jar
```

The build checks both archives, missing or forbidden debug content, the server preset codecs and the stored dimension policy. For developers only: `bash gradlew build -PorbisDebug=true`, output in `build/debug/libs/`.

What was actually tested in Minecraft, and its limits, is recorded per version: [Alpha.11](verification/alpha11/RESULTS.md), [Alpha.10](verification/alpha10/RESULTS.md), [Alpha.8](verification/alpha8/RESULTS.md), [Alpha.7](verification/alpha7/RESULTS.md).
