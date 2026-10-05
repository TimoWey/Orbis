# Debug atlas

The optional debug build adds a read-only atlas for local singleplayer testing. Press **M** in a world; change the binding in Minecraft's Controls menu if another map mod uses M. Close with the same binding, Escape or Close. The world keeps running while the atlas is open.

## Build and install

With Java 25:

```powershell
.\gradlew.bat build -PorbisDebug=true
```

Install `build/debug/libs/orbis-0.1.0-alpha.2-debug.jar` in the test instance, replacing the normal Orbis JAR. Do not install both. The normal `gradlew.bat build` produces `build/libs/orbis-0.1.0-alpha.2.jar` without debug classes, debug resources or the debug entrypoint. Separate output directories prevent stale debug classes from leaking between variants. `clean` on the normal build also removes the nested debug output.

Separate CI release/debug artifacts are pending a workflow update; the current GitHub login cannot publish workflow changes. Build the debug JAR locally for now. Run `python tools/verify_standalone.py --debug <debug.jar>` for debug packaging, or omit `--debug` for the normal artifact.

Development launch: `gradlew.bat runClient -PorbisDebug=true`. Do not switch build variants while this development client is running: Gradle can clean the previous compilation outputs. The packaged JARs remain separate.

## Map and navigation

- Flat map: drag with the left mouse button or use WASD to pan; scroll or use +/− to zoom. Player / Home centers the player, World restores the world overview. The button centers the view; it is not a visibility toggle.
- Globe: drag or use WASD to rotate; scroll or use +/− to zoom. Flat-map wheel zoom stays anchored under the pointer. This is a spherical map projection for orientation; Orbis actually connects both X and Z edges, a torus topology. No globe view or whole-world sample is offered in an unwrapped dimension such as the End.
- Both overlays start off. Chunks shows inspected cells only above one GUI pixel per chunk and loaded-chunk outlines above six pixels. The globe subtly shades loaded regions in surface coordinates instead of drawing dots. Place markers are grouped into small screen tiles; selecting an entry always shows its exact marker. These are server chunks, not client render distance.
- P marks the player, + a known structure or portal observation, and X the selected entry. Select a biome or place on the right to center it and show its coordinates. This moves the camera on the map, not the player.
- Biomes, Places and Stats switch the right-hand list. Use the arrow buttons, mouse wheel over the panel, or Page Up/Down to browse. Flat controls and selectable locations support Tab, Enter and narration; statistics and unconfirmed entries are read-only text; hover hints are brief and wrapped; exact coordinates appear when selecting a location.
- Biomes shows **found / total dimension biomes**, unconfirmed entries, and sample progress. Places shows its own place-type coverage and generator-check state. Counts use the union of loaded-chunk and sample observations, so the same biome is never counted twice. Filtering never changes the coverage totals.
- In Biomes, cycle **All / Unfound / Found**, and **Name A–Z / Samples**. Unfound means not detected yet, including after a completed sample. The always-visible Samples column counts queries, with a brief header hint; evidence definitions are below. Samples sort descending and refer to generator queries at four heights, not number of chunks, biome patches or surface area. Repeating a completed sample resets the sample counts; pausing/resuming retains them.
- Stats shows the world preset inferred from the Overworld width (also when viewing the Nether or End), and the last map update time, texture resolution and upload count. Old worlds that do not match the current presets correctly show Custom; the original menu choice is not stored separately.
- The map colors are biome categories, not an aerial terrain texture. Whole-world background cells represent sea-level biome samples; loaded-chunk cells use the surface biome at the chunk center.

