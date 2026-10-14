"""Render the clean README diagrams with NumPy, Pillow and ffmpeg.

Run: python docs/media/render_orbis.py [--preview | --routes-only]
All artwork is procedural. No Minecraft textures or gameplay footage are used.
"""
from functools import lru_cache
from pathlib import Path
import argparse
import math
import subprocess
import tempfile

import numpy as np
from PIL import Image, ImageChops, ImageDraw

OUT = Path(__file__).resolve().parent
W, H, FPS, SS = 1440, 600, 25, 2
BG = '#10191e'
CYAN, GOLD, ROUTE = '#56cbdc', '#eabb69', '#fff0bd'
N = 28
SUBDIVISIONS = 3
GRID = N*SUBDIVISIONS
MAP_X, MAP_Y, MAP_SIZE = 880, 48, 504
CLIPS = {'torus': 24, 'walk-x': 12, 'walk-z': 12, 'walk-diagonal': 14}
rng = np.random.default_rng(18)
colors = []
for j in range(N):
    row = []
    for i in range(N):
        c = np.array([87, 144, 66]) + rng.integers(-14, 15)
        if (i-19)**2 + (j-7)**2 < 22:
            c = np.array([47, 122, 169]) + rng.integers(-8, 9)
        if (i-6)**2 + (j-20)**2 < 17:
            c = np.array([181, 168, 108]) + rng.integers(-10, 11)
        if j in (13, 14):
            c = np.array([153, 128, 83]) + rng.integers(-9, 10)
        if (i, j) in [(4, 5), (5, 5), (5, 6), (22, 21), (23, 21), (22, 22), (8, 9), (9, 9)]:
            c = np.array([37, 91, 51])
        row.append(tuple(int(v) for v in c))
    colors.append(row)


class Pen:
    """Draw in logical pixels, then supersample for clean edges."""
    def __init__(self, image):
        self.draw = ImageDraw.Draw(image)

    def polygon(self, points, fill):
        self.draw.polygon([(round(x*SS), round(y*SS)) for x, y in points], fill=fill)

    def line(self, points, fill, width=2):
        self.draw.line([(round(x*SS), round(y*SS)) for x, y in points], fill=fill,
                       width=round(width*SS), joint='curve')

    def rectangle(self, bounds, fill):
        self.draw.rectangle(tuple(round(v*SS) for v in bounds), fill=fill)

    def ellipse(self, bounds, fill=None, outline=None, width=2):
        self.draw.ellipse(tuple(round(v*SS) for v in bounds), fill=fill,
                          outline=outline, width=round(width*SS))


def ease(x):
    x = np.clip(x, 0, 1)
    return x*x*(3-2*x)


def folding(t):
    """Hold each form; unfold again so the overview loops without a cut."""
    if t < 13:
        return float(ease((t-1.5)/3.5)), float(ease((t-6.5)/4))
    if t < 18:
        return 1.0, float(1-ease((t-13)/4))
    return float(1-ease((t-18)/3.5)), 0.0


def surface(u, v, k, b):
    """Z closes first; X closes second. A topology illustration, not a scale model."""
    theta = (np.asarray(v)-.5)*2*np.pi
    phi = (np.asarray(u)-.5)*2*np.pi
    r = 1.05
    if b <= 0:
        p = r*theta if k < 1e-5 else r*np.sin(k*theta)/k
        h = np.zeros_like(theta) if k < 1e-5 else r*(np.cos(k*theta)-1)/k
        q = r*phi
    else:
        radius = r + (2.55-r)*ease(b)
        p = r*np.sin(theta)
        q = (radius/b+r*np.cos(theta))*np.sin(b*phi)
        h = (radius/b+r*np.cos(theta))*np.cos(b*phi)-radius/b-r
    return np.stack(np.broadcast_arrays(p, q, h), axis=-1)


