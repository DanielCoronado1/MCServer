#!/usr/bin/env python3
"""One isolated headless smoke; never touches the installed server or launches clients."""
import os,subprocess,threading,time,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
run=root/'mods/space/run'; run.mkdir(parents=True,exist_ok=True)
(run/'eula.txt').write_text('eula=true\n')
(run/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-players=1\nlevel-name=space-smoke\n')
env=os.environ.copy();env['JAVA_TOOL_OPTIONS']='-Ddolfinspace.smoke=true';env['JAVA_HOME']='/Users/emi0x7d1/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home'
p=subprocess.Popen(['./gradlew','runServer','--no-daemon','--args=nogui'],cwd=root/'mods/space',env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
ready=threading.Event();lines=[]
def read():
    for line in p.stdout:
        lines.append(line)
        if 'Done (' in line:ready.set()
threading.Thread(target=read,daemon=True).start()
end=time.monotonic()+150
while p.poll() is None and not ready.is_set() and time.monotonic()<end:ready.wait(1)
if ready.is_set():
    commands=sys.argv[1:]
    for command in commands:
        p.stdin.write(command+'\n');p.stdin.flush();time.sleep(2)
    p.stdin.write('stop\n');p.stdin.flush()
try:p.wait(timeout=40)
except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
out=root/'runtime/space';out.mkdir(parents=True,exist_ok=True);(out/'smoke.log').write_text(''.join(lines))
print(''.join(line for line in lines if any(s in line for s in ['ERROR','WARN','Exception','Done (','event=','BUILD','Located','nearest','biome'])))
sys.exit(0 if ready.is_set() and p.returncode==0 and not any('ERROR' in s for s in lines) and any('event=space_runtime_probe_complete' in s for s in lines) else 1)
