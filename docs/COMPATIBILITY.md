# Mod compatibility

As of 2026-10-16. Orbis ships integration code for two mods, Distant Horizons and C2ME (below); none for the others. The client game tests run with Sodium, Lithium and Distant Horizons via `gradlew runClientGameTest -PorbisCompat=true`, and with C2ME via `-PorbisC2me=true`.

| Mod | Priority | What to check | Orbis |
| --- | --- | --- | --- |
| Distant Horizons | Required; particularly important | Check LOD across seams | 3.3.4: all seam game tests pass with it (new engine, 2026-10-14). Far terrain continues past the seams: Orbis maps every DH section beyond the world edge onto its image inside the world (reads, writes and generation), only for DH 3.3.x |
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
| C2ME | Requested 2026-10-16 | Check parallel generation near seams | 0.4.2-alpha.0.104: the seam, debug world, behaviour, parcours and map game tests pass with it (2026-10-16). Orbis folds C2ME's chunk scheduler keys, neighbour waits, generation locks and random positions across the seam, and its aquifer hooks run after C2ME's aquifer rewrite. Only loaded when C2ME is installed |

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

## Distant Horizons across the seams

The client never jumps at a seam; it keeps walking in its own frame, one or more world widths away from the world's own coordinates. Distant Horizons does not know the torus, so on its own it showed far terrain only around the world's own coordinates: past a seam the player stood in empty far view, with the world floating in the distance as a block.

Orbis (`dev.orbis.engine.dh`, mixins into DH 3.3 internals, as DH has no public way to repeat its terrain) treats every DH section outside the world as an image of one inside it:

- Reading a section beyond the edge reads its image inside the world and hands it out at the asked position.
- Terrain DH builds there (chunks the client holds past the seam) is stored at the image, so there is one copy and every image shows changes.
- Generation is asked for at the image, never beyond the edge.
- When a section changes, its images in the far view are rebuilt too.
- In worlds narrower than DH's coarsest aligned sections (the 128-chunk debug world), those sections are put together from their quarters.

Other DH versions run untouched (a warning is logged). `ORBIS_DH_TORUS=off` in the environment turns the mapping off, for comparison. Singleplayer is game-tested; on a server the client uses the world shape the server announces (not yet tested).

