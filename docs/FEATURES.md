# Features

Status for 0.1.0-alpha.22. See [Implementation status](IMPLEMENTATION.md) for test evidence and limitations.

| Feature | Behaviour | Status |
| --- | --- | --- |
| Seamless world | Walking or flying across a boundary continues on the opposite side of the same world | Implemented; seams and corners verified on a server |
| Size selection | Logarithmic slider, Tiny/Small/Normal/Large/Earth shortcuts, and exact side length in chunks between Tiny and Earth | Implemented |
| Connected Nether | Always exactly 1:8 of the Overworld width | Implemented; portal mapping verified |
| Unchanged End | Familiar exploration, dragon fight, portals and gateways | Implemented; gateways not yet verified |
| Player map | Corner minimap, M atlas with flat map and globe, player markers, saved settings | Implemented |
| Server support | `orbis:tiny` … `orbis:earth` level types; players need Orbis, a game without it is turned away with an install message | Implemented |
| Admin check | `/orbis check` looks for expected biomes and structures in a fixed time budget | Implemented |
| Performance mods | Distant Horizons, Sodium, Lithium and C2ME as optional installs | All seam game tests pass with them; see [compatibility](COMPATIBILITY.md) |
| Old-engine worlds | Worlds from alphas before the engine switch (such as alpha.11) | Open with their saved terrain; new chunks may not line up with old terrain |
| Biome-check screen | Check a new world before first play and report found / missing / unconfirmed | Parked |
| Existing-world conversion | Copy, preview and blend edge strips | Parked ([notes](CONVERSION.md)) |

Small worlds may miss biomes or structures. A sample that does not find something is reported as unconfirmed, never as proven absent.
