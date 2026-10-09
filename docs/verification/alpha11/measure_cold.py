# Local verification harness; requires the isolated /workspace/minecraft-test profile and mc_ui.py.
# Screenshot capture can block sampling; reported threshold times are observed upper bounds.
from pathlib import Path
import subprocess,time,sys,json,os,threading
label=sys.argv[1]
game=Path('/workspace/minecraft-test/client')
out=Path('/workspace/Orbis/docs/verification/alpha11')
log=open(out/(label+'.log'),'w')
proc=subprocess.Popen([sys.executable,'/workspace/scratch/launch_singleplayer.py'],stdout=log,stderr=subprocess.STDOUT)
started=time.monotonic(); joined=None; rows=[]; shots=set(); done=False
while proc.poll() is None:
    now=time.monotonic()
    if joined is None:
        text=(out/(label+'.log')).read_text(errors='replace')
        if 'logged in with entity id' in text:
            joined=now
            print('joined',label,flush=True)
    if joined is not None and not done:
        elapsed=now-joined
        count=0
        for path in game.glob('orbis-atlas-cache/*/*.tiles/*/*.tiles'):
            data=path.read_bytes()
            count+=sum(data[i] in (1,2,3) for i in range(8,len(data)-1032,1033))
        rows.append({'seconds':round(elapsed,3),'tiles':count})
        (out/(label+'-fill.json')).write_text(json.dumps(rows,indent=2)+'\n')
        for second in [1,3,5,10,20,30]:
            if elapsed>=second and second not in shots:
                shots.add(second)
                subprocess.run([sys.executable,'/workspace/scratch/mc_ui.py','shot',str(out/(label+f'-{second:02d}s.png'))],stdout=subprocess.DEVNULL)
                print(label,'seconds',round(elapsed,2),'tiles',count,flush=True)
        if elapsed>31 and (count >= 1246 or elapsed > 180):
            done=True
            print('measurement complete',label,flush=True)
    time.sleep(.25 if not done else 1)
log.close()
raise SystemExit(proc.returncode)