The interface separates navigation from data: quiet flat controls, a persistent coverage summary and progress bar, a biome table with status and sample columns, and plain label/value diagnostics. Long names and exact numbers are available in tooltips. Coordinates and navigation hints sit below the map instead of covering it with a pointer tooltip. The minimal map chrome was informed by [JourneyMap's full-screen map documentation](https://teamjm.github.io/journeymap-docs/latest/client/full-screen-map/); no JourneyMap code or assets are included.

## What the biome checklist actually proves

| State | Evidence |
| --- | --- |
| Observed (mint) | The biome was read from a real loaded chunk's biome cells, including underground sections. The location points to such a cell. |
| Sampled (blue) | The current generator and its RandomState returned the biome at a sampled location; terrain was not generated there. |
| Unconfirmed (gray) | The dimension's biome source permits the biome, but neither inspection nor sampling has found it. **This does not prove absence.** |

The candidate list comes from the selected dimension's biome source, including modded biome IDs. It is not a list of every registered biome from all dimensions. Observed or sampled IDs are also included if a mod's source did not list them.

**Sample** checks a fixed 256 × 256 grid inside the finite bounds at four heights: sea level, minimum Y + 16, Y 32 and Y 128 (clamped to the dimension's vertical bounds). It can pause/resume; a completed pass can repeat. This is deliberately a bounded diagnostic, not an exhaustive census. Rare and small biomes can be missed even on Tiny. Sample completion never turns unknown entries into “absent”. Old saves, edited terrain and later block changes can differ from the current generator.

The atlas cannot yet certify that a Tiny world contains every biome. An exhaustive or adaptive biome audit with explicit resolution and coverage would be a separate feature. Neither this atlas nor a larger preset guarantees full biome coverage.

## Structures and portals

The Places tab lists structure types eligible for the **current dimension's generator**, plus End portal room layouts where strongholds are eligible. It combines loaded-chunk observations with a separate **Check places** audit. Pause/resume preserves session progress.

| State | Meaning |
| --- | --- |
| Observed | A valid structure start was read from a loaded chunk. |
| Predicted | This seed and generator produce a valid layout at an in-bounds start. |
| Unconfirmed | No evidence yet, including paused or failed searches and unwrapped dimensions. |
| Not generated | Every eligible placement candidate for this type was checked in the finite bounds without a valid layout. This describes the current generator, not old or edited terrain. |

The audit uses Minecraft's weighted structure-set selection, biome/height validation and Orbis extra-start hooks. It enumerates random-spread regions and stronghold positions; other placements use a lazy chunk-by-chunk fallback. Once every listed type has a found location, the search stops. Absence requires exhaustion of all relevant searches. Large worlds or rare structures can take a long time.

Layouts are evaluated in disposable ProtoChunks that are never installed in the server chunk cache, saved, populated with terrain, or given chunk tickets. One predicted example per type is retained. Observed locations take precedence. Predictions are labelled on the map.

**All listed types found** means at least one observed or predicted example of every checklist entry in this dimension. It does not guarantee every biome, loot item, mob, feature or structure variant/piece. Check the Nether separately. The unwrapped End cannot receive a finite absence audit. Old chunks, datapack changes, player edits, intact blocks and portal travel still require gameplay verification.

End portal rooms are checked separately from strongholds: the layout must contain a PortalRoom piece. Coordinates indicate the bounding-box center, not a frame block. Loaded portal blocks and gateways remain map observations; they do not prove successful travel.

Observations remain session-local. Reopening reinspects loaded chunks; historical observations may be stale after edits.

## Performance and lifetime

All reads and biome sampling run on the integrated server thread; the client receives immutable snapshots twice per second. Each active tick starts at most eight chunk inspections, one requested structure-layout evaluation and 256 biome queries within a shared 3 ms scheduling budget. Layout evaluation or placement-hook initialization can exceed that budget; it is not a hard operation timeout. One individual operation can exceed that budget. No background executor touches live world objects.

Rendering uses one cached dynamic texture, sized for the display pixel scale and capped at 1,536 pixels on its longest side for the flat map and 768 for the globe, plus player/selection labels. Chunks, biome samples and structure marks are rasterized into that texture only when geometry, layers or view change. Changes to tick time or player position alone do not rebuild it. Immutable geometry is reused across server snapshots. Loaded biome palettes are checked before searching cells for newly observed biomes; pause/resume no longer reinspects all loaded chunks. View changes are coalesced to at most 30 texture updates per second; idle frames reuse the texture. Textures are released on close and size changes.

The texture approach was checked against [VoxelMap's region texture implementation](https://github.com/Updated-VoxelMap/VoxelMap/blob/master/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CompressibleMapRegionTexture.java). Orbis uses its own small renderer, with no VoxelMap code or new mod dependency. Minecraft 26.3's SDL mouse constants are used for dragging; the old hardcoded button 0 did not represent its left button.

Collection work stops when the screen closes. Lightweight load/unload events keep the loaded-coordinate set current; no chunk object is retained. Previously inspected map history is capped at 32,768 chunks and marker history at 4,096 entries per dimension, with a visible cap indication in Stats. The fixed sample raster has at most 65,536 cells regardless of preset size. Session data is cleared when the server stops and is never written into the world save.

## Test checklist and future diagnostics

Local validation on 2026-10-05: normal build passed 27 regression cases; debug build passed 31 (including globe projection round trips, survey coordinates across every preset and evidence-state semantics). Both final JARs passed standalone/debug-isolation checks. A separate development client created and loaded a new Tiny creative world; M opened the atlas, loaded-chunk/biome/structure observations appeared, and flat/globe rendering was observed. Full sample completion, portal correctness, keyboard narration and the compatibility matrix remain unverified in gameplay.

Follow-up validation on 2026-10-05: the compact-inspector/texture revision passed 40 tests against the concurrently updated world-size presets, including eight atlas tests (coverage union, filtering/sorting, preset matching, negative/offscreen raster geometry, globe pixels and projection math). Debug JAR packaging passed. The saved test world was opened in the updated client; the sample completed with **51/56 found, 5 unconfirmed**, and both textured map modes rendered. This is a seed-specific observation, not a biome-size guarantee. Parallel human input prevented an isolated automated drag/FPS comparison; the drag fix uses the verified 26.3 left-button constant. Portal correctness, narration and full compatibility testing remain outstanding.

UI/detail follow-up on 2026-10-05: debug build passed **41 tests** (nine atlas cases), including preservation of adjacent sample cells smaller than a GUI pixel in a display-resolution raster. Debug JAR packaging passed. In the development client, the 256 x 256 four-height sample completed at **51/56 found, 5 unconfirmed** in the same saved world. Flat-map dragging and globe dragging were verified. The compact table and plain Stats values were checked at 856 x 512 and a maximized 2560 x 1392 window. The maximized view reported a 1520 x 860 flat texture and a 768 x 434 globe texture; individual map refresh readings were 2.41 ms and 4.13 ms respectively. These are observations on this machine, not a frame-rate benchmark. Keyboard narration and the compatibility matrix remain unverified.

Use a new Tiny world first. Check M / Escape / a remapped binding, flat pan/zoom, globe rotation, checklist focus, pause/resume, paging at a large GUI scale and reopening after dimension changes. Cross both seams and a corner; compare canonical chunk positions and loaded outlines. Visit a stronghold, activate its portal, reopen the atlas, and compare the room marker with the actual blocks. Leave the world and join another seed to check isolation. Repeat with the priority performance mods.

Useful future additions: exact biome coverage audits, seam crossing/lap counters, Nether portal pairing and 1:8 coordinate checks, chunk generation/ticket states, generation latency, and exportable seed/size/mod-version test reports. These are not implemented by the first atlas.

## Hover, zoom and Places audit follow-up

The globe applies zoom to the raster and player/selection markers. Manual hover hints are explicitly wrapped. Both overlays default off. The Places checklist distinguishes observed, predicted, unconfirmed and exhaustively rejected generator layouts. Biome and structure checks have independent pause/resume commands.

Automated checks cover zoom clipping, placement enumeration against a brute-force chunk reference at negative boundaries, Earth-sized counters and evidence states. In-game validation of this revision's wheel input, hover size, overlays and completed structure audit is still pending.
