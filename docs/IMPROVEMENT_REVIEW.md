# Improvement review (2026-10-16)

A read-only review of the whole code base at PR #9 (commit f5de05d): engine, atlas (map, minimap,
globe), build, CI, tests and docs. Nothing was measured or tested in game during the review. The IDs
below match the German review document shared in the project; this file records the state of the
analysis before any of it was implemented.

Effort: S = under an hour, M = a few hours, L = a day or more. "Verified" means the code path was
traced; "likely" means it depends on vanilla behaviour that was not read during the review.

## Recommended first round

1. CI cost (B1, B2): the 50-minute game test restarts on every push while a PR carries the
   `gametest` label, and any other label cancels the running build.
2. Explored map survives mod updates (K1).
3. Height shading on the map (V1).
4. Map stutter (P1, P2).
5. Small engine fixes (K3, K4, K7).
6. Check the Earth size (K2).
7. Clean-ups: version (B7), icon size (B10), German strings (V10), old-engine leftovers (K10, B13).

## Performance

| ID | Finding | Where | Effort |
| --- | --- | --- | --- |
| P1 | Multiplayer chunk sampling runs on the render thread with a 2 ms budget per tick; every 10 s all loaded chunks are queued again | `atlas/AtlasTerrainCollector.java:12, 40-41`, `atlas/PlayerAtlasClient.java:113-117` | M |
| P2 | Any tile written anywhere re-projects and re-uploads the whole visible map/globe (global instead of regional revision) | `atlas/AtlasTerrainBackdrop.java:33, 41-44` | S |
| P3 | Globe backdrop allocates per pixel and renders twice per frame; HUD globe results are usually discarded | `atlas/AtlasTerrainBackdrop.java:52-58, 81-84` | M |
| P4 | Hot mixins (light, tickets, aquifer, entity distance) box through `WrapOperation`; light engine looks the torus up per step | `engine/mixin/LightPropagationMixin.java`, `LightEngineMixin.java`, `ChunkTrackerMixin.java`, `NoiseBasedAquiferMixin.java`, `EntityMixin.java` | M |
| P5 | `Level.getEntities` allocates arrays and lambdas on every call, even away from the seam | `engine/mixin/LevelMixin.java:36-59`, `engine/Fold.java:98-120` | S |
| P6 | Outgoing packets are always rebuilt or re-encoded, even when no coordinate changes | `engine/net/ClientPackets.java` | S |
| P7 | Structure locate scans the full square per ring (about 1.4M checks at radius 100) | `engine/mixin/ChunkGeneratorLocateMixin.java:54-88` | M |
| P8 | `getChunk` always divides, even inside the world | `engine/mixin/ServerChunkCacheMixin.java:23-36` | S |
| P9 | Periodic Perlin sampling does extra divisions per octave | `engine/noise/PeriodicNoises.java:111-119, 170-175` | S |
| P10 | `AtlasTerrainQueue.remove` is a linear scan on every chunk load/unload | `atlas/AtlasTerrainQueue.java:16-25` | S |
| P11 | Tile archive: up to about 136 MiB heap per dimension, 4 bytes per pixel, uncompressed | `atlas/AtlasTileArchive.java` | L |
| P12 | Each tile write opens its region file; one global lock blocks all readers | `atlas/AtlasTileArchive.java:49-86` | M |
| P13 | Every world load re-reads every saved chunk even when tiles exist | `atlas/AtlasSavedTerrain.java`, `atlas/AtlasTerrainImport.java` | S-M |
| P14 | Zoom saves the preferences file on every scroll step | `atlas/PlayerAtlasScreen.java:59-63`, `atlas/AtlasPreferences.java:62-65` | S |
| P15 | Per-frame small work: minimap colour conversion on the render thread, extra image copy, fog per pixel, translations per frame, normal-priority workers | various atlas files | S |

## Visuals and controls

| ID | Finding | Where | Effort |
| --- | --- | --- | --- |
| V1 | No height or water-depth shading like vanilla maps | `atlas/AtlasTerrain.java`, `atlas/AtlasSavedSurface.java`, `atlas/AtlasDistantTerrain.java` | M |
| V2 | Unexplored fog is bright grey and reads as real stone | `atlas/AtlasUnknownTerrain.java:7-8` | S |
| V3 | Flat minimap shimmers (1 texel per block, non-integer scale) | `atlas/AtlasMinimapTexture.java`, `atlas/AtlasMinimapCache.java` | M |
| V4 | Minimap goes blank while a new window renders | `atlas/AtlasMinimapTexture.java:19` | S |
| V5 | Full map lacks cursor coordinates, scale, compass, zoom-to-cursor, smooth zoom, waypoints | `atlas/PlayerAtlasScreen.java` | M (waypoints L) |
| V6 | Zoom keys use US key codes, so +/- likely fail on QWERTZ; numpad minus missing | `atlas/PlayerAtlasScreen.java:144-145` | S |
| V7 | Top-right minimap overlaps status effects and F3 text | `atlas/OrbisMinimap.java:14-19` | S |
| V8 | Full map is square on wide screens; globe rim is jagged on large screens | `atlas/PlayerAtlasScreen.java:24`, `atlas/AtlasMapRaster.java` | S-M |
| V9 | Player arrow turns in 22.5° steps; far players jump every 10 ticks | `atlas/AtlasPlayerMarker.java`, `atlas/AtlasPlayersServer.java` | S |
| V10 | German strings mix "ss" and "ß", a missing space, untranslated N/S/W and screen title | `lang/de_de.json`, `atlas/OrbisMinimap.java`, `atlas/PlayerAtlasScreen.java` | S |
| V11 | Numbers always use English formatting | `OrbisSettingsScreen.java:124-125` | S |
| V12 | Yellow seam line on map/minimap (known, decision open) | `atlas/AtlasMapRaster.java:120-123` | S |

