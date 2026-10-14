"""Applies listing-v2.md and the v2 images to the Orbis Modrinth draft. Run on a machine that has the token file.

    python apply_modrinth.py <token-file> <folder with the images and listing-v2.md>

Never submits the project for review and never uploads a version.
"""
import json, re, sys, urllib.parse, urllib.request
from pathlib import Path

token = Path(sys.argv[1]).read_text().strip()
folder = Path(sys.argv[2])
project = 'orbis-worlds'
base = 'https://api.modrinth.com/v2'
agent = 'TimoWey/Orbis modrinth-refresh (github.com/TimoWey/Orbis)'


def call(method, path, data=None, ctype=None):
    request = urllib.request.Request(base + path, data=data, method=method)
    request.add_header('Authorization', token)
    request.add_header('User-Agent', agent)
    if ctype:
        request.add_header('Content-Type', ctype)
    with urllib.request.urlopen(request) as response:
        body = response.read()
        return json.loads(body) if body else None


current = call('GET', f'/project/{project}')
for image in current.get('gallery') or []:
    call('DELETE', f'/project/{project}/gallery?url=' + urllib.parse.quote(image['url'], safe=''))

order = [
    ('anim-torus.gif', 'URL_TORUS', 'How Orbis works', 'A square world folds into a torus: opposite edges are neighbours.', True),
    ('anim-atlas.gif', 'URL_ATLAS_GIF', 'Atlas map and globe', 'Switch between map and globe and turn the globe.', False),
    ('anim-walk-x.gif', 'URL_WALK_X', 'East and west', 'Walk across the blue edge and arrive on the opposite side.', False),
    ('anim-walk-z.gif', 'URL_WALK_Z', 'North and south', 'The gold edges connect the same way.', False),
    ('anim-walk-diagonal.gif', 'URL_WALK_DIAGONAL', 'Diagonal', 'Both directions wrap independently.', False),
    ('gui-atlas-map.png', 'URL_ATLAS_MAP', 'Atlas map', 'The explored world in the atlas (press M).', False),
    ('gui-atlas-globe.png', 'URL_ATLAS_GLOBE', 'Atlas globe', 'The same world as a globe.', False),
    ('gui-minimap.png', 'URL_MINIMAP', 'Minimap', 'The corner minimap in game.', False),
]
for n, (name, _, title, text, featured) in enumerate(order):
    query = urllib.parse.urlencode({'ext': name.rsplit('.', 1)[1], 'featured': str(featured).lower(),
                                    'title': title, 'description': text, 'ordering': n})
    call('POST', f'/project/{project}/gallery?{query}', (folder / name).read_bytes(), 'application/octet-stream')

gallery = call('GET', f'/project/{project}')['gallery']
by_title = {image['title']: image['url'] for image in gallery}
body = re.search(r'```markdown\n(.*?)\n```', (folder / 'listing-v2.md').read_text(encoding='utf-8'), re.S).group(1)
for _, placeholder, title, _, _ in order:
    body = body.replace(placeholder, by_title[title])
assert 'URL_' not in body
summary = ('Finite Minecraft worlds with no wall: walk off one edge and arrive on the opposite side. '
           'Terrain, mobs, structures, portals and light all continue across the seam.')
call('PATCH', f'/project/{project}', json.dumps({'body': body, 'description': summary[:256]}).encode(), 'application/json')
print('done', project, len(gallery), 'gallery images')
