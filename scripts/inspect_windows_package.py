"""Inspect the actual portable app, its corresponding source and five package hashes."""
# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


root = Path.cwd()
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
out = root / 'package-inspection'
out.mkdir(exist_ok=True)
text = (root / 'SHA256SUMS.txt').read_text(encoding='utf-8-sig')
expected = re.findall(r'Hash\s*:\s*([A-F0-9]{64})\s*\r?\nPath\s*:\s*([^\r\n]+)', text)
require(len(expected) == 5, 'Expected exactly five package checksums')
packages = []
for digest, original in expected:
    file = Path(original)
    require(file.is_file(), 'Missing packaged file: ' + file.name)
    with file.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    require(actual == digest.lower(), 'Package checksum mismatch: ' + file.name)
    packages.append({'name': file.name, 'bytes': file.stat().st_size, 'sha256': actual})

with zipfile.ZipFile(root / 'podium-air-windows-source.zip') as source:
    require(source.comment.decode() == commit, 'Corresponding source has a different commit')
    tracked = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
    checked = 0
    for name in filter(None, tracked):
        data = source.read(name)
        expected_blob = subprocess.check_output(['git', 'rev-parse', 'HEAD:' + name], text=True).strip()
        def blob(content):
            return hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()
        if blob(data) != expected_blob:
            try:
                data.decode('utf-8')
                data = data.replace(b'\r\n', b'\n')
            except UnicodeDecodeError:
                pass
        require(blob(data) == expected_blob, 'Corresponding source differs: ' + name)
        checked += 1

dist = root / 'app-desktop/build/compose/binaries/main/app/Podium Air'
jars = list((dist / 'app').rglob('*.jar'))
application = []
for file in jars:
    with zipfile.ZipFile(file) as jar:
        if 'com/podium/air/desktop/MainKt.class' in jar.namelist():
            application.append(file)
require(len(application) == 1, 'Could not identify the actual application JAR')
app = application[0]
# Retain the exact small JAR as diagnostic evidence even if a subsequent gate fails.
(out / 'application.jar').write_bytes(app.read_bytes())
(out / 'classpath-jars.json').write_text(json.dumps([file.relative_to(dist).as_posix() for file in jars], indent=2) + '\n')
with zipfile.ZipFile(app) as jar:
    names = jar.namelist()
    for name in ('com/podium/air/desktop/DavSession.class', 'com/podium/air/desktop/DavClient.class',
                 'com/podium/air/desktop/ScrobblingIntegration.class', 'com/podium/air/desktop/ReplayStoriesKt.class'):
        require(name in names, 'Missing feature: ' + name)
    models = {}
    for filename, digest in {
        'beat_this_int8.onnx': '9dc29f1fcd713d18f48a2755109fce01429ba6d1639607af8ae5c7449b47070f',
        'vocals_umxhq_int8.onnx': 'a2be987b55a29bc149d3a6ae99b08175d81f85ee292a8ea21f96c3a473bc94cb',
    }.items():
        matches = [name for name in names if name.endswith('/' + filename)]
        require(len(matches) == 1, 'Missing or duplicate model: ' + filename)
        data = jar.read(matches[0])
        require(hashlib.sha256(data).hexdigest() == digest, 'Bundled model bytes differ: ' + filename)
        models[filename] = {'bytes': len(data), 'sha256': digest}
    binaries = {}
    for filename in ('PodiumAnalysis.dll', 'PodiumMediaBridge.exe'):
        matches = [name for name in names if name.endswith('/' + filename)]
        require(len(matches) == 1, 'Missing native binary: ' + filename)
        data = jar.read(matches[0])
        require(data.startswith(b'MZ'), 'Invalid PE resource: ' + filename)
        binaries[filename] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    notices = [name for name in names if name.startswith('licenses/') and not name.endswith('/')]
    require('licenses/INDEX.txt' in names and 'licenses/THIRD_PARTY_NOTICES.md' in names, 'Missing notice index')
    for required in ('agpl', 'onnx', 'beat', 'unmix'):
        require(any(required in name.lower() for name in notices), 'Missing bundled license: ' + required)
    require(not any('sfpro' in name.lower().replace('-', '').replace('_', '') and name.lower().endswith(('.otf', '.ttf')) for name in names), 'Restricted SF Pro font was bundled')

with zipfile.ZipFile(root / 'podium-air-windows-portable.zip') as portable:
    packaged_app = [name for name in portable.namelist() if name.endswith('/app/' + app.name)]
    require(len(packaged_app) == 1, 'Portable ZIP lacks the application JAR')
    require(hashlib.sha256(portable.read(packaged_app[0])).digest() == hashlib.sha256(app.read_bytes()).digest(), 'Portable JAR differs from tested app')
    legal = [name for name in portable.namelist() if '/runtime/legal/' in name and not name.endswith('/')]
    require(len(legal) >= 40, 'Portable runtime legal files are incomplete')
    jlayer = []
    for file in jars:
        if file == app:
            continue
        with zipfile.ZipFile(file) as dependency:
            if 'javazoom/jl/decoder/Bitstream.class' in dependency.namelist():
                jlayer.append(file)
    require(len(jlayer) == 1, 'LGPL JLayer is not a separate replaceable JAR')
    relative = jlayer[0].relative_to(dist).as_posix()
    entries = [name for name in portable.namelist() if name.endswith('/' + relative)]
    require(len(entries) == 1, 'Portable ZIP lacks the separate JLayer JAR')
    require(hashlib.sha256(portable.read(entries[0])).digest() == hashlib.sha256(jlayer[0].read_bytes()).digest(), 'Portable JLayer differs from tested library')

report = {'sourceCommit': commit, 'trackedSourceFilesChecked': checked, 'packages': packages,
          'applicationJar': {'name': app.name, 'bytes': app.stat().st_size, 'sha256': hashlib.sha256(app.read_bytes()).hexdigest()},
          'models': models, 'nativeBinaries': binaries, 'noticeFiles': len(notices),
          'portableRuntimeLegalFiles': len(legal), 'replaceableRuntimeJars': len(jars),
          'lgplJLayer': {'path': relative, 'sha256': hashlib.sha256(jlayer[0].read_bytes()).hexdigest()}, 'unsigned': True}
(out / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')
print('PASS: Five package hashes, exact source, portable JAR, both pinned models, native PE resources and bundled licenses verified.')
