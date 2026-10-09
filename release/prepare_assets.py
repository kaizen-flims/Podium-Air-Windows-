"""Prepare and validate assets from one successful Windows build; never rebuild them."""
import argparse
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--packages', type=Path, required=True)
parser.add_argument('--verification', type=Path, required=True)
parser.add_argument('--source-sha', required=True)
parser.add_argument('--run-id', required=True)
args = parser.parse_args()
manifest = json.loads(Path('release/preview.json').read_text())
assert re.fullmatch(r'[0-9a-f]{40}', args.source_sha)
assert args.run_id.isdigit()
reports = list(args.verification.rglob('TEST-*.xml'))
tests = failures = 0
for report in reports:
    suite = ET.parse(report).getroot()
    tests += int(suite.get('tests', 0))
    failures += int(suite.get('failures', 0)) + int(suite.get('errors', 0))
assert tests >= manifest['minimum_tests'] and failures == 0, (tests, failures)
for name in ('ui-smoke.txt', 'platform-smoke.txt', 'audio-smoke.txt', 'installer-smoke.txt',
             'dpi-1.5-smoke.txt', 'dpi-2.0-smoke.txt', 'performance-smoke.txt',
             'codec-flac-smoke.txt', 'codec-opus-smoke.txt', 'codec-mp3-smoke.txt',
             'codec-m4a-smoke.txt', 'codec-aiff-smoke.txt'):
    assert 'PASS:' in (args.verification / name).read_text(encoding='utf-8-sig'), name
source = args.packages / 'podium-air-windows-source.zip'
with zipfile.ZipFile(source) as archive:
    assert archive.comment.decode() == args.source_sha, 'Source archive is from a different commit'
    for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md',
                 'app-desktop/src/main/resources/licenses/skiko/LICENSE',
                 'app-desktop/src/main/resources/licenses/skiko/NOTICE',
                 'app-desktop/src/main/resources/licenses/skia/LICENSE'):
        assert name in archive.namelist(), name
checksums = (args.packages / 'SHA256SUMS.txt').read_text(encoding='utf-8-sig')
expected = re.findall(r'Hash\s*:\s*([A-F0-9]{64})\s*\r?\nPath\s*:\s*([^\r\n]+)', checksums)
assert len(expected) == 5, 'Expected MSI, EXE, portable and two source archives'
for digest, original_path in expected:
    filename = original_path.split('\\')[-1]
    candidates = [p for p in args.packages.rglob(filename) if p.is_file()]
    assert len(candidates) == 1, filename
    with candidates[0].open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    assert actual == digest.lower(), filename
out = Path('release-assets')
assert not out.exists(), 'Refusing to overwrite previously prepared assets'
out.mkdir()
version = manifest['package_version']
names = {
    f'Podium Air-{version}.msi': f'Podium-Air-Windows-{version}-x64.msi',
    f'Podium Air-{version}.exe': f'Podium-Air-Windows-{version}-x64-setup.exe',
    'podium-air-windows-portable.zip': f'Podium-Air-Windows-{version}-x64-portable.zip',
    'podium-air-windows-source.zip': 'podium-air-windows-source.zip',
    'dependency-sources.zip': 'dependency-sources.zip',
}
for old, new in names.items():
    matches = [p for p in args.packages.rglob(old) if p.is_file()]
    assert len(matches) == 1, old
    shutil.copyfile(matches[0], out / new)
shutil.copyfile('THIRD_PARTY_NOTICES.md', out / 'THIRD_PARTY_NOTICES.md')
repo = 'kaizen-flims/Podium-Air-Windows-'
evidence = f'Source commit: {args.source_sha}\nWindows build: https://github.com/{repo}/actions/runs/{args.run_id}\nTests: {tests}; failures/errors: {failures}\n' + manifest['approval'] + '\n'
for name in ('performance-metrics.txt', 'installer-smoke.txt', 'audio-smoke.txt'):
    evidence += (args.verification / name).read_text(encoding='utf-8-sig').strip() + '\n'
(out / 'BUILD_EVIDENCE.txt').write_text(evidence)
lines = []
for path in sorted(out.iterdir()):
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    lines.append(f'{digest}  {path.name}')
(out / 'SHA256SUMS.txt').write_text('\n'.join(lines) + '\n')
print(f'Validated {tests} tests and exact-source packages; prepared {len(lines) + 1} release assets.')
