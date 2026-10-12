# Sources and licensing

Research snapshot: 2026-10-05, upstream status checked 2026-10-12. Upstream links point to the pinned commit, whose files are MIT licensed. Upstream statements are not Orbis test results.

| Topic | Source |
| --- | --- |
| Vanilla world width | [World border analysis](https://techmcdocs.github.io/pages/GameMechanics/WorldBorder/) — default edges at approximately ±30 million on X and Z, hence roughly 60 million blocks per side |
| 2b2t history | [Official About page](https://www.2b2t.org/about/) — server founded in 2010; the exact world creation date is estimated as late 2010 or early 2011 |
| 2b2t visitors | [Official homepage](https://www.2b2t.org/) — over one million visitors, checked 2026-10-05; this is not a count of concurrently active players |
| Foundation | [Toroidal World at the pinned commit](https://github.com/anyttng/toroidal_world/tree/060080683bdc0e198da1fc32502dd3f62875413c), now renamed Exotic Worlds |
| Additional inspiration | [Circumnavigate](https://modrinth.com/mod/circumnavigate) — discussed during the initial finite-world feasibility research; inspiration only, not vendored engine code |
| License | [MIT license file](https://github.com/anyttng/toroidal_world/blob/060080683bdc0e198da1fc32502dd3f62875413c/LICENSE) |
| Periodic generation | [Technical explanation](https://github.com/anyttng/toroidal_world/blob/060080683bdc0e198da1fc32502dd3f62875413c/docs/how-it-works.md) |
| Minimum Nether size, 1:8, biome compression | [Server/generator documentation](https://github.com/anyttng/toroidal_world/blob/060080683bdc0e198da1fc32502dd3f62875413c/docs/dedicated-server.md) |
| Mod compatibility | [Upstream list](https://github.com/anyttng/toroidal_world/blob/060080683bdc0e198da1fc32502dd3f62875413c/docs/supported-mods.md) |
| Limits and DH distance | [Project description](https://modrinth.com/mod/toroidal-world) |
| C2ME bug report | [Issue 55](https://github.com/anyttng/toroidal_world/issues/55) |
| Java releases | [Official list](https://feedback.minecraft.net/hc/en-us/sections/360001186971-Release-Changelogs) |
| DH versions | [Fabric versions](https://modrinth.com/mod/distanthorizons/versions?g=26.3&l=fabric) |
| Vanilla biome tools | [Cubiomes](https://github.com/Cubitect/cubiomes) — not automatically accurate for toroidal generation |
| Earth surface area | [NASA educational material](https://www.nasa.gov/wp-content/uploads/2009/07/180576main_ETM.Roving.Vehicle.pdf), rounded to 510 million km² |

## Introduction fact check

The introduction uses 2b2t as a familiar example of a long-running world and its player history. No reliable first-party explored-area percentage was located, so it makes no claim about the fraction of 2b2t that has been explored. Save-file size is not a direct measure of explored surface area: compression, dimensions, stored entities and chunk pruning all affect it.

## Upstream license change

On 2026-10-08 Toroidal World was renamed Exotic Worlds, and on 2026-10-09 (upstream commit `8f417283`) it switched from MIT to PolyForm Shield 1.0.0, which forbids use in competing products. This does not affect Orbis's snapshot: commit `0600806` was published under MIT, and that grant stays valid. Rules for Orbis:

- Keep the MIT snapshot and its notice.
- Never copy code from `8f417283` or later. Ideas from that period may only be re-implemented independently, without looking at the code while writing.
- Commits before `8f417283` are MIT and may be ported with attribution.

## Reusing upstream code

The reviewed upstream file uses MIT, Copyright (c) 2026 Vitalik Yarina. Copying, modification and distribution are permitted under its terms. Retain the full license and copyright notice when importing code; review third-party components separately. Credit the foundation in Orbis.

The compatibility section links to each mod's Modrinth project and displays the project icon returned by the public Modrinth API on 2026-10-05. Icons are loaded from Modrinth's CDN and belong to their respective authors; they are not original Orbis artwork or covered by Orbis's MIT license.

Orbis is MIT licensed. It integrates the engine source from commit `060080683bdc0e198da1fc32502dd3f62875413c`; no separate Toroidal World mod is required. The source archive, checksum and exact integration patches are documented in [vendor/README](../vendor/README.md). The test bootstrap is adapted under the retained upstream MIT notice. See [Implementation](IMPLEMENTATION.md) for exact provenance. The animation uses no Minecraft textures or gameplay footage.
