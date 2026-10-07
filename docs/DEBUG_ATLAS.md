# Debug minimap and atlas

The optional **alpha.6-debug** build adds a corner minimap and an atlas window for local singleplayer worlds. Press **M** to open the larger map; the inset, translucent window leaves Minecraft visible and the world keeps running. Close with M, Escape or Close. The binding can be changed in Minecraft's Controls menu.

## Build and install

With Java 25:

```powershell
.\gradlew.bat build -PorbisDebug=true
```

Install `build/debug/libs/orbis-0.1.0-alpha.6-debug.jar`, replacing the normal Orbis JAR. Do not install both. The normal build produces `build/libs/orbis-0.1.0-alpha.6.jar` without these debug classes, resources or entrypoint. The minimap currently requires the debug build and an integrated singleplayer server; it does not query remote multiplayer servers.

Normal and debug outputs are separate; normal `clean` also removes the nested debug output. Development launch: `gradlew.bat runClient -PorbisDebug=true`. Do not switch build variants while a development client is running. `build` includes `verifyDistribution`, which checks the exact JAR and matching sources without Python. The independent check is `python tools/verify_standalone.py --debug <debug.jar>` (omit `--debug` for normal); it expects the matching sources JAR beside the binary, or accepts `--sources <sources.jar>`. CI builds and verifies both variants separately.

## Minimap, navigation and waypoints

- The HUD shows N/E/S/W (O for east in German), facing and waypoints, with separate labelled **X, Y, Z** lines below. Flat has a subdued pixel frame; Globe has transparent pixels outside the sphere, without a rectangular HUD panel. **Flat / Globe** is saved independently per dimension (including namespaced mod dimensions); **TR / TL / BR / BL** and **Size: S / M / L** are global preferences. All persist in `config/orbis-atlas.properties`. Sizes request 72, 96 or 128 GUI units and shrink to fit the scaled window.
- The non-pausing atlas is capped at 760 x 420 GUI units, leaving more game visible. Native Minecraft button sprites and selection outlines replace custom control backgrounds. Transparent map margins use the same translucent surface as the rest of the atlas; there is no black rectangle behind the globe. Narrow map panes put navigation controls in two rows. Long names remain available in tooltips and narration.
- In the atlas, left-drag or use WASD to navigate. Scroll or use +/- to zoom. In finite dimensions, Flat contains exactly one canonical world rectangle; zoom-out and panning are bounded. Wheel zoom anchors under the pointer where bounds allow. **You** / Home focuses the player; near a finite edge the map stays inside its bounds instead of centering the player exactly. **All** restores the complete finite-world overview; in unwrapped dimensions such as the End, it fits the mapped chunks, player and waypoints, including exploration beyond 4,096 blocks. Blank margins outside finite world bounds cannot receive waypoints.
- The globe is an orientation projection: Orbis connects both X and Z edges with torus topology. Unwrapped dimensions such as the End use the flat map and loaded observations; there is no finite whole-world survey there.
- Right-click the map to add a waypoint. Right-click near an existing visible marker to remove it. X/Z come from the selected map position; Y is the player's current height, not a terrain-height prediction. Up to 128 markers per save and dimension are supported. They appear in both the window and HUD.
- Waypoints persist in `config/orbis-waypoints.properties`, sharing the cache's save identity: real save-directory path, directory creation timestamp, seed and dimension. They do not modify the world save. Moving or renaming a save directory changes that association. Legacy path-only records are retained in the file but are not automatically assigned to a potentially different world. Load/save failures are reported; an unreadable file is not overwritten. Markers are simple numbered locations, without a name editor or teleport action.
- The overlay control cycles through **Off / Chunks / Places / Both**, independently selecting the diagnostic chunk grid and structure/place markers. Player, selected locations and waypoints remain visible in every mode. Select a found checklist row to center its location without moving the player.

Loaded terrain uses **16 × 16 surface map colors per chunk**, one per block. Heightmap reads and bounded transparent-block descent use only the already-loaded chunk. Immutable tiles store primitive pixel arrays and precomputed 4-block and chunk-average levels; Flat chooses 1/4/16-block levels from texture density, Globe 1/4-block levels. Finest colors persist in the cache, and coarse levels are rebuilt on restore. In ceiling dimensions such as the Nether, loaded terrain instead reads a horizontal **Y 32 slice**, clamped to the dimension bounds. Opaque block map colors are displayed directly; air and blocks without a map color use neutral dark gray. Biome predictions do not tint observed terrain. Stats identifies this layer. This is a surface color map, not a block-state/elevation archive or aerial screenshot. Unvisited areas retain the coarse generator biome preview without invented block details.

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