def geometry(k, b):
    uv = np.linspace(0, 1, GRID+1)
    u, v = np.meshgrid(uv, uv)
    xyz = surface(u, v, k, b)
    view = np.array([1.1+.4*b, -1-.8*b, 1.35+.15*b])
    view /= np.linalg.norm(view)
    # Turn the camera with the fold. The torus ends in a three-quarter view,
    # with its long axis almost horizontal so both diagrams fill their space.
    tilt = float(ease(b))
    right = np.cross(view, [tilt, 0, 1-tilt])
    right /= np.linalg.norm(right)
    up = np.cross(right, view)
    roll = math.radians(12)*tilt
    mat = np.stack([right*math.cos(roll)+up*math.sin(roll),
                    up*math.cos(roll)-right*math.sin(roll), view], axis=1)
    projected = xyz @ mat
    center = (projected.min(axis=(0, 1))+projected.max(axis=(0, 1)))/2
    span = np.ptp(projected, axis=(0, 1))
    scale = min(760/max(span[0], 1), 470/max(span[1], 1))

    def project(points):
        p = (points @ mat-center)*scale
        return np.stack([422+p[..., 0], 318-p[..., 1], p[..., 2]], axis=-1)

    return xyz, project, mat


def arrow(d, start, end, color=ROUTE, width=2):
    d.line([start, end], color, width)
    angle = math.atan2(end[1]-start[1], end[0]-start[0])
    d.polygon([end, (end[0]-9*math.cos(angle-.5), end[1]-9*math.sin(angle-.5)),
               (end[0]-9*math.cos(angle+.5), end[1]-9*math.sin(angle+.5))], color)


def axis_label(d, x, y, axis, color):
    """Small vector +X / +Z labels; no platform font dependency."""
    d.line([(x, y+7), (x+10, y+7)], color, 2)
    d.line([(x+5, y+2), (x+5, y+12)], color, 2)
    if axis == 'X':
        d.line([(x+17, y), (x+29, y+14)], color, 2)
        d.line([(x+29, y), (x+17, y+14)], color, 2)
    else:
        d.line([(x+17, y), (x+29, y), (x+17, y+14), (x+29, y+14)], color, 2)


def avatar(d, x, y, t, scale=1, shadow=True):
    """A small Steve-style walker, anchored at the feet in both views."""
    def box(bounds, color):
        d.rectangle(tuple((x if n % 2 == 0 else y)+v*scale for n, v in enumerate(bounds)), color)

    def arm(side, swing):
        # Pitch a rigid block arm about its fixed shoulder. A little sideways
        # projection makes forward/back motion readable in this front view.
        shoulder = np.array([side*10., -33.])
        angle = math.radians(28)*swing
        direction = np.array([.55*math.sin(angle),
                              math.cos(angle)-.25*math.sin(angle)])

        def segment(start, end, color):
            a, b = shoulder+direction*start, shoulder+direction*end
            points = [a+[-2, 0], a+[2, 0], b+[2, 0], b+[-2, 0]]
            d.polygon([(x+p[0]*scale, y+p[1]*scale) for p in points], color)

        segment(0, 15, '#bd8765')
        segment(0, 4, '#45b8bd')

    phase = math.sin(t*3*math.pi)
    if abs(phase) < 1e-12:
        phase = 0.0
    step = 2.2*phase
    arms = [(-1, -phase), (1, phase)]
    if shadow:
        d.ellipse((x-11*scale, y-3*scale, x+11*scale, y+3*scale), fill='#0a1116')
    box((-7, -17, -1, -3+step), '#465993')
    box((2, -17, 8, -3-step), '#465993')
    box((-7, -4+step, -1, step), '#303e58')
    box((2, -4-step, 8, -step), '#303e58')
    for side, swing in arms:
        if swing <= 0:
            arm(side, swing)
    box((-8, -34, 8, -18), '#45b8bd')
    for side, swing in arms:
        if swing > 0:
            arm(side, swing)
    box((-7, -49, 7, -35), '#c4916e')
    box((-7, -50, 7, -47), '#4b342b')
    box((-7, -47, -5, -42), '#4b342b')
    box((-4, -43, -2, -41), '#202b31')
    box((3, -43, 5, -41), '#202b31')
    box((-2, -38, 4, -36), '#704834')


