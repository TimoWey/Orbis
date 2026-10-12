# Changelog

All versions target Minecraft 26.3, Java 25, Fabric Loader 0.19.5 and Fabric API 0.161.0+26.3. Every version is a development alpha; start with a new test world. Detailed test records live in [docs/verification](docs/verification/).

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

- Standalone: the MIT engine source is compiled into the Orbis JAR; Toroidal World is no longer installed separately.
- World sizes Tiny, Small, Normal, Large and Earth with a logarithmic slider and exact input.

## 0.1.0-alpha.1 — 2026-10-05

- First Orbis world shape on top of a separately installed Toroidal World engine.