Searches restore automatically on entry into a local finite dimension. Completed and paused work remains stopped; interrupted running work resumes its exact next query. Closing the atlas leaves collection active for the HUD.

**Quick biome check:** a 16 × 16 grid at sea level (Y 32 in ceiling dimensions), minimum Y + 16, Y 32 and Y 128, clamped to vertical bounds and deduplicated. This is at most **1,024 biome queries**, or **768** for the standard Nether. It stops immediately when all expected types have evidence, including loaded observations and finds from the map preview. The limits are identical for Tiny, Normal, Large and Earth.

**Search+:** an explicit click adds at most **4,096 queries** to the existing search. Deterministic distributed coordinates (radical-inverse sequences) cover the canonical world and include additional vertical slices. Every result contributes to the shared missing-biome checklist, so there is no separate repeated scan per target. It stops early on full coverage. Total biome-search work is capped at **33,792 queries** until explicit Resample; a capped/unsuccessful search leaves missing types unconfirmed. Pause/resume and session restore preserve the exact sequence position and authorized limit. Resample restarts the small search, retains observations and map detail, and uses the same deterministic sequence for the same generator.

**Independent map preview:** automatically queries a coarse **16 × 16 grid at one height**, just **256 queries**. Coarse cells display constant biome colors, not invented surface detail. Combined automatic generator queries are therefore at most **1,280** in the Overworld or **1,024** in the Nether, often fewer through early checklist completion. **Map+** explicitly requests the separate 256 × 256 surface preview (65,536 additional queries); **Pause map / Map: go** pause and resume it. Its cursor and authorization persist separately. Refining the map is not a prerequisite for checklist completion. Loaded block-resolution terrain always takes precedence over either preview.

This is a bounded diagnostic, not an exhaustive biome census. Rare, small or vertically narrow biomes can be missed even on Tiny. Completing a search pass never marks an unconfirmed biome absent, and cannot certify that every candidate exists. Generator predictions can differ from old chunks or changed datapacks. Full biome coverage is not guaranteed by choosing a larger preset either.

### Structure and portal evidence

**Check** starts/resumes a bounded existence pass of at most **256 in-bounds layout evaluations**, regardless of world size. Random-spread placement regions use a deterministic spatial permutation: early probes cover both horizontal axes rather than walking one thin strip, and full traversal still visits every region once. Recently evaluated identical jobs are skipped by a bounded 4,096-job cache; jobs may be reevaluated after eviction or reload, while structure counts remain deduplicated separately. Already found types are skipped, except that a found stronghold still needs checking until its portal room has evidence. The pass stops when all expected types have been found, its evaluation budget is used, or no unfinished relevant search remains. Check after budget exhaustion authorizes up to 256 more evaluations; pausing/resuming within a pass does not extend its budget.

**Count all** explicitly switches to complete enumeration of the finite dimension. It resumes the same candidate cursors and deduplicated evidence, including tasks skipped once found during the quick check. It can be expensive in large worlds and never starts automatically for a new world. **Pause** stops it; **Count all** resumes it. A completed full count can be restarted with Count all. **Resample** resets prediction counts/cursors and returns to bounded existence mode, retaining observations. The saved mode, running/paused state, quick-pass limit, extra-start and ring-position indices all persist.

The structure-type total excludes the supplementary **Stronghold / portal room** row: that is a piece of a stronghold, not a separate structure type.

| State | Meaning |
| --- | --- |
| Observed | A valid structure start was read from a loaded chunk. |
| Predicted | This seed and current generator produce a valid layout at an in-bounds start. |
| Unconfirmed | No evidence yet, including incomplete, paused or failed searches. |
| Not found in check | All relevant candidates were checked without finding a valid layout. This is not proof of absence from saved terrain. |

The audit queries generation directly rather than issuing `/locate` commands. It uses Minecraft's weighted structure selection, biome/height checks and Orbis extra-start hooks, enumerating random-spread regions and stronghold positions. Other placement types use a lazy chunk-by-chunk fallback. Quick checks and full counting share the same traversal and cursor, preserving progress when switching modes. Each type retains one predicted example, and observations take precedence. Only the explicitly requested full count continues through all candidates after each type has been found; large worlds can take a long time. It retains one example location per type rather than adding every occurrence as a map marker.

Layouts use disposable ProtoChunks that are not installed in the chunk cache, terrain-populated or saved. **Generator count complete** means all placement candidates in the finite canonical dimension were checked. It does not certify intact blocks, loot, every structure variant, or portal travel. Check the Nether separately; the unwrapped End has no finite candidate-exhaustion audit.

