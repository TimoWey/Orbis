# Orbis identity audit

Alpha audit, 2026-10-06; updated 2026-10-15 after the old engine was removed. Orbis has its own presentation, progression and world engine.

| Surface | Orbis policy and implementation |
| --- | --- |
| Mod identity | The distributed mod is `orbis`, with Orbis metadata, entrypoints, configuration filename and commands. It needs no other world mod. |
| World creation | Orbis adds its own **World shape** row (Normal / Orbis) and owns the size selector, presets and dimensional policy. |
| Advancements | Orbis adds a small cartography branch to Minecraft's Adventure tree, using its own text and vanilla inventory criteria. |
| Interface wording | Authored English and German resources. Other locales fall back to English. |
| Art | Advancement icons use vanilla items. The new map interface does not bundle Xaero artwork or source. |
| Maps | Orbis owns its atlas, minimap, globe projection, waypoints and debug inspection. Xaero's Minimap is a usability reference. |
| World generation | Orbis's own engine (`dev.orbis.engine`, generator `orbis:torus`) handles periodic generation, connected edges and codecs. Worlds of the old engine open with their saved settings rewritten to it. |

## Cartography branch

These goals are deliberately independent of completing a world lap:

1. **Orbis: Find Your Bearings** — carry a compass.
2. **A World on Paper** — carry a filled vanilla map.
3. **Ready for the Horizon** — carry a compass, filled map and spyglass together.

The branch appears beneath Minecraft's Adventure root, rather than adding another top-level tab. Toasts are enabled; chat announcements are disabled to reduce clutter. The goals use vanilla inventory triggers, work in ordinary Minecraft worlds as well as finite worlds, and do not claim that a world has been fully mapped or surveyed. Parent relationships arrange the tree; they do not force an unlock order.

Orbis uses its own advancement IDs.

## Packaging guard

`tools/verify_standalone.py` checks that the built distribution contains no classes, assets or data of another mod, that Orbis's own engine, presets and three advancement definitions with their translations are present, and that `fabric.mod.json` keeps its `breaks` entry against the old engine mod. Run this on both the normal and debug artifacts (`--debug` for the latter).