@lru_cache(maxsize=1)
def torus_depth():
    """Frontmost surface depth at every supersampled image pixel.

    Rasterize the same mesh used by the picture, including the empty hole.
    Greater projected Z is closer to the orthographic camera.
    """
    xyz, project, _ = geometry(1.0, 1.0)
    p = project(xyz)
    p[..., :2] *= SS
    depth = np.full((H*SS, W*SS), -np.inf, dtype=np.float32)
    quads = np.stack([p[:-1, :-1], p[:-1, 1:], p[1:, 1:], p[1:, :-1]], axis=2)
    for quad in quads.reshape(-1, 4, 3):
        for indices in ((0, 1, 2), (0, 2, 3)):
            a, b, c = quad[list(indices)]
            left = max(0, int(np.floor(min(a[0], b[0], c[0]))))
            right = min(W*SS, int(np.ceil(max(a[0], b[0], c[0])))+1)
            top = max(0, int(np.floor(min(a[1], b[1], c[1]))))
            bottom = min(H*SS, int(np.ceil(max(a[1], b[1], c[1])))+1)
            denominator = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(denominator) < 1e-9 or left >= right or top >= bottom:
                continue
            y, x = np.mgrid[top:bottom, left:right] + .5
            wa = ((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/denominator
            wb = ((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/denominator
            wc = 1-wa-wb
            inside = (wa >= -1e-7) & (wb >= -1e-7) & (wc >= -1e-7)
            z = wa*a[2]+wb*b[2]+wc*c[2]
            patch = depth[top:bottom, left:right]
            np.maximum(patch, np.where(inside, z, -np.inf), out=patch)
    return depth


@lru_cache(maxsize=1)
def torus_camera():
    return geometry(1.0, 1.0)[2]


def occluded_avatar(point, t, uv, scale=1.4):
    """Composite a camera-facing sprite using depth per pixel, not per player.

    Visible pixels keep Steve's colours. Only pixels behind the torus become
    a translucent monochrome silhouette. Supersampling and a subpixel depth
    tolerance smooth the moving boundary without fading the entire person.
    """
    x, y, z = point
    left = max(0, math.floor((x-19*scale)*SS))
    right = min(W*SS, math.ceil((x+19*scale)*SS))
    top = max(0, math.floor((y-53*scale)*SS))
    bottom = min(H*SS, math.ceil((y+5*scale)*SS))
    sprite = Image.new('RGBA', (right-left, bottom-top))
    avatar(Pen(sprite), x-left/SS, y-top/SS, t, scale, shadow=False)
    pixels = np.asarray(sprite).astype(np.float32)
    # Follow the local tangent plane in depth. A constant-depth billboard
    # would cut into a sloping foreground surface and hide Steve's own torso.
    phi, theta = (np.asarray(uv)-.5)*2*np.pi
    normal = np.array([np.sin(theta), np.cos(theta)*np.sin(phi),
                       np.cos(theta)*np.cos(phi)]) @ torus_camera()
    # Regularize grazing angles continuously; dividing by a clamped signed
    # normal would flip the whole depth plane abruptly at the silhouette.
    slope = normal[2]/(normal[2]**2+.04)
    dx, dy = -normal[0]*slope, normal[1]*slope
    yy, xx = np.mgrid[top:bottom, left:right]
    sprite_depth = z+dx*((xx+.5)/SS-x)+dy*((yy+.5)/SS-y)
    hidden = np.clip((torus_depth()[top:bottom, left:right]-sprite_depth-.4)/.8, 0, 1)
    pixels[..., :3] = (pixels[..., :3]*(1-hidden[..., None])
                       + np.array([220, 235, 241])*hidden[..., None])
    pixels[..., 3] *= 1-hidden*(1-115/255)
    return Image.fromarray(np.rint(pixels).astype(np.uint8)), (left, top)


@lru_cache(maxsize=1)
def background(k, b):
    image = Image.new('RGB', (W*SS, H*SS), BG)
    d = Pen(image)
    xyz, project, mat = geometry(k, b)
    pp = project(xyz)
    world = np.stack([xyz[:-1, :-1], xyz[:-1, 1:], xyz[1:, 1:], xyz[1:, :-1]], axis=2)
    points = np.stack([pp[:-1, :-1], pp[:-1, 1:], pp[1:, 1:], pp[1:, :-1]], axis=2)
    normals = np.cross(world[:, :, 1]-world[:, :, 0], world[:, :, 3]-world[:, :, 0])
    normals /= np.maximum(np.linalg.norm(normals, axis=-1, keepdims=True), 1e-8)
    shade = .69+.31*np.abs(normals @ np.array([.4, -.3, .866]))
    tiles = np.repeat(np.repeat(np.array(colors, dtype=float), SUBDIVISIONS, axis=0),
                      SUBDIVISIONS, axis=1)
    tiles[normals @ mat[:, 2] > 0] = (117, 86, 58)
    tiles *= shade[..., None]
    tiles[:, :SUBDIVISIONS] = tiles[:, -SUBDIVISIONS:] = (73, 194, 202)
    tiles[:SUBDIVISIONS, :] = tiles[-SUBDIVISIONS:, :] = (232, 173, 77)
    depths = points[..., 2].mean(axis=2)
    for index in np.argsort(depths, axis=None):
        j, i = divmod(index, GRID)
        d.polygon(points[j, i, :, :2], tuple(tiles[j, i].astype(int)))
    tile = MAP_SIZE/N
    for j in range(N):
        for i in range(N):
            d.rectangle((MAP_X+i*tile, MAP_Y+j*tile,
                         MAP_X+(i+1)*tile, MAP_Y+(j+1)*tile), colors[j][i])
    d.line([(MAP_X-4, MAP_Y), (MAP_X-4, MAP_Y+MAP_SIZE)], CYAN, 5)
    d.line([(MAP_X+MAP_SIZE+4, MAP_Y), (MAP_X+MAP_SIZE+4, MAP_Y+MAP_SIZE)], CYAN, 5)
    d.line([(MAP_X, MAP_Y-4), (MAP_X+MAP_SIZE, MAP_Y-4)], GOLD, 5)
    d.line([(MAP_X, MAP_Y+MAP_SIZE+4), (MAP_X+MAP_SIZE, MAP_Y+MAP_SIZE+4)], GOLD, 5)
    # Match the map's coordinate directions without obscuring its terrain.
    arrow(d, (MAP_X+190, MAP_Y+MAP_SIZE+27),
          (MAP_X+270, MAP_Y+MAP_SIZE+27), CYAN)
    axis_label(d, MAP_X+283, MAP_Y+MAP_SIZE+20, 'X', CYAN)
    arrow(d, (MAP_X+MAP_SIZE+25, MAP_Y+210),
          (MAP_X+MAP_SIZE+25, MAP_Y+290), GOLD)
    axis_label(d, MAP_X+MAP_SIZE+10, MAP_Y+306, 'Z', GOLD)
    return image, project, mat[:, 2]


def hidden_by_torus(point, view):
    """Cast an orthographic ray toward the camera against the actual torus.

    Ignore the root at the starting surface. Any later intersection means
    this point is hidden, including self-occlusion around the inner ring.
    The finished torus has its symmetry axis along X in model coordinates.
    """
    radius, tube = 2.55, 1.05
    p = np.asarray(point) + np.array([0, 0, radius+tube])
    a = float(p @ p + radius*radius - tube*tube)
    c = float(p @ view)
    roots = np.roots([1, 4*c,
                     2*a+4*c*c-4*radius*radius*(view[1]**2+view[2]**2),
                     4*a*c-8*radius*radius*(p[1]*view[1]+p[2]*view[2]),
                     a*a-4*radius*radius*(p[1]**2+p[2]**2)])
    return bool(np.any((np.abs(roots.imag) < 1e-6) & (roots.real > 1e-4)))


@lru_cache(maxsize=3)
def route_geometry(name):
    origin, velocity, _, _ = route_position(name, 0)
    route = (origin+np.linspace(0, 1, 361)[:, None]*velocity) % 1
    _, project, view = background(1.0, 1.0)
    world = surface(route[:, 0], route[:, 1], 1.0, 1.0)
    hidden = [hidden_by_torus(p, view) for p in world]
    return route, project(world), hidden


def route_position(name, t):
    diagonal = name == 'walk-diagonal'
    period = 12 if diagonal else 10
    origin = np.array([.25, .40 if diagonal else .5])
    velocity = np.array({'walk-x': (1, 0), 'walk-z': (0, 1), 'walk-diagonal': (1, 1)}[name])
    progress = min(t/period, 1)
    return origin, velocity, (origin+progress*velocity) % 1, min(t, period)


def render(name, t):
    k, b = folding(t) if name == 'torus' else (1.0, 1.0)
    base, project, view = background(k, b)
    if name == 'torus':
        return base.resize((W, H), Image.Resampling.LANCZOS)
    image = base.copy()
    d = Pen(image)
    origin, velocity, uv, walk_time = route_position(name, t)
    route, points, hidden = route_geometry(name)
    for n in range(len(points)-1):
        if not hidden[n] or n % 8 < 4:
            d.line(points[n:n+2, :2], '#a4aaa2' if hidden[n] else ROUTE, 2)
    for a, c in zip(route[:-1], route[1:]):
        if np.max(abs(c-a)) < .1:
            d.line([(MAP_X+MAP_SIZE*a[0], MAP_Y+MAP_SIZE*a[1]),
                    (MAP_X+MAP_SIZE*c[0], MAP_Y+MAP_SIZE*c[1])], ROUTE, 2)
    for fraction in (.12, .52, .88):
        a = (origin+fraction*velocity) % 1
        c = a+.045*velocity
        if np.all(c < 1):
            arrow(d, (MAP_X+MAP_SIZE*a[0], MAP_Y+MAP_SIZE*a[1]),
                  (MAP_X+MAP_SIZE*c[0], MAP_Y+MAP_SIZE*c[1]))
    start = project(surface(*origin, k, b))
    d.ellipse((start[0]-7, start[1]-4, start[0]+7, start[1]+4), outline=ROUTE)
    x, y = MAP_X+MAP_SIZE*origin[0], MAP_Y+MAP_SIZE*origin[1]
    d.ellipse((x-7, y-7, x+7, y+7), outline=ROUTE)
    world_point = surface(*uv, k, b)
    point = project(world_point)
    sprite, position = occluded_avatar(point, walk_time, uv)
    image.paste(sprite, position, sprite)
    # On the flat map Steve leaves one edge while already entering the opposite one:
    # draw him at every wrapped position and clip each copy to the map square.
    layer = Image.new('RGBA', image.size)
    layer_pen = Pen(layer)
    for shift_x in (-1, 0, 1):
        for shift_z in (-1, 0, 1):
            wrapped = (uv[0]+shift_x, uv[1]+shift_z)
            if -.08 < wrapped[0] < 1.08 and -.14 < wrapped[1] < 1.14:
                avatar(layer_pen, MAP_X+MAP_SIZE*wrapped[0], MAP_Y+MAP_SIZE*wrapped[1], walk_time, scale=1.15)
    clip = Image.new('L', image.size)
    ImageDraw.Draw(clip).rectangle((MAP_X*SS, MAP_Y*SS, (MAP_X+MAP_SIZE)*SS, (MAP_Y+MAP_SIZE)*SS), fill=255)
    layer.putalpha(ImageChops.multiply(layer.getchannel('A'), clip))
    image.paste(layer, (0, 0), layer)
    return image.resize((W, H), Image.Resampling.LANCZOS)


def export(name, duration, directory):
    """Temporary frames avoid stale frame reuse and keep generated debris out of git."""
    frames = directory/name
    frames.mkdir()
    for f in range(duration*FPS):
        render(name, f/FPS).save(frames/f'{f:04d}.png')
        if f % (FPS*4) == 0:
            print(f'{name}: {f//FPS}/{duration}s', flush=True)
    base = ['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS),
            '-i', str(frames/'%04d.png')]
    palette = frames/'palette.png'
    subprocess.run(base+['-vf', 'palettegen=stats_mode=diff', '-frames:v', '1', str(palette)], check=True)
    subprocess.run(base+['-i', str(palette), '-lavfi',
                        'paletteuse=dither=none:diff_mode=rectangle',
                        '-loop', '0', str(frames/f'orbis-{name}.gif')], check=True)
    (frames/f'orbis-{name}.gif').replace(OUT/f'orbis-{name}.gif')
    if name == 'torus':
        subprocess.run(base+['-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18',
                            '-movflags', '+faststart', str(frames/'orbis-torus.mp4')], check=True)
        (frames/'orbis-torus.mp4').replace(OUT/'orbis-torus.mp4')
    print(f'{name}: exported', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true')
    parser.add_argument('--routes-only', action='store_true')
    args = parser.parse_args()
    if args.preview:
        directory = OUT/'.preview'
        directory.mkdir(exist_ok=True)
        for name, times in {'torus': (0, 5.5, 11), 'walk-x': (2, 8),
                            'walk-z': (2, 8), 'walk-diagonal': (3, 7.3, 9.1)}.items():
            for t in times:
                render(name, t).save(directory/f'{name}-{t}.png')
        print(directory)
    else:
        with tempfile.TemporaryDirectory(prefix='orbis-render-') as directory:
            for name, duration in CLIPS.items():
                if args.routes_only and name == 'torus':
                    continue
                export(name, duration, Path(directory))
