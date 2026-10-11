"""Loopback-only read-only WebDAV fixture for the packaged Windows integration gate."""
# SPDX-License-Identifier: GPL-3.0-only
import argparse
import base64
import io
import math
import struct
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote

parser = argparse.ArgumentParser()
parser.add_argument('--port-file', required=True)
args = parser.parse_args()
pcm = b''.join(struct.pack('<h', int(math.sin(i * 2 * math.pi * 440 / 44100) * 1500)) for i in range(44100 * 6))
buffer = io.BytesIO()
with wave.open(buffer, 'wb') as file:
    file.setnchannels(1); file.setsampwidth(2); file.setframerate(44100); file.writeframes(pcm)
audio = buffer.getvalue()
auth = 'Basic ' + base64.b64encode(b'smoke:smoke-only').decode()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass
    def authorized(self):
        if self.headers.get('Authorization') != auth:
            self.send_error(401)
            return False
        return True
    def send(self, code, data, mime):
        self.send_response(code)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('ETag', '"fixture"')
        self.end_headers()
        self.wfile.write(data)
    def do_PROPFIND(self):
        if not self.authorized(): return
        self.rfile.read(int(self.headers.get('Content-Length', 0)))
        if self.path != '/music/' or self.headers.get('Depth') != '1':
            self.send_error(400); return
        xml = ('<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response>'
               '<d:href>/music/%E9%9B%A8%20music.wav</d:href><d:propstat><d:prop>'
               '<d:displayname>雨 music.wav</d:displayname><d:resourcetype/>'
               f'<d:getcontentlength>{len(audio)}</d:getcontentlength><d:getetag>"fixture"</d:getetag>'
               '</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>')
        self.send(207, xml.encode(), 'application/xml; charset=utf-8')
    def do_GET(self):
        if not self.authorized(): return
        if unquote(self.path) != '/music/雨 music.wav' or self.headers.get('If-Match') != '"fixture"':
            self.send_error(400); return
        self.send(200, audio, 'audio/wav')


server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
Path(args.port_file).write_text(str(server.server_port))
server.serve_forever()
