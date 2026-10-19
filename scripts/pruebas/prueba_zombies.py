#!/usr/bin/env python3
"""#9 isolated Fabric stats / MSPT regression (never installs mods or deletes a world).

Provision runtime/zombies-budget, accept the EULA, set pause-when-empty-seconds=0,
and create .zombies-budget-instance there. Baseline mode requires NO dolfinzombies JAR;
verify mode requires the built mod and Fabric API. Both performance runs need the same
real survival player online. NoAI is used only during stats inspection, never for load.
See docs/zombies/issue-09.md for reproducible baseline and spark acceptance procedure.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import queue
import re
import statistics
import subprocess
import threading
import time
import traceback
import uuid
import zipfile

REPO = Path(__file__).resolve().parents[2]
KINDS = ('minero', 'ladron', 'bombero', 'abismo', 'escudero', 'hambriento', 'griton',
         'bruja', 'granjero', 'congelado', 'jinete', 'bilioso', 'enano', 'contagiado', 'mimo', 'explorador')
ATTRS = {'max_health': ('vida', 1), 'attack_damage': ('danio', 1),
         'movement_speed': ('velocidad', .23), 'armor': ('armadura', 1), 'scale': ('escala', 1)}


def nbt_number(output):
    match = re.search(r'has the following entity data:\s*([-+\d.eE]+)[bdfsL]?\s*$', output, re.M)
    if not match:
        raise AssertionError('Missing numeric entity data: ' + output)
    value = float(match.group(1))
    if not math.isfinite(value):
        raise AssertionError('Non-finite attribute')
    return value


def mspt(output):
    match = re.search(r'Average time per tick:\s*([\d.]+)ms', output)
    if not match:
        raise AssertionError('Missing /tick query measurement: ' + output)
    return float(match.group(1))


def evaluate(baseline, loaded, windows):
    delta = loaded - baseline
    # Tick query reports to 0.1 ms. Probe is a separate necessary diagnostic, not a spark delta.
    enough = len(windows) >= 3 and all(w['frames'] == 100 and w['minEntities'] >= 25
                                     and w['minNearPlayer'] >= 25 for w in windows)
    return {'deltaMspt': delta, 'budgetMs': 2, 'validLoad': enough,
            'passed': enough and delta <= 2 and all(w['entityBudgetPassed'] for w in windows)}


class Session:
    def __init__(self, root, java, mode, checks):
        self.root, self.java, self.mode, self.checks = root, java, mode, checks
        self.lines, self.transcript = queue.Queue(), []
        self.log = None
        self.process = None

    def start(self):
        self.log = (self.root / f'zombies-09-{self.mode}.log').open('w')
        self.process = subprocess.Popen([self.java, '-Xms512M', '-Xmx2G',
            '-Dfabric.log.disableAnsi=true', '-Ddolfinzombies.tickProbe=true',
            '-jar', 'fabric-server-launch.jar', 'nogui'], cwd=self.root,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding='utf-8', errors='replace', bufsize=1)
        def reader():
            for line in self.process.stdout:
                self.transcript.append(line)
                self.log.write(line); self.log.flush(); self.lines.put(line)
        self.thread = threading.Thread(target=reader, daemon=True); self.thread.start()
        self.wait(r'Done \(', 180)
        return self

    def wait(self, pattern, timeout=30):
        end, output = time.monotonic() + timeout, ''
        while time.monotonic() < end:
            if self.process.poll() is not None and self.lines.empty():
                raise AssertionError(f'Server exited {self.process.returncode}; expected {pattern}')
            try:
                output += self.lines.get(timeout=.2)
                if re.search(pattern, output): return output
            except queue.Empty: pass
        raise AssertionError(f'Timeout: {pattern}; console tail: {output[-2000:]}')

    def command(self, command, expected=None):
        marker = '__Z9_' + uuid.uuid4().hex + '__'
        self.process.stdin.write(command + '\nsay ' + marker + '\n'); self.process.stdin.flush()
        output = self.wait(re.escape(marker))
        self.checks.append({'command': command, 'output': output})
        if expected is not None and not re.search(expected, output):
            raise AssertionError(f'{command}: expected {expected}, actual {output}')
        return output

    def stop(self):
        if self.process is not None and self.process.poll() is None:
            self.process.stdin.write('stop\n'); self.process.stdin.flush()
            try: self.process.wait(timeout=40)
            except subprocess.TimeoutExpired: self.process.kill(); self.process.wait()
        if hasattr(self, 'thread'): self.thread.join(timeout=5)
        if self.log: self.log.close()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-dir', type=Path, default=REPO / 'runtime/zombies-budget')
    parser.add_argument('--java', default=str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java')
    parser.add_argument('--mode', choices=['baseline', 'verify'], default='verify')
    parser.add_argument('--baseline-report', type=Path)
    parser.add_argument('--stats-only', action='store_true')
    parser.add_argument('--player', help='Existing real survival player; no fake-player or client launch')
    parser.add_argument('--seconds', type=int, default=60, help='Measured load interval after a 15-second warmup (>=30)')
    args = parser.parse_args(argv)
    root = args.server_dir.resolve()
    if not root.is_relative_to(REPO / 'runtime') or not (root / '.zombies-budget-instance').is_file():
        parser.error('Use a marked isolated instance under this checkout/runtime; see issue-09.md')
    if not (root / 'fabric-server-launch.jar').is_file() or not (root / 'eula.txt').is_file() or not re.search(r'^eula=true\s*$', (root / 'eula.txt').read_text(), re.M):
        parser.error('Provision Fabric and explicitly accept the EULA in the isolated instance')
    if not args.stats_only and (not args.player or not re.fullmatch(r'[A-Za-z0-9_]{1,16}', args.player)):
        parser.error('Performance mode requires --player NAME (real online survival player)')
    if args.seconds < 30: parser.error('--seconds must be >=30')
    if args.mode == 'baseline' and args.stats_only: parser.error('Baseline measures MSPT; it has no custom entity stats')
    if args.mode == 'verify' and not args.stats_only and args.baseline_report is None:
        parser.error('Performance verify requires the no-mod --baseline-report')
    properties = (root / 'server.properties').read_text()
    if not re.search(r'^pause-when-empty-seconds=0\s*$', properties, re.M):
        parser.error('Set pause-when-empty-seconds=0 in this isolated instance')
    mod_ids = []
    for jar in (root / 'mods').glob('*.jar'):
        with zipfile.ZipFile(jar) as archive:
            if 'fabric.mod.json' in archive.namelist(): mod_ids.append(json.loads(archive.read('fabric.mod.json'))['id'])
    if ('dolfinzombies' in mod_ids) != (args.mode == 'verify'):
        parser.error('Baseline must omit dolfinzombies; verify must contain it')
    canonical_properties = dict(line.split('=', 1) for line in properties.splitlines()
                                if '=' in line and not line.lstrip().startswith(('#', '!')))
    fingerprint = hashlib.sha256(json.dumps(canonical_properties, sort_keys=True).encode()).hexdigest()
    result = {'passed': False, 'mode': args.mode, 'statsOnly': args.stats_only,
              'serverPropertiesSha256': fingerprint, 'modIds': sorted(mod_ids), 'checks': [], 'probeWindows': []}
    baseline = None
    if args.baseline_report:
        baseline = json.loads(args.baseline_report.read_text())
        if baseline.get('mode') != 'baseline' or not baseline.get('passed') or baseline.get('serverPropertiesSha256') != fingerprint or sorted(baseline.get('modIds', [])) != sorted(m for m in mod_ids if m != 'dolfinzombies'):
            parser.error('Baseline must be successful, without the mod, with identical server.properties')
    session = Session(root, args.java, args.mode, result['checks'])
    config_path = root / 'config/dolfinzombies/general.json'
    original = None
    try:
        s = session.start()
        startup = ''.join(s.transcript)
        loaded = 'mechanics=all_16_kinds_active' in startup
        if loaded != (args.mode == 'verify'):
            raise AssertionError('Baseline must omit dolfinzombies; verify must load the integrated build')
        if args.mode == 'verify':
            original = config_path.read_bytes()
            general = json.loads(original); general['lunaDeSangre']['activo'] = False
            config_path.write_text(json.dumps(general))
            s.command('zombies recargar', r'errors=0')
            s.command('zombies lunadesangre detener', r'active=false')
        s.command('gamerule minecraft:spawn_mobs false', r'spawn_mobs')
        s.command('gamerule minecraft:advance_time false', r'advance_time')
        s.command('time set midnight', r'time|Time')
        s.command('weather clear', r'weather')
        s.command('difficulty normal', r'difficulty|already')
        s.command('forceload add 0 0 31 31', r'force|already marked')
        s.command('fill 0 79 0 31 79 31 minecraft:stone', r'filled|No blocks')
        s.command('fill 0 80 0 31 88 31 minecraft:air', r'filled|No blocks')
        if not args.stats_only:
            s.command(f'execute if entity @a[name={args.player},gamemode=survival] run say __Z9_PLAYER_PRESENT', r'__Z9_PLAYER_PRESENT')
            # Stay beyond the fixture's 64-block Mimo profile selection range during base-stat checks.
            s.command(f'tp {args.player} 128 90 128', r'Teleported')
        if args.mode == 'verify':
            s.command('tick freeze', r'freez|frozen|Frozen')
            effective = json.loads((root / 'config/dolfinzombies/diagnostics/last-reload.json').read_text())['zombies']
            result['stats'] = {}
            for kind in KINDS:
                ident = kind + '_zombie'; tag = 'z9_' + kind
                s.command(f'execute positioned 8 80 8 run zombies invocar {ident}', r'Invocados 1/1')
                s.command(f'tag @e[type=dolfinzombies:{ident},x=8,y=80,z=8,distance=..3,tag=!z9_fixture,sort=nearest,limit=1] add {tag}', r'Added tag')
                selector = f'@e[type=dolfinzombies:{ident},tag={tag},limit=1]'
                s.command(f'tag {selector} add z9_fixture', r'Added tag')
                s.command(f'data merge entity {selector} {{NoAI:1b,PersistenceRequired:1b}}', r'Modified entity data')
                expected = effective[ident]; actual = {}
                for attribute, (key, factor) in ATTRS.items():
                    output = s.command(f'data get entity {selector} attributes[{{id:"minecraft:{attribute}"}}].base')
                    actual[attribute] = nbt_number(output)
                    assert math.isclose(actual[attribute], expected[key] * factor, rel_tol=1e-5, abs_tol=1e-5), (ident, attribute, actual, expected)
                actual['health'] = nbt_number(s.command(f'data get entity {selector} Health'))
                assert math.isclose(actual['health'], expected['vida'], rel_tol=1e-5), (ident, actual)
                result['stats'][ident] = {'actual': actual, 'expectedConfig': expected}
            # These are owned fixtures only; remove mount entities before replacing the load.
            s.command('execute as @e[tag=z9_fixture] on vehicle run tag @s add z9_fixture')
            s.command('kill @e[tag=z9_fixture]')
            s.command('tick unfreeze', r'running|unfrozen|Unfrozen|unfreez')
        if not args.stats_only:
            s.command(f'tp {args.player} 16 83 16', r'Teleported')
            # Player on a raised glass platform: visible, nearby, survival, AI stays active.
            s.command('fill 14 82 14 18 82 18 minecraft:glass', r'filled|No blocks')
            s.command(f'effect give {args.player} minecraft:resistance infinite 4 true', r'effect')
            if args.mode == 'verify':
                for i in range(25):
                    kind = KINDS[i % 16]
                    s.command(f'execute positioned {3+i%5*5} 80 {3+i//5*5} run zombies invocar {kind}_zombie', r'Invocados 1/1')
                for kind in KINDS:
                    s.command(f'tag @e[type=dolfinzombies:{kind}_zombie,x=0,y=78,z=0,dx=31,dy=12,dz=31] add z9_load')
            time.sleep(15)
            probe_path = root / 'config/dolfinzombies/diagnostics/tick-budget.json'
            # Snapshot the watermark AFTER warmup; old/stats/startup windows cannot pass.
            last_tick = json.loads(probe_path.read_text())['serverTick'] if probe_path.exists() else -1
            deadline = time.monotonic() + args.seconds; samples = []
            while time.monotonic() < deadline:
                time.sleep(5)
                samples.append(mspt(s.command('tick query')))
                s.command(f'execute if entity @a[name={args.player},gamemode=survival,x=16,y=83,z=16,distance=..48] run say __Z9_PLAYER_PRESENT', r'__Z9_PLAYER_PRESENT')
                if args.mode == 'verify' and probe_path.exists():
                    window = json.loads(probe_path.read_text())
                    if window['serverTick'] > last_tick:
                        result['probeWindows'].append(window); last_tick = window['serverTick']
            result['msptSamples'] = samples; result['medianMspt'] = statistics.median(samples)
            result['sparkTranscript'] = s.command('spark tps', r'MSPT|Tick durations|TPS|tick durations')
            # Raw spark output is retained; this script's arithmetic uses vanilla tick query, not a fabricated spark result.
            if baseline is not None:
                result['performance'] = evaluate(baseline['medianMspt'], result['medianMspt'], result['probeWindows'])
                assert result['performance']['passed'], result['performance']
        result['passed'] = True
    except BaseException:
        result['error'] = traceback.format_exc()
        raise
    finally:
        if session.process is not None and session.process.poll() is None:
            try:
                session.command('execute as @e[tag=z9_load] on vehicle run tag @s add z9_load')
                session.command('kill @e[tag=z9_load]')
                session.command('kill @e[tag=z9_fixture]')
                session.command('tick unfreeze')
            except Exception: pass
        try: session.stop()
        except Exception:
            result['stopError'] = traceback.format_exc(); result['passed'] = False
            if session.process is not None and session.process.poll() is None:
                session.process.kill(); session.process.wait()
        if original is not None: config_path.write_bytes(original)
        result['exitCode'] = session.process.returncode if session.process else None
        if result['exitCode'] not in (None, 0): result['passed'] = False
        report = root / f'zombies-09-{args.mode}-result.json'
        report.write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps({'passed': result['passed'], 'report': str(report)}))
    return 0 if result['passed'] else 1

if __name__ == '__main__':
    raise SystemExit(main())
