# Orbis animation

55 seconds: square → cylinder → torus → X walk → Z walk → offset diagonal walk. All labels are English.

| File | Purpose |
| --- | --- |
| `orbis-torus.gif` | Automatically looping README animation |
| `orbis-torus.mp4` | Video with playback and pause controls |
| `orbis-walk-x.gif` | X-only loop; no folding intro |
| `orbis-walk-z.gif` | Z-only loop; no folding intro |
| `orbis-walk-diagonal.gif` | Offset diagonal loop; no folding intro |
| `render_orbis.py` | Reproducible renderer; requires Python, NumPy, Pillow and ffmpeg |

Run from the repository root: `python docs/media/render_orbis.py`. To extract only the route loops from the existing full GIF, add `--routes-only`.

## Routes

- **X:** two laps in +X, with fixed Z. Each lap travels L.
- **Z:** two laps in +Z, with fixed X. Each lap travels L.
- **Offset diagonal:** start at x = 0.25 L, z = 0.40 L and move equally in +X and +Z. Z wraps first, then X. Each lap moves L along each axis and travels √2 L on the flat square world.

Both coordinates wrap independently. The 45° square-world example closes after one lap; arbitrary diagonal directions need not have the same return distance. Each route finishes with a pause at its starting position. The route and position marker remain visible even behind the torus surface.

The shape illustrates topology and is not to scale. Orbis does not bend in-game terrain or gravity; Y / height stays unchanged. The map and torus marker show the same position.

All artwork is procedural block-style graphics. No Minecraft textures, no gameplay footage and no evidence of implemented mod features.

The full video shows X at 0:17, Z at 0:29 and the diagonal at 0:41. The README also embeds each route as its own immediately playing loop.