**Counts:** rows show the number of distinct canonical start chunks per structure type. `12+` is a lower bound while work is incomplete; `12` means the relevant generator candidate search completed. Tooltips separate observed starts, predicted starts and their deduplicated union. Reloading a chunk or predicting an already observed start does not count it twice. Counts and exact start identities persist across sessions. Saved terrain can differ from generator predictions, so these are not certified totals of intact structures. Storage is capped at 131,072 combined observed/predicted identities per dimension (roughly 1 MiB raw keys plus maps/objects); hitting the cap stops prediction and keeps counts explicitly partial. Resample resets a failed or capped audit into bounded existence mode, retaining observed evidence.

A portal-room prediction requires a Stronghold PortalRoom piece; its location is the bounding-box center, not a frame block. The portal-room count is the number of distinct stronghold starts containing a PortalRoom piece, not the number of portal blocks or active portals. Loaded portal blocks and gateways appear as additional observed checklist rows/markers without entering the expected structure-type total. Reloaded/periodically inspected chunks refresh these block observations. Biome and structure observations remain historical evidence; they cannot certify that later edits left all blocks intact. Exhausted but unfound structures remain unchecked, with the completed-search detail in the tooltip.

## Performance and lifetime

Inspection and generation queries run on the integrated server thread; the renderer receives immutable snapshots twice per second. Each active tick schedules at most two loaded-chunk inspections, as many biome queries as fit, and at most one structure-layout evaluation against a shared **3 ms scheduling budget**. Minecraft's native caching biome resolver reuses density evaluations and caches biome styles. Biome search and map preview each get at most a 1 ms slice within the shared deadline; the total query budgets above apply independently of tick timing. When both checks run, structures get first choice of the remaining budget once in four ticks and biomes on the other three, preventing mutual starvation. Placement-task initialization is spread across ticks. A single layout, ring initialization or hook call cannot be preempted and may exceed that budget; this is not a hard tick-latency guarantee. Changing the active world cancels further work for its request.

The HUD keeps collection active after the atlas closes. Loaded chunks are queued again every 200 ticks (normally ten seconds), and immediately on reload, so edits replace cached surface colors. History retains at most **8,192 detailed chunks per dimension** (8 MiB raw finest colors, plus Java objects, indices, coarse levels and immutable snapshots), and 4,096 observed markers. Old changed/inserted tiles are evicted at the cap; Stats reports truncation. Evicted areas return to the biome preview until revisited. The overview remains at most 65,536 cells regardless of world size. No chunk objects are retained and no extra live chunks are requested.

Rendering uses cached dynamic textures, capped at 1,536 pixels on the longest flat-map side and 768 for the globe. Immutable terrain tiles are shared across snapshots and both map views. Terrain, biome-preview, loaded-overlay and place-overlay revisions are tracked separately: hidden overlays do not invalidate the texture. Flat views traverse only visible biome-preview cells; globe views cache inverse-projection offsets. View updates are coalesced to at most 30 texture uploads per second. The minimap quantizes its moving center to reduce uploads. Relevant visible changes still rasterize and upload a complete texture synchronously; this is not a GPU or asynchronous renderer. These changes have no measured FPS guarantee.

## Disposable persistent cache

Data lives outside saves at `<game directory>/orbis-atlas-cache/<world hash>/<dimension hash>.atlas.gz`. Remove that directory while the world is closed to discard map caches. It contains neither world saves nor user waypoints.

World identity hashes the real save-directory path, directory creation timestamp and seed; dimension uses its full namespaced ID. Distinct paths, recreated directories, seeds and dimensions stay separate. Moving/copying a world intentionally starts another cache. Restoring a backup over exactly the same directory with preserved identity is not universally detectable; remove its cache explicitly if necessary.

Schema **4** stores a magic number, version, generation fingerprint, independent biome/map cursors, limits and running states, prediction colors/counts/locations, separately flagged observations, structure-task progress/results with deduplicated observed/predicted start identities and explicit existence/count mode, markers and explored tiles. Earlier cache schemas are rejected because search traversal, evidence and terrain-color semantics have changed. Waypoints and view preferences are separate and retained. Unknown versions are ignored rather than guessed. Bump the version for incompatible sampling policies, tile/evidence semantics or checkpoint ordering.

