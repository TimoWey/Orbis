# Debug minimap and atlas

The optional **alpha.3-debug** build adds a corner minimap and an atlas window for local singleplayer worlds. Press **M** to open the larger map; the inset, translucent window leaves Minecraft visible and the world keeps running. Close with M, Escape or Close. The binding can be changed in Minecraft's Controls menu.

## Build and install

With Java 25:

```powershell
.\gradlew.bat build -PorbisDebug=true
```

Install `build/debug/libs/orbis-0.1.0-alpha.3-debug.jar`, replacing the normal Orbis JAR. Do not install both. The normal build produces `build/libs/orbis-0.1.0-alpha.3.jar` without these debug classes, resources or entrypoint. The minimap currently requires the debug build and an integrated singleplayer server; it does not query remote multiplayer servers.

Normal and debug outputs are separate; normal `clean` also removes the nested debug output. Development launch: `gradlew.bat runClient -PorbisDebug=true`. Do not switch build variants while a development client is running. Packaging checks use `python tools/verify_standalone.py --debug <debug.jar>`, or omit `--debug` for the normal artifact. Separate CI release/debug artifacts still require the workflow update.

## Minimap, navigation and waypoints

- The HUD shows N/E/S/W (O for east in German), facing and waypoints, with separate labelled **X, Y, Z** lines below. Flat has a subdued pixel frame; Globe has transparent pixels outside the sphere, without a rectangular HUD panel. **Flat / Globe**, **TR / TL / BR / BL** and **Size: S / M / L** persist in `config/orbis-atlas.properties`. Sizes request 72, 96 or 128 GUI units and shrink to fit the scaled window.
- The non-pausing atlas is capped at 760 x 420 GUI units, leaving more game visible. Native Minecraft button sprites and selection outlines replace custom control backgrounds. Narrow map panes put navigation controls in two rows. Long names remain available in tooltips and narration.
- In the atlas, left-drag or use WASD to navigate. Scroll or use +/− to zoom. Flat contains exactly one canonical world rectangle; zoom-out and panning are bounded. Wheel zoom anchors under the pointer where bounds allow. **You** / Home focuses the player; near an edge the map stays inside its bounds instead of centering the player exactly. **All** restores the complete finite-world overview. Blank margins cannot receive waypoints.
- The globe is an orientation projection: Orbis connects both X and Z edges with torus topology. Unwrapped dimensions such as the End use the flat map and loaded observations; there is no finite whole-world survey there.
- Right-click the map to add a waypoint. Right-click near an existing visible marker to remove it. X/Z come from the selected map position; Y is the player's current height, not a terrain-height prediction. Up to 128 markers per save and dimension are supported. They appear in both the window and HUD.
- Waypoints persist in `config/orbis-waypoints.properties`, keyed by the absolute save-directory path and dimension. They do not modify the world save. Moving or renaming a save directory changes that association. Markers are simple numbered locations, without a name editor or teleport action.
- **Grid** toggles diagnostic chunk/place overlays. Player, selected locations and waypoints remain visible independently. Select a found checklist row to center its location without moving the player.

Loaded terrain uses **16 × 16 surface map colors per chunk**, one per block. Heightmap reads and bounded transparent-block descent use only the already-loaded chunk. Derived 4-block and chunk-average levels keep the overview efficient; Flat chooses 1/4/16-block levels from texture density, Globe 1/4-block levels. Finest colors persist in the cache, and coarse levels are rebuilt on restore. This is a surface color map, not a block-state/elevation archive or aerial screenshot. Unvisited areas retain the coarse generator biome preview without invented block details.

## Right-hand checklist

**Biomes**, **Structures** (**Strukturen** in German) and **Stats** share the compact right-hand panel. The dimension ID is visible above it. Arrow buttons, wheel or Page Up/Down browse entries. Filter All / Unconfirmed / Found; sort biomes by Name A–Z or Samples, structures by Name or Found first. Filtering does not change totals. Both checklists use single-line rows: a checkmark indicates a find, **O** means observed, **~** predicted and **?** unconfirmed. Query counts appear only in tooltips and Stats. Full IDs/evidence remain available through hover and keyboard focus.

