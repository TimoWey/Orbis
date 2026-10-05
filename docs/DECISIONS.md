# Decisions

Updated: 2026-10-05. The latest user decision takes precedence.

| Decision | Value |
| --- | --- |
| Name / foundation | Orbis / Toroidal World |
| Platform | Fabric; latest stable Java Edition at implementation start |
| Initial scope | Singleplayer |
| Required mods | Distant Horizons, Sodium, Lithium |
| Dimensions | Periodic Overworld/Nether; fixed 1:8; unchanged End |
| Size structure | Five tiers plus an Earth profile |
| Small worlds | Missing biomes are acceptable; revise dimensions |
| Normal | Complete Overworld and Nether biome coverage as a goal |
| New world | Biome check; clear warning for missing or uncertain coverage |
| Existing world | Convert a copy; regenerate and blend selected edge chunks |
| Documentation | Concise tables; distinguish goals from tested features |
| Language | Keep documentation, animation labels and proposed UI text in English |
| Explainer | Square → cylinder → torus; X, Z and offset diagonal walks |

## Open

Final dimensions, minimum biome areas, scan depth/duration, target success rate, performance budgets, source save, exact version matrix and project license.

## Superseded

- Planet-scale Small/Medium/Large: replaced by five regular tiers plus Earth.
- Final profiles of 32/64/128/256/512 chunks: reopened after the biome-coverage discussion.
- A 4- or 8-chunk-wide Nether as a development target: replaced for now by a larger minimum.
- Dedicated multiplayer in the first release: excluded.

## Repository

Private repository: [TimoWey/Orbis](https://github.com/TimoWey/Orbis), created on 2026-10-05. Contains documentation, calculations and an explanatory animation; no upstream code imported and no playable mod.
