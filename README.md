# Orbis

Finite Minecraft worlds. Seamless exploration.

**Status: planning. No playable mod, no upstream code imported, no gameplay tests performed.**

## How Orbis works

![A square world becomes a torus, followed by X, Z and offset diagonal walks that return to the start.](docs/media/orbis-torus.gif)

**Square → cylinder → torus.** Opposite edges are direct neighbours. Walk one world width in X or Z and you return to the same position. No new copies of the world are created.

The diagonal example starts at **x = 0.25 L, z = 0.40 L** and crosses the two seams at different times. X and Z wrap independently. On this square world, a 45° route returns after moving L along each axis: a total distance of √2 L. Other angles need not return after this distance.

The torus illustrates connectivity. **In-game terrain and gravity stay normal; Y / height does not wrap.** This is an explainer, not gameplay footage.

[Play / pause the video](docs/media/orbis-torus.mp4) · [Animation and renderer](docs/media/README.md)

| Decision | Target |
| --- | --- |
| Foundation | Independent development based on Toroidal World |
| Platform | Fabric; latest stable Minecraft Java release when implementation begins |
| Mode | Singleplayer first |
| World | Overworld and Nether wrap along X and Z |
| Nether | Exact 1:8 coordinate and width ratio to the Overworld |
| End | Vanilla behaviour without Orbis bounds |
| Required mods | Distant Horizons, Sodium, Lithium |
| Existing worlds | Convert a copy; regenerate and blend selected edge strips |
| New worlds | Biome check with clear results and warnings |
| Sizes | Five tiers plus Earth; five tier dimensions remain open |
| Language | English documentation, animation and proposed UI text |

## Documentation

- [Requirements](docs/REQUIREMENTS.md)
- [Features](docs/FEATURES.md)
- [Mod compatibility](docs/COMPATIBILITY.md)
- [World sizes and biome coverage](docs/WORLD_SIZES.md)
- [World conversion](docs/CONVERSION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Decisions](docs/DECISIONS.md)
- [Sources and licensing](docs/SOURCES_AND_LICENSE.md)
- [Draft presets](spec/world-presets.json)

Updated: 2026-10-05. “Planned” and “documented upstream” do not mean “tested in Orbis”.
