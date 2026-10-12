# Integrated world engine

Orbis builds its engine from the **MIT-licensed source** in `toroidal-engine-0600806.zip`. No Toroidal World mod is downloaded, nested or required at runtime.

| Provenance | Value |
| --- | --- |
| Upstream | https://github.com/anyttng/toroidal_world |
| Reviewed commit | `060080683bdc0e198da1fc32502dd3f62875413c` |
| Snapshot | `LICENSE`, `common/src/main`, `fabric/src/main` from that commit |
| SHA-256 | `8fc7a18de0dfcbefae77059c2f2636d1adb8c8bc177357706db40869ac0709f3` |
| Copyright | 2026 Vitalik Yarina |
| License | MIT; retained in the archive and distributable |
| Upstream today | Renamed Exotic Worlds; PolyForm Shield since commit `8f417283` (2026-10-09) |

`engine.gradle` verifies the archive, extracts it into `build/vendor/engine`, applies the exact replacements in `engine-patches.json`, and compiles its Java together with Orbis. `bash gradlew prepareEngine` exposes the complete source tree for inspection. The `-sources.jar` includes the integrated sources.

The four patches use Orbis for runtime version lookup, log identity and configuration, and leave shape registration to Orbis. Existing engine package names and `toroidal_world` save/packet identifiers are retained to avoid changing the alpha's serialization format; they do not register another mod. Save migration is not gameplay-validated yet.

Optional compatibility APIs are **compile-only**, not user requirements. MixinSquared is a small bundled library used by the integration, not a separate installation step. The upstream mod metadata, NeoForge metadata and upstream world presets are excluded from Orbis's distribution.

To reproduce the source archive from a checkout at the pinned commit:

```sh
git archive --format=zip --output=toroidal-engine-0600806.zip 060080683bdc0e198da1fc32502dd3f62875413c LICENSE common/src/main fabric/src/main
```

Review and update patches, checksum, compatibility pins and regression tests together when changing the engine snapshot. Any newer snapshot must come from a commit before `8f417283`; later upstream code is not MIT and must not be copied (see [Sources and licensing](../docs/SOURCES_AND_LICENSE.md#upstream-license-change)). Never edit generated files in `build/` as the permanent source of a fix.
