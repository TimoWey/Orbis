# Decisions

Updated: 2026-10-05. The latest user decision takes precedence.

| Decision | Value |
| --- | --- |
| Name / foundation | Standalone Orbis / integrated MIT-licensed Toroidal World source |
| Installation | Fabric Loader + Fabric API + one Orbis JAR; no separate engine mod |
| Platform | Fabric; latest stable Java Edition at implementation start |
| Initial scope | Singleplayer |
| Required mods | Distant Horizons, Sodium, Lithium |
| Dimensions | Periodic Overworld/Nether; fixed 1:8; unchanged End |
| Size structure | Slider plus five shortcuts; Tiny 1,024 chunks minimum, Earth 2^29 km² target maximum; exact input in steps of 16 chunks; Normal is the default |
| Small worlds | Missing biomes are acceptable; revise dimensions |
| Normal | Complete Overworld and Nether biome coverage as a goal |
| New world | Biome check; clear warning for missing or uncertain coverage |
| Existing world | Convert a copy; regenerate and blend selected edge chunks |
| Documentation | Concise tables; distinguish goals from tested features |
| Language | Keep documentation, animation labels and proposed UI text in English |
| Explainer | Square → cylinder → torus; X, Z and offset diagonal walks |

## Open

Biome validation of the chosen dimensions, minimum biome areas, scan depth/duration, target success rate, performance budgets, source save, exact version matrix (MIT license selected).

## Superseded

- Planet-scale Small/Medium/Large: replaced by five regular tiers plus Earth.
- Initial five alpha tiers: replaced by four one-word tiers (Tiny, Small, Normal, Large) plus Earth on 2026-10-05.
- Final profiles of 32/64/128/256/512 chunks: reopened after the biome-coverage discussion.
- A 4- or 8-chunk-wide Nether as a development target: replaced for now by a larger minimum.
- Dedicated multiplayer in the first release: excluded.

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
