# Implementation status — 0.1.0-alpha.1

Orbis is a Fabric extension of **Toroidal World 0.19.3**, not a renamed copy. Install both mods. The engine remains separately licensed and credited.

## Implemented

- Own mod ID `orbis` and World Shape: **Orbis** in new-world creation.
- Five provisional size presets, experimental Earth, and a validated custom width.
- Square Overworld with X/Z wrapping through the engine; exact 1:8 Nether widths and vanilla coordinate scaling.
- Explicit unwrapped End. An identity-shaped engine generator prevents automatic dimension wrapping; it does not impose finite End bounds.
- World identity and bounds stored in the generator codec, with restoration on recreate.
- English customization screen, biome disclaimer on join, and `/orbis` information command.
- Regression tests for bounds, seam math, serialization and shape ownership.

## Validation

[GitHub Actions build](https://github.com/TimoWey/Orbis/actions/runs/37305137050) passed on 2026-10-05: Java compilation, JAR packaging and all 22 Fabric-loaded regression cases. These exercise the pinned engine, including the ownership mixin; they are not gameplay tests.

Still required before a playable release: create/save/reload/recreate a world; cross X, Z and offset corners in both dimensions; verify portals and End gateways; repeat with Sodium, Lithium and Distant Horizons together. Record exact versions and rendering distances in the compatibility matrix.

## Not implemented or verified

- Actual biome coverage scan, statistical size guarantees, and existing-world conversion/blending.
- Singleplayer visual/gameplay validation, portal placement tests, and the combined Sodium/Lithium/DH test matrix.
- Earth-sized runtime/performance validation. No full-world arrays or pregeneration are used by Orbis.

The mod is an early development alpha, not completion of the requirements. Start with new test worlds. Orbis does not offer a command to convert or rewrite existing saves.

## Build / install

Java 25 is required to run Gradle and Minecraft. Run `bash gradlew build` (Windows: `gradlew.bat build`). GitHub Actions performs the same build and publishes the JAR and test reports as workflow artifacts. Use the ordinary JAR, not the `-sources.jar`.

Download **orbis-fabric-26.3** from the successful run's **Artifacts** section, unzip it, and place `orbis-0.1.0-alpha.1.jar` in the test instance's `mods` folder. GitHub sign-in is required for this private repository. CI artifacts expire; rebuild from source if the download has expired.

Install Fabric Loader 0.19.5 for Minecraft 26.3, Fabric API 0.161.0+26.3, Toroidal World 0.19.3+mc26.3 and Orbis. Create a new world, choose **World Shape: Orbis**, then **Customize**. Selecting upstream's separate **Toroidal** option uses upstream rules, not Orbis rules.

## Pins / provenance

| Component | Pin |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader / API | 0.19.5 / 0.161.0+26.3 |
| Fabric Loom / Gradle | 1.17.21 / 9.7.0 |
| Toroidal World runtime | 0.19.3+mc26.3; Modrinth version `9VVTx2W8` |
| Reviewed upstream source | `060080683bdc0e198da1fc32502dd3f62875413c` |

The runtime dependency is deliberately exact. `TorusOwnershipMixin` patches the pinned engine's generic torus reader, which otherwise claims all two-axis worlds before foreign modules can restore their own settings. It returns null only for an Orbis-marked world. This is the only production dependency on upstream internals; re-review it before changing engine versions.

Orbis uses the public shape/option/generation API for other operations. The test bootstrap is adapted from upstream under MIT, retained in `licenses/Toroidal-World-MIT.txt`. Gradle's generated wrapper retains its Apache-2.0 headers.
