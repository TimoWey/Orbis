"""Uploads one Orbis JAR as a new version of the Modrinth project. Run on a machine that has the token file.

    python upload_version.py <token-file> <jar> <changelog.md>

Takes game versions, loaders and dependencies from the project's newest existing version, so it matches what was
uploaded by hand before. Refuses to upload when that version number already exists. Never submits the project for
review. Prints the new version's id and number.
"""
import hashlib, json, sys, uuid, urllib.request
from pathlib import Path

token = Path(sys.argv[1]).read_text().strip()
jar = Path(sys.argv[2])
changelog = Path(sys.argv[3]).read_text()
project = 'orbis-worlds'
base = 'https://api.modrinth.com/v2'
agent = 'TimoWey/Orbis modrinth-refresh (github.com/TimoWey/Orbis)'
number = jar.stem.removeprefix('orbis-')


def call(method, path, data=None, ctype=None):
    request = urllib.request.Request(base + path, data=data, method=method)
    request.add_header('Authorization', token)
    request.add_header('User-Agent', agent)
    if ctype:
        request.add_header('Content-Type', ctype)
    with urllib.request.urlopen(request) as response:
        body = response.read()
        return json.loads(body) if body else None


versions = call('GET', f'/project/{project}/version')
if any(v['version_number'] == number for v in versions):
    sys.exit(f'version {number} already exists, nothing uploaded')
latest = versions[0]
meta = {
    'name': f'Orbis {number}',
    'version_number': number,
    'changelog': changelog,
    'dependencies': latest['dependencies'],
    'game_versions': latest['game_versions'],
    'version_type': 'alpha',
    'loaders': latest['loaders'],
    'featured': False,
    'status': 'listed',
    'project_id': latest['project_id'],
    'file_parts': ['file'],
    'primary_file': 'file',
}
boundary = uuid.uuid4().hex
data = jar.read_bytes()
print('jar', jar.name, hashlib.sha256(data).hexdigest(), 'copying settings from', latest['version_number'])
body = b''.join([
    f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n\r\n'.encode(), json.dumps(meta).encode(), b'\r\n',
    f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'
    'Content-Type: application/java-archive\r\n\r\n'.encode(), data, b'\r\n',
    f'--{boundary}--\r\n'.encode(),
])
created = call('POST', '/version', body, f'multipart/form-data; boundary={boundary}')
print('uploaded', created['id'], created['version_number'], ', '.join(created['game_versions']), ', '.join(created['loaders']))