Biome coverage counts distinct found candidates, using the union of loaded observations and generator samples. Samples count queries, not chunks, biome patches or surface area. Stats includes preset, world bounds, performance and map-history information. The preset is inferred from the Overworld width; old/custom sizes may show Custom.

### Biome evidence

| State | Evidence |
| --- | --- |
| Observed | Read from a real loaded chunk's biome cells, including underground sections. The location points to such a cell. |
| Sampled | The current generator returned the biome at a sampled position; no terrain was generated there. |
| Unconfirmed | The dimension's biome source permits it, but inspection and sampling have not found it. This does not prove absence. |

Expected candidates come exclusively from `possibleBiomes()` of the selected dimension's generator, including registered mod biomes. Other dimensions and unused registry entries are not mixed into this checklist.

A survey restores automatically on entry into a local finite dimension. A saved completed survey stays completed; an interrupted one resumes its exact next query. A new survey queries a fixed **256 × 256 grid at four heights**: sea level, minimum Y + 16, Y 32 and Y 128, clamped to vertical bounds. Pause/resume preserves progress. Explicit Resample on a completed pass clears predictions/counts, retaining observations and terrain. Closing the atlas leaves collection active for the HUD.

This is a bounded diagnostic, not an exhaustive biome census. Rare, small or vertically narrow biomes can be missed even on Tiny. Completing the grid never marks an unconfirmed biome absent, and cannot certify that every candidate exists. Generator predictions can differ from old chunks or changed datapacks. Full biome coverage is not guaranteed by choosing a larger preset either.

### Structure and portal evidence

**Check** in Structures starts/resumes its separate audit. A finished check can be explicitly restarted. Structure task cursors, including extra-start and ring-position indices, persist. The structure-type total excludes the supplementary **Stronghold / portal room** row: that is a piece of a stronghold, not a separate structure type.

| State | Meaning |
| --- | --- |
| Observed | A valid structure start was read from a loaded chunk. |
| Predicted | This seed and current generator produce a valid layout at an in-bounds start. |
| Unconfirmed | No evidence yet, including incomplete, paused or failed searches. |
| Not found in check | All relevant candidates were checked without finding a valid layout. This is not proof of absence from saved terrain. |

The audit queries generation directly rather than issuing `/locate` commands. It uses Minecraft's weighted structure selection, biome/height checks and Orbis extra-start hooks, enumerating random-spread regions and stronghold positions. Other placement types use a lazy chunk-by-chunk fallback. Each type retains one predicted example, and observations take precedence. The search stops when every listed type has evidence; large worlds and rare structures can take a long time.

Layouts use disposable ProtoChunks that are not installed in the chunk cache, terrain-populated or saved. **All listed types found** means at least one observed or predicted example per checklist entry in this dimension. It does not certify intact blocks, loot, every structure variant, or portal travel. Check the Nether separately; the unwrapped End has no finite candidate-exhaustion audit.

A portal-room prediction requires a Stronghold PortalRoom piece; its location is the bounding-box center, not a frame block. Loaded portal blocks and gateways appear as additional observed checklist rows/markers without entering the expected structure-type total. Reloaded/periodically inspected chunks refresh these block observations. Biome and structure observations remain historical evidence; they cannot certify that later edits left all blocks intact. Exhausted but unfound structures remain unchecked, with the completed-search detail in the tooltip.

## Performance and lifetime

Inspection and generation queries run on the integrated server thread; the renderer receives immutable snapshots twice per second. Each active tick schedules at most two loaded-chunk inspections, 256 biome queries and one structure-layout evaluation against a shared **3 ms scheduling budget**. Placement-task initialization is spread across ticks. A single layout, ring initialization or hook call cannot be preempted and may exceed that budget; this is not a hard tick-latency guarantee. Changing the active world cancels further work for its request.

