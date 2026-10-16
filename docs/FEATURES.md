# Features

Status for 0.1.0-alpha.18. See [Implementation status](IMPLEMENTATION.md) for test evidence and limitations.

| Feature | Behaviour | Status |
| --- | --- | --- |
| Seamless world | Walking or flying across a boundary continues on the opposite side of the same world | Implemented; seams and corners verified on a server |
| Size selection | Logarithmic slider, Tiny/Small/Normal/Large/Earth shortcuts, and exact side length in chunks between Tiny and Earth | Implemented |
| Connected Nether | Always exactly 1:8 of the Overworld width | Implemented; portal mapping verified |
| Unchanged End | Familiar exploration, dragon fight, portals and gateways | Implemented; gateways not yet verified |
| Player map | Corner minimap, M atlas with flat map and globe, player markers, saved settings | Implemented |
| Server support | `orbis:tiny` … `orbis:earth` level types; players need Orbis, a game without it is turned away with an install message | Implemented |
| Admin check | `/orbis check` looks for expected biomes and structures in a fixed time budget | Implemented |
| Performance mods | Distant Horizons, Sodium and Lithium as optional installs | Engine support present; untested together |
| Biome-check screen | Check a new world before first play and report found / missing / unconfirmed | Parked |
| Existing-world conversion | Copy, preview and blend edge strips | Parked ([notes](CONVERSION.md)) |

Small worlds may miss biomes or structures. A sample that does not find something is reported as unconfirmed, never as proven absent.
