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

- The compact HUD map follows the player and shows position, facing direction and waypoints. In the atlas, select **Flat** or **Globe**; the HUD uses the same preference. Cycle **TR / TL / BR / BL** to place it in any corner. View and corner preferences persist in `config/orbis-atlas.properties`.
- In the atlas, left-drag or use WASD to pan the flat map or rotate the globe. Scroll or use +/− to zoom. Flat wheel zoom stays anchored under the pointer. **You** / Home centers the player; **All** restores the overview of the finite world.
- The globe is an orientation projection: Orbis connects both X and Z edges with torus topology. Unwrapped dimensions such as the End use the flat map and loaded observations; there is no finite whole-world survey there.
- Right-click the map to add a waypoint. Right-click near an existing visible marker to remove it. X/Z come from the selected map position; Y is the player's current height, not a terrain-height prediction. Up to 128 markers per save and dimension are supported. They appear in both the window and HUD.
- Waypoints persist in `config/orbis-waypoints.properties`, keyed by the absolute save-directory path and dimension. They do not modify the world save. Moving or renaming a save directory changes that association. Markers are simple numbered locations, without a name editor or teleport action.
- **Grid** toggles diagnostic chunk/place overlays. Player, selected locations and waypoints remain visible independently. Select a found checklist row to center its location without moving the player.

Loaded terrain uses a 4 × 4 grid of surface block map colors per chunk, sampled from already-loaded chunks. This gives terrain detail near explored locations; it is not a full-resolution aerial image. Unsurveyed terrain stays unknown. The finite-world background uses sea-level biome colors from the generator survey, so unexplored areas depict predicted biomes rather than actual terrain blocks.

## Right-hand checklist

**Biomes**, **Places** and **Stats** share the compact right-hand panel. Arrow buttons, the wheel over the panel, or Page Up/Down browse entries. Filter All / Unfound / Found; sort biomes by Name A–Z or Samples, and places by Name A–Z or Found first. Filtering does not change coverage totals. Hover a row for its full ID and evidence. Tab and Enter navigate controls and selectable found rows.

Biome coverage counts distinct found candidates, using the union of loaded observations and generator samples. Samples count queries, not chunks, biome patches or surface area. Stats includes preset, world bounds, performance and map-history information. The preset is inferred from the Overworld width; old/custom sizes may show Custom.

### Biome evidence

| State | Evidence |
| --- | --- |
| Observed | Read from a real loaded chunk's biome cells, including underground sections. The location points to such a cell. |
| Sampled | The current generator returned the biome at a sampled position; no terrain was generated there. |
| Unconfirmed | The dimension's biome source permits it, but inspection and sampling have not found it. This does not prove absence. |

Candidates come from the selected dimension's biome source, including modded IDs, with observed/sampled IDs added if necessary. Biomes belonging only to other dimensions or unused registry entries are not mixed into this checklist.

A survey starts automatically on entry into a local finite dimension. It queries a fixed **256 × 256 grid at four heights**: sea level, minimum Y + 16, Y 32 and Y 128, clamped to vertical bounds. Pause/resume preserves progress; repeating a completed pass resets sample counts. Closing the atlas leaves the survey active for the HUD.

This is a bounded diagnostic, not an exhaustive biome census. Rare, small or vertically narrow biomes can be missed even on Tiny. Completing the grid never marks an unconfirmed biome absent, and cannot certify that every candidate exists. Generator predictions can differ from old chunks or changed datapacks. Full biome coverage is not guaranteed by choosing a larger preset either.

### Structure and portal evidence

**Check places** starts a separate, pausable audit of structure types eligible for the current dimension's generator. Where strongholds are eligible, an End portal room layout is listed separately.

| State | Meaning |
| --- | --- |
| Observed | A valid structure start was read from a loaded chunk. |
| Predicted | This seed and current generator produce a valid layout at an in-bounds start. |
| Unconfirmed | No evidence yet, including incomplete, paused or failed searches. |
| Not found in check | All relevant candidates were checked without finding a valid layout. This is not proof of absence from saved terrain. |

