# Implementation status — 0.1.0-alpha.2

Orbis is a **standalone Fabric mod**. Its world engine is compiled from a pinned MIT source snapshot and ships inside the Orbis JAR. **Install Fabric Loader, Fabric API and Orbis. No Toroidal World mod is required.**

## Implemented

- Own mod ID `orbis` and World Shape: **Orbis** in new-world creation.
- Five provisional size presets, experimental Earth, and a validated custom width.
- Square Overworld with X/Z wrapping through the engine; exact 1:8 Nether widths and vanilla coordinate scaling.
- Explicit unwrapped End. An identity-shaped engine generator prevents automatic dimension wrapping; it does not impose finite End bounds.
- World identity and bounds stored in the generator codec, with restoration on recreate.
- English customization screen, biome disclaimer on join, and `/orbis` information command.
- Regression tests for bounds, seam math, serialization and shape ownership.

## Validation

[Standalone CI build](https://github.com/TimoWey/Orbis/actions/runs/37306724814) passed on 2026-10-05: source compilation, 25 regression cases and final-JAR inspection. The new checks initialize the real Orbis entrypoint, verify engine codec registration and confirm Toroidal World is not installed. Packaging checks verify every declared mixin and reject a separate or nested Toroidal World mod. These are not gameplay tests.

Still required before a playable release: create/save/reload/recreate a world; cross X, Z and offset corners in both dimensions; verify portals and End gateways; repeat with Sodium, Lithium and Distant Horizons together. Record exact versions and rendering distances in the compatibility matrix.

## Not implemented or verified

- Actual biome coverage scan, statistical size guarantees, and existing-world conversion/blending.
- Singleplayer visual/gameplay validation, portal placement tests, and the combined Sodium/Lithium/DH test matrix.
- Earth-sized runtime/performance validation. No full-world arrays or pregeneration are used by Orbis.

The mod is an early development alpha, not completion of the requirements. Start with new test worlds. Orbis does not offer a command to convert or rewrite existing saves.

## Build / install

Java 25 is required to run Gradle and Minecraft. Run `bash gradlew build` (Windows: `gradlew.bat build`). GitHub Actions performs the same build and publishes the JAR and test reports as workflow artifacts. Use the ordinary JAR, not the `-sources.jar`.

Download **orbis-fabric-26.3** from the successful run's **Artifacts** section, unzip it, and place `orbis-0.1.0-alpha.2.jar` in the test instance's `mods` folder. GitHub sign-in is required for this private repository. CI artifacts expire; rebuild from source if the download has expired.

Install Fabric Loader 0.19.5 for Minecraft 26.3, Fabric API 0.161.0+26.3 and Orbis. Create a new world, choose **World Shape: Orbis**, then **Customize**. Only Normal and Orbis are offered. If upgrading from alpha 1, remove the old Orbis and Toroidal World JARs first; simultaneous installation is explicitly rejected to prevent duplicate classes.

## Pins / provenance

| Component | Pin |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader / API | 0.19.5 / 0.161.0+26.3 |
| Fabric Loom / Gradle | 1.17.21 / 9.7.0 |
| Engine distribution | Source compiled into Orbis; no separate runtime mod |
| Reviewed upstream source | `060080683bdc0e198da1fc32502dd3f62875413c` |

The immutable source archive, checksum and integration patches are recorded in [vendor/README](../vendor/README.md). The engine retains its original internal package and save identifiers for serialization continuity. It uses Orbis for runtime version lookup and an `orbis-engine.json` configuration file. The prior ownership guard remains for direct reads through the engine API; the creation menu registers only Orbis and Normal.

The upstream MIT copyright notice is included in the source archive and `META-INF/licenses` in the JAR. The test bootstrap is also adapted under that license. Gradle's generated wrapper retains its Apache-2.0 headers. MixinSquared is bundled as a library; optional performance and map mods remain separate, optional installations.
