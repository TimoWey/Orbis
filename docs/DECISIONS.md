# Decisions

Updated: 2026-10-15. The latest user decision takes precedence.

| Decision | Value |
| --- | --- |
| Name / foundation | Standalone Orbis / own world engine ([2026-10-15](#own-engine-only-2026-10-15)) |
| Installation | Fabric Loader + Fabric API + one Orbis JAR; no separate engine mod |
| Platform | Fabric; latest stable Java Edition at implementation start |
| Scope | Wrapping Overworld/Nether, world sizes, fixed 1:8 Nether, small player map; singleplayer and dedicated servers |
| Compatibility targets | Distant Horizons, Sodium, Lithium (optional installs; combined test still open) |
| Dimensions | Periodic Overworld/Nether; fixed 1:8; unchanged End |
| Size structure | Slider plus five shortcuts; Tiny 1,024 chunks minimum, Earth 2^29 km² target maximum; exact input in steps of 16 chunks; Normal is the default |
| Small worlds | Missing biomes are acceptable; revise dimensions |
| Normal | Complete Overworld and Nether biome coverage as a goal |
| New world | Warning that biomes may be missing; operator `/orbis check` reports what it could confirm |
| Existing world | Not converted; parked (see 2026-10-12) |
| Documentation | Concise tables; distinguish goals from tested features |
| Language | Documentation in English; in-game text in English and German |
| Explainer | Square → cylinder → torus; X, Z and offset diagonal walks |
| Joining a server | Orbis required on every client; a game without it is turned away with an install message ([2026-10-12](#orbis-required-2026-10-12)) |

## Open

Biome validation of the chosen dimensions, minimum biome areas, scan depth/duration, target success rate, performance budgets, source save, exact version matrix (MIT license selected).

## Superseded

- Planet-scale Small/Medium/Large: replaced by five regular tiers plus Earth.
- Initial five alpha tiers: replaced by four one-word tiers (Tiny, Small, Normal, Large) plus Earth on 2026-10-05.
- Final profiles of 32/64/128/256/512 chunks: reopened after the biome-coverage discussion.
- A 4- or 8-chunk-wide Nether as a development target: replaced for now by a larger minimum.
- Dedicated multiplayer in the first release: excluded. Reversed on 2026-10-09; Alpha.7 added server support.
- Biome-check screen and existing-world conversion as release requirements: parked on 2026-10-12.
- Vanilla clients joining an Orbis server: replaced on 2026-10-12 by Orbis on every client.
- Integrated MIT Toroidal World source as the engine: replaced on 2026-10-15 by Orbis's own engine.

## Repository

Private repository: [TimoWey/Orbis](https://github.com/TimoWey/Orbis), created on 2026-10-05. Contains documentation, calculations, explanatory animations and the Fabric alpha implementation.

## Initial alpha architecture — superseded (2026-10-05)

Use the pinned Toroidal World engine as a required dependency and build Orbis through its extension API. This replaces the earlier full-source import plan. One version-pinned ownership mixin preserves Orbis settings on recreate. Development presets are 128 / 256 / 1024 / 2048 / 4096 chunks plus Earth (1,411,456); these are not final biome-coverage recommendations.

## Standalone distribution (2026-10-05)

The user explicitly rejected requiring Toroidal World as a separate installed mod. Alpha 2 compiles the pinned MIT engine source into Orbis and owns its initialization and metadata. Users install one Orbis JAR plus Fabric API. Engine compatibility code and attribution remain; no second Toroidal World mod is embedded.

## Size presets and Stronghold rings — superseded (2026-10-05)

The user confirmed Tiny as ±4,096 blocks from the origin: 8,192 blocks or 512 chunks per full side. Small, Normal and Large are 1,024, 4,096 and 16,384 chunks per side. Earth stays separate and experimental; every preset label is one word. Normal is the initial selection. Tiny surrounds the nominal first Java Stronghold ring with about 1,280 blocks of clearance to each side. This is a geometric sizing decision, not a measured guarantee of biome coverage or portal generation. Existing saved dimensions and the custom-width minimum are unchanged.

## Area presets and bounded slider (2026-10-05)

The user revised Tiny to approximately 1,000 chunks per full side, Earth to a target of 2^29 km² (536,870,912 km²), and Large to approximately 100 million km². New selections must stay between Tiny and Earth. Presets are shortcuts, with a slider and exact input for intermediate values.

The implementation uses Tiny at 1,024 chunks, and provisional intermediate area targets of 20,000 km² for Small and 1.5 million km² for Normal. All target areas are converted to the nearest full side length divisible by 16 chunks, preserving exact centred Nether boundaries. The resulting widths are 1,024 / 8,832 / 76,544 / 625,008 / 1,448,160 chunks. Normal remains the default. The slider is logarithmic to keep small worlds practical to select. Legacy save widths down to 128 chunks remain readable, while new menu choices enforce the Tiny minimum. In-game validation remains pending.

## Focus (2026-10-12)

The user narrowed Orbis to four things: the torus wrap, selectable world sizes, the fixed 1:8 Overworld/Nether ratio and the small player map. Everything else (world conversion, a biome-check screen, visual extras) is parked. Orbis should stay small enough to maintain without upstream help.

## Orbis required (2026-10-12)

With its own engine, Orbis no longer rewrites vanilla packets so that unmodded games can play across the seams. Every player installs Orbis. While a player connects to an Orbis world, the server greets the client's Orbis; a game that cannot take the greeting is turned away with a message naming the Orbis version to install. An Orbis client with a different protocol leaves with both versions named. Worlds that are not Orbis worlds are unaffected.

## Upstream license change — superseded (2026-10-12)

Toroidal World was renamed Exotic Worlds on 2026-10-08 and switched from MIT to PolyForm Shield 1.0.0 on 2026-10-09 (upstream commit `8f417283`). PolyForm Shield forbids use in competing products. Orbis keeps its MIT snapshot `0600806`. Code from `8f417283` or later is never copied; fixes from that period may only be re-implemented independently. MIT-era commits before `8f417283` may still be ported with attribution.

## Own engine only (2026-10-15)

The owner decided that Orbis is completely independent. The vendored Toroidal World engine is removed; Orbis compiles, ships and calls none of its code, and its own engine (generator `orbis:torus`) is the only one. Worlds created with the old engine by earlier alpha builds no longer load. Toroidal World and Circumnavigate are credited in a single line at the end of the README; user-facing text mentions them nowhere else. `fabric.mod.json` keeps `breaks: toroidal_world` so both cannot be installed together.
