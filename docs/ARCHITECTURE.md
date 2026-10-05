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
