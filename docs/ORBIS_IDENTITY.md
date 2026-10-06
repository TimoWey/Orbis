# Orbis identity audit

Alpha audit, 2026-10-06. Orbis has its own presentation and progression while retaining its documented MIT world-generation foundation. This is not a claim that the engine was independently rewritten.

| Surface | Orbis policy and implementation |
| --- | --- |
| Mod identity | The distributed mod is `orbis`, with Orbis metadata, entrypoints, configuration filename and commands. No separate Toroidal World installation is required. |
| World creation | Only Minecraft's default layout and Orbis are registered. Orbis owns the size selector, presets and dimensional policy. The upstream torus/cylinder datapack presets are excluded. |
| Advancements | The upstream `toroidal_world:circumnavigate` advancement is excluded. Orbis adds a small cartography branch to Minecraft's Adventure tree, using independently authored text and different inventory criteria. |
| Interface wording | Authored English and German resources replace the engine's language files, including its visible settings titles and tooltips. Other locales fall back to English. Stable translation identifiers remain internal compatibility details. |
| Art | Upstream logo and editable model assets are excluded. Advancement icons use vanilla items. The new map interface does not bundle Xaero artwork or source. |
| Maps | Orbis owns its atlas, minimap, globe projection, waypoints and debug inspection. Xaero's Minimap is a usability reference. Engine compatibility adapters for external map mods remain. |
| World generation | Periodic terrain generation, connected edges, codecs and mod compatibility retain the existing engine. Their technical namespaces stay stable for existing saves. |
| Attribution | The original MIT copyright notice, license, archive checksum and source history remain visible in the repository and distribution. They must not be removed as branding cleanup. |

## Cartography branch

These goals are deliberately independent of completing a world lap:

1. **Orbis: Find Your Bearings** — carry a compass.
2. **A World on Paper** — carry a filled vanilla map.
3. **Ready for the Horizon** — carry a compass, filled map and spyglass together.

The branch appears beneath Minecraft's Adventure root, rather than adding another top-level tab. Toasts are enabled; chat announcements are disabled to reduce clutter. The goals use vanilla inventory triggers, work in ordinary Minecraft worlds as well as finite worlds, and do not claim that a world has been fully mapped or surveyed. Parent relationships arrange the tree; they do not force an unlock order.

An existing save's historical upstream advancement record is left intact, but its removed definition will no longer appear in Orbis. Orbis uses new IDs and does not grant its goals based on the old lap achievement.

## Packaging guard

`tools/verify_standalone.py` checks the built distribution for the absence of upstream advancement and preset resources, the presence of Orbis's three advancement definitions and their translations, the authored engine language set, and retained upstream license attribution. Run this on both the normal and debug artifacts.

## Remaining shared implementation

The vendored archive still contains the unmodified upstream source and resources for provenance. The engine's lap criterion implementation, UI infrastructure, translation identifiers, `toroidal_world` save/network identifiers and compatibility classes remain in source or bytecode. None of those facts should be hidden or represented as newly authored Orbis code. An independently rewritten engine would be a separate project; this change intentionally preserves working generation.
