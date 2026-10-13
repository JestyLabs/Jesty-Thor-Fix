#!/usr/bin/env python3
"""Verify fixture HTTP framing and TLS shutdown with a strict real TLS client."""
import argparse
import importlib.util
import json
from pathlib import Path
import socket
import ssl
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('transport_fixture', ROOT / 'tests/android/updater-e2e/transport_server.py')
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)

def exchange(server, mode):
    server.mode = mode
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.load_verify_locations(server.certificate)
    with socket.create_connection(('127.0.0.1', server.port), timeout=5) as raw:
        raw.sendall(b'CONNECT github.com:443 HTTP/1.1\r\nHost: github.com:443\r\n\r\n')
        assert fixture.TransportServer.read_headers(raw).startswith(b'HTTP/1.1 200')
        with context.wrap_socket(raw, server_hostname='github.com', suppress_ragged_eofs=False) as client:
            client.sendall(b'GET /candidate.apk HTTP/1.1\r\nHost: github.com\r\nConnection: close\r\n\r\n')
            headers = fixture.TransportServer.read_headers(client)
            body = bytearray()
            try:
                while True:
                    part = client.recv(65536)
                    if not part: break
                    body.extend(part)
            except ssl.SSLEOFError:
                if mode != 'truncated': raise
                return headers, bytes(body), 'abrupt'
            client.unwrap().close()
            return headers, bytes(body), 'notified'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.is_relative_to(ROOT): raise RuntimeError('TLS evidence and keys must stay outside Git')
    if (output / 'results.json').exists(): raise RuntimeError('Use a fresh output directory')
    output.mkdir(parents=True, exist_ok=True)
    payload = bytes(range(256)) * 337 + b'fixture-boundary'
    apk = output / 'payload.bin'; apk.write_bytes(payload)
    server = fixture.TransportServer(output / 'private-tls', apk)
    results = []
    try:
        for mode in ('normal', 'chunked_ok', 'status_503', 'redirect_http', 'truncated'):
            try:
                headers, body, closed = exchange(server, mode)
                if mode == 'normal':
                    assert b'200 OK' in headers and body == payload and closed == 'notified'
                elif mode == 'chunked_ok':
                    expected = ('%x\r\n' % len(payload)).encode('ascii') + payload + b'\r\n0\r\n\r\n'
                    assert b'Transfer-Encoding: chunked' in headers and body == expected and closed == 'notified'
                elif mode == 'status_503':
                    assert b'503' in headers and b'Content-Length: 0' in headers and not body and closed == 'notified'
                elif mode == 'redirect_http':
                    assert b'302' in headers and b'Location: http://' in headers and not body and closed == 'notified'
                else:
                    assert body == payload[:1024] and closed == 'abrupt'
                    assert ('Content-Length: ' + str(len(payload))).encode('ascii') in headers
                results.append(dict(case='tls-shutdown-' + mode, result='PASS'))
                print('PASS: tls-shutdown-' + mode, flush=True)
            except Exception as error:
                results.append(dict(case='tls-shutdown-' + mode, result='FAIL', error=type(error).__name__ + ': ' + str(error)))
                print('FAIL: tls-shutdown-' + mode + ': ' + type(error).__name__, flush=True)
    finally:
        server.close()
        (output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
        (output / 'events.json').write_text(json.dumps(server.events, indent=2), encoding='utf-8')
    if any(result['result'] != 'PASS' for result in results): return 1
    print('TLS shutdown/framing: 5/5 PASS', flush=True)
    return 0

if __name__ == '__main__': sys.exit(main())
