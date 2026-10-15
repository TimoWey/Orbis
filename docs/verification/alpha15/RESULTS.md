# alpha.15: map, globe and Distant Horizons (2026-10-15)

Cloud client game tests, software rendering (llvmpipe), 128-chunk debug world. Absolute frame times say little here; compare the rows.

## Distant Horizons past the seam

`DistantHorizonsClientGameTest` with `-PorbisCompat=true` (Sodium 0.9.2, Lithium 0.26.2, DH 3.3.4). After flying over the middle of the world, the player flies north over the seam to client z -1700 (server z 348). DH's API is asked for the column at the world's middle (0, 0) and at its image one world width north (0, -2048).

| | column 0, 0 | column 0, -2048 | far view north past the seam |
| --- | --- | --- | --- |
| Orbis DH support on | 22 points | 22 points | far terrain continues ([shot](dh-past-seam-on.png)) |
| `ORBIS_DH_TORUS=off` | 22 points | 0 points | empty ([shot](dh-past-seam-off.png)) |

## Frame times

`MapPerfClientGameTest`, run alone, 300 ticks per case, player moving. Minimap ms is the minimap's own time on the render thread.

| Case | main b4fc6e5: fps, p99 frame ms, minimap ms mean / max | alpha.15 |
| --- | --- | --- |
| Minimap off | 28.6, 48.6, 0.00 / 0.17 | 28.4, 49.1, 0.01 / 0.04 |
| Minimap flat | 25.6, 65.8, 0.23 / 25.4 | 25.6, 65.8, 0.16 / 2.4 |
| Minimap globe | 27.7, 55.0, 4.66 / 14.9 | 28.7, 51.9, 0.20 / 2.9 |
| Map open, flat, dragged | 24.4, 66.9 | 26.5, 60.0 |
| Map open, globe, dragged | 16.8, 99.5 | 24.6, 68.1 |

`MapClientGameTest` passes. Not yet checked in real play on a GPU.
