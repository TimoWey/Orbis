# Orbis – Modrinth project draft, version 2 (proposal, not yet applied)

Status: **draft only, do not submit for review.** Changes against `../listing.md` are marked (new) or (changed).

## Basic settings

| Field | Value |
| --- | --- |
| Name | Orbis |
| Slug | `orbis-worlds` (unchanged) |
| Summary (changed) | Finite Minecraft worlds with no wall: walk off one edge and arrive on the opposite side. Terrain, mobs, structures, portals and light all continue across the seam. |
| Gallery order (changed) | 1 `anim-torus.gif` (featured), 2 `anim-walk-x.gif`, 3 `anim-walk-z.gif`, 4 `anim-walk-diagonal.gif`, 5 `gui-atlas-map.png`, 6 `gui-minimap.png` (landscape shots are optional, in `extra-landscapes/`) |
| Everything else | unchanged (categories Worldgen + Adventure, MIT, client optional, server required, links stay empty while the repo is private) |

## Description (paste into the Modrinth description editor)

The images are uploaded to the gallery first; their `cdn.modrinth.com` URLs replace the `URL_*` placeholders (`apply_modrinth.py` does this).

```markdown
**Finite Minecraft worlds. Seamless exploration.**

Minecraft Java worlds span roughly 60 million × 60 million blocks. For one player or a small group, a world that size is out of reach.

Orbis is built around a different idea: a world small enough to get to know. Discover its landscapes, find your favourite places, and gradually make the whole world familiar. Choose a size and keep exploring. When you cross an edge, you continue on the opposite side of the same world.

![A square landscape folds into a cylinder and then a torus. The map on the right stays unchanged.](URL_TORUS)

![Orbis Atlas: switch between map and globe and turn the globe to see how the world connects.](URL_ATLAS_GIF)

## How it works

Imagine a square map. Join its top and bottom edges to make a cylinder, then connect the two open ends to make a torus. Both pairs of opposite edges are now neighbours. In Minecraft, the ground and gravity stay normal; only the two horizontal directions wrap. Walk east long enough and you arrive back where you started. The same holds north–south and diagonally.

### East–west, north–south and diagonal

![Steve walks one east–west lap on the torus and on the flat map.](URL_WALK_X)
![Steve walks one north–south lap.](URL_WALK_Z)
![Steve walks diagonally, crossing both edge pairs at different times.](URL_WALK_DIAGONAL)

## An edge you cannot see

Orbis does not paint a wall at the edge or teleport you. The world on both sides of the seam is simply one world, so everything keeps working across it:

- **Terrain and light.** Blocks, lighting and water look and behave the same on both sides of the seam.
- **Mobs and combat.** Zombies chase you across it, skeletons and other ranged mobs aim across it, hits and knockback point the right way.
- **Villages, raids and spawning.** A village can straddle the seam, villagers find beds and workstations behind it, raids and natural spawning work on both sides.
- **Redstone, explosions and fluids.** TNT, pistons, minecarts, boats, lava and water do not care where the seam is.
- **Structures.** Dungeons, mineshafts and fortresses can cross the seam; `/locate` finds them across it.
- **Travel.** Walking, elytra, ender pearls, tridents, `/tp` and Nether portals all work across the seam, in every direction and diagonally through the corner.
- **Saving.** Quit and reload right next to the seam; the world is still there.

## Features

- **A finite world without a wall.** The Overworld and Nether connect across both horizontal edges.
- **A size that suits your world.** Pick Tiny, Small, Normal, Large or Earth, use the slider, or enter an exact width.
- **A connected Nether.** Its width is always exactly one eighth of the Overworld's, matching Minecraft's usual coordinate scale, so portals line up.
- **A familiar End.** The End keeps its normal, unwrapped behaviour.
- **A map that knows the edges.** The corner minimap and the Atlas (press **M**, the key can be changed under Controls) keep you in the centre and continue with the other side of the world. The Atlas has a flat map and a smooth 3D globe, zooms, shows relief, and keeps the terrain you explored, even after a restart or on a server. Other players in your dimension appear as markers.
- **Server ready.** One JAR for server and players. Every player needs Orbis on an Orbis world; anyone without it is turned away with a message naming the version to install. Joining shows one line: world size, that the edges connect, and the map key. Operators can run `/orbis check` (English or German) to look for the expected biomes and structures. Pre-generating with Chunky works.
- **Works with the big performance mods.** Sodium, Lithium, Distant Horizons and C2ME are tested with Orbis, and Distant Horizons' far terrain continues past the world's edges. See Compatibility below.

## The map

Orbis ships with its own map, the **Atlas**. A small **minimap** sits in the corner of your screen. Press **M** to open the full Atlas, which shows everything you have explored so far, in singleplayer and on servers.

![Minimap in the corner of the screen.](URL_MINIMAP)

The Atlas fills the screen, with a few controls on it. Switch between the two views with the **Map** and **Globe** buttons at the top left:

- **Map:** the flat overview, shaded with relief and water depth. Drag to move and scroll to zoom. In the gear panel, **Map: continued** shows the world going on past its edges, and **Map: one square** shows the world once.
- **Globe:** the same world as a sphere, which shows best that the edges connect. Drag to turn the globe and scroll to zoom. **Centre** brings you back to your position.

The atlas shows the block coordinates under the mouse, or your position. The gear also opens the minimap settings. The map key can be changed under Controls.

![Switching between map and globe, and turning the globe.](URL_ATLAS_GIF)

![The atlas in map view.](URL_ATLAS_MAP)

## World sizes

| Size | Blocks per side | Area |
| --- | ---: | ---: |
| Tiny | 16,384 | ~268 km² |
| Small | 141,312 | ~19,969 km² |
| Normal (default) | 1,224,704 | ~1.5 million km² |
| Large | 10,000,128 | ~100 million km² |
| Earth (experimental) | 23,170,560 | ~537 million km² |

Smaller worlds may miss biomes or structures; no preset currently guarantees them all.

## Getting started

1. Install Fabric Loader 0.19.5+ for Minecraft 26.3 (Java 25) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put the Orbis JAR in your `mods` folder.
3. Create a **new** world, set **World shape** to **Orbis** on the World tab, then **Customize** to choose the size.

Worlds from earlier Orbis alphas (such as alpha.11) open again; their saved terrain, seed and size stay as they are, and newly generated chunks come from the new engine and may not line up with old terrain next to them. Orbis does not convert or resize other existing worlds.

## Compatibility

Orbis is a **Fabric** mod for Minecraft 26.3 (Java 25). Fabric API is required. The mods below were run together with Orbis in real game sessions, and the seam and map tests pass with all of them installed at once, except where a row says otherwise.

| Mod | Tested version | Result |
| --- | --- | --- |
| Fabric API | 0.163.0+26.3 | Works (0.161.0+26.3 is the minimum Orbis is built against) |
| Sodium | 0.9.2, 0.9.3-beta.1 | Works |
| Lithium | 0.26.2 | Works. Orbis turns off Lithium's own point-of-interest search, which cannot see beds and workstations past the seam |
| Distant Horizons | 3.3.4 | Works. Far terrain continues past the world's edges |
| C2ME | 0.4.2-alpha.0.104 | Works. Chunks are generated in parallel across the seams |
| Iris Shaders | 1.11.7 | Loads and the seam tests pass; shader packs themselves were not tried |
| FerriteCore | 9.0.0 | Works |
| MiniHUD and Tweakeroo | 0.41.2, 0.30.1 (with malilib 0.30.2) | Load and the seam tests pass; their overlays and tools were not checked at the seam |
| Entity Culling | 1.11.3 | **Known problem:** with it installed, our seam test no longer sees a flying arrow cross the seam. The cause is not known yet |
| Chunky | 1.5.3 | Pre-generating a whole world works |

**Do not combine Distant Horizons and Chunky:** the game then hangs while creating a world, even a vanilla one (Distant Horizons warns about this itself). Other mods, such as Litematica and Mod Menu, are not tested yet.

## Alpha status

Orbis is an early alpha. The seam behaviour above is checked automatically in real Minecraft sessions at all four edges and both corners, in the Overworld and the Nether. Expect rough edges and back up anything you care about.

## Credits and license

Orbis is inspired by [Toroidal World](https://modrinth.com/mod/toroidal-world) and [Circumnavigate](https://modrinth.com/mod/circumnavigate). Orbis is released under the MIT License.
```

## Notes for Timo

- Matches alpha.21 (engine on main, C2ME, new atlas layout, old alpha worlds open again). Compatibility versions: Sodium 0.9.2, Lithium 0.26.2, Distant Horizons 3.3.4, C2ME 0.4.2.
- Draft only: do not submit for review and do not upload a version without Timo's word.
