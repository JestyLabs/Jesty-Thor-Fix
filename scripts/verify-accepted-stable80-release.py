"""Validate the exact accepted release without signing or contacting a device."""
import argparse
import hashlib
import json
import re
from pathlib import Path

REPOSITORY = 'JestyLabs/Jesty-Thor-Fix'
ASSET = 'Jesty-Thor-Fix-1.7.2.apk'
SHA256 = 'c5c1bdfba2474ddfbaa8b0247c7d7bd68d516ba16e75f000a2dde7dae2be15e1'


def verify(release, runs, apk_digest, apk_bytes, commit):
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Expected an exact integration commit')
    if release.get('draft') is not True or release.get('prerelease') is not False:
        raise ValueError('Expected an unpublished stable draft')
    if (release.get('tag_name') != 'v1.7.2'
            or release.get('name') != 'Jesty Thor Fix v1.7.2'
            or release.get('target_commitish') != commit):
        raise ValueError('Draft identity or source commit mismatch')
    body = release.get('body') or ''
    if ('https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84' not in body
            or 'not a demonstrated blackout fix' not in body):
        raise ValueError('Missing known-issue disclosure')
    assets = release.get('assets', [])
    if len(assets) != 1 or assets[0].get('name') != ASSET:
        raise ValueError('Expected only the accepted stable APK; no recovery assets')
    asset = assets[0]
    if type(asset.get('id')) is not int or asset['id'] <= 0:
        raise ValueError('Invalid immutable asset ID')
    if asset.get('state') != 'uploaded' or asset.get('size') != apk_bytes:
        raise ValueError('Incomplete asset or size mismatch')
    if apk_digest.lower() != SHA256:
        raise ValueError('APK differs from the physically accepted signed bytes')
    if asset.get('digest') not in (None, 'sha256:' + SHA256):
        raise ValueError('Server asset digest mismatch')
    for path in ('.github/workflows/host-tests.yml', '.github/workflows/codeql.yml'):
        if not any(
                run.get('path') == path
                and run.get('head_sha') == commit
                and run.get('head_branch') == 'main'
                and run.get('event') == 'push'
                and run.get('status') == 'completed'
                and run.get('conclusion') == 'success'
                and run.get('repository', {}).get('full_name') == REPOSITORY
                for run in runs.get('workflow_runs', [])):
            raise ValueError('Missing successful exact-main CI/CodeQL: ' + path)
    return asset['id']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', type=Path, required=True)
    parser.add_argument('--runs', type=Path, required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--commit', required=True)
    args = parser.parse_args()
    asset_id = verify(json.loads(args.release.read_text(encoding='utf-8-sig')),
                      json.loads(args.runs.read_text(encoding='utf-8-sig')),
                      hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                      args.apk.stat().st_size, args.commit)
    print('Verified accepted stable80 asset ID:', asset_id)


if __name__ == '__main__':
    main()
