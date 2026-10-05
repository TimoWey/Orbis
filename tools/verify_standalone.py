"""Fail CI if the distributable needs or embeds the separate Toroidal World mod."""
import io
import json
import sys
import zipfile
from pathlib import Path

debug_expected = '--debug' in sys.argv[1:]
jars = [Path(p) for p in sys.argv[1:] if p != '--debug' and not p.endswith('-sources.jar')]
assert len(jars) == 1, f'Expected one distributable, found {jars}'
with zipfile.ZipFile(jars[0]) as jar:
    names = set(jar.namelist())
    meta = json.loads(jar.read('fabric.mod.json'))
    assert meta['id'] == 'orbis'
    assert meta['entrypoints']['main'] == ['dev.orbis.Orbis']
    debug_entrypoint = 'dev.orbis.debug.OrbisDebugClient'
    debug_classes = [name for name in names if name.startswith('dev/orbis/debug/')]
    assert (debug_entrypoint in meta['entrypoints']['client']) == debug_expected
    assert bool(debug_classes) == debug_expected, 'Debug classes leaked or are missing'
    assert meta['version'].endswith('-debug') == debug_expected
    if debug_expected:
        assert 'dev/orbis/debug/OrbisDebugScreen.class' in names
        assert 'dev/orbis/debug/DebugSurvey.class' in names
        assert 'assets/orbis_debug/lang/en_us.json' in names
    assert 'toroidal_world' not in meta['depends']
    assert 'toroidal_world' not in meta.get('provides', [])
    assert meta['breaks']['toroidal_world'] == '*'
    for name in ('dev/orbis/Orbis.class', 'com/toroidalworld/ToroidalWorldFabric.class',
                 'com/toroidalworld/engine/gen/LoopedChunkGenerator.class',
                 'LICENSE-orbis', 'META-INF/licenses/Toroidal-World-MIT.txt', meta['accessWidener']):
        assert name in names, f'Missing {name}'
    for config_name in meta['mixins']:
        config = json.loads(jar.read(config_name))
        for side in ('mixins', 'client', 'server'):
            for name in config.get(side, []):
                assert (config['package'] + '.' + name).replace('.', '/') + '.class' in names
        if config.get('plugin'):
            assert config['plugin'].replace('.', '/') + '.class' in names
    for nested in meta.get('jars', []):
        with zipfile.ZipFile(io.BytesIO(jar.read(nested['file']))) as lib:
            nested_meta = json.loads(lib.read('fabric.mod.json'))
            assert nested_meta['id'] != 'toroidal_world', 'Separate engine mod was nested'
print(f'{jars[0].name}: standalone packaging verified; debug={debug_expected}.')