## Correctness and clean-up

| ID | Finding | Where | Effort | Confidence |
| --- | --- | --- | --- | --- |
| K1 | Updating any mod or datapack, or moving the world, discards the explored singleplayer map; old copies are never deleted | `atlas/AtlasCache.java:47-57`, `atlas/PlayerLocalTerrain.java`, `atlas/AtlasWorldIdentity.java` | M | verified |
| K2 | For widths above about 12.5M blocks (Earth) the periodic noise cell count overflows `int` | `engine/noise/PeriodicNoises.java:112, 165`, `engine/mixin/BlendedNoiseMixin.java:31-32` | S-M | likely |
| K3 | Player ticket mixin records levels in non-torus levels (End, normal worlds) and never removes them | `engine/mixin/PlayerTicketTrackerMixin.java:39-42` | S | verified |
| K4 | Pick block and other block-position packets fail after a lap around the world | `engine/mixin/ServerGamePacketListenerImplMixin.java`, `engine/mixin/PlayerMixin.java` | S | likely |
| K5 | Client lap offset is unbounded and the position is clamped before folding | `engine/net/ClientFrame.java:51-55`, `ServerGamePacketListenerImplMixin.java:49-61` | S-M | verified/likely |
| K6 | Untranslated packets: look-at, locator bar, compass spawn, world border | `engine/net/ClientPackets.java` | M | likely |
| K7 | World size is not validated by the codec; a broken level.dat crashes instead of reporting | `engine/TorusChunkGenerator.java:22-32` | S | verified |
| K8 | Scheduled ticks from world generation near the seam keep unfolded positions | `engine/mixin/LevelTicksMixin.java` | M | likely |
| K9 | On servers without Orbis the map is memory-only (8192 chunks) and lost on reconnect | `atlas/PlayerAtlasClient.java`, `atlas/AtlasTerrainCollector.java` | M | verified |
| K10 | Old-engine leftovers: no-op `GenerationChunkHolderMixin`, priority 2000 in `EntitySelectorMixin`, stale comments, missing `minVersion` | `engine/mixin/`, `engine/Tori.java:27` | S | verified |
| K11 | `engine/PeriodicNoise.java` is only used by tests | `engine/PeriodicNoise.java` | S | verified |
| K12 | Dead branch for views wider than the world | `engine/mixin/ChunkTrackingViewMixin.java:38-45` | S | verified |
| K13 | Duplicated fold-back logic; inconsistent thread safety in `ClientFrame` | `ServerGamePacketListenerImplMixin.java`, `engine/EntityFold.java`, `engine/net/ClientFrame.java` | S | verified |

## Build, tests and docs

| ID | Finding | Where | Effort |
| --- | --- | --- | --- |
| B1 | Game test reruns on every push while the `gametest` label is present | `.github/workflows/build.yml` | S |
| B2 | Any other label cancels the running build with no replacement | `.github/workflows/build.yml` | S |
| B3 | Two full jobs per run; the debug job repeats every normal unit test | `.github/workflows/build.yml`, `build.gradle` | S-M |
| B4 | A merge builds the same tree again on `main` | `.github/workflows/build.yml` | S |
| B5 | Artifacts kept for the default 90 days | `.github/workflows/build.yml` | S |
| B6 | Coarse Gradle cache shared by both jobs | `.github/workflows/build.yml`, `gradle.properties` | S |
| B7 | Version still `0.1.0-alpha.12`; rejection message tells alpha.12 players to install alpha.12 | `gradle.properties`, `CHANGELOG.md`, `README.md` | S |
| B8 | No unit tests for `Fold`, `ClientFrame` lap math or 1:8 across all sizes | `src/test/java/dev/orbis/engine/` | M |
| B9 | README links old-engine verification records | `README.md`, `docs/IMPLEMENTATION.md` | S |
| B10 | Mod icon is 586 KB | `src/main/resources/assets/orbis/icon.png` | S |
| B11 | Two jar verifiers with overlapping, drifting checks | `tools/verify_standalone.py`, `tools/distribution.gradle` | S-M |
| B12 | Game test sources may not compile in normal CI | `build.gradle` | S |
| B13 | Old-engine leftovers in CI paths, an API probe step, unused files, stale docs | `.github/workflows/build.yml`, `fabric.mod.json`, `docs/` | S |
| B14 | README loads about 24 MB of GIFs | `README.md`, `docs/media/` | S |