The SHA-256 fingerprint includes the encoded dimension generator, seed, bounds, structure-generation flag, mod IDs/versions and names/content bytes of effective server data resources. Editing a same-named datapack therefore invalidates the cache, including templates and tags. Unrelated server-data changes also conservatively invalidate it. Successful datapack reload schedules a new fingerprint/restore. Unencodable generators or unreadable resources leave collection usable in memory without trusting an unidentified cache. Undeclared behavior changes in mods with unchanged versions/configuration may require manual deletion/resampling.

Resource handles are captured on the server thread, then their contents and cache files are read on one background worker. The resource hash is shared across dimensions for each server resource generation and invalidated on reload; stale completions are rejected. The server does not block its tick waiting for restore, and starts no new survey until it resolves. Existing loaded chunks are then queued for fresh inspection. Only immutable captured map data crosses into the writer; it never reads chunks or live world state.

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

## Alpha.5 quick-search validation (2026-10-07)

The default workflow is tested with the actual wrapped generator and vanilla registries, seed 8172231, without a ServerLevel or any loaded/generated terrain. Counts below include both the early-stopping existence check and the independent 256-query coarse map. Search+ is an explicitly requested additional pass. These results describe one seed, not guaranteed coverage or measured in-game latency.

| Preset / dimension | Automatic queries | Types found initially | Additional Search+ queries | Types found afterward |
| --- | --- | --- | --- | --- |
| Tiny / Overworld | 1,280 | 41 / 56 | 4,096 | 54 / 56 |
| Tiny / Nether | 368 | 5 / 5 | Not needed | 5 / 5 |
| Large / Overworld | 1,280 | 48 / 56 | 2,734 (early stop) | 56 / 56 |
| Large / Nether | 293 | 5 / 5 | Not needed | 5 / 5 |

A separate deterministic test verifies the same 1,024-query worst-case biome-check budget at Tiny, Normal, Large and Earth widths. Tests also cover deduplicated Nether heights, early completion, independently paused map work, exact continuation after a partial deepening pass, the global deepening cap, completed/paused cache restore without escalation, existence/count mode persistence, and retaining the portal-room search after finding a stronghold. No `/locate` commands are dispatched.

## Historical Alpha.4 sampler benchmark (2026-10-07)

This measures the previous full-grid algorithm, not the Alpha.5 quick workflow. Local optional benchmark with the real Orbis wrapped generator, vanilla registries and seeded transformed noise state; seed 8172231, Tiny, 262,144 queries per dimension, one whole-pass warmup per implementation and three measured repetitions. The old and new paths use identical heights and assert equal hit counts, first locations and map colors. A separate parity test checks all 786,432 queries across Overworld, Nether and End. The End case tests resolver equivalence only; the UI still has no finite End survey.

| Dimension | Old measured processing | New measured processing | Old 3 ms tick slices | New slices | Completion extrapolated at 20 TPS |
| --- | --- | --- | --- | --- | --- |
| Overworld | 1,606–1,693 ms | 564–601 ms | 1,025–1,030 | 186–201 | 51.25–51.50 s → 9.30–10.05 s |
| Nether | 84–112 ms | 73–90 ms | 1,025 | 25–30 | 51.25 s → 1.25–1.50 s |

Measured processing is elapsed time inside an in-process workload, not thread CPU accounting. The 20 TPS column assumes the entire 3 ms is available for biomes: it is **not measured gameplay time**. Loaded-chunk inspections, simultaneous structure counting, rendering, cold initialization, mods and server load can change it. This benchmark neither generates nor loads terrain and makes no FPS claim. Much of the gain, especially in the Nether, comes from removing idle tick waiting imposed by the old query cap.

Reproduce separately from the default suite:

```powershell
$env:ORBIS_ATLAS_BENCHMARK='1'
.\gradlew.bat -PorbisDebug=true test --tests dev.orbis.debug.AtlasBiomeSamplerBenchmark --rerun-tasks
Remove-Item Env:ORBIS_ATLAS_BENCHMARK
```

The benchmark is intentionally skipped in normal regression runs; it was explicitly run and passed for the results above.

## Validation

**Alpha.6, 2026-10-07:** All production sources, including the debug atlas, compiled directly with Java 25 `javac`. The actual Fabric/JUnit engine completed **117 successful tests, zero failures and one skip**. The historical full-grid benchmark was skipped; the raster benchmark was enabled. A separate normal-only production compilation and Fabric/JUnit run passed **37 tests, zero failures and zero skips**. The revision covers corrected Search+ heights, spatial structure traversal, independent rendering layers and dimension policy. Gradle/Loom was blocked by Java real-path resolution needing parent-directory access in the Windows sandbox; direct compilation and the test launcher succeeded. Both normal and debug artifacts passed the unchanged `verifyDistribution` and `verifyPresets` tasks through a temporary Java-only Gradle adapter using the directly compiled classes, not a normal Loom build. The interactive gameplay checklist below remains outstanding.

