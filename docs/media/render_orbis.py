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
W,H,FPS,DURATION = 960,640,15,29
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

def render(t):
    k=float(ease((t-3)/4));b=float(ease((t-9)/5))
    stage=0 if t<3 else 1 if t<9 else 2 if t<17 else 3
    titles=['Eine endliche, quadratische Welt','Z-Kanten verbinden: ein Zylinder','X-Kanten verbinden: ein Torus','Immer +X laufen. Wieder am Start.']
    subtitles=['Gleiche Farben markieren zusammengehörende Kanten.','Oben und unten werden zu direkten Nachbarn.','Auch die beiden offenen Enden werden verbunden.','Nach jeder Weltbreite L wiederholt sich die Position.']
    im=Image.new('RGB',(W,H),BG);d=ImageDraw.Draw(im)
    d.rectangle((0,0,W,5),fill=CYAN)
    text(d,(30,23),'ORBIS',20,CYAN,True)
    text(d,(131,27),'ENDLICHE WELT · ENDLOSE WEGE',14,MUTED)
    for s,l in enumerate(['01  QUADRAT','02  Z VERBINDEN','03  X VERBINDEN','04  WEITERLAUFEN']):
        x=30+s*232
        d.rounded_rectangle((x,66,x+217,98),6,fill='#294447' if s==stage else '#1a2a31')
        text(d,(x+12,74),l,12,FG if s==stage else MUTED)
    text(d,(30,115),titles[stage],20,FG,True)
    text(d,(30,146),subtitles[stage],16,MUTED)
    d.rounded_rectangle((24,184,604,519),12,fill='#15262d')
    d.rounded_rectangle((623,184,936,519),12,fill='#1b3036')
    text(d,(643,199),'DIESELBE WELT ALS KARTE',12,MUTED)
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
    # A highlighted X cycle. The map remains authoritative when its back half is hidden.
    if stage==3:
        route=np.linspace(0,1,121)
        for n in range(120):
            a=proj(surface(route[n],.5,k,b));c=proj(surface(route[n+1],.5,k,b))
            d.line([tuple(a[:2]),tuple(c[:2])],fill='#ffd779',width=2)
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
    arrow(d,(mx+12,494),(mx+94,494),CYAN);text(d,(mx+104,485),'+X',14,CYAN)
    if stage<3:
        text(d,(47,483),'Topologisches Modell · Form nicht massstabsgetreu',12,MUTED)
    else:
        elapsed=min(max(t-17,0),10)
        distance=elapsed/5 # two complete laps
        u=(.25+distance)%1
        startx=mx+size*.25;py=my+size*.5
        d.ellipse((startx-8,py-8,startx+8,py+8),outline='#fff5c5',width=3)
        text(d,(startx-22,py+28),'START',12,'#fff5c5')
        arrow(d,(mx+6,py),(mx+size-5,py),'#ffe5a4',2)
        px=mx+u*size
        avatar(d,px,py,t,.7)
        # Position beacon with a dashed leader also remains readable on the back side.
        point=proj(surface(u,.5,k,b));x,y=point[:2]
        d.ellipse((x-7,y-7,x+7,y+7),fill='#fff4bf',outline='#243d38',width=2)
        d.line([(x,y-7),(x,y-23)],fill='#fff4bf',width=2)
        d.rectangle((x-5,y-32,x+5,y-23),fill=CYAN)
        laps=min(int(distance+1e-8),2)
        text(d,(43,469),f'Strecke: {distance:.2f} L     Runden: {laps}',16,FG,True)
        text(d,(43,493),'Punkt = Spielerposition, auch auf der Rückseite',12,MUTED)
        if distance>=1.99: msg='2 Runden. Gleicher Ort. Gleiche Blickrichtung.'
        elif abs(distance-1)<.13:msg='1 Runde: wieder am selben Startpunkt.'
        elif u<.1 or u>.93:msg='Rechts hinaus → links nahtlos weiter.'
        else:msg='x = (x₀ + Strecke) mod L · z bleibt gleich'
        text(d,(35,539),msg,18,CYAN,True)
    if stage<3:
        msgs=['X ↔ X  und  Z ↔ Z: keine Wand, keine neue Welt.','Ein Kantenpaar ist geschlossen; das zweite bleibt offen.','Beide Richtungen sind periodisch. Die Welt hat keinen Rand.']
        text(d,(35,539),'Jetzt werden auch die X-Kanten zusammengeführt.' if stage==2 and b<1 else msgs[stage],18,CYAN,True)
    d.line((30,575,930,575),fill='#365057',width=1)
    text(d,(30,589),'Im Spiel bleibt das Gelände flach und die Schwerkraft normal.',16,FG)
    text(d,(30,613),'Erklärmodell im Blockstil · keine Spielaufnahme · Y/Höhe bleibt unverändert',12,MUTED)
    return im

if __name__=='__main__':
    import sys
    if '--preview' in sys.argv:
        for t in (0,5,8,11,16,20,27):render(t).save(OUT/f'preview-{t}.png')
    else:
        for f in range(FPS*DURATION):
            render(f/FPS).save(FRAMES/f'{f:04d}.png')
            if f%75==0:print(f'frame {f}/{FPS*DURATION}',flush=True)
        for frame in sorted(FRAMES.glob('[0-9]*.png')):
            with Image.open(frame) as check:
                check.verify()
        base=['ffmpeg','-y','-loglevel','error','-framerate',str(FPS),'-i',str(FRAMES/'%04d.png')]
        subprocess.run(base+['-vf','palettegen=stats_mode=diff',str(FRAMES/'palette.png')],check=True)
        subprocess.run(base+['-i',str(FRAMES/'palette.png'),'-lavfi','paletteuse=dither=bayer:bayer_scale=4','-loop','0',str(OUT/'orbis-torus.gif')],check=True)
        subprocess.run(base+['-c:v','libx264','-pix_fmt','yuv420p','-crf','20','-movflags','+faststart',str(OUT/'orbis-torus.mp4')],check=True)
        print('Rendered GIF and MP4.',flush=True)
