# World sizes and biome coverage

## Current status

The alpha uses four one-word tiers: **Tiny, Small, Normal, Large**, plus **Earth**. All widths below are full side lengths, not radii. Normal is the default. New selections use a logarithmic size slider, preset buttons or exact chunk input, bounded by Tiny and Earth. Biome and gameplay validation remain open.

New menu selections require **at least 1,024 chunks per side (Tiny)** and at most **1,448,160 (Earth)**; typed values go down to 128 Overworld chunks (16 Nether chunks) for testing. Worlds created with the old engine of earlier alpha builds keep their saved width. Widths remain multiples of 16 for exact centred Nether mapping.

## Current presets — no measured biome success rates

| Preset | Overworld chunks/axis | Overworld blocks/axis | Nether chunks/axis | Nether blocks/axis |
| --- | ---: | ---: | ---: | ---: |
| Tiny | 1024 | 16384 | 128 | 2048 |
| Small | 8832 | 141312 | 1104 | 17664 |
| Normal | 76544 | 1224704 | 9568 | 153088 |
| Large | 625008 | 10000128 | 78126 | 1250016 |
| Earth | 1448160 | 23170560 | 181020 | 2896320 |

Nether area = Overworld area / 64. A square world with C chunks per axis contains C² chunks and has a horizontal area of C² × 256 m² (1 block = 1 metre). Existing saved bounds are not resized by changing presets.

The preset targets are Tiny's fixed 1,024-chunk side, Small 20,000 km², Normal 1,500,000 km², Large 100,000,000 km² and Earth 2²⁹ km². Small and Normal give roughly even multiplicative area steps between Tiny and Large. Area targets are converted to square side lengths and rounded to the nearest 16 chunks; the menu displays the actual area, not the ideal target. Custom input supports every aligned value between Tiny and Earth. The slider is continuous between presets, with logarithmic width mapped onto that same grid.

## Stronghold ring sizing

Tiny extends ±8,192 blocks (±512 chunks) from the origin, making its full side 16,384 blocks (1,024 chunks). It encloses the first two nominal Java Stronghold rings completely, with about 2,304 blocks of clearance beyond ring two's nominal outer radius of 5,888 blocks. These are approximate placement bands; biome searches and the structure footprint affect actual positions.

Small, Normal, Large and Earth contain all eight nominal rings (the outermost reaches roughly 24,320 blocks / 1,520 chunks in radius). Larger presets add area but no additional vanilla Stronghold rings. The rings are centred on X=0, Z=0, not necessarily the player spawn.

Complete, reachable portal rooms still require gameplay validation. See [Cubiomes' ring placement logic](https://github.com/Cubitect/cubiomes/blob/master/finders.c#L793).

## Choosing a suitable Normal size

- Use the actual Orbis generator; vanilla seed maps are insufficient.
- Explicitly fix Minecraft and mod versions.
- Include every naturally generating biome in the selected version, including variants and caves. Define minimum usable biome area separately.
- Sample random seeds; success requires complete evidence in **both** dimensions.
- Coarse scans speed up searches but missing samples do not prove absence. Confirm locations against terrain/caves.
- Check each new world individually too; preserve existing-world seeds.

**No seed simulation has been performed.** Rare, correlated biomes prevent a credible 99%-coverage size from being calculated from chunk count alone.

## Calculated validation effort

Proposed target: at least 99% joint coverage. For a configuration fixed in advance and an independent validation sample with zero failures, the exact one-sided 95% lower confidence bound is p = 0.05^(1/n).

| Target lower bound | Required worlds with zero failures |
| ---: | ---: |
| 95% | 59 |
| 99% | 299 |
| 99.9% | 2995 |

This is experimental planning, not an Orbis measurement or a guarantee for every world. Fix the configuration before final validation; do not discard failures. Separate 99% rates per dimension do not establish 99% joint coverage.

## Earth

The chosen target is **2²⁹ km² = 536,870,912 km²**, rather than an exact match to Earth's approximately 510 million km² including oceans. With 1 block = 1 metre:

- Rounded width: **1,448,160 chunks = 23,170,560 blocks per axis**.
- Total Overworld: **2,097,167,385,600 chunks**.
- Actual area: **536,874,850.7136 km²** (about +0.000734% relative to the target).
- Nether: **181,020 chunks per axis**.
- Extent: **±11,585,280 blocks**, within vanilla's coordinate limits; runtime gameplay validation remains pending.

This is an approximate Earth-scale area, not a geographic Earth map. Generate on demand; retain 64-bit area arithmetic and validate large coordinates in game.

[Calculation](../analysis/biome_sizing.py) · [Sources](SOURCES_AND_LICENSE.md)

## Development alpha

The alpha offers widths of 1,024, 8,832, 76,544, 625,008 and 1,448,160 chunks. Nether widths are always divided by 8. These presets are not a result of biome measurements; Normal also carries a coverage warning. Cancelling the menu leaves the original selection untouched; invalid text cannot be applied with Done.

In world creation, set **World shape** to **Orbis**, then **Customize**. The size controls and live world overview sit side by side on wide windows and stack on smaller windows. The active preset is underlined; custom widths are labelled Custom. Exact input stays visible, with validation beside the field. The overview displays actual square kilometres, both dimension widths and the unchanged End. Done and Cancel stay in the footer while long content scrolls. Resizing preserves the entered value. Native Minecraft controls provide keyboard navigation and narration; visual and interaction testing in game is still required.
