# Requirements

Updated: 2026-10-05. Requirements are the target; current source and validation status are tracked in [Implementation](IMPLEMENTATION.md).

| ID | Priority | Requirement | Acceptance |
| --- | --- | --- | --- |
| R01 | Required | Standalone Orbis; integrate MIT-licensed Toroidal World source | One Orbis JAR; no separate or embedded Toroidal World mod; retain attribution |
| R02 | Required | Fabric; latest stable Java Edition at project start | Pin exact tool and game versions |
| R03 | Required | Singleplayer first | Create, play, save and reload |
| R04 | Required | Seamless Overworld/Nether wrapping in X and Z | All edges and corners correct; Y unchanged |
| R05 | Required | Exact 1:8 Nether scaling | Coupled bounds and correct portal mapping |
| R06 | Required | Vanilla End behaviour | No Orbis bounds; working portals and gateways |
| R07 | Required | Slider with Tiny, Small, Normal, Large and Earth shortcuts | Exact input between Tiny (1,024) and Earth (1,448,160 chunks per full side), in steps of 16; see [World sizes](WORLD_SIZES.md); biome coverage still requires validation |
| R08 | Required | Tiny/Small may lack biomes | Clear warning |
| R09 | Goal | Normal contains all naturally generating Overworld/Nether biomes | Check both dimensions, including variants and caves |
| R10 | Required | New-world biome check with transparent results | Distinguish complete, incomplete and not fully checked |
| R11 | Required | Distant Horizons + Sodium + Lithium | Test together with pinned versions |
| R12 | Required | Convert existing worlds | Preserve original; preview changes on a new copy |
| R13 | Required | Selectively regenerate and blend edge chunks | Preserve protected areas; avoid abrupt new terrain seams |
| R14 | Required | Preserve inventories, entities and world data | Verify integrity after conversion and restart |
| R15 | Required | Consistent simulation across seams | No duplication; test light, fluids, redstone, mobs and vehicles |
| R16 | Required | On-demand generation | No full pregeneration; no global area arrays for Earth |
| R17 | Required | Correct DH views and caches | Terrain and LOD agree across seams and after conversion |
| R18 | Required | Never automatically change existing-world seeds | Report missing biomes and offer options |
| R19 | Required | English documentation and user-facing text | English README, docs, animation and proposed UI labels |

**Open:** biome validation of the chosen dimensions; minimum usable area per biome; target coverage rate (99% proposed); existing-world source version; performance budgets.

**Outside version 1:** dedicated multiplayer, curved in-game donut geometry, a geographic Earth map and universal mod compatibility.
