"""Offline rejection tests for exact-artifact publication authorization."""
import copy
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    'accepted_release', Path(__file__).with_name('verify-accepted-stable80-release.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
commit = 'a' * 40
release = dict(draft=True, prerelease=False, tag_name='v1.7.2',
               name='Jesty Thor Fix v1.7.2', target_commitish=commit,
               body='https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84; '
                    'not a demonstrated blackout fix',
               assets=[dict(id=123, name=module.ASSET, state='uploaded', size=456,
                            digest='sha256:' + module.SHA256)])
runs = dict(workflow_runs=[
    dict(path=path, head_sha=commit, head_branch='main', event='push',
         status='completed', conclusion='success',
         repository=dict(full_name=module.REPOSITORY))
    for path in ['.github/workflows/host-tests.yml', '.github/workflows/codeql.yml']])
assert module.verify(release, runs, module.SHA256, 456, commit) == 123
cases = [
    ('published', lambda r, c: r.update(draft=False)),
    ('prerelease', lambda r, c: r.update(prerelease=True)),
    ('other-version', lambda r, c: r.update(tag_name='v1.7.3')),
    ('other-title', lambda r, c: r.update(name='Recovery')),
    ('moving-target', lambda r, c: r.update(target_commitish='main')),
    ('missing-disclosure', lambda r, c: r.update(body='Release')),
    ('additional-recovery', lambda r, c: r['assets'].append(dict(name='recovery.apk'))),
    ('missing-asset', lambda r, c: r.update(assets=[])),
    ('other-asset', lambda r, c: r['assets'][0].update(name='return.apk')),
    ('invalid-id', lambda r, c: r['assets'][0].update(id=True)),
    ('incomplete-upload', lambda r, c: r['assets'][0].update(state='new')),
    ('different-size', lambda r, c: r['assets'][0].update(size=457)),
    ('server-digest', lambda r, c: r['assets'][0].update(digest='sha256:' + '0' * 64)),
    ('no-main-checks', lambda r, c: c.update(workflow_runs=[])),
]
for field, value in [('head_sha', 'b' * 40), ('head_branch', 'other'),
                     ('event', 'workflow_dispatch'), ('status', 'in_progress'),
                     ('conclusion', 'failure'), ('repository', {'full_name': 'Other/Repo'})]:
    cases.append(('wrong-ci-' + field,
                  lambda r, c, f=field, v=value: c['workflow_runs'][0].update({f: v})))
for name, mutate in cases:
    r, c = copy.deepcopy(release), copy.deepcopy(runs)
    mutate(r, c)
    try:
        module.verify(r, c, module.SHA256, 456, commit)
    except ValueError:
        continue
    raise AssertionError('Accepted invalid publication: ' + name)
for digest, sha in [('0' * 64, commit), (module.SHA256, 'main')]:
    try:
        module.verify(release, runs, digest, 456, sha)
    except ValueError:
        continue
    raise AssertionError('Accepted changed bytes or moving source')
print('Accepted stable80 release: 1 valid case and', len(cases) + 2,
      'publication rejection cases passed (offline, no release).')
