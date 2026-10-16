# Debug minimap and atlas

The optional **debug** build (`-PorbisDebug=true`) is a developer tool, not the player map. It adds a survey atlas for local singleplayer worlds on top of everything in the normal build. Press **M** to open the larger map; the inset, translucent window leaves Minecraft visible and the world keeps running. Close with M, Escape or Close. The binding can be changed in Minecraft's Controls menu.

## Build and install

With Java 25:

```powershell
.\gradlew.bat build -PorbisDebug=true
```

Install `build/debug/libs/orbis-0.1.0-alpha.17-debug.jar`, replacing the normal Orbis JAR. Do not install both. The normal build produces `build/libs/orbis-0.1.0-alpha.17.jar` without these debug classes, resources or entrypoint. This developer interface requires an integrated singleplayer server. The normal build has its own simple player atlas/minimap and admin server checks; see [Server and player guide](SERVER.md). The normal singleplayer atlas also shares the persistent terrain importer and prioritized capture path. Shared rendering/search helpers live in `dev.orbis.atlas`; `DeveloperClient`, `DeveloperScreen` and `DeveloperSurvey` remain debug-only.

Normal and debug outputs are separate; normal `clean` also removes the nested debug output. Development launch: `gradlew.bat runClient -PorbisDebug=true`. Do not switch build variants while a development client is running. `build` includes `verifyDistribution`, which checks the exact JAR and matching sources without Python. The independent check is `python tools/verify_standalone.py --debug <debug.jar>` (omit `--debug` for normal); it expects the matching sources JAR beside the binary, or accepts `--sources <sources.jar>`. CI builds and verifies both variants separately.

## Minimap and navigation

- Press **M**: both Flat and Globe open centered on the live player. **Follow: On/Off** controls continued centering; drag/WASD or selecting a biome/structure disables it. Home enables it again. Zoom preserves centering while following. At finite Flat edges the player remains centered, with blank space outside the single canonical world rectangle.
- The map is centered in the complete GUI, with symmetric sidebars. Navigation, overlays and minimap settings are on the left; biome and structure tabs, search, filters and lists are on the right. The window fits up to 1180 × 660 GUI units and adapts down to Minecraft's 320 × 240 GUI floor. Statistics are available over the left footer.
- The player uses a small two-color pixel triangle with a dark outline in the atlas and HUD. 16 precomputed heading frames avoid rerasterizing the marker during drawing. Minecraft yaw is projected through the same map geometry, including when the globe is moved away from the player. Globe markers use the displayed texture’s projection, including while a new view is waiting for its upload; follow mode holds the marker at the frame’s player center. Positions round to the nearest pixel to avoid floating-point jitter. The HUD also shows compass labels and X/Y/Z coordinates.
- **Flat / Globe** persists per dimension; minimap corner and size persist globally in `config/orbis-atlas.properties`. Unwrapped dimensions use Flat. Globe connects both world axes with torus topology.
- Scroll or +/- zooms. Free navigation keeps the finite world bounded; wheel zoom anchors under the pointer. **All** disables following and fits the world (or known chunks/player in unwrapped dimensions).
- Waypoints have been removed from the atlas and minimap, including right-click creation, persistence code, status and help. Existing `config/orbis-waypoints.properties` files are left untouched.
- **Off / Chunks / Places / Both** selects diagnostic overlays. Player and selected biome/structure locations remain visible independently.

Loaded terrain uses **16 × 16 surface map colors per chunk**, one per block. Heightmap reads and bounded transparent-block descent use only the already-loaded chunk. Immutable tiles retain the original block colors in primitive pixel arrays. Flat and Globe always sample the finest available terrain at each texture pixel, including cached chunks that are no longer loaded; zooming out never switches to averaged 4-block or chunk colors. Screen resolution still limits how many individual blocks can be visible at once. Finest colors persist in the cache. In ceiling dimensions such as the Nether, loaded terrain instead reads a horizontal **Y 32 slice**, clamped to the dimension bounds. Opaque block map colors are displayed directly; air and blocks without a map color use neutral dark gray. Biome predictions do not tint observed terrain. Stats identifies this layer. This is a surface color map, not a block-state/elevation archive or aerial screenshot. Areas without stored terrain use a coarse fictional map with two neutral gray inks: darker water and lighter land. It is identical for every seed and never uses biome samples. All saved chunks, including pregenerated chunks, remain eligible for full-detail display.

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

