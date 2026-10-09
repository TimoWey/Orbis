from pathlib import Path
from PIL import Image
import io,json,zipfile,sys
shot=Image.open(sys.argv[1]).convert('RGB')
texture=sys.argv[2] if len(sys.argv)>2 else 'player'
center=tuple(map(int,sys.argv[3:5])) if len(sys.argv)>4 else (640,400)
with zipfile.ZipFile('/workspace/minecraft-test/minecraft-client.jar') as jar:
 raw=jar.read('assets/minecraft/textures/map/decorations/'+texture+'.png')
 source=Image.open(io.BytesIO(raw)).convert('RGBA')
checks=[]
for y in range(8):
 for x in range(8):
  pixel=source.getpixel((x,y))
  if pixel[3]==255:
   for dy in range(2):
    for dx in range(2):
     screen=(center[0]-8+2*x+dx,center[1]-8+2*y+dy)
     checks.append((screen,shot.getpixel(screen),pixel[:3]))
errors=[c for c in checks if c[1]!=c[2]]
result={'screenshot':Path(sys.argv[1]).name,'vanilla_sprite':texture,'source_size':[8,8],'gui_scale':2,'center':center,'opaque_screen_pixels_checked':len(checks),'mismatches':len(errors)}
print(json.dumps(result,indent=2))
if errors:print('First mismatches:',errors[:5],file=sys.stderr)
assert not errors
