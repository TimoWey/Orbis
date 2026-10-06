# Orbis

Finite Minecraft worlds. Seamless exploration.

Minecraft Java worlds span roughly 60 million × 60 million blocks, about 30 million from the centre in each direction. Think of [2b2t](https://www.2b2t.org/): a server running since 2010 whose world has welcomed over a million players. For one player or a small group, exploring a whole world that size is out of reach.

Orbis is built around a different idea: a world small enough to get to know. Discover its landscapes, find your favourite places, and gradually make the whole world familiar. Each seed gives you a different world to call home.

Choose a size and keep exploring. When you cross an edge, you continue on the opposite side of the same world.

## How Orbis works

Imagine a square map. Join its top and bottom edges to make a cylinder, then connect the two open ends to make a torus. Both pairs of opposite edges are now neighbours.

![A square Minecraft-style landscape folds into a cylinder and then a torus. The map on the right stays unchanged.](docs/media/orbis-torus.gif)

The shape explains how the edges connect. In Minecraft, the ground and gravity stay normal. Only the two horizontal directions wrap.

### East and west

Walk across the blue edge and continue on the opposite side. Keep going and you arrive back where you started.

![Steve follows an east–west loop on the torus and the matching flat map.](docs/media/orbis-walk-x.gif)

### North and south

The gold edges connect in the same way. You can travel around the world in this direction too.

![Steve follows a north–south loop on the torus and the matching flat map.](docs/media/orbis-walk-z.gif)

### Diagonally

Each direction wraps independently. Here, Steve crosses the gold edge first, then the blue edge, while continuing along the same diagonal.

![Steve walks diagonally, crossing the two edge pairs at different times before returning to his starting point.](docs/media/orbis-walk-diagonal.gif)

## Features

Orbis is an early development alpha. These features are implemented; in-game validation is still pending. The animations above are illustrations.

- **A finite world without a wall.** The Overworld and Nether connect across both horizontal edges.
- **A size that suits your world.** Use the slider, choose Tiny, Small, Normal, Large or Earth, or enter an exact width. The world overview shows the area and dimension sizes before you apply them.
- **A connected Nether.** Its width stays eight times smaller than the Overworld, matching Minecraft's usual coordinate scale.
- **A familiar End.** The End keeps its normal, unwrapped behaviour.
- **An alpha atlas preview.** The optional debug build adds a corner minimap, Flat/Globe views, an M-key window, saved waypoints and biome/structure checklists. See [atlas installation and controls](docs/DEBUG_ATLAS.md).
- **Orbis cartography goals.** Three original advancements reward preparing a compass, map and field kit. See the [identity audit](docs/ORBIS_IDENTITY.md).

<!-- Replace each placeholder with an in-game capture when available. -->

| Exploring across an edge | Choosing a world size | Travelling through the Nether |
| --- | --- | --- |
| ![Placeholder for an in-game view across a world edge.](docs/media/screenshot-placeholder.svg) | ![Placeholder for the Orbis world-size selection screen.](docs/media/screenshot-placeholder.svg) | ![Placeholder for an in-game Nether journey.](docs/media/screenshot-placeholder.svg) |

## Compatibility

Orbis currently targets **Fabric**. Install Fabric Loader, Fabric API and the Orbis JAR. The current alpha targets Minecraft 26.3 and Java 25.

The following mods are priorities for compatibility testing. Their support in Orbis has **not yet been verified**.

<table>
  <tr>
    <td width="33%"><a href="https://modrinth.com/mod/fabric-api"><img src="https://cdn.modrinth.com/data/P7dR8mSH/icon.png" width="28" height="28" alt="" align="absmiddle"> Fabric API</a></td>
    <td width="33%"><a href="https://modrinth.com/mod/distanthorizons"><img src="https://cdn.modrinth.com/data/uCdwusMi/e716cda9bb568b5373ff76e363fb4c6d7278fba6_96.webp" width="28" height="28" alt="" align="absmiddle"> Distant Horizons</a></td>
    <td width="33%"><a href="https://modrinth.com/mod/iris"><img src="https://cdn.modrinth.com/data/YL57xq9U/18d0e7f076d3d6ed5bedd472b853909aac5da202_96.webp" width="28" height="28" alt="" align="absmiddle"> Iris Shaders</a></td>
  </tr>
  <tr>
    <td width="33%"><a href="https://modrinth.com/mod/sodium"><img src="https://cdn.modrinth.com/data/AANobbMI/295862f4724dc3f78df3447ad6072b2dcd3ef0c9_96.webp" width="28" height="28" alt="" align="absmiddle"> Sodium</a></td>
    <td width="33%"><a href="https://modrinth.com/mod/lithium"><img src="https://cdn.modrinth.com/data/gvQqBUqZ/bcc8686c13af0143adf4285d741256af824f70b7_96.webp" width="28" height="28" alt="" align="absmiddle"> Lithium</a></td>
    <td width="33%"><a href="https://modrinth.com/mod/ferrite-core"><img src="https://cdn.modrinth.com/data/uXXizFIs/222a126f26f8f9ae1eb339f3b767677f18bff31f_96.webp" width="28" height="28" alt="" align="absmiddle"> FerriteCore</a></td>
  </tr>
  <tr>
    <td width="33%"><a href="https://modrinth.com/mod/entityculling"><img src="https://cdn.modrinth.com/data/NNAgCjsB/7873452d6cede4daed12da3d7d8c193ab88b4fd6_96.webp" width="28" height="28" alt="" align="absmiddle"> Entity Culling</a></td>
    <td width="33%"><a href="https://modrinth.com/mod/modmenu"><img src="https://cdn.modrinth.com/data/mOgUt4GM/5a20ed1450a0e1e79a1fe04e61bb4e5878bf1d20.png" width="28" height="28" alt="" align="absmiddle"> Mod Menu</a></td>
    <td width="33%"><a href="https://modrinth.com/mod/litematica"><img src="https://cdn.modrinth.com/data/bEpr0Arc/25b5529d7a3b030ac136a6ce879d8ed2a1aa4a8d.png" width="28" height="28" alt="" align="absmiddle"> Litematica</a></td>
  </tr>
  <tr>
    <td width="33%"><a href="https://modrinth.com/mod/minihud"><img src="https://cdn.modrinth.com/data/UMxybHE8/4adf057a251f694983af139a06839e33bcd7a419.png" width="28" height="28" alt="" align="absmiddle"> MiniHUD</a></td>
    <td colspan="2"><a href="https://modrinth.com/mod/tweakeroo"><img src="https://cdn.modrinth.com/data/t5wuYk45/35af76cfb1d3074c5e8575d5f7385bb6c083c9d6.png" width="28" height="28" alt="" align="absmiddle"> Tweakeroo</a></td>
  </tr>
</table>

Fabric API is a required dependency. The other mods are separate installations. Exact versions and test results belong in the [compatibility notes](docs/COMPATIBILITY.md).

## World sizes

Choose a preset or use the size slider and exact chunk input. New selections range from Tiny to Earth, in steps of 16 chunks. Each width is the full side length of the square world. Normal is the default. One chunk is 16 × 16 blocks; areas assume one block equals one metre.

| Size | Overworld chunks per side | Overworld blocks per side | Area (km², rounded) |
| --- | ---: | ---: | ---: |
| Tiny | 1,024 | 16,384 | 268 |
| Small | 8,832 | 141,312 | 19,969 |
| Normal | 76,544 | 1,224,704 | 1,499,900 |
| Large | 625,008 | 10,000,128 | 100,002,560 |
| Earth | 1,448,160 | 23,170,560 | 536,874,851 |

These are provisional sizes. Smaller worlds may miss biomes or structures; no preset currently guarantees them all. Earth is experimental: its target is 2²⁹ km², about 5.27% above Earth's roughly 510 million km² surface area. Preset widths are rounded to the nearest 16 chunks. The slider uses a logarithmic scale to keep smaller sizes easy to select. Existing smaller alpha saves remain readable.

## Future ideas

Ideas for future versions; scope and implementation are still open.

- **Atlas refinements.** Build on the debug minimap with waypoint names, a larger exploration archive and multiplayer support.
- **Gentle world curvature.** An optional visual effect that lets you see a slightly curved horizon and atmospheric edge from high mountains or while flying. Building and gravity would keep their familiar Minecraft behaviour.
- **Exploration journal.** Keep a record of the biomes and landmarks you have visited. Gradually reveal your map and see how much of your finite world you have explored.

## World conversion

Existing-world conversion is planned. The idea is to choose an area of a world you already play, keep your builds, and connect its opposite edges by regenerating and blending selected strips of terrain.

The conversion would work on a copy, with a preview of the affected areas before making changes. It is not available in the current alpha. A biome check for new worlds is also planned.

## Try the alpha

Use a new test world. Build and installation instructions, known limitations and development documentation are in the [project guide](docs/IMPLEMENTATION.md).

## Inspiration and license

Orbis takes inspiration from [Toroidal World](https://modrinth.com/mod/toroidal-world) and [Circumnavigate](https://modrinth.com/mod/circumnavigate). Its world engine comes from Toroidal World under the MIT License. It ships as a standalone mod; no separate Toroidal World installation is needed.

Orbis is released under the [MIT License](LICENSE). Upstream credits and source details are recorded in [Sources and licensing](docs/SOURCES_AND_LICENSE.md).
