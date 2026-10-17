#!/usr/bin/env python3
"""Headless Fabric #3 integration: fixture probes, live reload, exact nights, restart persistence.
Uses ONLY the explicitly provisioned runtime/zombies-smoke instance. Preserves configs;
world fixtures occupy x=160..180,z=160..180,y=30..90. Logs/results survive any failure.
Visual sky/fog, real player sleep and multiplayer cap acceptance remain manual.
"""
import argparse
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import threading
import time
import traceback

class Session:
    def __init__(self, root, java, label, checks):
        self.root, self.checks = root, checks
        self.lines = queue.Queue()
        self.log = (root / ('console-bloodmoon-' + label + '.log')).open('w')
        self.process = subprocess.Popen([java, '-Xms512M', '-Xmx2G', '-Dfabric.log.disableAnsi=true', '-jar', 'fabric-server-launch.jar', 'nogui'], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        def reader():
            for line in self.process.stdout:
                self.log.write(line); self.log.flush(); self.lines.put(line)
        self.thread = threading.Thread(target=reader, daemon=True); self.thread.start()
        self.wait(r'Done \(', 180)
    def wait(self, pattern, timeout=30):
        deadline = time.monotonic() + timeout; output = ''
        while time.monotonic() < deadline:
            if self.process.poll() is not None and self.lines.empty(): raise AssertionError(f'Server exited {self.process.returncode}: expected {pattern}, see {self.log.name}')
            try:
                line = self.lines.get(timeout=.2); output += line
                if re.search(pattern, line): return output
            except queue.Empty: pass
        raise AssertionError(f'Timeout for {pattern}, see {self.log.name}, last output {output[-3000:]}')
    def command(self, command, expected):
        marker = '__BLOODMOON_%d__' % len(self.checks)
        self.process.stdin.write(command + '\nsay ' + marker + '\n'); self.process.stdin.flush()
        output = self.wait(marker)
        check = dict(command=command, expected=expected, output=output); self.checks.append(check)
        assert re.search(expected, output), str(check)
        return output
    def tick(self):
        # A command barrier does not ensure END_SERVER_TICK ran; wait for a diagnostic command
        # on the following frame. This wait is bounded and unrelated to scheduler correctness.
        time.sleep(.2)
    def stop(self):
        if self.process.poll() is None:
            self.process.stdin.write('stop\n'); self.process.stdin.flush()
            self.process.wait(timeout=60)
        self.thread.join(timeout=5); self.log.close()
        assert self.process.returncode == 0

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server-dir', type=Path, default=Path('runtime/zombies-smoke'))
    p.add_argument('--java', default=str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java')
    args=p.parse_args(); root=args.server_dir.resolve(); config=root/'config/dolfinzombies'
    assert (root/'eula.txt').exists() and 'eula=true' in (root/'eula.txt').read_text()
    assert (root/'fabric-server-launch.jar').exists()
    originals={f:f.read_bytes() for f in config.rglob('*.json') if 'diagnostics' not in f.parts}
    checks=[]; result={'passed':False,'checks':checks}; session=None
    def write(name, value): (config/name).write_text(json.dumps(value))
    general=json.loads((config/'general.json').read_text())
    general['activo']=True;general['lunaDeSangre']={'activo':True,'cadaNNoches':7,'probabilidadPorNoche':1,'multiplicadorSpawns':2.5,'soloEspeciales':True,'aviso':True,'cieloRojo':True,'dormirBloqueado':True,'recompensaXpExtra':1.5}
    general['maxZombiesEspecialesPorJugador']=12
    # A far future day avoids any previous smoke's lastNight watermark.
    day=int(time.time())%100000+100000
    seventh=day+(6-day%7)%7  # floor(time/24000)+1 divisible by seven
    try:
        write('general.json',general)
        session=Session(root,args.java,'first',checks);s=session
        s.command('gamerule minecraft:advance_time false',r'advance_time|Incorrect argument')
        s.command('zombies lunadesangre detener',r'active=false')
        s.command('forceload add 160 160 180 180',r'force|already marked')
        s.command('fill 160 30 160 180 90 180 minecraft:air',r'filled|No blocks')
        s.command('fill 160 49 160 180 49 180 minecraft:stone',r'filled|No blocks')
        s.command('fillbiome 160 30 160 180 90 180 minecraft:plains',r'biomes|biome')
        s.command(f'time set {seventh*24000+13000}',r'time|Time');s.tick()
        s.command('zombies diagnostico',r'active=true.*lastNight='+str(seventh+1))
        # Exact pool settings read from live generation. Fractional multiplier is retained.
        minero=json.loads((config/'zombies/minero_zombie.json').read_text());minero.update(peso=8,grupo=[2,3],biomas=['minecraft:plains'],biomasExcluidos=[],alturaY=[30,80],luzMax=0)
        minero['lunaDeSangre']['pesoExtra']=1.5;write('zombies/minero_zombie.json',minero)
        s.command('zombies recargar',r'errors=0');s.tick()
        s.command('execute positioned 170 50 170 run zombies inspeccionar minero_zombie',r'reason=eligible weight=12 group=2,3 placement=true')
        s.command('execute positioned 170 81 170 run zombies inspeccionar minero_zombie',r'reason=height')
        s.command('execute positioned 170 50 170 run zombies inspeccionar mimo_zombie',r'reason=mimo weight=0')
        s.command('execute positioned 170 50 170 run zombies inspeccionar congelado_zombie',r'reason=biome')
        s.command('execute positioned 170 50 170 run zombies inspeccionar enano_zombie',r'reason=height|reason=dwarf')
        # Reload biome exclusion and zero weight without rebuilding registries.
        minero['biomasExcluidos']=['minecraft:plains'];write('zombies/minero_zombie.json',minero)
        s.command('zombies recargar',r'errors=0')
        s.command('execute positioned 170 50 170 run zombies inspeccionar minero_zombie',r'reason=biome')
        minero['biomasExcluidos']=[];minero['peso']=0;write('zombies/minero_zombie.json',minero)
        s.command('zombies recargar',r'errors=0')
        s.command('execute positioned 170 50 170 run zombies inspeccionar minero_zombie',r'weight=0 group=2,3')
        # Abismo's hard water floor survives custom administrator biomes.
        abyss=json.loads((config/'zombies/abismo_zombie.json').read_text());abyss['biomas']=['minecraft:plains'];abyss['biomasExcluidos']=[];write('zombies/abismo_zombie.json',abyss)
        s.command('zombies recargar',r'errors=0')
        s.command('execute positioned 170 50 170 run zombies inspeccionar abismo_zombie',r'reason=water_depth')
        s.command('fill 168 50 168 172 53 172 minecraft:water',r'filled')
        s.command('execute positioned 170 51 170 run zombies inspeccionar abismo_zombie',r'reason=water_depth')
        s.command('fill 168 54 168 172 54 172 minecraft:water',r'filled')
        s.command('execute positioned 170 51 170 run zombies inspeccionar abismo_zombie',r'reason=eligible')
        # Snow guard cannot be bypassed by changing allowed biome list.
        snow=json.loads((config/'zombies/congelado_zombie.json').read_text());snow['biomas']=['minecraft:plains','minecraft:snowy_plains'];snow['biomasExcluidos']=[];write('zombies/congelado_zombie.json',snow)
        s.command('zombies recargar',r'errors=0')
        s.command('execute positioned 175 50 175 run zombies inspeccionar congelado_zombie',r'reason=snow')
        s.command('fillbiome 160 30 160 180 90 180 minecraft:snowy_plains',r'biome')
        s.command('execute positioned 175 50 175 run zombies inspeccionar congelado_zombie',r'reason=eligible')
        # Blood stats apply to new entities, persist through reload/restart; dawn exemptions.
        s.command('execute positioned 175 50 175 run zombies invocar bombero_zombie',r'Invocados 1/1')
        s.command('execute positioned 175 50 175 run zombies invocar hambriento_zombie',r'Invocados 1/1')
        s.command('tag @e[type=dolfinzombies:hambriento_zombie] add moon_persist',r'Added tag|already')
        s.command('data get entity @e[type=dolfinzombies:hambriento_zombie,tag=moon_persist,limit=1] Health',r'20.0f')
        s.command('save-all flush',r'Saved the game');s.stop();session=None
        session=Session(root,args.java,'restart',checks);s=session
        s.command('zombies diagnostico',r'active=true.*lastNight='+str(seventh+1))
        s.command('data get entity @e[type=dolfinzombies:hambriento_zombie,tag=moon_persist,limit=1] Health',r'20.0f')
        s.command(f'time set {(seventh+1)*24000+1000}',r'time|Time');s.tick()
        s.command('zombies diagnostico',r'active=false')
        s.command('data get entity @e[type=dolfinzombies:hambriento_zombie,tag=moon_persist,limit=1] Fire',r'[1-9][0-9]*s')
        s.command('data get entity @e[type=dolfinzombies:bombero_zombie,limit=1,sort=nearest] Fire',r'0s|-[0-9]+s')
        s.command(f'time set {(seventh+1)*24000+13000}',r'time|Time');s.tick()
        s.command('zombies diagnostico',r'active=false')
        s.command(f'time set {(seventh+7)*24000+13000}',r'time|Time');s.tick()
        s.command('zombies diagnostico',r'active=true')
        s.command('zombies lunadesangre detener',r'active=false');s.tick()
        s.command('zombies diagnostico',r'active=false')
        s.command('zombies lunadesangre iniciar',r'active=true')
        general['activo']=False;write('general.json',general)
        s.command('zombies recargar',r'errors=0');s.tick()
        s.command('zombies diagnostico',r'active=false')
        report=json.loads((config/'diagnostics/runtime.json').read_text())
        assert 'spawnCounters' in report and len(report['recentSpawns'])<=32
        s.stop();session=None;result['passed']=True
    except Exception:
        result['error']=traceback.format_exc();raise
    finally:
        if session is not None: session.stop()
        for path,raw in originals.items():path.write_bytes(raw)
        (root/'bloodmoon-smoke-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'passed':True,'checks':len(checks),'artifact':str(root/'bloodmoon-smoke-result.json')}))
if __name__=='__main__':main()
