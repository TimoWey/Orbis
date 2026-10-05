"""Render the Orbis topology explainer. Requires Python, NumPy, Pillow and ffmpeg.
Run from repository root: python docs/media/render_orbis.py
All artwork is procedural; no Minecraft textures or game footage are used.
"""
from pathlib import Path
import math, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
FRAMES = OUT / '.frames'
FRAMES.mkdir(exist_ok=True)
W,H,FPS,DURATION = 960,640,15,55
BG='#101c22'; FG='#ecf4ed'; MUTED='#a2b7b4'; CYAN='#56dbe4'; GOLD='#ffc968'
FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
BOLD='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
MONO='/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'
fonts={n:ImageFont.truetype(FONT,n) for n in (12,14,15,16,18,20)}
N=28
rng=np.random.default_rng(18)
colors=[]
for j in range(N):
    row=[]
    for i in range(N):
        c=np.array([87,144,66])+rng.integers(-14,15)
        if (i-19)**2+(j-7)**2<22: c=np.array([47,122,169])+rng.integers(-8,9)
        if (i-6)**2+(j-20)**2<17: c=np.array([181,168,108])+rng.integers(-10,11)
        if j in (13,14): c=np.array([153,128,83])+rng.integers(-9,10)
        if (i,j) in [(4,5),(5,5),(5,6),(22,21),(23,21),(22,22),(8,9),(9,9)]:c=np.array([37,91,51])
        row.append(tuple(int(v) for v in c))
    colors.append(row)

def ease(x):
    x=np.clip(x,0,1);return x*x*(3-2*x)

def surface(u,v,k,b):
    """Z closes first; then X. Geometry illustrates topology, not an isometry."""
    theta=(np.asarray(v)-.5)*2*np.pi
    phi=(np.asarray(u)-.5)*2*np.pi
    r=1.05
    if b<=0:
        p=r*theta if k<1e-5 else r*np.sin(k*theta)/k
        h=np.zeros_like(theta) if k<1e-5 else r*(np.cos(k*theta)-1)/k
        q=r*phi
    else:
        R=2.55
        # Stretch the tube smoothly while bending it into the second cycle.
        a=r+(R-r)*ease(b)
        p=r*np.sin(theta)
        q=(a/b+r*np.cos(theta))*np.sin(b*phi)
        h=(a/b+r*np.cos(theta))*np.cos(b*phi)-a/b-r
    return np.stack(np.broadcast_arrays(p,q,h),axis=-1)

def camera(b):
    v=np.array([1.1+.9*b,-1.0+.55*b,1.35-.8*b]);v/=np.linalg.norm(v)
    right=np.cross(v,[0,0,1]);right/=np.linalg.norm(right)
    up=np.cross(right,v)
    return np.stack([right,up,v],axis=1)

def geometry(k,b):
    uv=np.linspace(0,1,N+1)
    u,v=np.meshgrid(uv,uv)
    xyz=surface(u,v,k,b)
    mat=camera(b)
    proj=xyz@mat
    center=(proj.min(axis=(0,1))+proj.max(axis=(0,1)))/2
    span=np.ptp(proj,axis=(0,1))
    scale=min(500/max(span[0],1),270/max(span[1],1))
    def project(p):
        z=(p@mat-center)*scale
        return np.stack([314+z[...,0],326-z[...,1],z[...,2]],axis=-1)
    return xyz,project,mat

def text(d,xy,s,n=16,c=FG,bold=False):
    d.text(xy,s,font=ImageFont.truetype(BOLD,n) if bold else fonts[n],fill=c)

def arrow(d,a,b,c,w=3):
    d.line([a,b],fill=c,width=w)
    ang=math.atan2(b[1]-a[1],b[0]-a[0]);sz=8
    d.polygon([b,(b[0]-sz*math.cos(ang-.5),b[1]-sz*math.sin(ang-.5)),(b[0]-sz*math.cos(ang+.5),b[1]-sz*math.sin(ang+.5))],fill=c)

def avatar(d,x,y,t,scale=1):
    s=scale
    def box(a,c):d.rectangle(tuple(int(v) for v in (x+a[0]*s,y+a[1]*s,x+a[2]*s,y+a[3]*s)),fill=c)
    step=2*math.sin(t*14)
    box((-7,-22,7,-8),'#c4916e');box((-7,-23,7,-20),'#4b342b')
    box((-8,-7,8,7),'#49c1c5');box((-12,-6,-9,6),'#bd8765');box((9,-6,12,6),'#bd8765')
    box((-7,8,-1,18+step),'#425d9a');box((2,8,8,18-step),'#425d9a')
    box((-4,-16,-2,-14),'#202b31');box((3,-16,5,-14),'#202b31')

