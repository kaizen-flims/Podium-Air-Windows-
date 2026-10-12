"""Local protocol fixture for the real packaged OAuth client, never a live Google account."""
import argparse
import base64
import hashlib
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlparse

p = argparse.ArgumentParser()
p.add_argument('--port-file', required=True)
p.add_argument('--client-file', required=True)
args = p.parse_args()
READ = 'https://www.googleapis.com/auth/youtube.readonly'
client_id = 'fixture-client.apps.googleusercontent.com'
challenge = redirect = None

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, body, code=200):
        data = json.dumps(body, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        global challenge, redirect
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == '/authorize':
            assert query['client_id'] == [client_id]
            assert query['code_challenge_method'] == ['S256']
            assert READ in query['scope'][0].split()
            assert 'client_secret' not in query and 'code_verifier' not in query
            challenge, redirect = query['code_challenge'][0], query['redirect_uri'][0]
            assert urlparse(redirect).hostname == '127.0.0.1'
            self.send_response(302)
            self.send_header('Location', redirect + '?' + urlencode({'code': 'fixture-code', 'state': query['state'][0]}))
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        assert self.headers.get('Authorization') == 'Bearer fixture-access'
        assert 'access_token' not in query
        if parsed.path == '/userinfo':
            self.reply({'sub': 'fixture-subject', 'name': 'CI fixture account', 'email': 'fixture@example.invalid', 'email_verified': True})
        elif parsed.path == '/youtube/v3/playlists':
            assert query['mine'] == ['true']
            index = 2 if query.get('pageToken') == ['next-playlist'] else 1
            body = {'items': [{'id': 'PLfixture' + str(index), 'snippet': {'title': 'Fixture playlist ' + str(index) + ' · বাংলা'}, 'contentDetails': {'itemCount': 3}}]}
            if index == 1:
                body['nextPageToken'] = 'next-playlist'
            self.reply(body)
        elif parsed.path == '/youtube/v3/playlistItems':
            assert query['playlistId'] == ['PLfixture1']
            positions = [2] if query.get('pageToken') == ['next-item'] else [1, 0]
            body = {'items': [{'snippet': {'title': 'Fixture video ' + str(i), 'videoOwnerChannelTitle': 'Fixture artist', 'position': i, 'resourceId': {'videoId': 'fixture000' + str(i)}}} for i in positions]}
            if positions != [2]:
                body['nextPageToken'] = 'next-item'
            self.reply(body)
        else:
            self.reply({}, 404)

    def do_POST(self):
        form = parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode())
        if self.path == '/token':
            assert form['client_id'] == [client_id]
            if form['grant_type'] == ['authorization_code']:
                assert form['code'] == ['fixture-code'] and form['redirect_uri'] == [redirect]
                calculated = base64.urlsafe_b64encode(hashlib.sha256(form['code_verifier'][0].encode()).digest()).decode().rstrip('=')
                assert calculated == challenge
            else:
                assert form['grant_type'] == ['refresh_token'] and form['refresh_token'] == ['fixture-refresh']
            self.reply({'access_token': 'fixture-access', 'refresh_token': 'fixture-refresh', 'expires_in': 3600, 'token_type': 'Bearer', 'scope': 'openid email profile ' + READ})
        elif self.path == '/revoke':
            assert form['token'] == ['fixture-refresh']
            self.reply({})
        else:
            self.reply({}, 404)

server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
Path(args.client_file).parent.mkdir(parents=True, exist_ok=True)
Path(args.client_file).write_text(json.dumps({'installed': {'client_id': client_id, 'client_secret': 'fixture-secret', 'auth_uri': 'https://accounts.google.com/o/oauth2/auth', 'token_uri': 'https://oauth2.googleapis.com/token'}}))
Path(args.port_file).write_text(str(server.server_port))
server.serve_forever()