The audit queries generation directly rather than issuing `/locate` commands. It uses Minecraft's weighted structure selection, biome/height checks and Orbis extra-start hooks, enumerating random-spread regions and stronghold positions. Other placement types use a lazy chunk-by-chunk fallback. Each type retains one predicted example, and observations take precedence. The search stops when every listed type has evidence; large worlds and rare structures can take a long time.

Layouts use disposable ProtoChunks that are not installed in the chunk cache, terrain-populated or saved. **All listed types found** means at least one observed or predicted example per checklist entry in this dimension. It does not certify intact blocks, loot, every structure variant, or portal travel. Check the Nether separately; the unwrapped End has no finite candidate-exhaustion audit.

A portal room prediction requires a stronghold PortalRoom piece. Its coordinates indicate the bounding-box center, not a frame block. Observed portal blocks and gateways are additional map markers. Historical observations can become stale after world edits; they are session-local and are not continuously revalidated.

## Performance and lifetime

Inspection and generation queries run on the integrated server thread; the renderer receives immutable snapshots twice per second. Each active tick schedules at most eight loaded-chunk inspections, 256 biome queries and one structure-layout evaluation against a shared **3 ms scheduling budget**. Placement-task initialization is spread across ticks. A single layout, ring initialization or hook call cannot be preempted and may exceed that budget; this is not a hard tick-latency guarantee. Closing or changing the active world cancels further work for its request.

The HUD keeps collection active while the local world is open, including after the atlas window closes. Disconnect clears the active snapshot and releases HUD textures; server shutdown clears session survey history. No background worker touches live world objects and no chunk objects are retained. Map history is capped at 32,768 chunks and observed markers at 4,096 per dimension; Stats reports truncation. The survey raster has at most 65,536 cells regardless of preset size. Only preferences and user waypoints persist outside the save.

Rendering uses cached dynamic textures, capped at 1,536 pixels on the longest flat-map side and 768 for the globe. Immutable geometry is shared across snapshots. Changes to view, terrain or overlays refresh textures; idle frames reuse them. View updates are coalesced to at most 30 texture uploads per second. The minimap quantizes its moving center to reduce uploads. These measures bound stored history and scheduled work; they are not a measured FPS guarantee.

The interaction reference for this revision is [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap). The atlas uses its own UI and renderer; it does not require the Xaero minimap mod. Earlier renderer research also consulted [VoxelMap region textures](https://github.com/Updated-VoxelMap/VoxelMap/blob/master/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CompressibleMapRegionTexture.java).

## Validation

**Current alpha.3, 2026-10-06:** Java 25 normal/debug builds passed 33/57 automated tests and both final JARs passed standalone packaging checks. Tests cover terrain/projection/seams, waypoint persistence and failed-write isolation, survey lifecycle, evidence semantics, and Minecraft codec parsing of the new advancements. Interactive validation of the new HUD, window and persisted waypoints is still required; historical checks below do not validate these new features.

**Historical, 2026-10-05 (alpha.2 atlas):** normal/debug packaging and regression tests passed. A Tiny singleplayer world was loaded, both map projections and dragging were exercised, and its four-height sample completed with **51/56 found, 5 unconfirmed**. Compact and maximized layouts were inspected. This was one seed, not a biome-coverage guarantee or performance benchmark. Portal correctness, narration and the full mod compatibility matrix remained unverified.

For the current build, use a new Tiny world and check:

1. HUD positioning in all four corners, facing direction, Flat/Globe preference and M/remapped-key/Escape behavior.
2. Visible background world, continued simulation, map dragging/zooming and checklist paging at large GUI scales.
3. Right-click add/remove in both views, HUD markers, persistence after restart and isolation across saves/dimensions.
4. Automatic sample, pause/resume, structure audit and honest unconfirmed/predicted statuses after completion.
5. Both world seams and a corner, followed by Nether travel and disconnect/rejoin into a different save.
6. A known stronghold/portal room against actual blocks, then priority performance-mod compatibility and narration.

Exact biome coverage, waypoint naming, portal pairing and exportable audit reports remain future work.
