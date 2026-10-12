# Existing-world conversion

**Parked since 2026-10-12; not part of the current alpha.** These notes are kept for a possible later version.

| Step | Behaviour |
| --- | --- |
| Analyze | Inspect source version, generator, dimensions, existing chunks and important positions |
| Choose bounds | Couple Overworld and Nether at 1:8; show content outside the selection |
| Protect | Mark builds manually; automatic detection alone is unreliable |
| Preview | Show affected edge strips, protection conflicts and missing/unconfirmed biomes |
| Copy | Work on a separate world copy only |
| Regenerate | Replace selected edge chunks; blend into retained terrain and the opposite edge, including corners |
| Preserve data | Consistently migrate inventories, entities, block entities, POIs, ticks, structures, players and progress |
| Finish | Preserve the End; update DH data; verify the result and generate a report |

## Rules

- Never silently change an existing world's seed.
- Never merge distinct old chunks onto one target position using modulo.
- Make strip width configurable; difficult terrain may require more space.
- Handle transitions between retained and newly generated interior chunks too.
- Generate unexplored edges on demand; do not precompute the entire world.
- Missing biomes may require larger bounds or explicitly selected terrain changes.
- Without spare space, complete biome coverage and unchanged builds may conflict.

**Open:** source version/mod list, movable selection, default strip width and supported third-party generators.
