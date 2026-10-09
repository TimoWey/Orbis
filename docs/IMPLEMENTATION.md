# Implementation status - 0.1.0-alpha.7

Orbis is a **standalone Fabric mod**. Its world engine is compiled from a pinned MIT source snapshot and ships inside the Orbis JAR. **Install Fabric Loader, Fabric API and Orbis. No Toroidal World mod is required.**

See [dedicated-server installation and the player minimap](SERVER.md) for Alpha.7.

## Implemented

- Own mod ID `orbis` and World Shape: **Orbis** in new-world creation.
- Logarithmic world-size slider, five preset shortcuts (Tiny, Small, Normal, Large, Earth), and exact chunk input. New selections range from 1,024 to 1,448,160 chunks per side in steps of 16. Normal is the default; see [World sizes](WORLD_SIZES.md) for areas.
- Square Overworld with X/Z wrapping through the engine; exact 1:8 Nether widths and vanilla coordinate scaling.
- Explicit unwrapped End. The generator is configured to prevent automatic dimension wrapping; after baking its shape may be null. It does not impose finite End bounds.
- World identity and bounds stored in the generator codec, with restoration on recreate.
- English/German customization screen with responsive size controls, a live world overview, selected preset marks and inline validation; biome disclaimer on join, and `/orbis` information command. Optional Mod Menu integration opens the integrated engine configuration with Orbis wording.
- Normal-build multiplayer minimap using only received client chunks; M toggles the HUD. Five dedicated-server presets (`orbis:tiny` through `orbis:earth`) with exact 1:8 Nether scaling and an unwrapped End.
- Optional debug-only corner minimap and non-pausing M atlas window, per-dimension flat/globe preferences and saved per-world waypoints: loaded server chunks, navigable flat map and globe projection, biome evidence checklist, small bounded biome checks, explicit Search+ and independent Map+ refinement, Y 32 Nether terrain, bounded structure existence checks and opt-in persistent full counts, observed structures and world statistics. See [Debug atlas](DEBUG_ATLAS.md).
- Regression tests for bounds, seam math, serialization and shape ownership. Alpha.6 additionally checks baked/restored dimension policy, preservation of unrelated custom dimensions, rejection of altered Nether coordinate scale, and periodic vanilla-generator terrain columns/density/biomes across X/Z and diagonal offsets. These generator tests do not exercise live entities, portals, structures or rendering.

## Validation

On 2026-10-09, Alpha.7 normal/debug Gradle builds passed 38/116 tests (zero failures; three optional debug benchmarks skipped), both distribution checks and the standalone verifier. A real local dedicated server accepted an Orbis client and a vanilla client simultaneously. The normal minimap, M/F1 controls, all six X/Z/corner seam crossings, unwrapped End coordinates and save/restart were checked. See the [full test record and screenshots](verification/alpha7/RESULTS.md) for artifact hashes, exact scope and environmental limits.

Alpha.6 adds normal/debug CI builds, verification of the exact binary and sources archives, deterministic source fingerprints and a compiled preset/specification consistency check. The build emits `META-INF/orbis-build.json` in both archives. These are automated build gates; they do not establish live gameplay correctness.