The HUD keeps collection active after the atlas closes. Loaded chunks are queued again every 200 ticks (normally ten seconds), and immediately on reload, so edits replace cached surface colors. History retains at most **8,192 detailed chunks per dimension** (8 MiB raw finest colors, plus Java objects, indices, coarse levels and immutable snapshots), and 4,096 observed markers. Old changed/inserted tiles are evicted at the cap; Stats reports truncation. Evicted areas return to the biome preview until revisited. The overview remains at most 65,536 cells regardless of world size. No chunk objects are retained and no extra live chunks are requested.

Rendering uses cached dynamic textures, capped at 1,536 pixels on the longest flat-map side and 768 for the globe. Immutable geometry is shared across snapshots. Changes to view, terrain or overlays refresh textures; idle frames reuse them. View updates are coalesced to at most 30 texture uploads per second. The minimap quantizes its moving center to reduce uploads. These measures bound stored history and scheduled work; they are not a measured FPS guarantee.

## Disposable persistent cache

Data lives outside saves at `<game directory>/orbis-atlas-cache/<world hash>/<dimension hash>.atlas.gz`. Remove that directory while the world is closed to discard map caches. It contains neither world saves nor user waypoints.

World identity hashes the real save-directory path, directory creation timestamp and seed; dimension uses its full namespaced ID. Distinct paths, recreated directories, seeds and dimensions stay separate. Moving/copying a world intentionally starts another cache. Restoring a backup over exactly the same directory with preserved identity is not universally detectable; remove its cache explicitly if necessary.

Schema **1** stores a magic number, version, generation fingerprint, exact biome cursor, prediction colors/counts/locations, separately flagged observations, structure-task progress/results, markers and explored tiles. Unknown versions are ignored rather than guessed. Bump the version for incompatible sampling policies, tile/evidence semantics or checkpoint ordering.

The SHA-256 fingerprint includes the encoded dimension generator, seed, bounds, structure-generation flag, mod IDs/versions and names/content bytes of effective server data resources. Editing a same-named datapack therefore invalidates the cache, including templates and tags. Unrelated server-data changes also conservatively invalidate it. Successful datapack reload schedules a new fingerprint/restore. Unencodable generators or unreadable resources leave collection usable in memory without trusting an unidentified cache. Undeclared behavior changes in mods with unchanged versions/configuration may require manual deletion/resampling.

Fingerprint/file I/O runs on one background worker. The server does not block its tick waiting for restore, and starts no new survey until it resolves. Existing loaded chunks are then queued for fresh inspection. Only immutable captured map data crosses into the writer; it never reads chunks or live world state.

Changed state checkpoints every 600 ticks (normally 30 seconds), with at most one ordinary write in flight per dimension. Shutdown queues a final snapshot; only shutdown waits up to ten seconds. Compression writes a sibling temporary file, closes it and atomically replaces the previous file where supported (replace fallback otherwise). Failures appear in Stats/logs and are retried. Invalid headers/versions/fingerprints/counts, truncation or bad CRC discard the cache. Decompressed input is capped at 64 MiB. A crash can lose work since the previous checkpoint, without touching world data.

## Storage research and choice (2026-10-06)

| Reference | Public evidence consulted | Consequence for Orbis |
| --- | --- | --- |
| JourneyMap | Official [map tools documentation](https://teamjm.github.io/journeymap-docs/5.7.1/Tools%20and%20Customisation/journeymap-tools/) describes default 512-pixel source tiles and resized PNG export. The [tools repository](https://github.com/TeamJM/journeymap-tools) distinguishes world, dimension, map type and underground layer. | Image tiles suit derived display/export data; PNG alone cannot restore search progress or provenance. |
| Xaero's Minimap / World Map | The [author's format explanation](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2624027-xaeros-world-map-1-21-11-1-20-x-1-19-4-1-18-2-1-17?page=36) distinguishes color caches from block-state map files: historically ZIP regions of 512 × 512 blocks with 4 × 4-chunk sections, palettes and versions. [Update notes](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2624027-xaeros-world-map-1-21-11-1-20-x-1-19-4-1-18-2-1-17?page=25) discuss separate mapping/file threads and cache rebuilding. These are historical documented formats, not claims about every current Minimap binary. | Keep source observations separate from rendering, version explicitly and move compression/file I/O off the game thread. |
| VoxelMap Updated | Public [CachedRegion source](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CachedRegion.java) uses 16 × 16 chunks (256 × 256 blocks), per-chunk update flags and a save executor. ZIP entries include data, block/biome keys and version control. [CompressibleMapData](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CompressibleMapData.java) stores heights, block states, light, biome IDs and multiple surface layers. | Keep observations refreshable and compression independent from textures. Orbis requires fewer attributes and one surface layer. |

