# Sources and licensing

Research snapshot: 2026-10-05; licensing updated 2026-10-15.

## Orbis code

Orbis is MIT licensed ([LICENSE](../LICENSE)). All of its code is its own; the world engine (`dev.orbis.engine`) was written from scratch for Orbis.

Earlier alpha builds (up to 0.1.0-alpha.12/13) compiled an MIT-licensed snapshot of Toroidal World (commit `0600806`). That snapshot has been removed: Orbis no longer compiles, ships or calls any of its code. No code from the later, relicensed upstream versions was ever used.

Orbis was inspired by Toroidal World and Circumnavigate; the credit is at the end of the [README](../README.md).

## Research sources

| Topic | Source |
| --- | --- |
| Vanilla world width | [World border analysis](https://techmcdocs.github.io/pages/GameMechanics/WorldBorder/) — default edges at approximately ±30 million on X and Z, hence roughly 60 million blocks per side |
| 2b2t history | [Official About page](https://www.2b2t.org/about/) — server founded in 2010; the exact world creation date is estimated as late 2010 or early 2011 |
| 2b2t visitors | [Official homepage](https://www.2b2t.org/) — over one million visitors, checked 2026-10-05; this is not a count of concurrently active players |
| Java releases | [Official list](https://feedback.minecraft.net/hc/en-us/sections/360001186971-Release-Changelogs) |
| DH versions | [Fabric versions](https://modrinth.com/mod/distanthorizons/versions?g=26.3&l=fabric) |
| Vanilla biome tools | [Cubiomes](https://github.com/Cubitect/cubiomes) — not automatically accurate for toroidal generation |
| Earth surface area | [NASA educational material](https://www.nasa.gov/wp-content/uploads/2009/07/180576main_ETM.Roving.Vehicle.pdf), rounded to 510 million km² |

## Introduction fact check

The introduction uses 2b2t as a familiar example of a long-running world and its player history. No reliable first-party explored-area percentage was located, so it makes no claim about the fraction of 2b2t that has been explored. Save-file size is not a direct measure of explored surface area: compression, dimensions, stored entities and chunk pruning all affect it.

## Other assets

The compatibility section links to each mod's Modrinth project and displays the project icon returned by the public Modrinth API on 2026-10-05. Icons are loaded from Modrinth's CDN and belong to their respective authors; they are not original Orbis artwork or covered by Orbis's MIT license.

Gradle's generated wrapper retains its Apache-2.0 headers. The animation uses no Minecraft textures or gameplay footage.
