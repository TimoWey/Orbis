# Orbis

Finite Minecraft worlds. Seamless exploration.

**Status: 0.1.0-alpha.2 — standalone build and 25 regression tests pass. Gameplay and required-mod compatibility remain unverified.**

[Standalone JAR / build](https://github.com/TimoWey/Orbis/actions/runs/37306724814) · [Implementation PR](https://github.com/TimoWey/Orbis/pull/1)

**Installation: Fabric Loader + Fabric API + one Orbis JAR. No Toroidal World mod required.**

[Implementation, build and installation](docs/IMPLEMENTATION.md) — new worlds only. Biome scans and existing-world conversion are not implemented.

## How Orbis works

**Full sequence (55 seconds):** folding 0:00–0:17 · X 0:17–0:29 · Z 0:29–0:41 · diagonal 0:41–0:55. Separate movement loops are below.

![A square world becomes a torus, followed by X, Z and offset diagonal walks that return to the start.](docs/media/orbis-torus.gif)

**Square → cylinder → torus.** Opposite edges are direct neighbours. Walk one world width in X or Z and you return to the same position. No new copies of the world are created.

The diagonal example starts at **x = 0.25 L, z = 0.40 L** and crosses the two seams at different times. X and Z wrap independently. On this square world, a 45° route returns after moving L along each axis: a total distance of √2 L. Other angles need not return after this distance.

The torus illustrates connectivity. **In-game terrain and gravity stay normal; Y / height does not wrap.** This is an explainer, not gameplay footage.

[Play / pause the video](docs/media/orbis-torus.mp4) · [Animation and renderer](docs/media/README.md)

### Walk in X

![X movement: cross the right edge and reappear at the left edge, with Z unchanged.](docs/media/orbis-walk-x.gif)

### Walk in Z

![Z movement: cross the bottom edge and reappear at the top edge, with X unchanged.](docs/media/orbis-walk-z.gif)

### Walk diagonally from an offset start

![Offset diagonal movement: cross the Z seam first, then the X seam, and return to the start.](docs/media/orbis-walk-diagonal.gif)

| Decision | Target |
| --- | --- |
| Foundation | Standalone Orbis; integrated MIT-licensed engine source |
| Platform | Fabric; Minecraft 26.3, Java 25 |
| Mode | Singleplayer first |
| World | Overworld and Nether wrap along X and Z |
| Nether | Exact 1:8 coordinate and width ratio to the Overworld |
| End | Vanilla behaviour without Orbis bounds |
| Required mods | Distant Horizons, Sodium, Lithium |
| Existing worlds | Convert a copy; regenerate and blend selected edge strips |
| New worlds | Biome check with clear results and warnings |
| Sizes | Five provisional tiers plus experimental Earth; final sizes need biome measurements |
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