Orbis uses its **own Gzip-compressed binary checkpoint**, bounded counts and Gzip CRC. NBT would also fit but adds no advantage to this small disposable schema; JSON/PNG alone would inflate pixels or omit structured progress. A single bounded file per dimension keeps cursors, evidence and terrain from different checkpoint generations together. Regional storage would scale better for unlimited exploration but needs a manifest/transaction mechanism; the explicit 8,192-tile cap permits simple whole-checkpoint replacement.

No third-party implementation or parser was copied. JourneyMap Tools' MIT license was inspected. No license grant for code reuse is assumed for Xaero or the inspected VoxelMap tree. These are architectural references, not dependencies.

## Globe compromise

A torus cannot map bijectively and without distortion to a sphere. Rotating the old fixed-latitude globe leaves its polar compression baked into the assignment. The replacement changes the assignment itself: shortest wrapped X/Z displacements from the viewed center have equal scale and map radial distance to a spherical angle (azimuthal equidistant chart, then orthographic display).

Both world axes have equal scale at the center, even at former poles. Compression increases toward the limb; the visible hemisphere covers a disk of radius one quarter of the world span. The torus cut locus and collapsed far side stay hidden. This is a local viewing globe, not a globally faithful spherical texture. Panning shifts the origin continuously along both connected axes. Terrain, player, waypoints, facing and right-click inverse use the same mapping. North stays up; Flat provides the complete one-world overview.

## Validation

**Current revision, 2026-10-06:** Clean normal and debug builds passed **33 / 69 tests**, with no failures or skips, and both JARs passed standalone packaging checks. Checks cover cache round trips, completed-survey start policy, exact partial cursors and structure indices, world/dimension isolation, fingerprint rejection, corrupt/truncated/versioned files, overwrite/failure cleanup, block colors/LOD, single Flat bounds, projection/inverse alignment, equal local globe scale at former poles and both seams, and layout geometry across GUI scales. These are file/logic/raster tests, **not live Tiny-world re-entry or visual validation**. Existing Java/Gradle deprecation warnings remain. An initial normal-artifact check caught an old excluded engine resource in the pre-existing output directory; a clean rebuild removed it and passed verification.

**Historical, 2026-10-05 (alpha.2 atlas):** normal/debug packaging and regression tests passed. A Tiny singleplayer world was loaded, both map projections and dragging were exercised, and its four-height sample completed with **51/56 found, 5 unconfirmed**. Compact and maximized layouts were inspected. This was one seed, not a biome-coverage guarantee or performance benchmark. Portal correctness, narration and the full mod compatibility matrix remained unverified.

For the current build, use a new Tiny world and check:

1. Finish Tiny sampling, leave/re-enter and verify restored progress without a new full scan. Repeat with incomplete biome and structure checks, pause/resume and explicit resample.
2. HUD positioning and sizes in all corners, facing, Flat/Globe preference and M/remapped-key/Escape behavior.
3. Visible game, native controls, readable text/tooltips, continued simulation and checklist paging at different GUI scales.
4. Right-click add/remove in both projections, restart persistence, two saves, Nether and a supported mod biome. Change same-named datapack contents and test `/reload` invalidation.
5. Explore then zoom to individual surface blocks, edit/reload a chunk and restart to verify detail. Monitor tick/texture refresh while moving.
6. Both connected edges/corner and former globe poles: verify terrain, facing, selected places and waypoint interactions remain aligned.
7. Compare stronghold/portal-room predictions against real blocks and verify supplemental portal observations do not inflate type totals. Check performance-mod compatibility and narration.

Exact biome coverage, waypoint naming, portal pairing and exportable audit reports remain future work.
