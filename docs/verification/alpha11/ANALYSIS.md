# First map fill: VoxelMap comparison and Orbis Alpha.11

Investigation date: 2026-10-09. Orbis baseline: Alpha.10, implementation `cc9490fe76cc37d87d6da6ae331f5d1fbce683f2`. User symptom: visible map fills slowly on the first visit but appears immediately after reconnecting; new construction does appear.

## Reference and scope

Inspected Updated-VoxelMap/VoxelMap at pinned commit [`dab1a50a7ae789f2671313b30db9c2662fd8c9b5`](https://github.com/Updated-VoxelMap/VoxelMap/tree/dab1a50a7ae789f2671313b30db9c2662fd8c9b5), its live minimap, chunk cache, persistent regions and worker scheduling. This is a source-level architectural comparison, **not** a VoxelMap versus Orbis FPS benchmark. No VoxelMap code or assets were copied.

### What VoxelMap actually does

| Area | Evidence in pinned source | Consequence |
| --- | --- | --- |
| Live map vs world archive | [`Map.run`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/Map.java#L275), [`CachedRegion.load`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CachedRegion.java#L205) | A dedicated live-map calculation loop and a separate persistent-region path. A nearby minimap does not require the whole saved world to be imported first. |
| Movement | [`Map.mapCalc`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/Map.java#L717) | Shifts existing image/map arrays; computes newly exposed strips. Recalculates fully when needed, such as large jumps, lighting or mode changes. |
| Changes | [`MapChunk.checkIfChunkChanged`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/util/MapChunk.java#L26), [`Map.processChunk`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/Map.java#L852) | Chunk load/unload/dirty state triggers a bounded rectangle update. Not-yet-loaded chunks are checked again. |
| Locality | [`MapChunkCache`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/util/MapChunkCache.java) | A viewport-sized chunk window reuses entries when the player moves. |
| Persistent data | [`CachedRegion.load`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CachedRegion.java#L205) | Loads cached region data, merges current chunks, then optionally reads local Anvil data. Modified chunk flags limit later updates. |
| Saving | [`CachedRegion.saveData`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/CachedRegion.java#L531), [`ThreadManager`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/persistent/ThreadManager.java#L12) | Dirty region data is saved through a separate executor; unchanged regions are not rewritten on every live-map check. |
| GPU upload | [`Map`](https://github.com/Updated-VoxelMap/VoxelMap/blob/dab1a50a7ae789f2671313b30db9c2662fd8c9b5/common/src/main/java/com/mamiyaotaru/voxelmap/Map.java#L1521) | Texture update is guarded by an image-changed flag. |

VoxelMap reads client chunk objects on its worker. That is not automatically a safe pattern for every Minecraft/mod combination. Orbis continues sampling live chunks on their owner thread and sends immutable color tiles to workers. Its saved-reader path does not install, generate or request live world chunks.

## Where Orbis was spending work

Singleplayer path: integrated-server chunk events -> collector (32 chunks/tick, soft 2 ms budget) -> immutable snapshot -> minimap worker/raster -> texture. Separately: saved chunk NBT -> full `SerializableChunkData.parse` -> surface color scan -> tile archive -> projection. Multiplayer uses received client chunks, without access to server save files.

1. **Proven starvation in the live collector.** Every 200 ticks Alpha.10 reinserted *all* loaded chunks into the same nearest-first queue. If initial work did not complete in that interval, nearby already-captured chunks displaced missing outer chunks again. A deterministic 33 x 33 test with a simulated 2.000001 ms capture cost observed only **200/1,089 unique chunks after 1,200 ticks**. This is a scheduling regression test, not a measured user-world completion time.
2. **Unnecessary saved-chunk decoding.** `SerializableChunkData.parse` decodes all sections, biomes, lighting and further chunk metadata. Alpha.10 then searched down each column for visible colors. The map only needs block palettes intersecting the surface (or the fixed Nether slice). A previous Alpha.10 JFR had saved parsing/sampling among Orbis execution samples, but those sample counts alone do not measure total wall time.
3. **Live pixels could lose to older disk pixels.** `PlayerLocalTerrain` published an empty loaded set. The existing raster therefore preferred archived colors over current live colors until the archive caught up. Visible edits should not depend on the disk writer completing.
4. **Unchanged live captures were sent back to disk.** The collector suppressed identical history revisions but still sent identical tiles to the archive queue. Disk code opened/read the relevant tile before rejecting unchanged content.
5. **Cold and warm work differ.** Rejoining reuses color tiles instead of decoding original saved chunks again. This explains why reconnect can help; it does not imply that first-fill delays are unavoidable. The current fingerprint includes mod versions, including Orbis, so a version upgrade can still cause one new cold import. Alpha.11 does not weaken world/generator/datapack cache identity to hide that cost.

## Implemented in Alpha.11

- Separate first-capture and refresh queues. A refresh cycle finishes before a new one starts. First captures have priority; one in eight captures remains available for refresh, so construction can update during long exploration. Same soft 2 ms / 32 chunk owner-thread budget.
- A temporary unavailable chunk is retried without spinning on it in the same tick or reinserting it ahead of all first captures.
- Publish immutable loaded membership, with its own revision. Both flat and globe cache keys account for it, even when chunk outlines are hidden. Live pixels take precedence over old archive pixels.
- Send only new or changed live tiles to persistence.
- New detached saved-surface reader uses `WORLD_SURFACE` heights and lazily decodes only needed block palettes. No biome/light/entity decoding. Missing or malformed heightmaps fall back to scanning available sections. Nether remains a Y=32 slice. Transparent surfaces retain the bounded 16-block search and original Minecraft map colors.
- Preserve the Alpha.10 decoder **only in test sources** for pixel and timing comparisons.

## Verification design

`AtlasSavedSurfaceBenchmark` reads 512 real Overworld and 512 real Nether FULL chunk NBT records from the local test save. It compares every color against the Alpha.10 full-parser/reference sampler, then alternates old/new decode order across timing rounds. Disk reads, registry bootstrap and initial comparison occur before timed rounds. This measures decode + surface extraction, **not** network arrival, disk I/O, archive writing, GPU time or end-to-end map fill. Round zero is warm-up; host scheduling/GC make timings variable. Exact output is retained alongside results.

Normal regression tests cover unavailable chunk retry, slow initial capture, continued refresh while initial capture is pending, suppression of unchanged disk writes, immutable loaded snapshots, cache invalidation, negative heights, transparent blocks, missing/malformed heightmaps, Nether slice isolation and live-over-archive raster precedence.

## Deliberately separate follow-up work

VoxelMap's event-driven dirty rectangles and sliding per-pixel images remain useful designs for a later live-render optimization. Orbis already caches/pads the minimap texture and skips identical uploads; this investigation targets **first data arrival**, the symptom reported here. Replacing globe projection or blindly increasing server-thread budgets would not repair collector starvation or unnecessary saved NBT decoding.

Other remaining costs: sequential Minecraft storage futures, per-tile archive file opens/global file lock, and client/server chunk availability. The persistent importer and foreground collector remain distinct; the game cannot map remote chunks that the server has never sent. The test save/hardware cannot establish the cause or a guaranteed fill time for the user's exact modpack/world, which has not been supplied.
