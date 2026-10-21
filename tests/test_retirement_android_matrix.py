"""Offline completeness checks for the actual Android report validator; no ADB."""
from pathlib import Path
import importlib.util

spec = importlib.util.spec_from_file_location(
    'retirement_android_matrix', Path(__file__).resolve().parents[1] / 'scripts/test-retirement-android.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)

def rejected(call, reason):
    try:
        call()
    except ValueError:
        return
    raise AssertionError(reason)

origins = [{'predecessor': version} for version in driver.VERSIONS]
driver.validate_origins(origins)
rejected(lambda: driver.validate_origins(origins[:-1]), 'missing frozen origin accepted')
rejected(lambda: driver.validate_origins(origins[:-1] + origins[:1]), 'duplicate frozen origin accepted')
cases = [dict(purpose=purpose, fixture_origin=origin, support=support, mode=mode, result='PASS')
         for purpose, origin, support, mode in sorted(driver.expected_cases())]
assert len(cases) == driver.EXPECTED_CASE_COUNT
driver.validate_results(cases)
rejected(lambda: driver.validate_results(cases[:-1]), 'missing case accepted')
rejected(lambda: driver.validate_results(cases[:-1] + cases[:1]), 'duplicate replacing case accepted')
rejected(lambda: driver.validate_results(cases + cases[:1]), 'extra duplicate accepted')
foreign = [dict(case) for case in cases]
foreign[0]['fixture_origin'] = 'unknown'
rejected(lambda: driver.validate_results(foreign), 'foreign case accepted')
failed = [dict(case) for case in cases]
failed[0]['result'] = 'FAIL'
rejected(lambda: driver.validate_results(failed), 'failed case accepted as PASS')
print('PASS retirement Android matrix contract: complete unique cases and origins; seven invalid reports rejected; no ADB')