def movement(t):
    """Normalized square-world coordinates; two laps, followed by a hold."""
    if t < 29:
        mode, start, period, origin, velocity = 'X', 17, 5, (.25,.5), (1,0)
    elif t < 41:
        mode, start, period, origin, velocity = 'Z', 29, 5, (.25,.5), (0,1)
    else:
        mode, start, period, origin, velocity = 'DIAGONAL', 41, 6, (.25,.40), (1,1)
    progress = min(max((t-start)/period,0),2)
    uv = (np.array(origin)+progress*np.array(velocity)) % 1
    return mode, progress, np.array(origin), np.array(velocity), uv


def render(t):
    k=float(ease((t-3)/4));b=float(ease((t-9)/5))
    stage=0 if t<3 else 1 if t<9 else 2 if t<17 else 3
    mode, progress, origin, velocity, uv = movement(t)
    titles=['A finite, square world','Join the Z edges: a cylinder','Join the X edges: a torus',{'X':'Keep walking in +X. Back to the start.','Z':'Keep walking in +Z. Back to the start.','DIAGONAL':'Walk diagonally from an offset start.'}[mode]]
    subtitles=['Matching colours identify opposite edges.','The top and bottom edges become direct neighbours.','Now connect the two remaining open ends.', 'Offset: x₀ = 0.25 L, z₀ = 0.40 L. The two edges are crossed at different times.' if mode=='DIAGONAL' else 'After each world width L, you return to the same position.']
    im=Image.new('RGB',(W,H),BG);d=ImageDraw.Draw(im)
    d.rectangle((0,0,W,5),fill=CYAN)
    text(d,(30,23),'ORBIS',20,CYAN,True)
    text(d,(131,27),'FINITE WORLD · ENDLESS JOURNEYS',14,MUTED)
    for s,l in enumerate(['01  SQUARE','02  JOIN Z','03  JOIN X','04  WALK '+mode]):
        x=30+s*232
        d.rounded_rectangle((x,66,x+217,98),6,fill='#294447' if s==stage else '#1a2a31')
        text(d,(x+12,74),l,12,FG if s==stage else MUTED)
    text(d,(30,115),titles[stage],20,FG,True)
    text(d,(30,146),subtitles[stage],16,MUTED)
    d.rounded_rectangle((24,184,604,519),12,fill='#15262d')
    d.rounded_rectangle((623,184,936,519),12,fill='#1b3036')
    text(d,(643,199),'THE SAME WORLD, UNWRAPPED',12,MUTED)
    xyz,proj,mat=geometry(k,b)
    pp=proj(xyz)
    faces=[]
    for j in range(N):
        for i in range(N):
            inds=[(j,i),(j,i+1),(j+1,i+1),(j+1,i)]
            p=np.array([pp[a] for a in inds]);world=np.array([xyz[a] for a in inds])
            normal=np.cross(world[1]-world[0],world[3]-world[0]);normal/=max(np.linalg.norm(normal),1e-8)
            shade=.63+.37*abs(normal@np.array([.4,-.3,.866]))
            col=tuple(int(v*shade) for v in colors[j][i])
            # The reverse side is earthy brown: a visibly block-like terrain sheet.
            if normal@mat[:,2]>0:col=tuple(int(v*shade) for v in (117,86,58))
            if i in (0,N-1):col=(73,194,202)
            if j in (0,N-1):col=(232,173,77)
            faces.append((p[:,2].mean(),p[:,:2],col))
    for _,p,col in sorted(faces,key=lambda a:a[0]):
        d.polygon([tuple(q) for q in p],fill=col,outline=tuple(int(v*.78) for v in col))
    # The same route on the torus and on the unwrapped map.
    if stage==3:
        route = (origin + np.linspace(0,1,241)[:,None]*velocity) % 1
        points = proj(surface(route[:,0],route[:,1],k,b))
        for n in range(len(points)-1):
            d.line([tuple(points[n,:2]),tuple(points[n+1,:2])],fill='#ffd779',width=2)
    # Map: unmodified tile layout throughout the entire animation.
    mx,my,size=668,239,224
    for j in range(N):
        for i in range(N):d.rectangle((mx+i*8,my+j*8,mx+i*8+7,my+j*8+7),fill=colors[j][i])
    d.line([(mx-3,my),(mx-3,my+size)],fill=CYAN,width=5)
    d.line([(mx+size+3,my),(mx+size+3,my+size)],fill=CYAN,width=5)
    d.line([(mx,my-3),(mx+size,my-3)],fill=GOLD,width=5)
    d.line([(mx,my+size+3),(mx+size,my+size+3)],fill=GOLD,width=5)
    text(d,(642,341),'X',14,CYAN,True);text(d,(908,341),'X',14,CYAN,True)
    text(d,(775,217),'Z',14,GOLD,True);text(d,(775,468),'Z',14,GOLD,True)
    arrow(d,(mx+12,494),(mx+68,494),CYAN);text(d,(mx+79,485),'+X',14,CYAN)
    arrow(d,(mx+151,482),(mx+151,506),GOLD);text(d,(mx+165,485),'+Z',14,GOLD)
    if stage<3:
        text(d,(47,483),'Topology model · shape is not to scale',12,MUTED)
    else:
        u,v=uv
        startx,starty=mx+size*origin[0],my+size*origin[1]
        d.ellipse((startx-8,starty-8,startx+8,starty+8),outline='#fff5c5',width=3)
        text(d,(startx-22,starty+25),'START',12,'#fff5c5')
        # Clip at each seam: never draw a spurious line across the map.
        for n in range(len(route)-1):
            if np.max(abs(route[n+1]-route[n]))<.1:
                d.line([(mx+size*route[n,0],my+size*route[n,1]),(mx+size*route[n+1,0],my+size*route[n+1,1])],fill='#ffe5a4',width=2)
        for fraction in (.12,.52,.88):
            a=(origin+fraction*velocity)%1
            c=a+.045*velocity
            if np.all(c<1):arrow(d,(mx+size*a[0],my+size*a[1]),(mx+size*c[0],my+size*c[1]),'#ffe5a4',2)
        avatar(d,mx+u*size,my+v*size,t,.7)
        # The position beacon stays visible even behind the surface.
        point=proj(surface(u,v,k,b));x,y=point[:2]
        d.ellipse((x-7,y-7,x+7,y+7),fill='#fff4bf',outline='#243d38',width=2)
        d.line([(x,y-7),(x,y-23)],fill='#fff4bf',width=2)
        d.rectangle((x-5,y-32,x+5,y-23),fill=CYAN)
        laps=min(int(progress+1e-8),2)
        if mode=='DIAGONAL':
            metric=f'ΔX = ΔZ: {progress:.2f} L   |   Distance: {progress*math.sqrt(2):.2f} L'
        else:metric=f'Distance: {progress:.2f} L     Laps: {laps}'
        text(d,(43,469),metric,15,FG,True)
        text(d,(43,493),'Marker = player position, including the hidden side',12,MUTED)
        if progress>=1.99:msg='2 laps. Same place. Same direction.'
        elif abs(progress-1)<.08:msg='1 lap: back at the same starting point.'
        elif mode=='DIAGONAL':
            if v<.09 or v>.93:msg='Cross the Z edge: bottom → top. X stays unchanged.'
            elif u<.09 or u>.93:msg='Cross the X edge: right → left. Z stays unchanged.'
            else:msg='Both coordinates wrap independently: x mod L, z mod L.'
        elif mode=='Z':
            msg='Bottom → top. Keep walking in +Z.' if v<.1 or v>.93 else 'z = (z₀ + distance) mod L · x stays unchanged'
        else:msg='Right → left. Keep walking in +X.' if u<.1 or u>.93 else 'x = (x₀ + distance) mod L · z stays unchanged'
        text(d,(35,539),msg,18,CYAN,True)
    if stage<3:
        msgs=['X ↔ X  and  Z ↔ Z: no wall, no new copy of the world.','One edge pair is connected; the other is still open.','Both directions are periodic. The world has no edge.']
        text(d,(35,539),'The remaining X edges are coming together.' if stage==2 and b<1 else msgs[stage],18,CYAN,True)
    d.line((30,575,930,575),fill='#365057',width=1)
    text(d,(30,589),'In-game terrain stays flat. Gravity stays normal.',16,FG)
    text(d,(30,613),'Block-style explainer · not gameplay footage · Y / height never wraps',12,MUTED)
    return im

