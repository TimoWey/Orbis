# Orbis animations

Four quiet diagrams for the README: one folding overview and three independent walks. Each image contains the world on the left and its flat map on the right, with small +X and +Z direction labels. There are no titles, stage controls, metrics or formulas inside the images.

| File | Content | Duration |
| --- | --- | ---: |
| `orbis-torus.gif` | Square → cylinder → torus, then unfold back to the square | 24 seconds |
| `orbis-torus.mp4` | Video version of the folding overview | 24 seconds |
| `orbis-walk-x.gif` | One east–west lap, followed by a short pause | 12 seconds |
| `orbis-walk-z.gif` | One north–south lap, followed by a short pause | 12 seconds |
| `orbis-walk-diagonal.gif` | One offset diagonal lap, followed by a short pause | 14 seconds |

## Reproduce

Requires Python with NumPy and Pillow, plus `ffmpeg` on PATH. No system fonts are needed.

```sh
python docs/media/render_orbis.py
```

The renderer exports at **1440 × 600, 25 fps**, with 2× supersampling for clean edges. A tilted three-quarter camera and a larger map make better use of the frame. The 28 × 28 block-style terrain uses three geometry subdivisions per tile to make the curved silhouettes smoother. Frame files live in a temporary directory and are removed after export.

- `--preview` writes a few stills to the ignored `docs/media/.preview/` folder.
- `--routes-only` renders only the three walks, independently of the overview.

## What the diagrams show

Blue marks the paired east–west edges; gold marks the paired north–south edges. The original two-tile-wide blue seam on the folded world is preserved. Steve's feet indicate the same position in both views. A depth buffer checks each pixel of his camera-facing sprite against the torus: only the covered parts become a translucent silhouette. His head, arms or legs keep their normal colours wherever they remain visible, including through the hole. Supersampling smooths the moving boundary. The hidden route uses muted dashes. Small rings mark the starting positions.

Each walk makes one complete circuit. The diagonal starts away from the corner, crosses the gold seam first and then the blue seam, and returns to its start. Both coordinates wrap independently. This particular diagonal closes after one lap; arbitrary angles need not do so.

Steve's arms swing from fixed shoulder joints, opposite to the legs. The rear arm passes behind the torso and the forward arm in front; the shoulders and head stay steady.

The overview pauses at the square, cylinder and torus, then unfolds without a cut back to the beginning. Its map remains unchanged throughout.

The torus illustrates connectivity, not the physical shape of the Minecraft world. In-game terrain and gravity stay normal; height does not wrap. All artwork is procedural, including the Steve-style character. No Minecraft textures or gameplay footage are used, and these diagrams are not evidence of gameplay validation.
