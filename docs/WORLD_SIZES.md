# World sizes and biome coverage

## Current status

The five tier dimensions are **open again**. The previous 32/64/128/256/512-chunk sequence is no longer binding. Earth remains an additional option.

The documented upstream **minimum Nether size** is 16 × 16 chunks. With fixed 1:8 scaling, the minimum Overworld is **128 × 128 chunks = 2,048 × 2,048 blocks**. This does not guarantee biome coverage.

## Calculated candidates — no measured success rates

| Overworld chunks/axis | Overworld blocks/axis | Nether chunks/axis | Nether blocks/axis | Purpose |
| ---: | ---: | ---: | ---: | --- |
| 128 | 2048 | 16 | 256 | Minimum / Very-small candidate |
| 256 | 4096 | 32 | 512 | Small candidate |
| 512 | 8192 | 64 | 1024 | Normal measurement candidate |
| 1024 | 16384 | 128 | 2048 | Normal measurement candidate |
| 2048 | 32768 | 256 | 4096 | Normal measurement candidate |
| 4096 | 65536 | 512 | 8192 | Normal measurement candidate |

No assignment is final. Width is not radius. Nether area = Overworld area / 64. A square world with C chunks per axis contains C² chunks.

## Choosing a suitable Normal size

- Use the actual Toroidal/Orbis generator; vanilla seed maps are insufficient.
- Explicitly fix Minecraft/mod versions and biome compression. Upstream provides off/auto/strong/custom; its shipped presets use off.
- Include every naturally generating biome in the selected version, including variants and caves. Define minimum usable biome area separately.
- Sample random seeds; success requires complete evidence in **both** dimensions.
- Coarse scans speed up searches but missing samples do not prove absence. Confirm locations against terrain/caves.
- Check each new world individually too; preserve existing-world seeds.

**No seed simulation has been performed.** Rare, correlated biomes prevent a credible 99%-coverage size from being calculated from chunk count alone. Biome compression improves climate diversity but does not guarantee every biome.

## Calculated validation effort

Proposed target: at least 99% joint coverage. For a configuration fixed in advance and an independent validation sample with zero failures, the exact one-sided 95% lower confidence bound is p = 0.05^(1/n).

| Target lower bound | Required worlds with zero failures |
| ---: | ---: |
| 95% | 59 |
| 99% | 299 |
| 99.9% | 2995 |

This is experimental planning, not an Orbis measurement or a guarantee for every world. Fix the configuration before final validation; do not discard failures. Separate 99% rates per dimension do not establish 99% joint coverage.

## Earth

Assuming 510 million km² including oceans and 1 block = 1 metre:

- Ideal: 1,992,187,500,000 chunks; square width about 22,583,179.581 blocks.
- Rounded: **1,411,456 chunks = 22,583,296 blocks per axis**.
- Total Overworld: **1,992,208,039,936 chunks**.
- Nether: **176,432 chunks per axis**.
- Area difference from the assumption: about +0.001031%.

Equal area only, not an Earth map. Generate on demand; verify 64-bit area arithmetic and large coordinates.

[Calculation](../analysis/biome_sizing.py) · [Sources](SOURCES_AND_LICENSE.md)
