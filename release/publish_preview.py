"""Publish the explicitly approved preview, keeping all uploads private until complete."""
import hashlib
import json
import mimetypes
import os
import urllib.parse
import urllib.request
from pathlib import Path

manifest = json.loads(Path('release/preview.json').read_text())
repo = os.environ['GH_REPO']
assert repo == 'kaizen-flims/Podium-Air-Windows-'
source = os.environ['SOURCE_SHA']
source_branch = os.environ.get('SOURCE_BRANCH', 'main')
assert source_branch in ('main', 'feat/complete-native-parity')
assert manifest.get('source_branch', 'main') == source_branch
token = os.environ['GH_TOKEN']
base = f'https://api.github.com/repos/{repo}'
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json',
           'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Podium-Air-approved-preview'}

def request(url, method='GET', payload=None, raw=None, content_type=None):
    target = urllib.parse.urlparse(url)
    assert target.scheme == 'https' and target.netloc in ('api.github.com', 'uploads.github.com')
    data = raw if raw is not None else (json.dumps(payload).encode() if payload is not None else None)
    h = headers | ({'Content-Type': content_type or 'application/json'} if data is not None else {})
    with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=h, method=method), timeout=300) as response:
        body = response.read()
        return json.loads(body) if body else None

releases = request(base + '/releases?per_page=100')
existing = [r for r in releases if r['tag_name'] == manifest['tag']]
if any(not r['draft'] for r in existing):
    print('The approved preview is already published; no public assets were changed.')
    raise SystemExit(0)
assert len(existing) <= 1, 'Multiple drafts require review before publishing'
promotion_sha = os.environ.get('PROMOTION_SHA', source)
def verify_source():
    assert request(base + '/git/ref/heads/' + source_branch)['object']['sha'] == promotion_sha, 'Source branch changed during publication'
    if promotion_sha != source:
        diff = request(base + '/compare/' + source + '...' + promotion_sha)
        assert diff['total_commits'] == 1 and [file['filename'] for file in diff['files']] == ['release/promotion.json'], 'Promotion must change only its reviewed build pointer'
    run = request(base + '/actions/runs/' + os.environ['BUILD_RUN'])
    assert run['head_sha'] == source and run['conclusion'] == 'success' and run['event'] == 'push', 'Preview needs the exact successful source build'
verify_source()
notes = Path('release/preview-notes.md').read_text() + '\n\n' + Path('release-assets/BUILD_EVIDENCE.txt').read_text()
metadata = {'tag_name': manifest['tag'], 'target_commitish': source, 'name': manifest['title'],
            'body': notes, 'draft': True, 'prerelease': True, 'make_latest': 'false'}
if existing:
    assert existing[0]['name'] == manifest['title'], 'Refusing to modify an unrelated draft'
    release = request(base + f"/releases/{existing[0]['id']}", 'PATCH', metadata)
else:
    release = request(base + '/releases', 'POST', metadata)
assert release['draft'] and release['target_commitish'] == source
# Drafts can have no Git tag yet. Validate their target and upload by release ID;
# verify the actual public Git tag only after publishing.
for asset in release['assets']:
    request(base + f"/releases/assets/{asset['id']}", 'DELETE')
files = sorted(p for p in Path('release-assets').iterdir() if p.is_file())
assert len(files) == 8
upload = release['upload_url'].split('{', 1)[0]
for path in files:
    data = path.read_bytes()
    expected = 'sha256:' + hashlib.sha256(data).hexdigest()
    result = request(upload + '?' + urllib.parse.urlencode({'name': path.name}), 'POST',
                     raw=data, content_type=mimetypes.guess_type(path.name)[0] or 'application/octet-stream')
    assert result['name'] == path.name and result['state'] == 'uploaded' and result['size'] == len(data), path.name
    if result.get('digest'):
        assert result['digest'] == expected, path.name
    print('Uploaded and verified ' + path.name, flush=True)
verify_source()
state = request(base + f"/releases/{release['id']}")
assert state['draft'] and state['target_commitish'] == source and len(state['assets']) == len(files)
public = request(base + f"/releases/{release['id']}", 'PATCH', {'draft': False, 'prerelease': True, 'make_latest': 'false'})
ref = request(base + '/git/ref/tags/' + urllib.parse.quote(manifest['tag'], safe=''))['object']
if ref['type'] == 'tag':
    ref = request(base + '/git/tags/' + ref['sha'])['object']
assert ref['type'] == 'commit' and ref['sha'] == source
assert not public['draft'] and public['prerelease']
print(json.dumps({'published': public['html_url'], 'source': source, 'assets': len(public['assets'])}))
with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
    summary.write(public['html_url'] + '\n')
