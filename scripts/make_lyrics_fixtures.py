"""Encode real audio and embed lyrics in the container, with no sidecars or synthetic UI state."""
# SPDX-License-Identifier: GPL-3.0-only
import json
import struct
import subprocess
from pathlib import Path

root = Path('embedded-lyrics-fixtures')
root.mkdir(exist_ok=True)
cases = []

def encode(name, codec, tags=None):
    path = root / name
    command = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i',
               'sine=frequency=440:duration=6', '-ac', '1', '-c:a', codec]
    for key, value in (tags or {}).items():
        command += ['-metadata', f'{key}={value}']
    subprocess.run(command + ['-y', str(path)], check=True)
    return path

def synchsafe(n):
    return bytes((n >> shift) & 127 for shift in (21, 14, 7, 0))

def id3(path, name, payload, version=3):
    data = path.read_bytes()
    if data[:3] == b'ID3':
        n = 0
        for byte in data[6:10]:
            n = (n << 7) | byte
        data = data[10 + n:]
    frame = name.encode() + (synchsafe(len(payload)) if version == 4 else struct.pack('>I', len(payload))) + b'\0\0' + payload
    path.write_bytes(b'ID3' + bytes((version, 0, 0)) + synchsafe(len(frame)) + frame + data)

marker = 'Podium embedded USLT'
path = encode('id3-uslt.mp3', 'libmp3lame')
id3(path, 'USLT', b'\1eng\xff\xfe\0\0' + b'\xff\xfe' + (marker + '\nবাংলা গান').encode('utf-16le'))
cases.append({'file': str(path.resolve()), 'marker': marker})

marker = 'Podium synchronized SYLT'
path = encode('id3-sylt.mp3', 'libmp3lame')
payload = b'\3eng\2\1\0' + marker.encode() + b'\0' + struct.pack('>I', 0)
payload += b'Second timed line\0' + struct.pack('>I', 1500)
id3(path, 'SYLT', payload, 4)
cases.append({'file': str(path.resolve()), 'marker': marker})

marker = 'Podium enhanced FLAC'
text = '[00:00.00]<00:00.00>Podium <00:00.50>enhanced <00:01.00>FLAC\n[00:02.00]Second line'
path = encode('android-enhanced.flac', 'flac', {'BITCHORD_LYRICS': text, 'LYRICS': 'Plain fallback'})
cases.append({'file': str(path.resolve()), 'marker': marker})

marker = 'Podium embedded Opus'
path = encode('vorbis-comments.opus', 'libopus', {'LYRICS': marker + '\nSecond untimed line'})
cases.append({'file': str(path.resolve()), 'marker': marker})

marker = 'Podium embedded M4A'
path = encode('mp4-lyrics.m4a', 'aac', {'lyrics': '[00:00.00]' + marker + '\n[00:02.00]Second timed line'})
cases.append({'file': str(path.resolve()), 'marker': marker})

Path('embedded-lyrics-cases.json').write_text(json.dumps(cases, indent=2), encoding='utf-8')
print('Generated real MP3 USLT/SYLT, Android FLAC, Opus and M4A tagged audio fixtures.')
