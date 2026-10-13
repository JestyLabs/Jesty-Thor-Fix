"""Non-forwarding loopback TLS/HTTP fixture. Never resolves or contacts target hosts."""
from pathlib import Path
import os
import shutil
import socketserver
import ssl
import subprocess
import threading


class TransportServer:
    def __init__(self, output, apk):
        self.output = Path(output)
        self.output.mkdir(parents=True, exist_ok=True)
        self.payload = Path(apk).read_bytes()
        self.mode = 'normal'
        self.events = []
        self.lock = threading.Lock()
        self.stop = threading.Event()
        openssl = shutil.which('openssl')
        if not openssl and os.name == 'nt':
            openssl = r'C:\Program Files\Git\usr\bin\openssl.exe'
        if not openssl or not Path(openssl).is_file():
            raise RuntimeError('OpenSSL is required for the disposable TLS fixture')
        def context(name):
            certificate = self.output / (name + '.pem')
            key = self.output / (name + '.key')
            if certificate.exists() or key.exists():
                raise RuntimeError('TLS fixture requires a fresh private output directory')
            subprocess.run([openssl, 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '2',
                            '-keyout', str(key), '-out', str(certificate), '-subj', '/CN=Local Transport Fixture',
                            '-addext', 'subjectAltName=DNS:github.com,DNS:assets.invalid',
                            '-addext', 'basicConstraints=critical,CA:TRUE'],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            tls.load_cert_chain(certificate, key)
            return certificate, tls
        self.certificate, self.tls = context('transport-ca')
        _, self.untrusted_tls = context('untrusted-ca')
        fixture = self

        class Server(socketserver.ThreadingTCPServer):
            allow_reuse_address = False
            daemon_threads = True

        class Handler(socketserver.BaseRequestHandler):
            def handle(self):
                connection = self.request
                connection.settimeout(10)
                mode = fixture.mode
                try:
                    initial = fixture.read_headers(connection)
                    first = initial.split(b'\r\n', 1)[0].decode('ascii')
                    mode = fixture.mode
                    if first.startswith('CONNECT '):
                        authority = first.split()[1]
                        fixture.event(mode, 'connect', authority)
                        # This is a terminator, not a tunnel or a forwarding proxy.
                        if authority not in ('github.com:443', 'assets.invalid:443', 'mismatch.invalid:443'):
                            connection.sendall(b'HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n')
                            return
                        connection.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')
                        tls = fixture.untrusted_tls if mode == 'untrusted_cert' else fixture.tls
                        connection = tls.wrap_socket(connection, server_side=True)
                        request = fixture.read_headers(connection)
                        fixture.event(mode, 'https', request.split(b'\r\n', 1)[0].decode('ascii'))
                        fixture.respond(connection, request, mode)
                    else:
                        fixture.event(mode, 'plaintext', first)
                        connection.sendall(b'HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n')
                except (OSError, ssl.SSLError) as failure:
                    fixture.event(mode, 'connection_closed', type(failure).__name__)
                finally:
                    connection.close()

        self.server = Server(('127.0.0.1', 0), Handler)
        self.port = self.server.server_address[1]
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    @staticmethod
    def read_headers(connection):
        data = bytearray()
        while not data.endswith(b'\r\n\r\n'):
            part = connection.recv(1)
            if not part: raise OSError('Connection closed before complete headers')
            data.extend(part)
            if len(data) > 16384: raise OSError('Fixture header limit exceeded')
        return bytes(data)

    def event(self, mode, kind, detail):
        with self.lock: self.events.append({'mode': mode, 'kind': kind, 'detail': detail})

    def observed(self, mode):
        with self.lock: return [dict(e) for e in self.events if e['mode'] == mode]

    def respond(self, connection, request, mode):
        request_path = request.split(b'\r\n', 1)[0].split()[1].decode('ascii')
        data = self.payload
        def headers(status='200 OK', extra=''):
            connection.sendall(('HTTP/1.1 ' + status + '\r\nConnection: close\r\n' + extra + '\r\n').encode('ascii'))
        if mode == 'header_timeout':
            self.stop.wait(40)
        elif mode in ('redirect_https', 'redirect_bad_hostname') and request_path != '/candidate.apk':
            host = 'assets.invalid' if mode == 'redirect_https' else 'mismatch.invalid'
            headers('302 Found', 'Content-Length: 0\r\nLocation: https://' + host + '/candidate.apk\r\n')
        elif mode == 'redirect_http':
            headers('302 Found', 'Content-Length: 0\r\nLocation: http://plaintext.invalid/candidate.apk\r\n')
        elif mode == 'redirect_loop':
            headers('302 Found', 'Content-Length: 0\r\nLocation: https://github.com/loop\r\n')
        elif mode == 'status_503':
            headers('503 Unavailable', 'Content-Length: 0\r\n')
        elif mode == 'header_size':
            headers(extra='Content-Length: ' + str(len(data) + 10) + '\r\n')
        elif mode in ('oversize', 'chunked_short', 'chunked_ok'):
            headers(extra='Transfer-Encoding: chunked\r\n')
            body = data + b'X' if mode == 'oversize' else (data[:1024] if mode == 'chunked_short' else data)
            connection.sendall(('%x\r\n' % len(body)).encode('ascii') + body + b'\r\n0\r\n\r\n')
        elif mode == 'truncated':
            headers(extra='Content-Length: ' + str(len(data)) + '\r\n')
            connection.sendall(data[:1024])
        elif mode == 'body_timeout':
            headers(extra='Content-Length: ' + str(len(data)) + '\r\n')
            connection.sendall(data[:1024])
            self.stop.wait(40)
        else:
            headers(extra='Content-Length: ' + str(len(data)) + '\r\n')
            connection.sendall(data)

    def close(self):
        self.stop.set()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)
