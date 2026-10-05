# Mod compatibility

As of 2026-10-05. **No Orbis compatibility tests have been performed.**

| Mod | Priority | Upstream / research | Orbis |
| --- | --- | --- | --- |
| Distant Horizons | Required; particularly important | Support documented upstream | Untested |
| Fabric API | Runtime dependency | Required by the standalone build | Build passes; gameplay untested |
| Sodium | Required | Support documented upstream | Untested |
| Lithium | Required | Support documented upstream | Untested |
| FerriteCore | Compatibility priority | Memory optimization | Untested |
| Entity Culling | Compatibility priority | Check visibility across seams | Untested |
| Litematica | Compatibility priority | Check schematic placement across seams | Untested |
| MiniHUD | Compatibility priority | Check overlays and coordinates across seams | Untested |
| Tweakeroo | Compatibility priority | Check camera and placement tools across seams | Untested |
| Mod Menu | Compatibility priority | Check listing and settings access | Untested |
| ScalableLux | Candidate | Support documented upstream | Untested |
| Iris Shaders | Compatibility priority; particularly important | Support documented upstream | Untested |
| C2ME | Optional investigation | Listed as supported; open bug report #55 | Untested / risk |

“Required” for Distant Horizons, Sodium and Lithium describes the release acceptance target. They are optional installations in the current alpha; only Fabric Loader and Fabric API are runtime requirements. A matching build of each mod and its dependencies must exist for the selected Minecraft version before testing.

Additional compatibility request: the exact identity of “EM²” is still to be confirmed.

## Acceptance

- Record exact Minecraft, Java, Fabric and mod versions.
- Test the required combination together, not just each mod in isolation.
- Check seams, corners, large view distances, generation, save/reload and conversion.
- DH: LOD across seams, detail transitions, block edits and caches after conversion.
- Small worlds: repeated rendering must not cause duplicate simulation.
- Measure frame time, tick time, memory and generation time on documented hardware.

Upstream describes DH support up to half the world width. Small Nether worlds therefore require an explicit view-distance policy. Arbitrary view distances are not promised.

[Sources](SOURCES_AND_LICENSE.md)
