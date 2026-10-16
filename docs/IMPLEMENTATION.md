# Implementation status — 0.1.0-alpha.17

Orbis is a **standalone Fabric mod** with its own world engine (`dev.orbis.engine`, generator `orbis:torus`). **Install Fabric Loader, Fabric API and Orbis.** Worlds created with the old engine of earlier alpha builds no longer load.

Server setup and map controls are in the [server and player guide](SERVER.md); per-version changes are in the [changelog](../CHANGELOG.md).

## Implemented

- Own mod ID `orbis` and a **World shape** row (Normal / Orbis) on the World tab of Create World, with **Customize** for the size. Re-creating a world starts from its shape. World types whose Overworld or Nether is not noise-based (Superflat, Debug) cannot become Orbis worlds; the row is greyed out.
- Logarithmic world-size slider, five preset shortcuts (Tiny, Small, Normal, Large, Earth), and exact chunk input. New selections range from 1,024 to 1,448,160 chunks per side in steps of 16; typed values go down to 128 chunks for testing. Normal is the default; see [World sizes](WORLD_SIZES.md) for areas.
- Square Overworld with X/Z wrapping through the Orbis engine; exact 1:8 Nether widths and vanilla coordinate scaling.
- Explicit unwrapped End. It does not impose finite End bounds.
- World identity and bounds stored in the generator codec, with restoration on recreate.
- English/German customization screen with responsive size controls, a live world overview, selected preset marks and inline validation; one-line message on join, and `/orbis` information command.
- Player map in the normal build: corner minimap and an **M** atlas with flat map and globe, drag and mouse-wheel zoom, saved settings (`config/orbis-atlas.properties`). It draws only chunks the client has seen and keeps them in a per-world cache, on servers too (keyed by an opaque world id the server sends); in singleplayer it also imports saved terrain and optional Distant Horizons data. Vanilla map markers show the player and, with Orbis on the server, all other players in the dimension.
- Dedicated-server presets `orbis:tiny` through `orbis:earth` with exact 1:8 Nether scaling and an unwrapped End. Every player needs Orbis; a game without it is turned away with an install message.
- Operator-only `/orbis check` with a fixed time budget for the expected Overworld/Nether biomes and structures; "not found" means unconfirmed, not proven missing.
- Separate debug build with a developer atlas: navigable flat map and globe projection, biome evidence checklist, small bounded biome checks, explicit Search+ and independent Map+ refinement, Y 32 Nether terrain, bounded structure existence checks and opt-in persistent full counts, observed structures and world statistics. See [Debug atlas](DEBUG_ATLAS.md).
- Regression tests for bounds, seam math, serialization and shape ownership. Alpha.6 additionally checks baked/restored dimension policy, preservation of unrelated custom dimensions, rejection of altered Nether coordinate scale, and periodic vanilla-generator terrain columns/density/biomes across X/Z and diagonal offsets. These generator tests do not exercise live entities, portals, structures or rendering.

## Validation