**Independent surface sampling:** automatically queries a **16 x 16 grid at one height**, just **256 queries**, for the biome checklist. Combined automatic generator queries remain at most **1,280** in the Overworld or **1,024** in the Nether. **Sample+** requests the separate 256 x 256 surface pass (65,536 additional queries); **Pause / Sample** pause and resume it. Its cursor and authorization persist separately. These samples never color the map or invalidate its textures. Saved and loaded block-resolution terrain overlays the fictional background.

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

Inspection and generation queries run on the integrated server thread. Immutable snapshots publish twice per second, or up to ten times per second while terrain changes. Surface capture has its own deduplicated queue ordered by distance to the player: at most 32 already-loaded chunks within a 2 ms slice of the shared **3 ms scheduling budget**. It no longer waits behind the two-per-tick biome/structure inspections. The remaining shared budget admits up to two inspections, biome queries and one structure-layout evaluation. Minecraft's native caching biome resolver reuses density evaluations and caches biome styles. Biome search and map preview each get at most a 1 ms slice within the shared deadline; the total query budgets above apply independently of tick timing. When both checks run, structures get first choice of the remaining budget once in four ticks and biomes on the other three, preventing mutual starvation. Placement-task initialization is spread across ticks. A single capture, layout, ring initialization or hook call cannot be preempted and may exceed that budget; this is not a hard tick-latency guarantee. Changing the active world cancels further work for its request.

The HUD keeps collection active after the atlas closes. Loaded chunks are queued again every 200 ticks (normally ten seconds), and immediately on reload, so edits replace cached surface colors. A chunk that unloads before its inspection turn is sampled during the unload event; this captures short-lived Chunky generation loads. The in-memory history remains capped at **8,192 detailed chunks per dimension**, plus 4,096 observed markers. Full-resolution terrain also persists in a separate sparse region archive, so exceeding the memory cap no longer discards map detail. The archive grows with imported terrain and available disk space; it is not limited to 8,192 tiles. No extra live chunks are requested.

### Chunky and Distant Horizons terrain

Existing Minecraft region headers are enumerated in the background. Only stored, fully generated chunks are read through Minecraft's asynchronous chunk storage; detached block palettes are decoded without loading chunks, creating entities/POIs or generating terrain. This includes chunks previously generated by Chunky. Saved chunks within twelve chunks of the player get priority, nearest first, ahead of the full-world scan. This priority list refreshes after moving four chunks or after 30 seconds; already archived tiles bypass the priority import while the background scan still refreshes saved data. Batches process up to 256 entries within a 35 ms scheduling slice. Full scans repeat after completing a pass, at least 30 seconds after its start, skipping unchanged region files within the session. Large pregenerated worlds fill progressively rather than instantly.

When Distant Horizons 3.3 is installed, a separate optional adapter enumerates its stored leaf sections and listens for data-source updates, including DH's own background generation. Each batch reads one leaf section (up to sixteen chunks). Only complete block-resolution columns are imported; coarser parent LODs and missing columns cannot masquerade as detailed terrain. The Nether uses the same Y 32 slice, including the dimension's minimum-height offset. The adapter reads DH's data, never requests DH generation, and detaches listeners when the world closes or datapacks reload. It uses the existing pinned 3.3 provider/leaf interfaces because the public chunk-modified event alone does not cover every generation path. Newer incompatible DH interfaces are reported as an import failure rather than crashing the atlas. Remote multiplayer import remains unsupported.

The detail archive is stored beside the dimension checkpoint, under its world/dimension/generator-resource fingerprint. Each sparse region contains independent, CRC-checked 16 x 16 block-color tiles. Live observations outrank saved-chunk imports, which outrank DH imports. It stores map colors only, not playable Minecraft chunks. Biome/structure checklists do not treat imported terrain as player observations.