if __name__=='__main__':
    import sys
    if '--preview' in sys.argv:
        for t in (0,8,16,20,32,40,44,45,46,48,54):render(t).save(OUT/f'preview-{t}.png')
    else:
        for f in range(FPS*DURATION):
            frame_path=FRAMES/f'{f:04d}.png'
            temp_path=FRAMES/'current.tmp'
            render(f/FPS).save(temp_path,format='PNG')
            temp_path.replace(frame_path)
            if f%75==0:print(f'frame {f}/{FPS*DURATION}',flush=True)
        for frame in sorted(FRAMES.glob('[0-9]*.png')):
            with Image.open(frame) as check:
                check.verify()
        base=['ffmpeg','-y','-loglevel','error','-framerate',str(FPS),'-i',str(FRAMES/'%04d.png')]
        subprocess.run(base+['-vf','palettegen=stats_mode=diff',str(FRAMES/'palette.png')],check=True)
        subprocess.run(base+['-i',str(FRAMES/'palette.png'),'-lavfi','paletteuse=dither=bayer:bayer_scale=4','-loop','0',str(OUT/'orbis-torus.gif')],check=True)
        subprocess.run(base+['-c:v','libx264','-pix_fmt','yuv420p','-crf','20','-movflags','+faststart',str(OUT/'orbis-torus.mp4')],check=True)
        print('Rendered GIF and MP4.',flush=True)