| Version | Date | Automated checks | In Minecraft | Record |
| --- | --- | --- | --- | --- |
| Alpha.12 | 2026-10-12 | CI on PR #4; new tests for the server map archive and world id | Not yet tested in Minecraft | [changelog](../CHANGELOG.md) |
| Alpha.11 | 2026-10-09 | Normal 144 passed / 3 optional skipped; debug 157 / 6 skipped; plus 3 DH adapter tests each; both JAR/sources verifications | Map build after a block edit, globe drag/recentre, cold and warm map fill timings | [alpha11](verification/alpha11/RESULTS.md) |
| Alpha.10 | 2026-10-09 | Normal 137 / 3 skipped; debug 151 / 5 skipped; plus 3 DH tests each | Dedicated server with an Orbis and a vanilla client: vanilla map cursors, distant player markers, drag/recentre, dimension change, logout | [alpha10](verification/alpha10/RESULTS.md) |
| Alpha.8 | 2026-10-09 | Normal 92 / 2 skipped; debug 143 / 5 skipped; plus 3 DH tests | Server atlas and globe in all three dimensions, `/orbis check` as operator and denied for non-operators, settings after restart | [alpha8](verification/alpha8/RESULTS.md) |
| Alpha.7 | 2026-10-09 | Normal 38 / debug 116 tests, distribution checks | Dedicated server with an Orbis and a vanilla client; all six X/Z/corner seam crossings, a real Nether portal at exact 1:8, unwrapped End, save and restart | [alpha7](verification/alpha7/RESULTS.md) |
| Alpha.2–6 | 2026-10-05 to 07 | Seam math, bounds, serialization, periodic generator columns across X/Z/diagonal offsets, preset/spec consistency | Debug atlas smoke tests | [debug atlas](DEBUG_ATLAS.md#validation) |

Every build runs the regression tests, compares compiled presets with `spec/world-presets.json`, and verifies the exact binary and sources JARs (`verifyDistribution`). `META-INF/orbis-build.json` records version, variant, source SHA-256 and commit. GitHub Actions builds and uploads the normal and debug variants separately.

Before calling a release playable, still check: Nether portals built near a seam, End gateways, singleplayer create/save/reload on a fresh world, and Sodium + Lithium + Distant Horizons together. Record exact versions in the [compatibility matrix](COMPATIBILITY.md).

## Not implemented or verified

- Not planned for now: existing-world conversion and a biome-coverage guarantee or certification. `/orbis check` and the debug atlas report a bounded sample, not proof that something is missing.
- Not yet verified: Nether portals placed near a seam, End gateways, the combined Sodium/Lithium/Distant Horizons matrix, and Large/Earth worlds in long play sessions. Orbis never pregenerates or allocates whole-world arrays.

Orbis is an early development alpha. Start with new test worlds; it does not convert or resize existing saves.

## Build / install

Java 25 is required to run Gradle and Minecraft. Run `bash gradlew build` (Windows: `gradlew.bat build`). The build runs regression tests, compares compiled presets against `spec/world-presets.json`, and verifies its exact binary and sources JARs. Verification uses Gradle's standard libraries and does not require a local Python installation. GitHub Actions builds both normal and debug variants and publishes separate JARs and test reports. Use the ordinary JAR, not the `-sources.jar`.

For the optional developer atlas, run `gradlew.bat build -PorbisDebug=true` and use `build/debug/libs/orbis-0.1.0-alpha.17-debug.jar` instead of the normal JAR. The normal build excludes the debug classes and entrypoint entirely. CI artifact names end in `-normal` or `-debug`; inspect their recorded revision before installing.

The normal build target is `build/libs/orbis-0.1.0-alpha.17.jar`. Place the verified output in the test instance's `mods` folder. `verifyDistribution` verifies exact Gradle archive paths, so older JARs left in the directory cannot be mistaken for the current artifact. `META-INF/orbis-build.json` records the version, variant, source SHA-256 and optional `-PorbisCommit=<commit>` (`GITHUB_SHA` in CI). The source fingerprint also changes for uncommitted input changes; a commit alone is not a claim that the working tree was clean. GitHub sign-in is required for this private repository.

Install Fabric Loader 0.19.5 for Minecraft 26.3, Fabric API 0.161.0+26.3 and Orbis. Create a new world, set **World shape** to **Orbis**, then **Customize**. Remove older Orbis JARs first.

## Project documentation

- [Changelog](../CHANGELOG.md)
- [Server and player guide](SERVER.md)
- [Features and scope](FEATURES.md)
- [Mod compatibility](COMPATIBILITY.md)
- [World sizes and biome coverage](WORLD_SIZES.md)
- [World conversion (parked)](CONVERSION.md)
- [Debug atlas](DEBUG_ATLAS.md)
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
| World engine | Orbis's own (`dev.orbis.engine`, generator `orbis:torus`); no separate runtime mod |

The access widener is `orbis.accesswidener`. Earlier alpha builds compiled an MIT engine snapshot; see [Sources and licensing](SOURCES_AND_LICENSE.md).

Gradle's generated wrapper retains its Apache-2.0 headers. Optional performance and map mods remain separate, optional installations.