The Alpha.6 raster benchmark used a **768 x 512** raster, **8,192 terrain tiles**, **65,536 preview cells** and **40 measured frames**:

| Workload | Median | 95th percentile |
| --- | --- | --- |
| Flat dragging | 6.403 ms | 7.108 ms |
| Globe rotation | 8.860 ms | 11.339 ms |

These are in-process elapsed raster timings, not game FPS or server TPS. They exclude live gameplay, graphics-driver texture upload and the combined performance-mod environment, and provide no frame-rate guarantee.

**Alpha.5, 2026-10-07:** Normal and debug builds passed **33 / 92 regression tests**, with no failures; the historical opt-in full-grid benchmark is intentionally skipped in the default suite (one skip). Both Alpha.5 JARs passed standalone packaging verification. The new native-generator checks above ran as part of the debug suite. In-game controls, frame/tick behavior, live cache re-entry and end-to-end structure-count completion still require gameplay validation. Existing Java/Gradle deprecation warnings remain.

**Alpha.4, 2026-10-07:** Normal and debug builds passed **33 / 80 regression tests**, with no failures. The opt-in benchmark is one additional test: skipped by default, explicitly run and passed separately. Both Alpha.4 JARs passed standalone packaging verification. New checks cover dimension-specific preference persistence/migration, Y 32 selection and distinct Nether tints, native cached/uncached biome equivalence, deadline/cancellation/resumption, deduplicated structure counts, count-cache round trips and schema-1 rejection. All alpha.3 regression tests remain present. The sampler benchmark above is an isolated workload, not a live server measurement. The updated GUI, Nether layer, full structure-count completion and dimension switching still require in-game validation. Existing Java/Gradle deprecation warnings remain.

**Previous alpha.3 revision, 2026-10-06:** Clean normal and debug builds passed **33 / 69 tests**, with no failures or skips, and both JARs passed standalone packaging checks. Checks cover cache round trips, completed-survey start policy, exact partial cursors and structure indices, world/dimension isolation, fingerprint rejection, corrupt/truncated/versioned files, overwrite/failure cleanup, block colors/LOD, single Flat bounds, projection/inverse alignment, equal local globe scale at former poles and both seams, and layout geometry across GUI scales. These are file/logic/raster tests, **not live Tiny-world re-entry or visual validation**. Existing Java/Gradle deprecation warnings remain. An initial normal-artifact check caught an old excluded engine resource in the pre-existing output directory; a clean rebuild removed it and passed verification.

**Historical, 2026-10-05 (alpha.2 atlas):** normal/debug packaging and regression tests passed. A Tiny singleplayer world was loaded, both map projections and dragging were exercised, and its four-height sample completed with **51/56 found, 5 unconfirmed**. Compact and maximized layouts were inspected. This was one seed, not a biome-coverage guarantee or performance benchmark. Portal correctness, narration and the full mod compatibility matrix remained unverified.

For the current build, use a new Tiny world and check:

1. Finish a quick Tiny check and its coarse preview, leave/re-enter and verify neither restarts. Repeat with incomplete Search+, Map+ and Count all; verify each mode/cursor/limit and pause state survives. Resample must return to quick mode without erasing terrain.
2. HUD positioning and sizes in all corners, facing, different Flat/Globe choices per dimension across restarts, and M/remapped-key/Escape behavior. The unwrapped End remains Flat.
3. Visible game, native controls (including the added Map+/Count all and Resample row), readable text/tooltips, continued simulation and checklist paging at different GUI scales.
4. Right-click add/remove in both projections, restart persistence, two saves, Nether and a supported mod biome. Change same-named datapack contents and test `/reload` invalidation.
5. Explore then zoom to individual surface blocks, edit/reload a chunk and restart to verify detail. In the Nether, verify Y 32 colors and biome readability beneath the roof. Monitor tick/texture refresh while moving and compare resampling times with the benchmark.
6. Both connected edges/corner and former globe poles: verify terrain, facing, selected places and waypoint interactions remain aligned.
7. Verify Check stops at its budget or existence completion, keeps N+ lower bounds, and preserves the missing-portal-room search after finding a stronghold. Then explicitly start Count all: numbers should continue increasing, never double-count reloads, and resume from cached counts. Compare stronghold/portal-room predictions against real blocks and verify supplemental portal observations do not inflate type totals. Check performance-mod compatibility and narration.

Exact biome coverage, waypoint naming, portal pairing and exportable audit reports remain future work.
