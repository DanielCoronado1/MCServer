"""Import the preserved local server through Crafty 4's API and enable recovery.

Run on the Linux Docker host, with Crafty listening on loopback. Credentials are
read from its private generated file and are never printed or stored by this tool.
"""
import argparse
import json
import ssl
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path


class Crafty:
    def __init__(self, credentials, base_url='https://127.0.0.1:8443'):
        self.base_url = base_url
        self.context = ssl._create_unverified_context()  # Crafty's local self-signed certificate
        self.token = None
        self.token = self.request('POST', '/api/v2/auth/login', {
            'username': credentials['username'], 'password': credentials['password'],
        })['token']

    def request(self, method, path, body=None):
        headers = {'Content-Type': 'application/json'}
        if self.token:
            headers['Authorization'] = f'Bearer {self.token}'
        request = urllib.request.Request(self.base_url + path, method=method, headers=headers,
                                         data=json.dumps(body).encode() if body is not None else None)
        try:
            with urllib.request.urlopen(request, context=self.context, timeout=60) as response:
                payload = json.loads(response.read())
        except urllib.error.HTTPError as error:
            # Do not print authentication response bodies or tokens.
            raise RuntimeError(f'Crafty {method} {path}: HTTP {error.code}') from None
        if payload.get('status') != 'ok':
            raise RuntimeError(f'Crafty {method} {path} failed')
        return payload.get('data')


def configure(root, java, heap_max):
    credentials = root / 'runtime/crafty/config/default-creds.txt'
    deadline = time.monotonic() + 180
    while True:
        try:
            client = Crafty(json.loads(credentials.read_text()))
            break
        except (FileNotFoundError, json.JSONDecodeError, urllib.error.URLError):
            if time.monotonic() >= deadline:
                raise TimeoutError('Crafty did not initialize within three minutes') from None
            time.sleep(3)
    existing = [s for s in client.request('GET', '/api/v2/servers') if s['server_name'] == 'DolfinServer']
    if len(existing) > 1:
        raise RuntimeError('Multiple DolfinServer entries found; refusing to choose one')
    if existing:
        server_id = existing[0]['server_id']
    else:
        source = root / 'runtime/server'
        if 'eula=true' not in (source / 'eula.txt').read_text():
            raise RuntimeError('The preserved server must already have an accepted EULA')
        archive = root / 'runtime/crafty/import/upload/dolfin.zip'
        archive.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as zipped:
            for path in sorted(source.rglob('*')):
                if path.is_file():
                    zipped.write(path, path.relative_to(source).as_posix())
        # The container's Crafty user must be able to remove the consumed archive.
        archive.parent.chmod(0o770)
        archive.chmod(0o640)
        data = client.request('POST', '/api/v2/servers', {
            'name': 'DolfinServer', 'monitoring_type': 'minecraft_java',
            'minecraft_java_monitoring_data': {'host': '127.0.0.1', 'port': 25565},
            'create_type': 'minecraft_java',
            'minecraft_java_create_data': {
                'create_type': 'import_server',
                'import_server_create_data': {
                    'archive_name': 'dolfin.zip', 'archive_internal_path': '',
                    'jarfile': 'fabric-server-launch.jar', 'mem_min': 2, 'mem_max': heap_max,
                    'server_properties_port': 25565,
                },
            },
        })
        server_id = data['new_server_id']
        deadline = time.monotonic() + 300
        while client.request('GET', f'/api/v2/servers/{server_id}/stats').get('importing', True):
            if time.monotonic() >= deadline:
                raise TimeoutError('Crafty server import did not finish within five minutes')
            time.sleep(3)
    client.request('PATCH', f'/api/v2/servers/{server_id}', {
        'execution_command': f'{java} -Xms2G -Xmx{heap_max}G -XX:+UseG1GC -jar fabric-server-launch.jar nogui',
        'auto_start': True, 'auto_start_delay': 10, 'crash_detection': True,
        'shutdown_timeout': 90,
    })
    stats = client.request('GET', f'/api/v2/servers/{server_id}/stats')
    if not stats.get('running'):
        client.request('POST', f'/api/v2/servers/{server_id}/action/start_server')
    info = client.request('GET', f'/api/v2/servers/{server_id}')
    print(json.dumps({k: info[k] for k in ('server_id', 'path', 'auto_start', 'crash_detection', 'execution_command')}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--java', default='/usr/lib/jvm/java-25-openjdk-amd64/bin/java')
    parser.add_argument('--heap-max', type=int, default=10)
    args = parser.parse_args()
    configure(args.root.resolve(), args.java, args.heap_max)
