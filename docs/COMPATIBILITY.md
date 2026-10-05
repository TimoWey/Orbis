# Mod compatibility

As of 2026-10-05. **No Orbis compatibility tests have been performed.**

| Mod | Priority | Upstream / research | Orbis |
| --- | --- | --- | --- |
| Distant Horizons | Required; particularly important | Support documented upstream | Untested |
| Sodium | Required | Support documented upstream | Untested |
| Lithium | Required | Support documented upstream | Untested |
| FerriteCore | Candidate | Memory optimization; integration undecided | Untested |
| ImmediatelyFast | Candidate | Rendering optimization; integration undecided | Untested |
| ScalableLux | Candidate | Support documented upstream | Untested |
| Iris | Additional goal | Support documented upstream | Untested |
| C2ME | Optional investigation | Listed as supported; open bug report #55 | Untested / risk |

## Acceptance

- Record exact Minecraft, Java, Fabric and mod versions.
- Test the required combination together, not just each mod in isolation.
- Check seams, corners, large view distances, generation, save/reload and conversion.
- DH: LOD across seams, detail transitions, block edits and caches after conversion.
- Small worlds: repeated rendering must not cause duplicate simulation.
- Measure frame time, tick time, memory and generation time on documented hardware.

Upstream describes DH support up to half the world width. Small Nether worlds therefore require an explicit view-distance policy. Arbitrary view distances are not promised.

[Sources](SOURCES_AND_LICENSE.md)
