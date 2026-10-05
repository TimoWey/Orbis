# Features

The development alpha implements world wrapping, size selection and an unwrapped End. Gameplay validation is still pending. Biome checks and existing-world conversion are planned. See [Implementation status](IMPLEMENTATION.md) for build evidence and limitations.

| Feature | Behaviour |
| --- | --- |
| Seamless world | Walking or flying across a boundary continues on the opposite side of the same world |
| Size selection | Logarithmic slider, Tiny/Small/Normal/Large/Earth shortcuts, and exact full side length in chunks; bounded by Tiny and Earth |
| Biome check | Examine Overworld and Nether before first play |
| Biome report | Show found, missing and unconfirmed biomes per dimension |
| Existing-world conversion | Preview, protected areas, a new world copy and blended edge strips |
| Performance mods | Distant Horizons, Sodium and Lithium as the required combination |
| Unchanged End | Familiar exploration, dragon fight, portals and gateways |

## Proposed biome-check flow

1. Choose size and seed; fix generator parameters.
2. Show “Checking biomes…” with progress and cancellation.
3. Display results per dimension:

| Status | Message |
| --- | --- |
| Complete coverage confirmed | “All required biomes were found.” |
| Coverage proven incomplete | “This world is missing the following biomes: …” |
| Incomplete scan | “The scan could not confirm these biomes: …” |

If coverage is incomplete: **Start anyway**, **Change size**, and for new worlds only, **Check another seed**. A missed sample alone must not be reported as proven absence.

Suggested warning: “This world is finite. Some biomes and structures may be missing. You can keep playing or change your settings.”

Normal aims for complete coverage; warnings prevent a false guarantee. Guaranteed coverage or mandatory seed search has not been decided. Evaluate biomes and structures separately. All proposed UI text is English.
