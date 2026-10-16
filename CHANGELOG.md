# Changelog

All versions target Minecraft 26.3, Java 25, Fabric Loader 0.19.5 and Fabric API 0.161.0+26.3. Every version is a development alpha; start with a new test world. Detailed test records live in [docs/verification](docs/verification/).

## 0.1.0-alpha.18 — 2026-10-16

- The map and the globe no longer flicker while you move them. Each block of the flat map is a fixed pixel in its picture, the picture moves in whole screen pixels, and the globe is drawn at screen resolution.
- Zooming with the mouse wheel and the buttons glides instead of jumping.
- New map setting in the gear panel: **Map: continued** (the world goes on past its edges, as before) or **Map: one square** (the world shown once, dark past its outlined edge).
- The yellow seam lines are gone from the map and the minimap.
- The map's buttons (close, zoom, centre, gear) are drawn as pixel icons in one style.
- Map and globe pictures render on all processor cores.

## 0.1.0-alpha.17 — 2026-10-16

- New atlas layout: the map fills the screen, and its few controls sit on it. Map or globe at the top left, close (×) at the top right, zoom in and out, centre and the minimap settings (gear) at the bottom right. The minimap settings open in a small panel beside the gear.
- The atlas shows the block coordinates under the mouse on the flat map, otherwise the player's position.

## 0.1.0-alpha.16 — 2026-10-16

- The map shows relief: slopes and water depth are shaded like vanilla maps (softer). Areas mapped before keep their flat look until they are seen again.
- Unexplored areas on the map are dark slate instead of light grey, so they no longer look like stone.
- The explored singleplayer map survives mod and data pack updates. Before, updating any mod started an empty map; old copies are cleaned up.
- The open map only redraws when the part you look at changes; zoom is saved when the map closes.
- Zoom keys work on German and other keyboard layouts, including numpad minus.
- Pick block (middle click) and other block actions work after walking once around the world.
- A memory leak in the End and in normal worlds is fixed.
- A broken world size in a save reports an error instead of crashing.
- German texts use ß consistently.

- Distant Horizons: far terrain continues past the world's edges, like the world itself. Before, past a seam the far view was empty and the world floated in the distance as a block.
- Map and globe follow the mouse exactly when dragged; the picture is drawn in the background, so the game no longer stutters while the map is open or the minimap is on.
- The globe turns freely in every direction.
- Orbis runs on its own world engine only. The old engine is removed; worlds created with it by earlier alpha builds no longer load. Start a new world.
- New **World shape** row on the World tab of Create World (Normal / Orbis), with **Customize** for the size.
- Server `level-type` presets `orbis:tiny` to `orbis:earth` create worlds on the new engine.
- The map continues across the seam.
- The globe is lit in 3D, on the map screen and in the corner minimap.
- Chunky no longer leaves half-generated chunks at the world edge.
- Server maps keep the terrain you explored.
- The map key (**M**) can be rebound; the map zooms.
- One-line join message.
- `/orbis check` in English and German.
- Every player needs Orbis on an Orbis world; players without it, or with an incompatible version, are turned away with a clear message naming the version.

## 0.1.0-alpha.12 — 2026-10-12

- Server maps remember what you explored: terrain is saved on your computer per server world and shown again after reconnecting. Before, the map only showed the area around you after a new login. Server and players need alpha.12.
- Documentation brought up to date for the first alpha release.

## 0.1.0-alpha.11 — 2026-10-09

- Faster first map build: initial capture no longer competes with periodic refreshes, so every loaded chunk gets mapped. In the test world the full map archive was ready about 25% sooner.
- Saved chunks are decoded about ten times faster by reading only surface heights and the block palettes needed for colours.
- Live terrain colours now take priority over older archived colours; unchanged tiles are no longer rewritten.
- [Test record](docs/verification/alpha11/RESULTS.md).

## 0.1.0-alpha.10 — 2026-10-09

- Map cursors use Minecraft's own map marker textures; other players appear as green markers with their name on hover.
- With Orbis on the server, player positions are sent twice per second, so distant players in the same dimension show up on the map.
- Dragging the map and globe works with Minecraft 26.3's mouse handling; **Center** returns to following the player.
- [Test record](docs/verification/alpha10/RESULTS.md).

## 0.1.0-alpha.9 — 2026-10-09

- Singleplayer map reuses saved terrain and Distant Horizons data again, with nearby chunks captured first.
- Map textures are released on the client thread after disconnecting.

## 0.1.0-alpha.8 — 2026-10-09

- The full map/globe atlas is available in the normal build and on servers.
- Operator-only `/orbis check` looks for the expected biomes and structures in the Overworld and Nether, within a fixed time budget.
- Simpler atlas: no on-map zoom buttons; unexplored terrain is drawn as two shades of gray.
- [Test record](docs/verification/alpha8/RESULTS.md).

## 0.1.0-alpha.7 — 2026-10-09

- Dedicated server support with `level-type` presets `orbis:tiny` to `orbis:earth`.
- Player minimap in the normal build, using only chunks the client has received.
- Verified on a real local server with an Orbis client and a vanilla client. [Test record](docs/verification/alpha7/RESULTS.md).

## 0.1.0-alpha.3 to alpha.6 — 2026-10-06 to 2026-10-08

- Corner minimap, windowed atlas, flat and globe views, persistent map cache and the Orbis cartography advancements.
- Separate debug build with biome and structure surveys ([debug atlas](docs/DEBUG_ATLAS.md)).
- Normal and debug builds verified separately in CI.

## 0.1.0-alpha.2 — 2026-10-05

- Standalone: the world engine is compiled into the Orbis JAR; no separate engine mod is installed.
- World sizes Tiny, Small, Normal, Large and Earth with a logarithmic slider and exact input.

## 0.1.0-alpha.1 — 2026-10-05

- First Orbis world shape on top of a separately installed engine mod.
