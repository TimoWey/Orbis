# Architecture draft

Orbis is standalone. The MIT-licensed engine source is compiled into the Orbis JAR. See [Implementation](IMPLEMENTATION.md). This table also includes future components.

| Component | Responsibility |
| --- | --- |
| Upstream foundation | Compile the pinned MIT source snapshot into Orbis; retain attribution; no separate engine mod |
| World configuration | Persist bounds, size profile, generator version and schema version |
| Coordinates | Unique canonical chunks; correct normalization of negative coordinates |
| Rendering | Periodic views of the same data, without duplicate simulation |
| Distances | Shortest periodic paths; appropriate pathfinding and interactions |
| Generation | Periodic noise grid; check features, structures, caves and fluids too |
| Dimensions | Nether bounds = Overworld bounds / 8; explicitly exclude the End |
| Biome check | Query the actual generator; distinguish presence, absence and uncertainty |
| Converter | Existing-chunk mask, protected areas, deterministic transitions and complete data migration |
| Distant Horizons | Correct LOD coordinates and cache handling |

Singleplayer uses an integrated server, so server-side logic still needs changes.

Periodic generation conceptually satisfies N(x+Lx,y,z)=N(x,y,z) and N(x,y,z+Lz)=N(x,y,z). Keeping the seed while changing the generator does not guarantee identical terrain.

No startup algorithm may iterate through every theoretical chunk. Use at least 64-bit arithmetic for total areas and chunk counts.

## Atlas boundaries

The atlas keeps three kinds of data separate: observed terrain colors from loaded chunks, biome previews predicted by the current wrapped generator, and structure evidence/checkpoint state. Predictions are not a replacement for observed blocks. The HUD and full atlas share the same block-resolution tiles, with separate projection/view state. Detailed saved/loaded terrain takes display priority over a seed-independent fictional map with light gray water and three muted land colors. Generator biome samples support the debug checklist but never color the map. All saved chunks, including pregenerated regions, are eligible for detailed display.

This follows the useful separation used by established map tools: sampled world data, derived display tiles and user overlays have distinct lifetimes. Orbis must retain its own wrapped-generator prediction path: a generic vanilla seed map cannot account for its periodic generation. A separate region tile archive preserves imported and observed block colors beyond the bounded in-memory history. Background workers read saved Minecraft terrain and optional DH leaf data, then prepare view-sized archive layers. The flat HUD uses a padded block-resolution image composed entirely on a worker; camera movement scrolls GPU texture coordinates, with local archive invalidation and no client-thread rasterization. Full-atlas/globe GPU projection and partial texture uploads remain future extensions.

Build metadata and matching source archives identify each variant. CI verifies both variants, while seam traversal, portals, persistence in a running world and the combined performance-mod matrix still require gameplay validation.