On 2026-10-07, all Alpha.6 production sources, including the debug atlas, compiled directly with Java 25 `javac`. The actual Fabric/JUnit engine ran **117 successful tests, zero failures and one skip**: the historical full-grid benchmark was skipped, while the raster benchmark was enabled. A separate normal-only production compilation and Fabric/JUnit run passed **37 tests with zero failures or skips**. Gradle/Loom could not build in the Windows sandbox because Java real-path resolution required parent-directory access; direct compilation and the test launcher succeeded. Both normal and debug artifacts passed the unchanged `verifyDistribution` and `verifyPresets` tasks through a temporary Java-only Gradle adapter using the directly compiled classes; this was not a normal Loom build. See [Alpha.6 validation](DEBUG_ATLAS.md#validation) for raster timings and their limits. The live gameplay checklist remains outstanding.

On 2026-10-07, Alpha.5 normal/debug builds passed 33/92 regression tests and both standalone packaging checks. The older opt-in sampler benchmark is skipped by default. Native quick-workflow tests cover Tiny and Large with the real generator; fixed-budget tests also cover Normal and Earth. Search/map progress and modes, pause/resume, caps, count opt-in and portal-room continuation have regression coverage. See [Alpha.5 results](DEBUG_ATLAS.md#alpha5-quick-search-validation-2026-10-07). No live gameplay/visual validation of this revision was performed.

On 2026-10-07, Alpha.4 normal/debug builds passed 33/80 regression tests and standalone packaging verification for both JARs. The optional sampler benchmark also passed when explicitly enabled (skipped in default test runs). The faster sampler matched 786,432 native biome queries exactly across all three dimensions. Per-dimension preferences, Nether tint rules, persistent deduplicated structure counts and old-cache rejection are tested. See [Debug atlas](DEBUG_ATLAS.md#historical-alpha4-sampler-benchmark-2026-10-07) for measured results and their limits. No new live gameplay or visual validation was performed.

On 2026-10-06, the revised alpha.3 atlas passed 33 normal-build tests and 69 debug-build tests with Java 25, plus standalone packaging checks on both clean-built JARs. Checks include persistent cache recovery/failure isolation, partial survey checkpoints, block-resolution terrain and LOD, single-world Flat bounds, moving-origin globe projection/inverse alignment, GUI layout geometry and waypoint persistence. Live Tiny-world re-entry, visual UI validation and gameplay performance remain pending; see [Debug atlas](DEBUG_ATLAS.md) for sources, cache limits and the interactive checklist.

[Standalone CI build](https://github.com/TimoWey/Orbis/actions/runs/37306724814) passed on 2026-10-05: source compilation, 25 regression cases and final-JAR inspection. The new checks initialize the real Orbis entrypoint, verify engine codec registration and confirm Toroidal World is not installed. Packaging checks verify every declared mixin and reject a separate or nested Toroidal World mod. These are not gameplay tests.

Still required before a playable release: create/save/reload/recreate a world; cross X, Z and offset corners in both dimensions; verify portals and End gateways; repeat with Sodium, Lithium and Distant Horizons together. Record exact versions and rendering distances in the compatibility matrix.

After the slider and area-preset update on 2026-10-05, local Java 25 release and debug builds passed all 32 and 40 regression cases respectively, plus standalone JAR packaging verification for both variants. Checks cover every preset, aligned intermediate widths, logarithmic slider round trips, area arithmetic, selection limits and legacy save widths. This does not verify the in-game menu, Stronghold or portal generation.

## Not implemented or verified

- Exhaustive biome coverage certification, statistical size guarantees, and existing-world conversion/blending. The optional debug atlas provides observations and a bounded sample, not proof of missing biomes.
- Singleplayer visual/gameplay validation, portal placement tests, and the combined Sodium/Lithium/DH test matrix.
- Earth-sized runtime/performance validation. No full-world arrays or pregeneration are used by Orbis.

The mod is an early development alpha, not completion of the requirements. Start with new test worlds. Orbis does not offer a command to convert or rewrite existing saves.

## Build / install

Java 25 is required to run Gradle and Minecraft. Run `bash gradlew build` (Windows: `gradlew.bat build`). The build runs regression tests, compares compiled presets against `spec/world-presets.json`, and verifies its exact binary and sources JARs. Verification uses Gradle's standard libraries and does not require a local Python installation. GitHub Actions builds both normal and debug variants and publishes separate JARs and test reports. Use the ordinary JAR, not the `-sources.jar`.

For the optional debug atlas, run `gradlew.bat build -PorbisDebug=true` and use `build/debug/libs/orbis-0.1.0-alpha.10-debug.jar` instead of the normal JAR. The normal build excludes the debug classes and entrypoint entirely. CI artifact names end in `-normal` or `-debug`; inspect their recorded revision before installing.

The normal build target is `build/libs/orbis-0.1.0-alpha.10.jar`. Place the verified output in the test instance's `mods` folder. `verifyDistribution` verifies exact Gradle archive paths, so older JARs left in the directory cannot be mistaken for the current artifact. `META-INF/orbis-build.json` records the version, variant, source SHA-256 and optional `-PorbisCommit=<commit>` (`GITHUB_SHA` in CI). The source fingerprint also changes for uncommitted input changes; a commit alone is not a claim that the working tree was clean. GitHub sign-in is required for this private repository.

Install Fabric Loader 0.19.5 for Minecraft 26.3, Fabric API 0.161.0+26.3 and Orbis. Create a new world, choose **World Shape: Orbis**, then **Customize**. Only Normal and Orbis are offered. If upgrading from alpha 1, remove the old Orbis and Toroidal World JARs first; simultaneous installation is explicitly rejected to prevent duplicate classes.

## Project documentation

- [Features and planned biome checks](FEATURES.md)
- [Mod compatibility](COMPATIBILITY.md)
- [World sizes and biome coverage](WORLD_SIZES.md)
- [World conversion](CONVERSION.md)
- [Requirements](REQUIREMENTS.md)
- [Architecture](ARCHITECTURE.md)
- [Roadmap](ROADMAP.md)
- [Decisions](DECISIONS.md)
- [Draft presets](../spec/world-presets.json)
- [Animation source](media/README.md)

## Pins / provenance

| Component | Pin |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader / API | 0.19.5 / 0.161.0+26.3 |
| Fabric Loom / Gradle | 1.17.21 / 9.7.0 |
| Engine distribution | Source compiled into Orbis; no separate runtime mod |
| Reviewed upstream source | `060080683bdc0e198da1fc32502dd3f62875413c` |

The immutable source archive, checksum and integration patches are recorded in [vendor/README](../vendor/README.md). The engine retains its original internal package and save identifiers for serialization continuity. It uses Orbis for runtime version lookup and an `orbis-engine.json` configuration file. The creation menu registers only Orbis and Normal; Alpha.6 removes the obsolete generic-Torus ownership guard. An Orbis-specific dimension policy preserves unrelated custom dimensions during derivation and retains existing stamps without silently migrating older saves. Overworld/Nether scale and aligned bounds are validated when reading baked/restored settings; the End remains explicitly unwrapped, including when its baked shape is null.

The upstream MIT copyright notice is included in the source archive and `META-INF/licenses` in the JAR. The test bootstrap is also adapted under that license. Gradle's generated wrapper retains its Apache-2.0 headers. MixinSquared is bundled as a library; optional performance and map mods remain separate, optional installations.