Flat and Globe build a screen-sized archive layer on background workers, retaining only a bounded tile/file cache. Panning or rotating cancels stale projections; recently loaded terrain overlays the archive immediately. Detail can appear progressively after moving the view or importing new regions. The displayed colors always come from the finest available samples, while screen pixel count still limits what is visible. Save changes, datapack reloads and world changes isolate the workers and archive namespaces. Old archive directories are disposable and are not automatically deleted.

The **flat HUD minimap** uses a dedicated padded texture, normally 384 x 384 texels covering a 256-block visible area. Every texel retains the original block color. A worker loads archive regions and composes the complete image, including live tiles and the fictional map background. Camera movement only changes GPU texture coordinates; it performs no image rasterization or file I/O on the client thread. A 64-block-aligned window and a 16-block prefetch margin allow the current texture to bridge nearby window changes. Teleports and world/session changes cannot display a misplaced old image. Region-local archive revisions avoid rebuilding for imports far away, and identical prepared pixels reuse the previous GPU image. Only changed images need a client-thread upload. This is a native block-resolution texture, independent of GUI scale, without lowering the stored terrain resolution.

The full atlas and globe retain screen-sized dynamic textures, capped at 1,536 pixels on the longest flat-map side and 768 for the globe. Immutable terrain tiles are shared across snapshots and both map views. Terrain, biome-preview, loaded-overlay and place-overlay revisions are tracked separately: hidden overlays do not invalidate the texture. The decorative map uses a 64 x 64 pattern with four flat colors, generated once and tiled in world coordinates; precomputed majority-color mip levels preserve its small palette. It has no fine grain or hill shading; wrapped worlds fit whole repeats to avoid seams. Flat views sample terrain only at covered texture pixels; globe views cache inverse-projection offsets. Both views use original terrain colors at every zoom level. Their view updates are coalesced to at most 30 texture uploads per second, with the globe HUD centered on the exact player position. Their archive layer is prepared asynchronously; live terrain, overlays and final uploads still run on the render thread. Full-atlas/globe GPU projection remains future work. These changes have no measured FPS guarantee.

