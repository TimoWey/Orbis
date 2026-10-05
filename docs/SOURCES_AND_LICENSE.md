# Sources and licensing

Research snapshot: 2026-10-05. Pin versions and commits before implementation. Upstream statements are not Orbis test results.

| Topic | Source |
| --- | --- |
| Foundation | [Toroidal World](https://github.com/anyttng/toroidal_world) |
| Additional inspiration | [Circumnavigate](https://modrinth.com/mod/circumnavigate) — discussed during the initial finite-world feasibility research; inspiration only, not vendored engine code |
| License | [MIT license file](https://github.com/anyttng/toroidal_world/blob/main/LICENSE) |
| Periodic generation | [Technical explanation](https://github.com/anyttng/toroidal_world/blob/main/docs/how-it-works.md) |
| Minimum Nether size, 1:8, biome compression | [Server/generator documentation](https://github.com/anyttng/toroidal_world/blob/main/docs/dedicated-server.md) |
| Mod compatibility | [Upstream list](https://github.com/anyttng/toroidal_world/blob/main/docs/supported-mods.md) |
| Limits and DH distance | [Project description](https://modrinth.com/mod/toroidal-world) |
| C2ME bug report | [Issue 55](https://github.com/anyttng/toroidal_world/issues/55) |
| Java releases | [Official list](https://feedback.minecraft.net/hc/en-us/sections/360001186971-Release-Changelogs) |
| DH versions | [Fabric versions](https://modrinth.com/mod/distanthorizons/versions?g=26.3&l=fabric) |
| Vanilla biome tools | [Cubiomes](https://github.com/Cubitect/cubiomes) — not automatically accurate for toroidal generation |
| Earth surface area | [NASA educational material](https://www.nasa.gov/wp-content/uploads/2009/07/180576main_ETM.Roving.Vehicle.pdf), rounded to 510 million km² |

## Reusing upstream code

The reviewed upstream file uses MIT, Copyright (c) 2026 Vitalik Yarina. Copying, modification and distribution are permitted under its terms. Retain the full license and copyright notice when importing code; review third-party components separately. Credit the foundation in Orbis.

The compatibility section links to each mod's Modrinth project and displays the project icon returned by the public Modrinth API on 2026-10-05. Icons are loaded from Modrinth's CDN and belong to their respective authors; they are not original Orbis artwork or covered by Orbis's MIT license.

Orbis is MIT licensed. The [development alpha](https://github.com/TimoWey/Orbis/tree/orbis-alpha) integrates the engine source from commit `060080683bdc0e198da1fc32502dd3f62875413c`; no separate Toroidal World mod is required. The source archive, checksum and exact integration patches are documented in [vendor/README](https://github.com/TimoWey/Orbis/blob/orbis-alpha/vendor/README.md). The test bootstrap is adapted under the retained upstream MIT notice. See [Implementation](https://github.com/TimoWey/Orbis/blob/orbis-alpha/docs/IMPLEMENTATION.md) for exact provenance. The animation uses no Minecraft textures or gameplay footage.
