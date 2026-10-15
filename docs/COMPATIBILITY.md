# Mod compatibility

As of 2026-10-15. Orbis ships no integration code for other mods. The client game tests run with Sodium, Lithium and Distant Horizons via `gradlew runClientGameTest -PorbisCompat=true`.

| Mod | Priority | What to check | Orbis |
| --- | --- | --- | --- |
| Distant Horizons | Required; particularly important | Check LOD across seams | 3.3.4: all seam game tests pass with it (new engine, 2026-10-14); LOD rendering across seams not yet checked |
| Fabric API | Runtime dependency | Required by the standalone build | Tested on a dedicated server, alpha.7–11 |
| Vanilla client | Joining an Orbis server | Turned away: Orbis is required on every client | Game test: the disconnect screen names the Orbis version to install |
| Sodium | Required | Check rendering across seams | 0.9.2: all seam game tests pass with it (2026-10-14) |
| Lithium | Required | Check AI and POI across seams | 0.26.2: all seam game tests pass with it (2026-10-14). Orbis turns off `mixin.ai.poi`, whose own POI search does not see beds and workstations past the seam |
| FerriteCore | Compatibility priority | Memory optimization | Untested |
| Entity Culling | Compatibility priority | Check visibility across seams | Untested |
| Litematica | Compatibility priority | Check schematic placement across seams | Untested |
| MiniHUD | Compatibility priority | Check overlays and coordinates across seams | Untested |
| Tweakeroo | Compatibility priority | Check camera and placement tools across seams | Untested |
| Mod Menu | Compatibility priority | Check listing; Orbis has no Mod Menu settings screen | Untested |
| ScalableLux | Candidate | Check lighting across seams | Untested |
| Iris Shaders | Compatibility priority; particularly important | Check shaders across seams | Untested |
| C2ME | Optional investigation | Check parallel generation near seams | Untested / risk |

“Required” for Distant Horizons, Sodium and Lithium describes the release acceptance target. They are optional installations in the current alpha; only Fabric Loader and Fabric API are runtime requirements. A matching build of each mod and its dependencies must exist for the selected Minecraft version before testing.
Additional compatibility request: the exact identity of “EM²” is still to be confirmed.

## Acceptance

- Record exact Minecraft, Java, Fabric and mod versions.
- Test the required combination together, not just each mod in isolation.
- Check seams, corners, large view distances, generation and save/reload.
- DH: LOD across seams, detail transitions, block edits and caches.
- Small worlds: repeated rendering must not cause duplicate simulation.
- Measure frame time, tick time, memory and generation time on documented hardware.

Small worlds, especially small Nethers, may need an explicit Distant Horizons view-distance policy. Arbitrary view distances are not promised.

Conflict: `fabric.mod.json` declares `breaks: toroidal_world`, so Fabric refuses to load Orbis together with that mod.

[Sources](SOURCES_AND_LICENSE.md)