The flat HUD architecture was checked against FTB Chunks' public [MapRegion source](https://github.com/FTBTeam/FTB-Chunks/blob/main/common/src/main/java/dev/ftb/mods/ftbchunks/client/map/MapRegion.java), which separates asynchronous region loading, reusable region textures and dirty updates. JourneyMap's [advanced options](https://teamjm.github.io/journeymap-docs/6.0.x/client/settings/advanced/) also distinguish chunk caching, map-task timing and LOD tiles. No third-party code was copied.

Reproduce the CPU-only camera benchmark with `ORBIS_MINIMAP_BENCHMARK=1` and `gradlew.bat test -PorbisDebug=true --tests dev.orbis.atlas.AtlasMinimapBenchmark`. It compares the previous quantized screen-reprojection path against the padded-image path over the same 1,024-frame trace, using original archived block colors. Client update, worker preparation and prepared image counts are reported separately; GPU upload time and whole-game FPS are excluded.

## Disposable persistent cache

Data lives outside saves at `<game directory>/orbis-atlas-cache/<world hash>/<dimension hash>.atlas.gz`. Remove that directory while the world is closed to discard map caches. It contains neither world saves nor user waypoints.

World identity hashes the real save-directory path, directory creation timestamp and seed; dimension uses its full namespaced ID. Distinct paths, recreated directories, seeds and dimensions stay separate. Moving/copying a world intentionally starts another cache. Restoring a backup over exactly the same directory with preserved identity is not universally detectable; remove its cache explicitly if necessary.

Schema **4** stores a magic number, version, generation fingerprint, independent biome/map cursors, limits and running states, prediction colors/counts/locations, separately flagged observations, structure-task progress/results with deduplicated observed/predicted start identities and explicit existence/count mode, markers and explored tiles. Earlier cache schemas are rejected because search traversal, evidence and terrain-color semantics have changed. View preferences are separate and retained. Unknown versions are ignored rather than guessed. Bump the version for incompatible sampling policies, tile/evidence semantics or checkpoint ordering.

The SHA-256 fingerprint includes the encoded dimension generator, seed, bounds, structure-generation flag, mod IDs/versions and names/content bytes of effective server data resources. Editing a same-named datapack therefore invalidates the cache, including templates and tags. Unrelated server-data changes also conservatively invalidate it. Successful datapack reload schedules a new fingerprint/restore. Unencodable generators or unreadable resources leave collection usable in memory without trusting an unidentified cache. Undeclared behavior changes in mods with unchanged versions/configuration may require manual deletion/resampling.

Resource handles are captured on the server thread, then their contents and cache files are read on one background worker. The resource hash is shared across dimensions for each server resource generation and invalidated on reload; stale completions are rejected. The server does not block its tick waiting for restore, and starts no new survey until it resolves. Existing loaded chunks are then queued for fresh inspection. Only immutable captured map data crosses into the writer; it never reads chunks or live world state.

Changed state checkpoints every 600 ticks (normally 30 seconds), with at most one ordinary write in flight per dimension. Shutdown queues a final snapshot; only shutdown waits up to ten seconds. Compression writes a sibling temporary file, closes it and atomically replaces the previous file where supported (replace fallback otherwise). Failures appear in Stats/logs and are retried. Invalid headers/versions/fingerprints/counts, truncation or bad CRC discard the cache. Decompressed input is capped at 64 MiB. A crash can lose work since the previous checkpoint, without touching world data.

## Storage research and choice (2026-10-06)

| Reference | Public evidence consulted | Consequence for Orbis |
| --- | --- | --- |
| JourneyMap | Official [map tools documentation](https://teamjm.github.io/journeymap-docs/5.7.1/Tools%20and%20Customisation/journeymap-tools/) describes default 512-pixel source tiles and resized PNG export. The [tools repository](https://github.com/TeamJM/journeymap-tools) distinguishes world, dimension, map type and underground layer. | Image tiles suit derived display/export data; PNG alone cannot restore search progress or provenance. |
| Xaero's Minimap / World Map | The [author's format explanation](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2624027-xaeros-world-map-1-21-11-1-20-x-1-19-4-1-18-2-1-17?page=36) distinguishes color caches from block-state map files: historically ZIP regions of 512 × 512 blocks with 4 × 4-chunk sections, palettes and versions. [Update notes](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2624027-xaeros-world-map-1-21-11-1-20-x-1-19-4-1-18-2-1-17?page=25) discuss separate mapping/file threads and cache rebuilding. These are historical documented formats, not claims about every current Minimap binary. | Keep source observations separate from rendering, version explicitly and move compression/file I/O off the game thread. |
| VoxelMap Updated | Public [CachedRegion source](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CachedRegion.java) uses 16 × 16 chunks (256 × 256 blocks), per-chunk update flags and a save executor. ZIP entries include data, block/biome keys and version control. [CompressibleMapData](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CompressibleMapData.java) stores heights, block states, light, biome IDs and multiple surface layers. | Keep observations refreshable and compression independent from textures. Orbis requires fewer attributes and one surface layer. |

Orbis uses its **own Gzip-compressed binary checkpoint**, bounded counts and Gzip CRC. NBT would also fit but adds no advantage to this small disposable schema; JSON/PNG alone would inflate pixels or omit structured progress. A bounded checkpoint per dimension keeps cursors, evidence and recent terrain together. The separate region archive contains independently recoverable, CRC-checked terrain tiles, preserving details beyond that checkpoint's 8,192-tile memory limit. Archive slots publish their validity byte after their payload; incomplete or corrupt slots are ignored and can be reimported.

No third-party implementation or parser was copied. JourneyMap Tools' MIT license was inspected. No license grant for code reuse is assumed for Xaero or the inspected VoxelMap tree. These are architectural references, not dependencies.

## Globe compromise

A torus cannot map bijectively and without distortion to a sphere. Rotating the old fixed-latitude globe leaves its polar compression baked into the assignment. The replacement changes the assignment itself: shortest wrapped X/Z displacements from the viewed center have equal scale and map radial distance to a spherical angle (azimuthal equidistant chart, then orthographic display).

Both world axes have equal scale at the center, even at former poles. Compression increases toward the limb; the visible hemisphere covers a disk of radius one quarter of the world span. The torus cut locus and collapsed far side stay hidden. This is a local viewing globe, not a globally faithful spherical texture. Panning shifts the origin continuously along both connected axes. Terrain, player, selected locations and facing use the same mapping. North stays up; Flat provides the complete one-world overview.

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
.\gradlew.bat -PorbisDebug=true test --tests dev.orbis.atlas.AtlasBiomeSamplerBenchmark --rerun-tasks
Remove-Item Env:ORBIS_ATLAS_BENCHMARK
```

The benchmark is intentionally skipped in normal regression runs; it was explicitly run and passed for the results above.

## Validation

**Generated-terrain integration, 2026-10-07:** Normal Gradle/Loom build and its 37 tests passed. The debug suite covers saved region headers/palettes, surface and Nether colors, archive restart/isolation/CRC handling, retention beyond 8,192 tiles, stale-projection cancellation and Flat/Globe agreement. A separate `testDistantTerrain` task exercises the actual DH leaf representation, missing/downsampled columns and Nether height offsets with pinned DH 3.3.0 and locally installed DH 3.3.4. The regular debug suite runs without DH on its runtime classpath. These are automated tests; actual in-game Chunky/DH generation, long-running disk usage and visual performance remain to be validated. Chunky is currently disabled in the local profile and was not enabled by this change.

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

Historical developer checklist (written for alpha.5/6; waypoints were removed in alpha.8). For a debug build, use a new Tiny world and check:

1. Finish a quick Tiny check and its coarse preview, leave/re-enter and verify neither restarts. Repeat with incomplete Search+, Map+ and Count all; verify each mode/cursor/limit and pause state survives. Resample must return to quick mode without erasing terrain.
2. HUD positioning and sizes in all corners, facing, different Flat/Globe choices per dimension across restarts, and M/remapped-key/Escape behavior. The unwrapped End remains Flat.
3. Visible game, native controls (including the added Map+/Count all and Resample row), readable text/tooltips, continued simulation and checklist paging at different GUI scales.
4. Right-click add/remove in both projections, restart persistence, two saves, Nether and a supported mod biome. Change same-named datapack contents and test `/reload` invalidation.
5. Explore then zoom to individual surface blocks, edit/reload a chunk and restart to verify detail. In the Nether, verify Y 32 colors and biome readability beneath the roof. Monitor tick/texture refresh while moving and compare resampling times with the benchmark.
6. Both connected edges/corner and former globe poles: verify terrain, facing, selected places and the direction arrow remain aligned.
7. Verify Check stops at its budget or existence completion, keeps N+ lower bounds, and preserves the missing-portal-room search after finding a stronghold. Then explicitly start Count all: numbers should continue increasing, never double-count reloads, and resume from cached counts. Compare stronghold/portal-room predictions against real blocks and verify supplemental portal observations do not inflate type totals. Check performance-mod compatibility and narration.

Exact biome coverage, portal pairing and exportable audit reports remain future work.

## Region rendering and import performance (2026-10-07)

Further primary-source review: VoxelMap's [CachedRegion](https://github.com/Updated-VoxelMap/VoxelMap/blob/master/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CachedRegion.java) separates saved region data, prepared image textures and dirty uploads; its optional PNG export is separate from the data archive. JourneyMap's [official 6.0 changelog](https://github.com/TeamJM/journeymap-docs/blob/6.0.x/docs/en/changelogs.md) identifies region image caching, unchanged-region writes and excess zoom-cache I/O as performance concerns. No implementation code was copied.

Orbis retains its own original-color `.tiles` archive. Each 32 × 32-chunk region is read sequentially once and decoded into immutable cells. A session-wide LRU retains up to 128 regions (128 MiB of original pixel payload plus object overhead); minimap, Flat and Globe share it across screen close/reopen. Projections hold bounded region references. Writes replace only the affected cached chunk and advance the revision. Unknown regions use an in-memory directory index rather than a filesystem probe per pixel. Disk loading stays on workers; camera motion can immediately sample resident original pixels without file access. There is no averaged chunk/4-block terrain LOD.

The previous fixed 500 ms projection delay is replaced with a 33 ms refresh interval. Import batches may run every server tick and process up to 256 saved chunks / 32 DH leaves, with separate 35 ms worker deadlines (a single I/O/decode may exceed the deadline). Archive imports no longer queue redundant copies for the server to drain. Saved regions with unchanged modification time and size skip repeat decoding during the session; failed regions retry. Empty vertical sections are skipped. DH updates force refresh, while unchanged database timestamps skip repeat leaf decompression. The first import still depends on source size and disk speed; already archived terrain is reusable immediately after background loading.

Reproducible benchmark: set `ORBIS_ARCHIVE_BENCHMARK=1`, run debug `test --tests '*AtlasArchiveBenchmark'`. It writes 16,384 patterned chunks, reopens the archive, then projects twelve changing 512 × 512 views per mode. Windows/Java 25 results from this change:

| Archived projection | Before median | After median | First view before / after |
| --- | ---: | ---: | ---: |
| Flat | 104.50 ms | 2.41 ms | 158.85 / 51.74 ms |
| Globe | 39.75 ms | 4.36 ms | 59.33 / 11.34 ms |

Globe follows Flat in the benchmark and benefits from already loaded regions. These timings cover original-color archive projection, excluding game FPS, GPU upload, source-world/DH decoding and biome preview rasterization. No in-game validation is implied.

## Flat minimap validation (2026-10-08)

Base commit: `5a97628` (alpha.6). The padded minimap cache retains all 256 original colors per chunk. The new tests cover nearby queue ordering/deduplication, negative region boundaries, original pixel preservation, no client-thread archive loading, scrolling reuse, prefetch handoff, teleports, local versus distant invalidation, identical-image reuse, cancellation and world isolation.

`AtlasMinimapBenchmark`, Windows / Java 25, 2,304 patterned archived chunks, 1,024 camera frames, three rounds (last two warm rounds shown):

| CPU-only camera trace | Warm round 1 | Warm round 2 |
| --- | ---: | ---: |
| Previous screen projection + raster | 253.88 ms | 281.42 ms |
| New client cache updates | 0.94 ms | 1.09 ms |
| New worker image preparation | 11.45 ms | 12.53 ms |
| Previous / new prepared images | 129 / 9 | 129 / 9 |

Times are totals over the trace, not per-frame times or game FPS. This is about 20 times less combined map CPU work in these warm runs and 14 times fewer prepared images; GPU transfers and source-chunk import are not timed. The new CPU rendering path applies to the flat HUD; the full atlas/globe retain their previous projection paths and benefit from the shared capture/import priority changes.

Automated validation: debug build and distribution verification passed, with 134 executed tests (including this benchmark; three other opt-in benchmarks skipped), plus three separate Distant Horizons adapter tests. The normal build passed all 37 tests and its distribution verification. `tools/verify_standalone.py` verified both variants independently, including their matching source provenance.

Actual gameplay validation ran in **local Minecraft 26.3 / Fabric 0.19.5**, using `build/minimap-playtest` and a copied development world. No cloud Minecraft environment was available. Verified the detailed Overworld HUD, a long teleport, opening the flat atlas/globe, returning to the HUD, Nether slice display, returning to known Overworld terrain, and a complete client restart with the persistent archive retained. The return to known terrain showed the detailed map while Minecraft was still rebuilding its 3D scene. Screenshots, benchmark XML, client logs and the installation receipt are retained under `build/minimap-playtest` and `build/minimap-evidence`.

No atlas/minimap exceptions were observed. The development client logged authentication/Realms and Windows performance-counter diagnostics; a teleport that required chunk loading/generation logged a roughly two-second server catch-up warning. This is a gameplay smoke test, not a measured startup latency or whole-game FPS claim. Combined third-party performance mods and the in-game DH importer were not exercised; the DH adapter was tested automatically. The verified debug JAR was installed into the Fabric 26.3 Modrinth profile with a matching SHA-256; the previous Orbis JAR was backed up outside `mods` and the six unrelated mods were hash-checked unchanged.
