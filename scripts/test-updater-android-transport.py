#!/usr/bin/env python3
"""Actual Android HTTPS/download verification against a non-forwarding TLS fixture."""
import argparse
import importlib.util
import json
from pathlib import Path
import time
import sys

sys.dont_write_bytecode = True


def module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


ROOT = Path(__file__).resolve().parents[1]
native = module(ROOT / 'scripts/test-updater-android-e2e.py', 'updater_native')
server = module(ROOT / 'tests/android/updater-e2e/transport_server.py', 'transport_fixture')


class TransportSuite(native.Suite):
    def run(self):
        transport = server.TransportServer(self.output / 'private-tls', self.args.build / 'candidate.apk')
        try:
            for name, mode, message in (
                    ('tls-truncated-body', 'truncated', None),
                    ('tls-short-chunked-body', 'chunked_short', 'download is incomplete'),
                    ('tls-content-length-mismatch', 'header_size', 'download size does not match'),
                    ('tls-stream-exceeds-size', 'oversize', 'download is too large'),
                    ('tls-status-failure', 'status_503', 'HTTP 503'),
                    ('tls-read-timeout', 'body_timeout', 'SocketTimeoutException'),
                    ('tls-response-header-timeout', 'header_timeout', 'SocketTimeoutException'),
                    ('tls-http-redirect-rejected', 'redirect_http', None),
                    ('tls-redirect-loop-bounded', 'redirect_loop', None),
                    ('tls-redirect-hostname-rejected', 'redirect_bad_hostname', None)):
                def failure(mode=mode, message=message):
                    transport.mode = mode
                    self.fresh(transport=transport)
                    began = time.monotonic()
                    state = self.settled(timeout=65)
                    assert state['version'] == '1.0.0' and state['launches'] == 0 and not state['downloadExists']
                    logs = self.call('logcat', '-d', '-s', 'ThorDisplayUpdate:V', '*:S')
                    assert 'update failed' in logs
                    if message: assert message in logs
                    if mode in ('body_timeout', 'header_timeout'): assert time.monotonic() - began >= 25
                    observed = transport.observed(mode)
                    assert any(e['kind'] == 'connect' for e in observed)
                    assert any(e['kind'] == 'https' for e in observed)
                    assert not any(e['kind'] == 'plaintext' for e in observed)
                    if mode == 'redirect_loop':
                        assert 2 <= sum(e['kind'] == 'https' for e in observed) <= 25
                    self.recover(transport, mode)
                self.case(name, failure)

            def untrusted_certificate():
                transport.mode = 'untrusted_cert'
                self.fresh(transport=transport)
                state = self.settled()
                assert state['version'] == '1.0.0' and state['launches'] == 0 and not state['downloadExists']
                assert 'SSLHandshakeException' in self.call('logcat', '-d', '-s', 'ThorDisplayUpdate:V', '*:S')
                observed = transport.observed('untrusted_cert')
                assert any(e['kind'] == 'connect' for e in observed) and not any(e['kind'] == 'https' for e in observed)
                self.recover(transport, 'untrusted_cert')
            self.case('tls-untrusted-certificate-rejected', untrusted_certificate)

            def success():
                transport.mode = 'redirect_https'
                self.fresh(transport=transport); self.installer()
                observed = transport.observed('redirect_https')
                assert any(e['kind'] == 'connect' and e['detail'] == 'assets.invalid:443' for e in observed)
                assert sum(e['kind'] == 'https' for e in observed) == 2
                assert self.state()['nativeSessions'] == 1
                self.click('Cancel', 'com.android.packageinstaller'); self.settled()
            self.case('tls-https-redirect-native-consent', success)
            def chunked_success():
                transport.mode = 'chunked_ok'
                self.fresh(transport=transport); self.installer()
                assert self.state()['nativeSessions'] == 1
                self.click('Cancel', 'com.android.packageinstaller'); self.settled()
            self.case('tls-unknown-length-native-consent', chunked_success)
            if self.args.case and set(self.args.case) != {r['case'] for r in self.results}:
                raise AssertionError('Requested transport case was not executed')
        finally:
            transport.close()
            # Owner-local request evidence; never upload it as public CI material.
            (self.output / 'transport-events.json').write_text(json.dumps(transport.events, indent=2), encoding='utf-8')

    def recover(self, transport, failed_mode):
        before = self.state()
        transport.mode = 'recovery-' + failed_mode
        self.command('update', scenario='normal')
        self.click('Update', native.PACKAGE); self.click('Download', native.PACKAGE)
        self.installer()
        retry = self.state()
        assert retry['pid'] == before['pid'] and retry['reservations'] == before['reservations'] + 1
        assert retry['nativeSessions'] == 1 and retry['launches'] == 1
        assert any(e['kind'] == 'https' for e in transport.observed(transport.mode))
        self.click('Cancel', 'com.android.packageinstaller'); self.settled()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--avd', required=True)
    parser.add_argument('--adb-port', type=int, default=5037)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--case', action='append')
    TransportSuite(parser.parse_args()).run()


if __name__ == '__main__': main()
